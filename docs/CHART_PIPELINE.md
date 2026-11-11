# Chart pipeline: rendering, viewport and incremental indicators

Audience: any agent (or human) touching the price chart. Read this before editing
`CryptoDetailScreen.kt` (the `PriceChart` block), `chart/*`, `IndicatorTail.kt`,
`ClosedCandleCache.kt`, `MarketChart` wiring or the chart-related parts of the ViewModel.

State: reflects `main` at `ce22db7` + the docs pass + the socket-fed detail — 72 unit
tests green.

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

The tick loop's **price** comes from the shared WebSocket (the live candle's close/high/low
and the volume bar); the REST only refreshes the 24h high/low/percent every 30 s and takes
over if the socket goes stale. The tick cadence itself did not change. Details:
`docs/DATA_SOURCES.md` §4.

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

### 4.8 The taker series is network-fed, not computed

`C/V Taker` does not belong to `ChartComputation`. It comes from Binance Futures klines
(`fapi/v1/klines`), aligned to candle **open time** — the tail sync shifts the list, the
timestamps do not — and it is cached by candle time in `takerCache`.

It used to come from `futures/data/takerlongshortRatio`, which only publishes **closed**
buckets: the in-progress candle never appeared in the subchart. The klines endpoint
carries the partial candle, so the live bar exists on every timeframe.

Its network cadence is `Timeframes.takerRefreshMs` (2 s on 1m/5m up to 60 s on 2h+).
The bar itself grows locally on every tick; the call only corrects the buy/sell split.
See `docs/DATA_SOURCES.md` §6.

### 4.9 Early divergences expire

`mergeConfirmedAndEarly(confirmed, early, lastIndex)` keeps an early ("Pre") divergence
only while its confirmation window is still open:

```kotlin
e.idx2 + RSI_DIV_LOOKBACK > lastIndex
```

When the window has been evaluated and the divergence did **not** confirm, it is dropped
instead of staying painted forever. The confirmed list is untouched by this rule.

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
| `RsiDivLiveCandleTest` (2) | divergences depend on the live candle; the change is the `n-6` pivot; it fails if it finds no case at all |
| `MeasureContextKeyTest` (3) | the measure survives a new candle and dies when the window rolls (see §7) |
| `ClosedCandleCacheTest` (3) | cache hit/miss semantics |
| `RsiCalcTest`, `MacdCalcTest`, `SignalsTest`, `SmcTest`, `RsiDivTest` (5) | the pure math the chart depends on |
| `DrawOverlayCodecTest` (4) | drawing overlay persistence codec (text annotations included) |
| `LivePriceFeedTest` (7) | the socket freshness guard: a dead socket must fall back to REST instead of freezing the price |

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
- **The measure tool (`rangeSelection`)** — it is stored by candle index, so it survives a
  new candle (the last one) but dies when the window rolls (the first candle changes),
  the pair changes or the TF changes. `measureContextKey` (`chart/MeasureContext.kt`) is
  that contract; clearing it on every `viewportKey()` change makes the drawing vanish on
  every candle close. Do not "simplify" it by keying on `viewportKey()`.

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

Full recipes (build, install, screenshots, counters) live in `docs/DEVELOPMENT.md`.

---

## 9. File map: which file to edit

`presentation/crypto_detail/` owns the chart. Pick the file by **change type**.

### 9.1 Entry points and state

| File | Owns |
|---|---|
| `CryptoDetailScreen.kt` | Compose entry point, `PriceChart` AndroidView wiring, toolbar/sheet state, dataset rebuild gate. Legacy size (~2,090 lines): **do not add new chart code here**, create a focused file. |
| `CryptoDetailViewModel.kt` | detail state, source-specific loads, tail synchronization, off-main chart computation, indicator toggles, Pulse refresh, taker alignment, CRUD for fibs / drawings / positions / alerts. |
| `chart/PriceCombinedChart.kt` | the `CombinedChart` subclass, shared paints, the `updateState(...)` boundary, callbacks. |
| `chart/MeasureContext.kt` | `measureContextKey`: what invalidates the measure tool. |

### 9.2 Rendering

| File | Owns |
|---|---|
| `chart/PriceChartMainRenderer.kt` | base price drawing, extrema labels, price/crosshair tags, MA timeframe tags, the draw dispatcher. |
| `chart/SmcOverlayRenderer.kt` | SMC zones, tags, liquidity/equilibrium labels, ratio formatting. |
| `chart/VolumeOverlayRenderer.kt` | normal volume, taker volume, volume-profile overlays. |
| `chart/RangeSelectionRenderer.kt` | measurement rendering and hit testing. |
| `chart/FibOverlayRenderer.kt` | Fibonacci hit testing, dragging, levels, pending hints. |
| `chart/DrawOverlayRenderer.kt` | drawing tools: hit testing, dragging, shape + text rendering. |
| `chart/PositionOverlayRenderer.kt` | LONG/SHORT geometry, labels, hit testing, dragging. |
| `chart/ChartRendering.kt` | shared axes, renderers, typography, datasets, MA labels, initial candle window. |
| `chart/ChartUpdates.kt` | in-place live-tick updates (candles, MAs, StochRSI, RSI, MACD). |
| `chart/ChartSynchronization.kt` | subchart refs, pan forwarding, viewport/zoom sync, highlight propagation, `highlightXSafe`. |
| `chart/ChartHighlight.kt` | safe crosshair highlights: `dataIndex` fix, block lookup, `highlightSafe`. |

