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
- Bottom-sheet UI: the matching file under `sheets/`; shared sheet controls belong in `SheetPrimitives.kt` or `WideColorPicker.kt`.

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

#### Shared presentation sources of truth

- `core/Timeframes.kt` is the only catalog for chart intervals, durations, exchange mappings, MA timeframes, alert settings, taker support, and chart aggregation. Do not add another local timeframe list.
- `ui/theme/Color.kt` contains `ChartColors`, including Compose colors, alpha variants, and hex values used by chart rendering and persisted overlay defaults. Do not add new chart color literals elsewhere.
- `presentation/crypto_detail/PriceFormat.kt` owns the chart, Pulse, repository, axis-price, and compact-volume policies. Keep the named policies separate when their output contracts differ, but do not duplicate their implementations.

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

## UI Guidelines

- Use Material 3 Compose components.
- Keep cards compact, dark, and information-dense.
- Use stable `LazyColumn` keys for lists.
- Keep transaction and wallet screens grouped by network where applicable.
- Show loading, empty, and error states distinctly.
- Avoid one giant decorative redesign. Improve the actual workflow.
- For charts, remember MPAndroidChart is embedded through `AndroidView`; avoid unnecessary recreation.
- The price countdown refreshes only its tag area when the text changes; keep that path separate from price/data invalidations.

## Performance Guidelines

- Preserve coroutine cancellation. Always rethrow `CancellationException`.
- Move CPU-heavy chart calculations off the main thread.
- Avoid network loops faster than needed.
- Use cached/remembered derived UI state in Compose when grouping/filtering large lists.
- Avoid excessive background/service updates.

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

- Run `:app:assembleDebug` when possible.
- Mention if build could not be run and why.
- Check that UI changes keep existing labels/routes intact.
- Confirm no unrelated files were reverted.
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

## Common Pitfalls

- Treating API errors as empty lists.
- Parallelizing third-party API calls and hitting rate limits.
- Displaying tiny token amounts as `0.0000`; use adaptive precision.
- Breaking Wallet/History by changing DTO fields without checking actual API JSON.
- Reintroducing removed fallback clients without a clear current use.
