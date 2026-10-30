# Chart pipeline: rendering, viewport and incremental indicators

Audience: any agent (or human) touching the price chart. Read this before editing
`CryptoDetailScreen.kt` (the `PriceChart` block), `chart/*`, `IndicatorTail.kt`,
`ClosedCandleCache.kt`, `MarketChart` wiring or the chart-related parts of the ViewModel.

State: reflects `main` at `ce22db7` — 56 unit tests green.

> Rules live in `AGENTS.md`. This document explains them: why each one exists, the
> measurements behind it, and how to verify it. If the two disagree, `AGENTS.md` wins.

---

## 1. Data flow

```
loadChartData() / syncTail() / tick loop (2 s, DETAIL_REFRESH_MS)
        -> CryptoDetailState                      (main thread, ViewModel)
        -> List<CandleData>.toChartComputation(...)   [Dispatchers.Default]
              fast path: IndicatorTail  -> only index n-1 recalculated
              full path: everything      -> buildIndicatorTailState() cached
        -> ChartComputation
        -> PriceChart (CryptoDetailScreen `update` block)
              decides: rebuild dataset  OR  patch last point in place
        -> PriceCombinedChart.onDraw + chart/*Renderer
```

`toChartComputation` is the only place that produces `ChartComputation`. Subcharts consume
`state` fields; the price chart consumes the same object plus its own dataset.

---

## 2. Golden rule: what is drawn == state candles of the current interval

If this breaks, the user sees any of: axis frozen, "previous TF" candles, vertical spikes,
marker showing a different date than the axis, Y labels disappearing. It has happened
before (`2c27abd`) and was fixed in `260562b`.

The gate lives in the `PriceChart` update block and must keep all four conditions:

| Piece | Meaning |
|---|---|
| `viewportKey()` (in `CryptoDetailScreen.kt`) | `symbol + interval + size + firstTime + lastTime`. Changes on new candle, window shift, TF or symbol. |
| `intervalChanged` | `state.selectedInterval != lastCandleInterval` (the interval actually painted). |
| `tfSwitching` | `isLoading && isNewDataset && candles.isNotEmpty() && intervalChanged` — the state still carries the previous TF candles; do **not** paint them. |
| `datasetStale` | `chart` candle dataset `entryCount != state.candles.size`. |

```kotlin
val rebuildData = !tfSwitching && (
    isNewDataset || lastPrefsKey.value != prefsKey || intervalChanged || datasetStale
)
```

Rules:

- Never patch in place while `tfSwitching` (the in-place branch is guarded with `!tfSwitching`).
- `updateLastCandleInPlace` writes only when `ds.entryCount == state.candles.size`
  (`ChartUpdates.kt`). Do not relax it back to `>`, that is what wrote today's price into a
  foreign index and produced the spikes.
- Do not "simplify" the key back to `symbol+interval`. It is the regression that froze the
  chart: new candles arrived in state but never reached the dataset.
- `alignViewportXWith(force = isNewDataset && !tfSwitching, ...)`: offsets are recalculated
  only for a real dataset change, never per tick.

---

## 3. Viewport contract

- The price `CombinedChart` is the X source of truth; subcharts copy its `matrixTouch`
  (`syncViewportFrom`) and reset their own Y.
- New dataset (first mount or new TF): `applySyncAndInitialZoom(resetViewport = true, ...)`
  → initial window = `INITIAL_VISIBLE_CANDLES` (100) candles, fitted Y.
- Same dataset: `keepZoom` restores the user matrix; if the user is at the right edge,
  the matrix is shifted by `delta * scaleX` so the live edge stays visible.
- `applySyncAndInitialZoom` repositions inside `post { }`. Anything that repositions the
  matrix must not race with that block (see §7).
- Last used timeframe is remembered in `IndicatorPrefsRepository.lastChartInterval`
  (written in `loadChartData`, read in the ViewModel `init` when the nav argument is empty).
  It is in-memory only by design.

---

## 4. Incremental indicators (the "point 6" work)

Two independent caches. Both are keyed by content, never by `isLoading` or timing.

### 4.1 `ClosedCandleCache<T>` (`ClosedCandleCache.kt`)

Single-value cache for whatever depends only on **closed** candles. Today: the SMC
(`smcFor(...)`), key `"$symbol|$interval|${prefs.smcSignature()}|$closedVersion"`.

### 4.2 `IndicatorTail` (`IndicatorTail.kt`)

Recomputes only the last point of BB, RSI, RSI-MA (signal), StochRSI (K and D), MACD
(line/signal/hist) and the chart-TF MAs.

`IndicatorTailState` is the state **at index n-2**:

| field | used by |
|---|---|
| `bbSum`, `bbSumSq` | Bollinger rolling sums |
| `rsiAvgGain`, `rsiAvgLoss` | Wilder RSI |
| `rsiMaSum` | RSI signal line rolling sum |
| `macdFastEma`, `macdSlowEma` | MACD EMA12/EMA26 |

The series prefix comes from the cached `ChartComputation`; only the last element is
replaced (`series.subList(0, size - 1) + (lastIndex to value)`).

### 4.3 Tail key

```kotlin
indicatorTailKey(symbol, interval, prefs, closedVersion, candles)
  = "$symbol|$interval|${prefs.prefsKey()}|$closedVersion|${candles.size}|${candles[size-2].time}"
```

- The live candle (index `n-1`) is deliberately **out** of the key: its close/high/low is
  exactly what the tail recomputes.
- The time of candle `n-2` is **in**: other-TF MAs are aligned by time, so a retimed closed
  candle must invalidate.
