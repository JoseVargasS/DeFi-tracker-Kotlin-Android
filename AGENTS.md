# AGENTS.md

Guidance for coding agents working on this Android project.

## Project Identity

This repo is an Android app named Octopus / DeFi Tracker. It tracks crypto pairs, charts, wallets, balances, and transaction history.

Primary package: `com.defitracker.app` — main module: `app`.

## Documentation Map

**`AGENTS.md` is authoritative for rules.** The docs explain *why*: the measurements
behind each rule, the file maps, the recipes and the step-by-step checklists. If a doc
and this file disagree, this file wins and the doc must be fixed in the same change.

Do not move a *rule* out of this file. Rationale, measurements, long tables and
step-by-step checklists belong in `docs/`.

| You are touching | Read first |
|---|---|
| the price chart, `chart/*`, `CryptoDetailScreen.kt`'s `PriceChart` block, `CryptoDetailViewModel`'s chart code, indicators, overlays, the measure tool | `docs/CHART_PIPELINE.md` |
| an endpoint, a cadence, a cache, a socket, the repository, the Pairs screen price flow | `docs/DATA_SOURCES.md` |
| price alerts, notification channels, sounds, logcat triage | `docs/ALERTS_NOTIFICATIONS.md` |
| colors, typography, spacing, chart hand-tuned values (px vs dp) | `docs/DESIGN.md` |
| building, installing on a device, screenshots, counters, probes | `docs/DEVELOPMENT.md` |

Read the matching doc before editing those areas; update it in the same change when a
contract changes.

## Ground Rules

- Preserve existing working behavior unless the user explicitly asks to change it.
- Keep edits scoped. Do not do broad refactors while fixing a screen or API bug.
- Prefer existing patterns: Compose screens + ViewModels + `CryptoRepository`.
- Do not change font family or numeric legibility unless explicitly requested.
- Avoid hard-coded UI sizes that break narrow phones.
- Keep dark trading-app visual style consistent.
- Remove code with no references. If intentionally retained, add a nearby comment in the form `KEEP: reason` explaining why it must stay.
- Surface API errors clearly. Do not convert auth/rate-limit errors into empty UI states.
- Never write the word "ponytail" inside code comments in any file.

