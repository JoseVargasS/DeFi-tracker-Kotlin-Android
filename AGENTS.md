# AGENTS.md

Guidance for coding agents working on this Android project.

## Project Identity

This repo is an Android app named Octopus / DeFi Tracker. It tracks crypto pairs, charts, wallets, balances, and transaction history.

Primary package:

```text
com.defitracker.app
```

Main module:

```text
app
```

## Documentation Map

Two focused docs carry the detail that does not belong in this contract:

- `docs/CHART_PIPELINE.md` — chart data flow, the dataset rebuild gate, viewport rules, the
  incremental indicator caches (`ClosedCandleCache`, `IndicatorTail`), their invalidation
  points, guarded tests, and the high-risk zones.
- `docs/ALERTS_NOTIFICATIONS.md` — price alerts, one notification channel per tone, the
  sound URI rules (never probe it from the app), and known MIUI log noise.
- `docs/DESIGN.md` — color/typography tokens, shape and component conventions, the chart's
  hand-tuned visual values (px vs dp), and known visual debt.

Read the matching doc before editing those areas; update it in the same change when a
contract changes.

**`AGENTS.md` is authoritative for rules.** The docs explain *why*, the measurements behind
each rule, and how to verify it. If a doc and this file disagree, this file wins and the doc
must be fixed in the same change.

Do not move a *rule* out of this file. An agent may not open `docs/`, so anything that must be
obeyed has to be here; rationale, measurements, long tables and step-by-step checklists belong
in `docs/`.

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

Preferred verification:

```bash
:app:assembleDebug
```

The Gradle wrapper is versioned in the repository. Use `./gradlew :app:assembleDebug` on Unix-like shells or `gradlew.bat :app:assembleDebug` on Windows.

If the wrapper cannot run, use Android Studio or an installed Gradle distribution with JDK 17.