- `IndicatorTailKeyTest` locks the contract (same key on a tick, different key on symbol,
  interval, any indicator pref, version, size or a retimed/closed candle).

### 4.4 Invalidation — all points, no exceptions

`closedCandleVersion++` in:

1. `updatePrefs` (any indicator setting),
2. `loadChartData` (new dataset / reload),
3. `syncTail` (the merge can rewrite already-closed candles **without changing list size**),
4. `refreshExtraMas` when extra-TF candles actually changed (other-TF MA alignment).

Symbol and interval live in the key. The version travels **with the candle snapshot**
(`closedVersion` parameter of `toChartComputation`): if `syncTail` bumps while a tick is
computing, the old snapshot cannot be stored under the new key.

### 4.5 When the tail gives up

`recomputeIndicatorTail` returns `null` and the caller runs the full computation. Reasons:
series shape mismatch, `buildIndicatorTailState` returns null (`n < 27` today), EMA MA line
too short for a previous value, etc.

**MACD edge:** with exactly `slow + signal - 1 = 34` candles the series has **one** point,
and at that index DEA is the *seed* (mean of 9 DIF), not an EMA step, so it cannot be
derived from the state. The tail recomputes only the full MACD in that case (cheap, correct,
covered by tests for n = 33/34/35/36).

### 4.6 What must NOT be cached

**RSI divergences.** `fractalPivots` confirms pivots in `[lookback, n-6]`, and the pivot at
`n-6` uses the window `[n-11, n-1]`, which includes the live candle. Moving only the last
candle changes the confirmed list (measured: 8 / 1600 random cases). `RsiDivLiveCandleTest`
proves it; keep both variants (`RSI_DIV_LOOKBACK` and `RSI_DIV_EARLY_LOOKBACK`) live.

### 4.7 Known costs (do not "fix" blindly)

- The tail still copies list references per series per tick (O(n)); what it removes is the
  arithmetic and the `Pair`/boxing allocations.
- SMA tails compute the window sum directly (20/200 closes), not the rolling accumulator:
  the difference is far below the 1e-9 test tolerance but it is not bit-identical.

---

## 5. Invariant → symptom

| Broken invariant | What the user sees |
|---|---|
| rebuild gate missing `intervalChanged` / `datasetStale` | previous TF candles, frozen axis, price tag moving alone |
| in-place patch on a mismatched dataset | spikes, wicks across the whole chart |
| `tfSwitching` painting while loading | squeezed chart, candles crowded to one side |
| viewport offsets recalculated per tick | panes jump mid-gesture, crosshair misaligned |
| tail key missing a closed-candle change | stale indicator until the next candle close |
| tail caching divergences | a divergence that never disappears (or appears late) |
| `alignViewportXWith(force = true)` on ticks | `setViewPortOffsets` thrash, subchart misalignment |

---

## 6. Guarded tests (never weaken, add cases instead)

| Test | Guards |
|---|---|
| `IndicatorTailTest` (6) | tail == full within 1e-9 for 12 series; sizes/indices/history; n=34/36 edge; MACD 33..36; extreme trends |
| `IndicatorTailKeyTest` (9) | the key contract in §4.3 |
| `RsiDivLiveCandleTest` (2) | divergences depend on the live candle; the change is the `n-6` pivot |
| `ClosedCandleCacheTest` (3) | cache hit/miss semantics |
| `RsiCalcTest`, `MacdCalcTest`, `SignalsTest`, `SmcTest`, `RsiDivTest` | the pure math the chart depends on |

Any change to the tail, the gate or a series shape must keep these green; if a new series
is added, extend `IndicatorTailTest` with its last-value comparison.

---

## 7. High-risk zones (read twice before touching)

- **`PriceChart` update block** in `CryptoDetailScreen.kt` — the rebuild gate and the
  in-place branch. It is the single place where the chart can be desynchronized.
- **`viewportKey()`** — changing it silently changes rebuild frequency and correctness.
- **`applySyncAndInitialZoom`** — repositioning happens in `post { }`; do not reorder the
  calls around `chart.data = ...`, `notifyDataSetChanged()` and the matrix restore.
- **`alignViewportXWith`** — offsets vs margins: it passes *margins* to
  `setViewPortOffsets`, and passing `contentBottom()` there collapses the rect.
- **`updateLastCandleInPlace` / `updateLastMAInPlace`** — index arithmetic on live datasets.
- **`SmcOverlayRenderer` mitigated zones** — filled/mitigated zones must stay **hidden**
  (explicit user requirement). Only active zones are drawn, with dashed border + tag.
- **`IndicatorTail` key/state** — a false cache hit shows a stuck indicator until close.

---

## 8. Verifying on a device

```powershell
# build + install + launch (repo helper; always pass -Device, adb lists mdns duplicates)
./r -Device <serial>

# screenshots: write on the device and pull (do NOT use `exec-out >` from PowerShell)
adb -s <serial> shell screencap -p /sdcard/x.png
adb -s <serial> pull /sdcard/x.png "$env:TEMP\x.png"

# keep the screen alive while measuring (restore afterwards)
adb -s <serial> shell svc power stayon true
adb -s <serial> shell svc power stayon false
```

- Measuring tick cost: add a temporary counter/probe, run 2–3 minutes, read `logcat -s`,
  then **remove the probe** and grep for leftovers before reporting.
- Expected steady state on 1m: ~20 chart updates/min and **1 dataset rebuild/min**
  (one per closed candle). A rebuild per tick means the gate regressed.
- The app has no media permissions; unit tests have no Robolectric/`returnDefaultValues`,
  so keep decision logic free of `Uri`, `Settings` and `Context` if you want it tested.