### 9.3 Gestures

| File | Owns |
|---|---|
| `chart/ChartGestureController.kt` | the whole touch flow: pan/zoom, drawing mode, overlay select/drag, range measurement, crosshair, pinch, long press. |
| `chart/ChartGestureMath.kt` | viewport matrix helpers, touch→candle, resize preservation, pan/Y-zoom/pinch primitives, range tap. |

### 9.4 Maths and models (pure)

| File | Owns |
|---|---|
| `Smc.kt` | swings, BOS/CHoCH, order blocks, FVG, premium/equilibrium, liquidity, confluence. |
| `RsiCalc.kt` | RSI, RSI MA, EMA series, tolerant kline parsing. |
| `RsiDiv.kt` | regular/hidden RSI divergences, confirmed and early lookbacks. |
| `MacdCalc.kt` | MACD(12, 26, 9) aligned to candle indices. |
| `Signals.kt` | MA rejection, FVG tap, sweep/reclaim. |
| `Analysis.kt` | Pulse analysis, S/R zones, multi-timeframe MA matrix, bias, score. |
| `PriceFormat.kt` | chart / Pulse / repository / axis-price / compact-volume formatting policies. |
| `LivePriceFeed.kt` | the socket freshness guard for the detail's live price (pure functions). |
| `FibDrawing.kt`, `DrawOverlay.kt`, `PositionOverlay.kt` | overlay models and their persistence codecs. |
| `ClosedCandleCache.kt` | single-value cache for closed-candle-only results (today the SMC). |
| `IndicatorTail.kt` | incremental tail of the chart series, `indicatorTailKey`, fallbacks. |

### 9.5 Sheets and preferences

| File | Owns |
|---|---|
| `sheets/IndicatorsSheet.kt` | indicator visibility toggles, entry point for MA settings. |
| `sheets/MaListSheet.kt` | MA list and MA row helpers. |
| `sheets/DrawingToolsSheet.kt` | drawing-tool sheet, tool cells, overlay edit bar, text annotations. |
| `sheets/DrawTextDialog.kt` | text annotation dialog for the drawing tools. |
| `sheets/FibLevelsSheet.kt` | Fibonacci level/colour/width configuration. |
| `sheets/PositionConfigSheet.kt` | floating position bar, leverage, position form. |
| `sheets/AnalysisSheet.kt` | Pulse analysis sheet. |
| `sheets/SheetPrimitives.kt` | shared sheet primitives. |
| `sheets/WideColorPicker.kt` | shared HSV/hex colour picker. |
| `presentation/components/OptionPillGrid.kt` | `PillGrid` / `PillOption` / `SheetSectionTitle`. |
| `IndicatorPrefs.kt` | `IndicatorPrefs`, MA configuration, DataStore settings, per-symbol overlay persistence. |
| `PriceAlertSheet.kt` | price-alert UI (storage and monitoring live in `data/local` and `alerts`). |

---

## 10. Adding a new sub-indicator

Use the MACD behaviour as the reference (`MacdCalc.kt` + `chart/MacdChart.kt`). Every new
chart component goes in its own focused file; never grow `CryptoDetailScreen.kt`.

1. **Pure calc** in `presentation/crypto_detail/<Name>Calc.kt`, aligned to candle index as
   `List<Pair<Long, Double>>`.
2. **State**: fields in `CryptoDetailState` + `ChartComputation`; compute inside
   `toChartComputation` (already off-main); copy into all three `state.copy` blocks
   (tick, `loadChartData`, `syncTail`).
3. **Prefs**: a `*Visible` flag (default off unless requested) in `IndicatorPrefs` +
   DataStore key + `prefsKey()` + `toggle*()` in the ViewModel.
4. **Sub chart**: an `AndroidView` cloning `RsiChart` (viewport, time axis, in-place tick
   update) in its own file. If it needs bars, use `CombinedChart` like `MacdChart`.
5. **Highlight rule (crash standard)**: never call `highlightValue(x, 0)` directly on a
   `CombinedChart` sub — `CombinedData` throws `IndexOutOfBoundsException`
   (`dataIndex -1`). The cause is MPAndroidChart's constructor
   `Highlight(x, dataSetIndex, stackIndex)`: it never sets `dataIndex`, so it stays `-1`.
   The only valid way is `setDataIndex(...)`, and the block must be the real one
   (candles for the price chart, lines for MACD). Always go through `highlightXSafe(x)`
   or `highlightSafe(x)` from `chart/ChartHighlight.kt`, with the `try/catch` fallback
   that clears the highlight. `ChartHighlightTest` pins the crash and the contract.
6. **Wiring**: integrate through the extracted chart wiring (`PriceChart` refs, `sync*`
   helpers, layout weights/dividers, `IndicatorsSheet` grid). Do not append wiring to
   `CryptoDetailScreen.kt`.
7. **Network-fed series** (taker C/V is the exception): fetch in `loadChartData` / tick
   via `repository`, align by candle time in the ViewModel, keep them out of
   `toChartComputation`.
8. **Exactness**: if the series must stay exact on every tick, add its tail to
   `IndicatorTail` (state at `n-2` + a last-value recompute) and cover it in
   `IndicatorTailTest` with a 1e-9 last-value comparison. Series that depend on the live
   candle (like RSI divergences) must stay in the full path.