Windows/JBR example:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
gradle :app:assembleDebug
```

In this environment, Gradle may be available from the user Gradle wrapper cache rather than PATH.

Unit tests (56 tests: chart caches, incremental tail, key contract, alerts math):

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Build + install + launch on a connected device:

```powershell
./r -Device <adb-serial>
```

## Architecture

Layers:

- `data/local`: Room entities and DAOs.
- `data/remote`: Retrofit API interfaces, WebSocket streams, and DTOs; TradingView lives under `data/remote/tv`.
- `data/repository`: concrete repository implementation.
- `domain/model`: app/domain UI models. There is currently no repository-contract file under `domain/repository`.
- `presentation/*`: Compose screens and ViewModels.
- `di`: Hilt module.
- `ui/theme`: colors/theme, including the shared `ChartColors` palette.
- `core/Timeframes.kt`: single catalog for chart labels, durations, Binance/MEXC/TradingView mappings, alert intervals, MA timeframes, and chart aggregation rules.
- `presentation/crypto_detail`: chart screen, chart ViewModel, pure indicator calculations, chart preferences, and per-symbol overlays.

Navigation is in:

```text
app/src/main/java/com/defitracker/app/MainActivity.kt
```

Repository contract:

```text
app/src/main/java/com/defitracker/app/domain/repository/CryptoRepository.kt
```

Repository implementation:

```text
app/src/main/java/com/defitracker/app/data/repository/CryptoRepositoryImpl.kt
```

## API Behavior

### Crypto Prices And Charts

- Binance powers symbol discovery, 24h stats, prices, and klines.
- `BinanceTickerStream` and `MexcTickerStream` provide reconnecting WebSocket live-price flows consumed by `CryptoRepositoryImpl.observeLivePrices()` and the Pairs screen.
- `TradingViewClient` is a WebSocket client for the `TV` chart source, with timeframe resolution mapping and a short memo cache.
- `BinanceFuturesApi` provides unauthenticated taker buy/sell volume. The detail ViewModel aligns it to candle timestamps and exposes it as the optional `C/V Taker` subchart; it is not part of `ChartComputation`.
- Retrofit's Binance, Binance Futures, CoinStats, and MEXC clients share the named REST `OkHttpClient` from `AppModule`; the WebSocket client remains separate with its long-lived timeout and ping settings.
- Price list refresh should stay conservative. Avoid very short intervals or per-item busy loops.

### Chart Detail Map

`presentation/crypto_detail/` currently contains these responsibilities:

- `CryptoDetailScreen.kt`: Compose entry point, `PriceChart` AndroidView wiring, toolbar/sheet state, and dataset updates. The large chart view, gestures, and overlay rendering now live under `chart/`; do not add new chart implementation code here.
- `chart/PriceCombinedChart.kt`: the dedicated `CombinedChart` subclass, shared chart paints, explicit `updateState(...)` boundary, callbacks, and Android callback delegation.
- `chart/ChartGestureController.kt`: the complete touch-event flow for pan/zoom, drawing mode, overlay selection/dragging, range measurement, crosshair selection, pinch, and long-press behavior.
- `chart/ChartGestureMath.kt`: viewport matrix helpers, touch-to-candle conversion, resize preservation, pan/Y-zoom/pinch primitives, and range-tap helpers used by the gesture controller.
- `chart/PriceChartMainRenderer.kt`: base price-chart drawing, extrema labels, price/crosshair tags, MA timeframe tags, and the draw dispatcher.
- `chart/SmcOverlayRenderer.kt`: SMC background/foreground zones, tags, liquidity/equilibrium labels, and ratio formatting.
- `chart/VolumeOverlayRenderer.kt`: normal volume, taker volume, and fixed-range volume-profile overlays.
- `chart/RangeSelectionRenderer.kt`: measurement/range selection rendering and hit testing.
- `chart/FibOverlayRenderer.kt`: Fibonacci hit testing, dragging callbacks, level rendering, and pending Fibonacci hints.
- `chart/DrawOverlayRenderer.kt`: drawing-tool hit testing, dragging callbacks, shape rendering, and pending drawing hints.
- `chart/PositionOverlayRenderer.kt`: LONG/SHORT geometry, labels, hit testing, dragging callbacks, and position rendering.
- `chart/PriceChartRenderState.kt`: shared chart-tool types (`DrawingTool`, `MeasureZone`) and the virtual Fibonacci extension constant.
- `chart/ChartSynchronization.kt`: shared subchart references, pan forwarding, viewport/zoom synchronization, highlight propagation, and `highlightXSafe`.
- `chart/ChartRendering.kt`: shared chart axes, renderer, typography, datasets, MA labels, and initial candle-window constant.
- `chart/ChartUpdates.kt`: in-place live-tick updates for candles, MAs, StochRSI, RSI, and MACD.
- `ClosedCandleCache.kt`: single-value cache for anything that depends only on **closed** candles (today the SMC). Keyed by symbol + interval + prefs signature + `closedCandleVersion`.
- `IndicatorTail.kt`: incremental tail of BB / RSI / RSI-MA / StochRSI / MACD / chart-TF MAs; holds the tail state (index n-2), the cached seed, `indicatorTailKey`, and the fallbacks to a full computation.
- `chart/StochRsiChart.kt`, `RsiChart.kt`, `MacdChart.kt`, `TakerChart.kt`: the four optional subchart composables and their chart-specific drawing/formatting.
- `sheets/IndicatorsSheet.kt`: indicator visibility controls and the entry point for MA settings.
- `sheets/MaListSheet.kt`: MA list/configuration UI and MA row helpers.
- `sheets/WideColorPicker.kt`: shared HSV/hex color picker used by MA, drawing, position, and Fibonacci controls.
- `sheets/AnalysisSheet.kt`: Pulse analysis sheet and its time formatter.
- `sheets/DrawingToolsSheet.kt`: drawing-tool sheet, tool cells, overlay edit bar, and drawing actions.
- `sheets/PositionConfigSheet.kt`: floating position bar, leverage choices, and position configuration form.
- `sheets/FibLevelsSheet.kt`: Fibonacci level/color/width configuration sheet.
- `sheets/SheetPrimitives.kt`: shared sheet grid cells, dividers, and chart resize divider.
- `CryptoDetailViewModel.kt`: owns detail state, source-specific loads, tail synchronization, off-main chart computation, indicator toggles, Pulse refresh, taker alignment, and CRUD/selection for Fibonacci, drawings, positions, and price alerts.
- `Analysis.kt`: pure Pulse analysis, support/resistance zones, multi-timeframe MA matrix, bias, score, and explanatory items.
- `DrawOverlay.kt`: OKX-style drawing models, supported drawing kinds, limits, labels, and persistence codecs.
- `FibDrawing.kt`: Fibonacci models, level/width constants, candle-time mapping, shared codec helpers, and Fibonacci persistence codecs.
- `IndicatorPrefs.kt`: `IndicatorPrefs`, MA configuration, DataStore-backed indicator settings, and per-symbol Fibonacci/drawing/position persistence.
- `MacdCalc.kt`: pure MACD(12, 26, 9) calculation aligned to candle indices.
- `PositionOverlay.kt`: LONG/SHORT overlay model, defaults, profit/loss calculations, input parsing, and persistence codec.
- `PriceAlertSheet.kt`: Compose UI for LONG/SHORT point or range alerts, enable/disable/delete actions, and notification sound selection.
- `PriceFormat.kt`: the single home for chart, Pulse, repository price, axis-price, and compact-volume formatting policies. The policies remain separate because their existing output contracts differ.
- `RsiCalc.kt`: RSI, RSI moving average, EMA series, tolerant kline parsing, and shared numeric parsing helpers.
- `RsiDiv.kt`: pure TradingView-style regular/hidden RSI divergence detection, including early and confirmed lookbacks.
- `Signals.kt`: pure MA rejection, FVG tap, and sweep/reclaim signal detection.
- `Smc.kt`: pure SMC calculations for swings, BOS/CHoCH, order blocks, FVGs, premium/equilibrium, liquidity, and multi-timeframe confluence.

Choose the file by change type:

- Pure indicator or signal math: the matching `*Calc.kt`, `RsiDiv.kt`, `Signals.kt`, `Smc.kt`, or `Analysis.kt` file.
- Chart state, loading, alignment, persistence actions, or network-fed derived data: `CryptoDetailViewModel.kt`.
- Indicator settings or overlay persistence: `IndicatorPrefs.kt` and the corresponding model/codec file.
- Price-alert UI: `PriceAlertSheet.kt`; storage and monitoring are in `data/local` and `alerts`.
- PriceChart Compose wiring, callbacks, or dataset rebuilding: `CryptoDetailScreen.kt`.
- Price chart lifecycle/state boundary: `chart/PriceCombinedChart.kt`.
- Pan, zoom, pinch, selection, drawing mode, range measurement, or long-press behavior: `chart/ChartGestureController.kt` and its `ChartGestureMath.kt` helpers.
- Fibonacci, drawing, position, volume-profile/taker, or range overlays: the corresponding `chart/*OverlayRenderer.kt` file.
- Main price/crosshair/MA/SMC rendering: `chart/PriceChartMainRenderer.kt` or `chart/SmcOverlayRenderer.kt`.
- Subchart composable: the matching file under `chart/`.
- Subchart viewport, zoom, highlight, or pan behavior: `chart/ChartSynchronization.kt`.
- Shared chart axes, renderers, datasets, or labels: `chart/ChartRendering.kt`.
- Live in-place dataset updates: `chart/ChartUpdates.kt`.
- Caching or incremental recalculation of chart series: `ClosedCandleCache.kt` (single value) and `IndicatorTail.kt` (series tails). Never cache RSI divergences — see `docs/CHART_PIPELINE.md`.
- Bottom-sheet UI: the matching file under `sheets/`; `PillGrid` / `PillOption` / `SheetSectionTitle` live in `presentation/components/OptionPillGrid.kt`, other shared controls in `sheets/SheetPrimitives.kt` or `sheets/WideColorPicker.kt`.

### File Size And Chart Contracts

- No new file should exceed approximately 600 lines. If a change would cross that limit, split it by responsibility first.
- Do not add new code to `CryptoDetailScreen.kt`; create a focused file instead. Its existing size is legacy debt, not a target for further growth.

#### Chart contracts

- The price `CombinedChart` is the horizontal viewport source of truth. Its touch matrix supplies `scaleX` and `translationX` to every visible subchart through `syncViewportFrom`; subcharts reset their Y matrix so their own scales do not alter the shared X frame.
- `alignViewportXWith` gives the price chart and subcharts the same content left/right bounds. Recalculate offsets only for a new dataset; doing it on every tick or gesture can move the panes mid-interaction.
- First mount or a new timeframe resets the initial zoom and fits Y to candles. Same-timeframe updates preserve the user's matrix; when the user is at the right edge, newly arriving candles shift the viewport to keep the live edge visible.
- `syncSubFrame` is the standard subchart update sequence: apply the time axis, perform initial zoom only when appropriate, copy the price viewport, store the render key, then align the shared X rectangle.
- Highlights originate in the price chart and `syncHighlights` propagates the selected candle X to StochRSI, RSI, MACD, and taker charts. Clearing the price highlight clears all subchart highlights.
- Always use `highlightXSafe` for subchart highlights. `CombinedChart` requires `Highlight(x, 0, 0)` so `dataIndex` points to its `LineData`; calling `highlightValue(x, 0)` directly on a `CombinedChart` can produce `IndexOutOfBoundsException`. Keep its `try/catch` fallback that clears the highlight.
- Main-chart pan/zoom updates the shared matrix and immediately resynchronizes all subcharts. Horizontal drags that begin in a subchart are forwarded to the price chart; subcharts must never pan independently.
- All indicator and taker series must remain aligned to candle indices, and live ticks should update datasets in place where the current implementation supports it instead of recreating Android views.
- Every `ACTION_DOWN` must initialize the general gesture state before overlay hit-tests, so panning from a locked Fibonacci or drawing overlay starts at the current touch position without a jump.
- The drawn candle dataset must always be the state candles of the current interval. The `PriceChart` update block decides between rebuilding and patching in place using `viewportKey()` (symbol + interval + size + first/last candle time), `intervalChanged`, `tfSwitching` (loading a new TF while the state still holds the old candles) and `datasetStale` (drawn `entryCount != state.candles.size`). Do not reduce that gate to symbol+interval, and never patch in place while `tfSwitching`; `updateLastCandleInPlace` must keep its `ds.entryCount == state.candles.size` guard.
- `INITIAL_VISIBLE_CANDLES` (100) is the first-mount window. `applySyncAndInitialZoom` repositions inside `post { }`, so anything else that moves the matrix must not race with it.
- The last used interval is remembered in `IndicatorPrefsRepository.lastChartInterval` (in-memory, written in `loadChartData`) and used when the nav argument is empty: leaving the detail screen and coming back must keep the timeframe.

#### Shared presentation sources of truth

- `core/Timeframes.kt` is the only catalog for chart intervals, durations, exchange mappings, MA timeframes, alert settings, taker support, and chart aggregation. Do not add another local timeframe list.
- `ui/theme/Color.kt` contains `ChartColors`, including Compose colors, alpha variants, and hex values used by chart rendering and persisted overlay defaults. Do not add new chart color literals elsewhere.
- `presentation/crypto_detail/PriceFormat.kt` owns the chart, Pulse, repository, axis-price, and compact-volume policies. Keep the named policies separate when their output contracts differ, but do not duplicate their implementations.

#### Incremental indicator caches

- Two layers: `ClosedCandleCache<T>` (single value, today the SMC) and `IndicatorTail` (series tails for BB / RSI / RSI-MA / StochRSI / MACD / chart-TF MAs).
- `closedCandleVersion` must be bumped in `updatePrefs`, `loadChartData`, `syncTail` and `refreshExtraMas` (only when extra-TF candles actually changed). The version travels with the candle snapshot (`closedVersion` parameter), so a `syncTail` landing mid-computation cannot store an old result under a new key.
- `indicatorTailKey` excludes the live candle (its price is what the tail recomputes) and includes the time of candle `n-2` (other-TF MAs align by time). `IndicatorTailKeyTest` is the contract; do not change the format without updating it.
- Never cache RSI divergences: the `n-6` fractal pivot is confirmed with a window that includes the live candle. `RsiDivLiveCandleTest` proves it (8 of 1600 random cases change).
- `recomputeIndicatorTail` returns `null` whenever it cannot guarantee equivalence; the caller must fall back to the full computation. Never return a partial or empty series instead.
- `buildIndicatorTailState` returns null below ~27 candles (it needs `MACD_SLOW - 1` closed candles). With exactly 34 candles the MACD series has one point and that DEA is the seed, so the tail recomputes only the full MACD; covered for n = 33/34/35/36.
- Do not widen the 1e-9 tolerance in `IndicatorTailTest` or drop a series from it: add cases instead.
- The tail keeps exact state only at index `n-2`. If you add a series, extend `IndicatorTailTest` with a last-value comparison (1e-9) before wiring it.

### Wallet Balances

- CoinStats powers balances.
- Calls are intentionally sequential with delays to avoid rate limits.
- Do not parallelize CoinStats balance calls unless rate behavior is measured and handled.
- Treat 401/403/429 as user-visible errors.

### Transaction History

- CoinStats powers transaction history.
- Query supported `connectionId` values sequentially.
- `Fill` transaction types from CoinStats are synthetic balance records and are currently filtered out.
- Keep grouping by network in the UI.

### Alerts And Notifications

- One notification channel per picked tone: `priceChannelIdFor(soundUri)` derives the id from the resolved URI hash and `ensurePriceChannel` deletes the other `price_alerts*` channels. That is what makes a newly picked tone actually play on MIUI/HyperOS; do not go back to a single fixed channel id.
- Never open or validate the sound URI from the app (`openAssetFileDescriptor`, etc.). The app has no media read permission and the sound is played by SystemUI, so probing it silently downgrades valid tones to the default. `resolveSound` stays syntactic (blank / unparseable / no scheme → default).
- `MediaPlayer: NullPointerException: uri` in logcat is MIUI's hour-of-day "nature sound", logged by `system_server`/`systemui`. It is not this app; do not add defensive code for it.
- Constants: `CHANNEL_ALERTS = div_alerts`, `CHANNEL_PRICE = price_alerts` (+ `_<hash>` per tone), `CHANNEL_SERVICE = div_monitor_min`, `SERVICE_NOTI_ID = 7701`. Price alerts use `alertId.hashCode()` as notification id and a five-minute repeat throttle lives in `DivScanService`.
- Silent alert triage: does a `price_alerts_<hash>` channel with the expected `mSound` exist (`adb shell dumpsys notification --noredact`)? Is it `mDeleted=true`? Divergence notifications intentionally use the system default tone, only price alerts carry the picked one.
- Channel ids, notification ids and the five-minute throttle are documented in `docs/ALERTS_NOTIFICATIONS.md`.

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
| `DivAlertNotifier.priceChannelIdFor` + `resolveSound` | a silent alert with no error at all | `docs/ALERTS_NOTIFICATIONS.md` §2–3 |
| `Timeframes.kt`, `ChartColors`, `PriceFormat.kt` | shared sources of truth | this file |

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

## Performance Guidelines

- Preserve coroutine cancellation. Always rethrow `CancellationException`.
- Move CPU-heavy chart calculations off the main thread.
- Avoid network loops faster than needed.
- Use cached/remembered derived UI state in Compose when grouping/filtering large lists.
- Avoid excessive background/service updates.
- Chart indicators must stay incremental: the 2 s tick should only move the last point (plus the cached SMC). A full `toChartComputation` per tick is the regression this project already paid for.

## Database Notes

Room database version is in:

```text
app/src/main/java/com/defitracker/app/data/local/AppDatabase.kt
```

The app currently uses:

```kotlin
version = 6
MIGRATION_5_6
```

`MIGRATION_5_6` adds the `price_alerts` table without touching existing data. `AppModule` registers that migration and still keeps `fallbackToDestructiveMigration()` as a last-resort development fallback. Do not bump schema casually; add and register an explicit migration for every production-facing schema change.

`PriceAlertDao` stores point/range LONG/SHORT alerts. `DivScanService` checks enabled price alerts sequentially, notifies on a hit, and throttles repeat notifications to five minutes.

## Secrets

The CoinStats API key is read from the ignored root-level file:

```text
local.properties
```

Gradle exposes it as `BuildConfig.COINSTATS_API_KEY`, using an empty default when the property is absent. Never hard-code, log, or include the key in source control. If asked to prepare for release, use a backend proxy or another production-safe secret mechanism.

## Verification Checklist

Before final response after code changes:

- Run `:app:assembleDebug` and `:app:testDebugUnitTest` when possible; report the real per-class test counts (read `app/build/test-results/testDebugUnitTest`), not just "BUILD SUCCESSFUL".
- Mention if build or device verification could not be run and why.
- Check that UI changes keep existing labels/routes intact.
- Confirm no unrelated files were reverted and no temporary probe was left behind.
- For chart or alert changes, state which guarded test covers the change and update `docs/` if a contract changed.
- Summarize changed files and behavior, not every small implementation detail.

## Adding A New Sub-Indicator (Chart Standard)

Use the existing MACD behavior as the reference (`MacdCalc.kt` and the current `MacdChart` implementation), but put every new chart component in a focused file; do not grow `CryptoDetailScreen.kt`:

- Pure calc in `presentation/crypto_detail/<Name>Calc.kt`, aligned to candle index as `List<Pair<Long, Double>>`.
- State fields in `CryptoDetailState` + `ChartComputation`; compute inside `toChartComputation` (already off-main); copy into all three `state.copy` blocks (tick, `loadChartData`, `syncTail`).
- Prefs flag (`*Visible`, default off unless requested) in `IndicatorPrefs` + DataStore key + `prefsKey()` + `toggle*()` in the ViewModel.
- Sub chart as `AndroidView` cloning `RsiChart` (viewport/time-axis/in-place tick update) in the new focused file. If it needs bars, use `CombinedChart` like `MacdChart`.
- Highlight rule (crash standard): never call `highlightValue(x, 0)` directly on a `CombinedChart` sub — `CombinedData` throws `IndexOutOfBoundsException` (`dataIndex -1`). Always go through `highlightXSafe(x)`, which pins `dataIndex = 0` (its `LineData`) with try/catch fallback.
- Integrate through extracted chart wiring for `PriceChart` refs, `sync*` helpers, layout weights/dividers, and the `IndicatorsSheet` grid (`PillGrid`); do not append that wiring to `CryptoDetailScreen.kt`.
- Network-fed subs (like taker C/V from Binance Futures) are the exception: fetch in `loadChartData`/tick via `repository`, align by candle time in the ViewModel, keep them out of `toChartComputation`.
- If the series must stay exact per tick, add its tail to `IndicatorTail` (state at `n-2` + a last-value recompute) and cover it in `IndicatorTailTest`; otherwise it will be recomputed in full on every tick. Series that depend on the live candle (like RSI divergences) must stay in the full path.

## Environment And Device Verification

- `./r -Device <serial>` (repo helper: `r.ps1` → `run-debug.ps1`) builds, installs with `adb -s` and launches. Always pass `-Device`: `adb devices` usually lists a wireless serial plus an mDNS duplicate.
- Screenshots: `adb shell screencap -p /sdcard/x.png` then `adb pull`. Do not use `exec-out >` from PowerShell, it corrupts the binary.
- For measurements keep the screen on (`adb shell svc power stayon true`) and restore it (`false`); with the screen off the chart never recomposes and counters read zero.
- Unit tests run on the JVM without Robolectric or `returnDefaultValues`: Android classes (`Uri`, `Settings`, `Context`) throw. Keep decision logic in pure Kotlin if it must be tested.
- Never rewrite Kotlin sources with PowerShell `Set-Content`/`Out-File`; it re-encodes UTF-8 (mojibake in accents, BOM). Use the editor tools.
- Temporary probes (tick/rebuild counters) are fine for verification, but remove them and grep for leftovers before reporting.
- Steady state on 1m: ~20 chart updates per minute and **1 dataset rebuild per minute** (one per closed candle). More rebuilds means the gate regressed.

## Common Pitfalls

- Treating API errors as empty lists.
- Parallelizing third-party API calls and hitting rate limits.
- Displaying tiny token amounts as `0.0000`; use adaptive precision.
- Breaking Wallet/History by changing DTO fields without checking actual API JSON.
- Reintroducing removed fallback clients without a clear current use.
- Rebuilding the chart dataset on every tick, or patching in place on a dataset whose size does not match the state.
- Caching anything that depends on the live candle (RSI divergences) or skipping a `closedCandleVersion` bump.
- Validating the alert sound URI from the app instead of letting the system play it.
- Chasing `MediaPlayer` NPE lines in logcat: they come from MIUI, not from this app.
- Assuming unit tests can touch Android framework classes.
