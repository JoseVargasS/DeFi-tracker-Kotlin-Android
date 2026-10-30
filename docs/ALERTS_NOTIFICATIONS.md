# Alerts: price notifications, channels and sound

Audience: agents modifying `alerts/*`, `PriceAlertSheet.kt` or anything that posts a
notification. Read this before touching the sound flow — it is small, it has burned us
before, and a wrong change makes alerts **silent** with no visible error.

State: reflects `main` at `ce22db7`. No uncommitted changes in this area.

> Rules live in `AGENTS.md`. This document explains them: why each one exists and how to
> diagnose the failure mode it prevents. If the two disagree, `AGENTS.md` wins.

---

## 1. Flow

```
PriceAlertSheet (picker)             -> onSoundPicked(uri) -> viewModel.setPriceSoundUri
IndicatorPrefsRepository / DivMonitorPrefs  "price_sound" (raw string, "" = default)
DivScanService (Dispatchers.IO)      -> notifier.ensurePriceChannel(soundUri)
                                     -> notifier.notifyPriceAlert(..., soundUri)
DivAlertNotifier                     -> channel id per tone -> NotificationCompat
SystemUI                             -> resolves and plays the channel sound
```

Key files:

| File | Responsibility |
|---|---|
| `alerts/DivAlertNotifier.kt` | channels, `resolveSound`, notification builders (`post`, divergence, price, signal) |
| `alerts/DivMonitorPrefs.kt` | DataStore for monitor config + `price_sound` |
| `alerts/DivScanService.kt` | foreground service, scan loop, 5-minute throttle per price alert |
| `presentation/crypto_detail/PriceAlertSheet.kt` | alert UI + system ringtone picker |

---

## 2. One channel per tone (do not go back to a fixed id)

`priceChannelIdFor(soundUri)`:

```kotlin
if (soundUri.isBlank())  return CHANNEL_PRICE                       // "price_alerts"
val h = resolveSound(soundUri).toString().hashCode()
return "${CHANNEL_PRICE}_${abs(h)}"                                 // "price_alerts_<hash>"
```

Why: on MIUI/HyperOS, deleting and recreating the *same* channel id restores the old sound.
A fresh id per tone makes the picked tone actually play. That was the fix for "el sonido que
elijo no agarra" and it lives in `f59575d` (not part of the incremental-indicator work).

`ensurePriceChannel` also deletes every other `price_alerts*` channel, so Settings does not
accumulate tones. Notification sound is fixed **at channel creation**: changing the tone
means a new channel, never an update.

Rules:

- Keep `priceChannelIdFor` derived from the **resolved** URI so id and sound always match.
- Keep the old-channel cleanup.
- Do not add a fixed `CHANNEL_PRICE`-only fallback that bypasses the hash for known tones.

---

## 3. Never probe the sound URI from the app

**Rule: pass the URI to the channel and let the system play it. Do not open it to validate.**

Reasons, learned the hard way:

- The app declares only `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC` — **no media read permission**.
- The notification sound is resolved and played by **SystemUI**, which does have access.
- So `contentResolver.openAssetFileDescriptor(uri, "r")` from our process can fail on a
  perfectly valid tone (for example a user file in `content://media/external/...`) and a
  "validate then fall back" check silently downgrades it to the default sound — exactly the
  bug users report as "no suena el tono que elegí".

`resolveSound` must stay purely syntactic (blank / unparseable / no scheme → default):

```kotlin
private fun resolveSound(soundUri: String): Uri {
    if (soundUri.isBlank()) return Settings.System.DEFAULT_NOTIFICATION_URI
    // parse; if EMPTY or without scheme -> DEFAULT_NOTIFICATION_URI
}
```

Consequence to accept: if the stored tone points to nothing, the alert can be silent. That
is the pre-existing behaviour and it is not worth reintroducing an in-app probe for it.

---

## 4. Channels and constants

| Constant | Value | Notes |
|---|---|---|
| `CHANNEL_ALERTS` | `div_alerts` | divergences / signals, `IMPORTANCE_HIGH`, system default sound |
| `CHANNEL_PRICE` | `price_alerts` | price alerts with the default tone |
| `CHANNEL_PRICE + "_" + hash` | `price_alerts_<hash>` | price alerts with a picked tone |
| `CHANNEL_SERVICE` | `div_monitor_min` | foreground service, `IMPORTANCE_MIN`, silent |
| `CHANNEL_SERVICE_LEGACY` | `div_monitor` | deleted on purpose (legacy) |
| `SERVICE_NOTI_ID` | `7701` | foreground notification id |

- Price alerts use `alertId.hashCode()` as notification id so the same alert updates instead
  of stacking.
- Notification sound for price alerts is set on the channel (`setSound` + `USAGE_NOTIFICATION`
  / `CONTENT_TYPE_SONIFICATION`). Pre-O has no channels: the sound goes on the notification
  itself and only when `soundUri` is not blank.
- The 5-minute repeat throttle lives in `DivScanService`, not in the notifier.

---

## 5. Known noise that is NOT ours

`logcat` shows, periodically:

```
W MediaPlayer: java.lang.NullPointerException: uri
    at android.content.ContentResolver.openAssetFileDescriptor(...)
```

This comes from **MIUI/HyperOS** (`com.android.systemui`, `system_server`), right after
`AudioService: createActualUriForNatureSound ... mTimeZone=Evening` — its hour-of-day
"nature sound" for notifications. The app never uses `MediaPlayer` and its own process logs
nothing about audio. Do not chase it, do not add defensive code for it.

---

## 6. Checklist when a price alert is silent

1. Settings → Notifications → Octopus → "Alertas de precio": does a channel with the picked
   tone exist? (MIUI sometimes keeps the old channel cached.)
2. `adb shell dumpsys notification --noredact` → look for `price_alerts_<hash>` and its
   `mSound=` value; compare with the tone the user expects.
3. Is the channel `mDeleted=true`? The cleanup deletes other tone channels on purpose.
4. Divergence notifications (`div_alerts`) use the system default sound by design; only
   price alerts carry the picked tone.
5. Do not "fix" it by opening the URI from the app (§3).

---

## 7. Do not touch casually

- `priceChannelIdFor` and the channel cleanup (the tone-per-channel mechanism).
- `resolveSound` semantics (syntactic only, §3).
- Notification ids (`alertId.hashCode()`, `SERVICE_NOTI_ID`): changing them duplicates or
  hides notifications.
- `POST_NOTIFICATIONS` permission check in `post()`: without it nothing is posted at all.
- The foreground service type (`FOREGROUND_SERVICE_DATA_SYNC`) and its channel importance.