## Important Commands

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:testDebugUnitTest --console=plain   # JVM unit tests
.\gradlew.bat :app:assembleDebug --console=plain       # debug APK
./r -Device <adb-serial>                                # build + install + launch
```

The Gradle wrapper is versioned in the repository. Use `./gradlew` on Unix-like shells.
If the wrapper cannot run, use Android Studio or an installed Gradle with JDK 17.

Report the **real per-class test counts** (read `app/build/test-results/testDebugUnitTest`),
not just "BUILD SUCCESSFUL". Build, device and measurement recipes: `docs/DEVELOPMENT.md`.

## Architecture

- `data/local`: Room entities and DAOs.
- `data/remote`: Retrofit API interfaces, WebSocket streams, and DTOs; TradingView lives under `data/remote/tv`.
- `data/repository`: concrete repository implementation.
- `domain/model`: app/domain UI models. There is currently no repository-contract file under `domain/repository`.
- `presentation/*`: Compose screens and ViewModels.
- `di`: Hilt module.
- `ui/theme`: colors/theme, including the shared `ChartColors` palette.
- `core/Timeframes.kt`: single catalog for chart labels, durations, Binance/MEXC/TradingView mappings, alert intervals, MA timeframes, and chart aggregation rules.
- `core/Brokers.kt`: single catalog for the `source` strings (`Bitunix`, `BingX`, `Binance`, `MEXC`, `TV`), the search chip order, futures/spot classification and source labels. Do not write the source strings by hand elsewhere.
- `presentation/crypto_detail`: chart screen, chart ViewModel, pure indicator calculations, chart preferences, and per-symbol overlays.

Navigation: `app/src/main/java/com/defitracker/app/MainActivity.kt`.
Repository contract: `app/src/main/java/com/defitracker/app/domain/repository/CryptoRepository.kt`.
Repository implementation: `app/src/main/java/com/defitracker/app/data/repository/CryptoRepositoryImpl.kt`.

Which file owns which chart responsibility: `docs/CHART_PIPELINE.md` §9.

## API Behavior

- Binance powers symbol discovery, 24h stats, prices, and klines.
- Bitunix and BingX power the same for their USDⓈ-M futures (USDT pairs only). Their symbols differ: `BTCUSDT` (Bitunix) vs `BTC-USDT` (BingX).
- **Every source must hand the app klines in ascending time order.** Binance and MEXC already return them oldest-first inside each page; Bitunix and BingX return them newest-first, so the repository sorts them (`ascendingRows()` in `FuturesKlineMapping.kt`). Reversing the *page* order is not enough for those two: it leaves the list non-chronological, which reverses the chart's time axis, saturates the SMC and makes the tail sync append duplicate candles (the chart then jumps left on every refresh).
- **Bitunix publishes closed candles only**, so the in-progress bucket is synthesized in the ViewModel (`withLiveCandle`) after the load and after every tail merge. Without it the tick treats the last candle as closed, runs `syncTail` every 2 s and forces a full reload every third pass: the chart reloads by itself and the last closed candle is overwritten with the live price. Never remove it, and never hand a source's list to the chart without the current bucket.
- The Bitunix bucket is **not** flat: it is built from the WS `trade` channel (`BitunixLiveBuckets`, in the repository), so the candle in progress is drawn with its real O/H/L/C/V. The subscribe message carries **two args per symbol** (`ticker` + `trade`) and the socket needs a plain text `PING` every 20 s or the exchange cuts it at ~61 s and the bucket loses trades. The bucket only applies when its start time is the last candle's time; the phase comes from `Timeframes.bucketStartMs` (epoch intraday, Monday for `1w`, month start UTC for `1mo`).
- `BinanceTickerStream`, `MexcTickerStream`, `BitunixTickerStream` and `BingxTickerStream` feed `CryptoRepositoryImpl.observeLivePrices()` with reconnecting WebSocket live prices. That flow is **shared** (one socket per exchange, four in total, for the whole app); never collect a cold copy of it.
- All four ticker sockets are **per-symbol**: they follow the **tracked pairs of that source** and reconnect when that set changes. Binance asks for them in one combined URL (`stream?streams=<sym>@miniTicker/...`), MEXC with one `sub.ticker` message per symbol. A pair that is not tracked has no live price (it falls back to REST), and a source with no tracked pairs does not open a socket at all.
- BingX's socket sends **gzip binary frames** and closes without a ping: `BingxTickerStream` decompresses with `GZIPInputStream` and pings every 4 s. Do not "simplify" either away.
- The detail screen takes the live price from that shared socket and falls back to REST when the socket is stale (>15 s without a tick). The price and the in-progress candle still update every 2 s in **every** timeframe; only the taker ratio has a per-timeframe cadence.
- `TradingViewClient` is a WebSocket client for the `TV` chart source, with timeframe resolution mapping and a short memo cache.
- `BinanceFuturesApi` provides unauthenticated taker buy/sell volume. The detail ViewModel aligns it to candle timestamps and exposes it as the optional `C/V Taker` subchart; it is not part of `ChartComputation`.
- The taker ratio comes from **Binance Futures for every source** (MEXC, Bitunix, BingX included), with the symbol normalized (`BTC_USDT` / `BTC-USDT` → `BTCUSDT`). None of those exchanges publishes taker buy/sell volume in its public API (checked 2026-11-12), so this is the one chart subchart that is not broker-native; the same is true for the Binance spot pairs.
- Retrofit's Binance, Binance Futures, CoinStats, MEXC, Bitunix and BingX clients share the named REST `OkHttpClient` from `AppModule`; the WebSocket client remains separate with its long-lived timeout and ping settings.
- Price list refresh should stay conservative. Avoid very short intervals or per-item busy loops.
- The Pairs screen's REST fallback is **selective**: it only asks for what the socket does not cover (TradingView pairs, pairs with no tick for >30 s, pairs still at `0.00`). A tracked-pair change refreshes **only the new pairs**, and a removal is **optimistic** (the pair leaves the UI at once and comes back only if the `DELETE` fails). Prices are applied to the **current** list, never to a captured copy, so a pair deleted mid-request does not come back.
- The chart's price and the in-progress candle update every 2 s in **every** timeframe. Do not slow that down per timeframe; only the taker ratio has a per-timeframe cadence.

Endpoints, sockets, caches and their costs: `docs/DATA_SOURCES.md`.

## File Size

- No new file should exceed approximately 600 lines. If a change would cross that limit, split it by responsibility first.
- Do not add new code to `CryptoDetailScreen.kt`; create a focused file instead. Its existing size is legacy debt, not a target for further growth.

## Chart Contracts

- The price `CombinedChart` is the horizontal viewport source of truth. Its touch matrix supplies `scaleX` and `translationX` to every visible subchart through `syncViewportFrom`; subcharts reset their Y matrix so their own scales do not alter the shared X frame.
- `alignViewportXWith` gives the price chart and subcharts the same content left/right bounds. Recalculate offsets only for a new dataset; doing it on every tick or gesture can move the panes mid-interaction.
- First mount or a new timeframe resets the initial zoom and fits Y to candles. Same-timeframe updates preserve the user's matrix; when the user is at the right edge, newly arriving candles shift the viewport to keep the live edge visible.
- `syncSubFrame` is the standard subchart update sequence: apply the time axis, perform initial zoom only when appropriate, copy the price viewport, store the render key, then align the shared X rectangle.
- Highlights originate in the price chart and `syncHighlights` propagates the selected candle X to StochRSI, RSI, MACD, and taker charts. Clearing the price highlight clears all subchart highlights.
- Always use `highlightXSafe` for subchart highlights. MPAndroidChart's `Highlight(x, dataSetIndex, stackIndex)` does **not** set `dataIndex` (it stays `-1`), so `CombinedData.getEntryForHighlight()` throws `IndexOutOfBoundsException`; the only valid way is `setDataIndex(...)` (see `chart/ChartHighlight.kt`). Calling `highlightValue(x, 0)` directly on a `CombinedChart` can produce `IndexOutOfBoundsException`. Keep its `try/catch` fallback that clears the highlight.
- Main-chart pan/zoom updates the shared matrix and immediately resynchronizes all subcharts. Horizontal drags that begin in a subchart are forwarded to the price chart; subcharts must never pan independently.
- All indicator and taker series must remain aligned to candle indices, and live ticks should update datasets in place where the current implementation supports it instead of recreating Android views.
- Every `ACTION_DOWN` must initialize the general gesture state before overlay hit-tests, so panning from a locked Fibonacci or drawing overlay starts at the current touch position without a jump.
- The drawn candle dataset must always be the state candles of the current interval. The `PriceChart` update block decides between rebuilding and patching in place using `viewportKey()` (symbol + interval + size + first/last candle time), `intervalChanged`, `tfSwitching` (loading a new TF while the state still holds the old candles) and `datasetStale` (drawn `entryCount != state.candles.size`). Do not reduce that gate to symbol+interval, and never patch in place while `tfSwitching`; `updateLastCandleInPlace` must keep its `ds.entryCount == state.candles.size` guard.
- `INITIAL_VISIBLE_CANDLES` (100) is the first-mount window. `applySyncAndInitialZoom` repositions inside `post { }`, so anything else that moves the matrix must not race with it.
- The last used interval is remembered in `IndicatorPrefsRepository.lastChartInterval` (in-memory, written in `loadChartData`) and used when the nav argument is empty: leaving the detail screen and coming back must keep the timeframe.
- The measure tool lives by candle index: it survives a new candle and dies when the window rolls, the pair changes or the TF changes. `measureContextKey` (`chart/MeasureContext.kt`) is that contract; do not key it on `viewportKey()`.
- Mitigated SMC zones stay **hidden** (explicit user requirement).

Why each of these exists, and how to verify it: `docs/CHART_PIPELINE.md` §2–§3, §7.

## Shared presentation sources of truth

- `core/Timeframes.kt` is the only catalog for chart intervals, durations, exchange mappings, MA timeframes, alert settings, taker support, and chart aggregation. Do not add another local timeframe list.
- `ui/theme/Color.kt` contains `ChartColors`, including Compose colors, alpha variants, and hex values used by chart rendering and persisted overlay defaults. Do not add new chart color literals elsewhere.
- `presentation/crypto_detail/PriceFormat.kt` owns the chart, Pulse, repository, axis-price, and compact-volume policies. Keep the named policies separate when their output contracts differ, but do not duplicate their implementations.

## Incremental indicator caches

- Two layers: `ClosedCandleCache<T>` (single value, today the SMC) and `IndicatorTail` (series tails for BB / RSI / RSI-MA / StochRSI / MACD / chart-TF MAs).
- `closedCandleVersion` must be bumped in `updatePrefs`, `loadChartData`, `syncTail` and `refreshExtraMas` (only when extra-TF candles actually changed). The version travels with the candle snapshot (`closedVersion` parameter), so a `syncTail` landing mid-computation cannot store an old result under a new key.
- `indicatorTailKey` excludes the live candle (its price is what the tail recomputes) and includes the time of candle `n-2` (other-TF MAs align by time). `IndicatorTailKeyTest` is the contract; do not change the format without updating it.
- Never cache RSI divergences: the `n-6` fractal pivot is confirmed with a window that includes the live candle. `RsiDivLiveCandleTest` proves it (8 of 1600 random cases change).
- `recomputeIndicatorTail` returns `null` whenever it cannot guarantee equivalence; the caller must fall back to the full computation. Never return a partial or empty series instead.
- `buildIndicatorTailState` returns null below ~27 candles (it needs `MACD_SLOW - 1` closed candles). With exactly 34 candles the MACD series has one point and that DEA is the seed, so the tail recomputes only the full MACD; covered for n = 33/34/35/36.
- Do not widen the 1e-9 tolerance in `IndicatorTailTest` or drop a series from it: add cases instead.
- The tail keeps exact state only at index `n-2`. If you add a series, extend `IndicatorTailTest` with a last-value comparison (1e-9) before wiring it.
- Adding a new sub-indicator follows the checklist in `docs/CHART_PIPELINE.md` §10.

## Wallet Balances

- CoinStats powers balances.
- Calls are intentionally sequential with delays to avoid rate limits.
- Do not parallelize CoinStats balance calls unless rate behavior is measured and handled.
- Treat 401/403/429 as user-visible errors.

## Transaction History

- CoinStats powers transaction history.
- Query supported `connectionId` values sequentially.
- `Fill` transaction types from CoinStats are synthetic balance records and are currently filtered out.
- Keep grouping by network in the UI.

## Alerts And Notifications

- One notification channel per picked tone: `priceChannelIdFor(soundUri)` derives the id from the resolved URI hash and `ensurePriceChannel` deletes the other `price_alerts*` channels. Do not go back to a single fixed channel id.
- Never open or validate the sound URI from the app (`openAssetFileDescriptor`, etc.). The app has no media read permission and the sound is played by SystemUI, so probing it silently downgrades valid tones to the default. `resolveSound` stays syntactic (blank / unparseable / no scheme → default).
- `MediaPlayer: NullPointerException: uri` in logcat is MIUI's hour-of-day "nature sound", logged by `system_server`/`systemui`. It is not this app; do not add defensive code for it.
- Price alerts use `alertId.hashCode()` as notification id and a five-minute repeat throttle lives in `DivScanService`.
- The background scanner (`DivScanService`) watches **all four brokers** (Binance, MEXC, Bitunix, BingX) with the same maths: RSI divergences, entry signals and multi-timeframe confluence. It must request the **same candles as the chart** (`presentation/crypto_detail/ChartIntervals.kt` owns the timeframe mapping and the calendar aggregation), synthesize the live candle for Bitunix, and keep the **source in the alert id and in the dedupe queries**: Binance and Bitunix share the symbol (`BTCUSDT`) and without it one broker's alert suppresses the other's. Sequential requests only, 400 ms apart (500 ms for MEXC). TV is not scanned.
- Price alerts already work per source (`getPairDetail(symbol, source)`); the symbol shown to the user is always without a separator (`Brokers.displaySymbol`).

Channel ids, notification ids, the throttle, the per-broker scanner and silent-alert triage: `docs/ALERTS_NOTIFICATIONS.md`.

## Critical Zones (do not touch without reading the doc)

| Zone | Why | Read first |
|---|---|---|
| `PriceChart` update block in `CryptoDetailScreen.kt` | rebuild vs in-place gate; a wrong key freezes or desyncs the chart | `docs/CHART_PIPELINE.md` §2 |
| `viewportKey()` | rebuild trigger; already regressed once into a frozen chart | `docs/CHART_PIPELINE.md` §2 |
| `updateLastCandleInPlace` / `updateLastMAInPlace` | index arithmetic on live datasets; produced spikes when unguarded | `docs/CHART_PIPELINE.md` §2 |
| `IndicatorTail` key + state | a false cache hit shows a stuck indicator until the candle closes | `docs/CHART_PIPELINE.md` §4 |
| `ClosedCandleCache` invalidation points | stale SMC / stale indicators | `docs/CHART_PIPELINE.md` §4.4 |
| `SmcOverlayRenderer` mitigated zones | must stay hidden (explicit user requirement) | `docs/CHART_PIPELINE.md` §7 |
| `applySyncAndInitialZoom` / `alignViewportXWith` | `post { }` ordering and offsets-vs-margins | `docs/CHART_PIPELINE.md` §3 |
| the measure tool (`measureContextKey`) | keying it on `viewportKey()` erases the drawing on every candle close | `docs/CHART_PIPELINE.md` §7 |
| `DivAlertNotifier.priceChannelIdFor` + `resolveSound` | a silent alert with no error at all | `docs/ALERTS_NOTIFICATIONS.md` §2–3 |
| `observeLivePrices()` | it is **shared** (`shareIn` on the repository's app scope): one socket per exchange for the whole app. Collecting a cold copy opens a second one | `docs/DATA_SOURCES.md` §2.1 |
| the detail's live price | the socket wins, but only while its tick is fresh (>15 s = dead); without the guard a dead socket freezes the price silently | `docs/DATA_SOURCES.md` §4.1 |
| the taker endpoint | the ratio endpoint only publishes closed buckets; the klines endpoint carries the live candle | `docs/DATA_SOURCES.md` §6 |
| `Timeframes.kt`, `Brokers.kt`, `ChartColors`, `BrokerColors`, `PriceFormat.kt` | shared sources of truth | this file |

## UI Guidelines

- Use Material 3 Compose components.
- Keep cards compact, dark, and information-dense.
- Use stable `LazyColumn` keys for lists.
- Keep transaction and wallet screens grouped by network where applicable.
- Show loading, empty, and error states distinctly.
- Avoid one giant decorative redesign. Improve the actual workflow.
- For charts, remember MPAndroidChart is embedded through `AndroidView`; avoid unnecessary recreation.
- The price countdown refreshes only its tag area when the text changes; keep that path separate from price/data invalidations.
- All colors come from `ui/theme/Color.kt` (Compose tokens plus the `ChartColors` hex palette) and all type from `ui/theme/Type.kt` (Geist, `GeistTnum` for prices). No new color or font literals anywhere else.
- In chart `Canvas` code, custom paint `textSize`/stroke values are **pixels** (some scaled as `density * x`), while MPAndroidChart axis `textSize` is dp. Do not convert one to the other; a "dp fix" breaks chart labels.
- Shared UI pieces: `PillGrid` / `PillOption` / `SheetSectionTitle` live in `presentation/components/OptionPillGrid.kt`; sheet layout primitives in `CryptoDetail/sheets/SheetPrimitives.kt`; the color picker in `sheets/WideColorPicker.kt`. Reuse them instead of new one-off controls.

Tokens, hand-tuned chart values and known visual debt: `docs/DESIGN.md`.

## Performance Guidelines

- Preserve coroutine cancellation. Always rethrow `CancellationException`.
- Move CPU-heavy chart calculations off the main thread.
- Avoid network loops faster than needed.
- Use cached/remembered derived UI state in Compose when grouping/filtering large lists.
- Avoid excessive background/service updates.
- Chart indicators must stay incremental: the 2 s tick should only move the last point (plus the cached SMC). A full `toChartComputation` per tick is the regression this project already paid for.
- The live-price sockets are shared state: cap the **visual** publish rate in the ViewModel (do not re-emit with `sample()`), and never open a second socket per exchange for the same data.

## Database Notes

Room database version is in `app/src/main/java/com/defitracker/app/data/local/AppDatabase.kt`.

```kotlin
version = 7
MIGRATION_5_6
MIGRATION_6_7
```

`MIGRATION_5_6` adds the `price_alerts` table without touching existing data. `MIGRATION_6_7` rebuilds `tracked_pairs` with the composite `(symbol, source)` key, copying every row (new table → copy → drop → rename). `AppModule` registers both migrations and still keeps `fallbackToDestructiveMigration()` as a last-resort development fallback. Do not bump schema casually; add and register an explicit migration for every production-facing schema change.

- `tracked_pairs` is keyed by `(symbol, source)`, not by `symbol`: the same `AVAXUSDT` exists on Binance and Bitunix, and `AVAX-USDT` on BingX. A symbol-only key silently replaces the other broker's row on upsert and makes deleting one pair delete all of them, so every insert and every delete must carry both fields.
- The Room `PRIMARY KEY` column order in a migration must match the entity's `primaryKeys` order, or Room fails schema validation at open time.

`PriceAlertDao` stores point/range LONG/SHORT alerts. `DivScanService` checks enabled price alerts sequentially, notifies on a hit, and throttles repeat notifications to five minutes.

## Secrets

The CoinStats API key is read from the ignored root-level file `local.properties`.

Gradle exposes it as `BuildConfig.COINSTATS_API_KEY`, using an empty default when the property is absent. Never hard-code, log, or include the key in source control. If asked to prepare for release, use a backend proxy or another production-safe secret mechanism.

## Verification Checklist

Before final response after code changes:

- Run `:app:assembleDebug` and `:app:testDebugUnitTest` when possible; report the real per-class test counts.
- Mention if build or device verification could not be run and why.
- Check that UI changes keep existing labels/routes intact.
- Confirm no unrelated files were reverted and no temporary probe was left behind.
- For chart or alert changes, state which guarded test covers the change and update `docs/` if a contract changed.
- Summarize changed files and behavior, not every small implementation detail.

## Environment And Device Verification

- `./r -Device <serial>` (repo helper: `r.ps1` → `run-debug.ps1`) builds, installs with `adb -s` and launches. Always pass `-Device`: `adb devices` usually lists a wireless serial plus an mDNS duplicate.
- Keep the screen on while measuring (`adb shell svc power stayon true`) and restore it (`false`); with the screen off the chart never recomposes and counters read zero.
- Unit tests run on the JVM without Robolectric or `returnDefaultValues`: Android classes (`Uri`, `Settings`, `Context`) throw. Keep decision logic in pure Kotlin if it must be tested.
- Never rewrite Kotlin sources with PowerShell `Set-Content`/`Out-File`; it re-encodes UTF-8 (mojibake in accents, BOM). Use the editor tools.
- Temporary probes (tick/rebuild counters) are fine for verification, but remove them and grep for leftovers before reporting.
- Steady state on 1m: ~20 chart updates per minute and **1 dataset rebuild per minute** (one per closed candle). More rebuilds means the gate regressed.

Recipes and counters: `docs/DEVELOPMENT.md`.

## Common Pitfalls

- Treating API errors as empty lists.
- Parallelizing third-party API calls and hitting rate limits.
- Displaying tiny token amounts as `0.0000`; use adaptive precision.
- Breaking Wallet/History by changing DTO fields without checking actual API JSON.
- Reintroducing removed fallback clients without a clear current use.
- Rebuilding the chart dataset on every tick, or patching in place on a dataset whose size does not match the state.
- Caching anything that depends on the live candle (RSI divergences) or skipping a `closedCandleVersion` bump.
- Clearing the measure tool on every `viewportKey()` change (the drawing dies at every candle close).
- Opening a second socket because `observeLivePrices()` was collected cold (it is shared now).
- Validating the alert sound URI from the app instead of letting the system play it.
- Chasing `MediaPlayer` NPE lines in logcat: they come from MIUI, not from this app.
- Assuming unit tests can touch Android framework classes.
