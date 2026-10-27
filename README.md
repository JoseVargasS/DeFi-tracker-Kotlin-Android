# Octopus DeFi Tracker

Android app for tracking crypto pairs, interactive price charts, multi-chain wallet balances, and transaction history. The app is built with Kotlin, Jetpack Compose, Hilt, Room, Retrofit, OkHttp WebSockets, MPAndroidChart, Coil, and DataStore.

## What The App Does

Octopus is organized around three main tabs:

- `Pairs`: track USDT crypto pairs (Binance spot + MEXC futures), search symbols, view live prices, open detail charts, and review RSI divergence alerts from the bell icon.
- `Wallet`: save wallet addresses, inspect multi-chain token balances, copy/delete wallets, and refresh balances.
- `History`: inspect wallet transactions grouped by network using CoinStats wallet transactions.

The detail screen includes candlestick charts, volume, Bollinger Bands, StochRSI, RSI divergence marks (`Bull`/`Bear` plus early `Pre-Bull`/`Pre-Bear`), and SMC overlays.

## Current Features

- Real-time pair tracking for Binance-backed symbols plus MEXC perpetual futures.
- Search and add tracked crypto pairs.
- Pair detail screen with candlestick chart, live price updates, volume, Bollinger Bands, StochRSI, RSI divergences, and SMC structure/zones.
- Push alerts for RSI divergences (`Pre-Bull`/`Pre-Bear` early signals that upgrade to `Bull`/`Bear` on confirmation) on tracked MEXC futures pairs, with a foreground monitor scanning every 60s across configurable timeframes (default `5m`, `15m`, `30m`, `1h`).
- Alerts bottom sheet behind the bell icon: unread dot, arrow icons per direction, tap-to-open chart on the alert timeframe, and mark-all-seen action.
- Saved wallet list backed by Room.
- Multi-chain wallet balances through CoinStats.
- Transaction history through CoinStats, grouped by network.
- Price alerts per pair, supporting LONG/SHORT targets or ranges, persisted locally and delivered by the foreground monitor.
- Chart drawings, Fibonacci retracements, and LONG/SHORT position overlays persisted per symbol.
- Pulse analysis sheet with multi-timeframe moving-average confluence, support/resistance, RSI divergence, sweep/reclaim, and market-structure context.
- Compose navigation with animated transitions.
- Dark Material 3 UI tuned for a trading/portfolio app.
- Splash screen with app branding.

## Tech Stack

- Language: Kotlin
- UI: Jetpack Compose, Material 3, Compose Animation
- Navigation: Navigation Compose
- Dependency Injection: Hilt
- Persistence: Room
- Networking: Retrofit + Gson and OkHttp WebSockets
- Async: Kotlin Coroutines and Flow
- Charts: MPAndroidChart through `AndroidView`
- Images: Coil
- Preferences: Jetpack DataStore Preferences
- Minimum SDK: 26
- Target/Compile SDK: 34
- Java/Kotlin target: JVM 17

## Project Structure

```text
app/src/main/java/com/defitracker/app/
|-- core/
|   |-- Constants.kt
|   `-- Timeframes.kt
|-- alerts/
|   |-- DivAlertNotifier.kt
|   |-- DivMonitorPrefs.kt
|   `-- DivScanService.kt
|-- data/
|   |-- local/
|   |   |-- AppDatabase.kt
|   |   |-- DivAlertDao.kt
|   |   |-- DivAlertEntity.kt
|   |   |-- PriceAlertDao.kt
|   |   |-- PriceAlertEntity.kt
|   |   |-- TrackedPairDao.kt
|   |   |-- TrackedPairEntity.kt
|   |   |-- WalletDao.kt
|   |   `-- WalletEntity.kt
|   |-- remote/
|   |   |-- BinanceApi.kt
|   |   |-- BinanceFuturesApi.kt
|   |   |-- BinanceTickerStream.kt
|   |   |-- CoinStatsApi.kt
|   |   |-- MexcFuturesApi.kt
|   |   |-- MexcTickerStream.kt
|   |   |-- dto/
|   |   `-- tv/
|   |       `-- TradingViewClient.kt
|   `-- repository/
|       `-- CryptoRepositoryImpl.kt
|-- di/
|   `-- AppModule.kt
|-- domain/
|   |-- model/
|   `-- repository/
|-- presentation/
|   |-- alerts/
|   |   |-- AlertsBottomSheet.kt
|   |   `-- AlertsViewModel.kt
|   |-- crypto_detail/
|   |   |-- Analysis.kt
|   |   |-- chart/
|   |   |   |-- ChartRendering.kt
|   |   |   |-- ChartSynchronization.kt
|   |   |   |-- ChartUpdates.kt
|   |   |   |-- ChartGestureController.kt
|   |   |   |-- ChartGestureMath.kt
|   |   |   |-- DrawOverlayRenderer.kt
|   |   |   |-- FibOverlayRenderer.kt
|   |   |   |-- MacdChart.kt
|   |   |   |-- PositionOverlayRenderer.kt
|   |   |   |-- PriceChartMainRenderer.kt
|   |   |   |-- PriceChartRenderState.kt
|   |   |   |-- PriceCombinedChart.kt
|   |   |   |-- RangeSelectionRenderer.kt
|   |   |   |-- RsiChart.kt
|   |   |   |-- SmcOverlayRenderer.kt
|   |   |   |-- StochRsiChart.kt
|   |   |   |-- TakerChart.kt
|   |   |   `-- VolumeOverlayRenderer.kt
|   |   |-- CryptoDetailScreen.kt
|   |   |-- CryptoDetailViewModel.kt
|   |   |-- DrawOverlay.kt
|   |   |-- FibDrawing.kt
|   |   |-- IndicatorPrefs.kt
|   |   |-- MacdCalc.kt
|   |   |-- PositionOverlay.kt
|   |   |-- PriceAlertSheet.kt
|   |   |-- PriceFormat.kt
|   |   |-- RsiCalc.kt
|   |   |-- sheets/
|   |   |   |-- AnalysisSheet.kt
|   |   |   |-- DrawingToolsSheet.kt
|   |   |   |-- FibLevelsSheet.kt
|   |   |   |-- IndicatorsSheet.kt
|   |   |   |-- MaListSheet.kt
|   |   |   |-- PositionConfigSheet.kt
|   |   |   |-- SheetPrimitives.kt
|   |   |   `-- WideColorPicker.kt
|   |   |-- RsiDiv.kt
|   |   |-- Signals.kt
|   |   `-- Smc.kt
|   |-- crypto_list/
|   |-- transactions/
|   `-- wallet/
|-- ui/theme/
|-- DeFiTrackerApp.kt
`-- MainActivity.kt
```

`core/Timeframes.kt` is the single catalog for interval labels, candle durations, Binance/MEXC/TradingView mappings, alert intervals, MA timeframes, taker support, and chart aggregation. Chart colors are centralized in `ui/theme/Color.kt` under `ChartColors`.

## Data Sources

The app currently has clients for several APIs:

- Binance: symbol search, ticker stats, prices, and klines.
- Binance WebSocket: `BinanceTickerStream` streams spot mini-tickers for live Pairs prices.
- Binance Futures: `BinanceFuturesApi` supplies unauthenticated taker buy/sell volume for the optional chart subpanel.
- MEXC Futures: perpetual contract list, REST tickers/klines, and `MexcTickerStream` for live futures prices and RSI divergence scans.
- TradingView: `TradingViewClient` uses a WebSocket chart session for the `TV` source, including timeframe resolution mapping and short-lived memoization.
- CoinStats: wallet balances and transaction history.

### API Keys

The CoinStats API key is read locally from `local.properties` and exposed to the app through Gradle `BuildConfig`. This file is ignored by Git and must not be committed.

To configure it:

1. Copy `local.properties.example` to `local.properties` in the repository root.
2. Set `COINSTATS_API_KEY` to your CoinStats API key:

```properties
COINSTATS_API_KEY=your_coinstats_api_key
```

The build uses an empty value when the property is missing. Wallet and History show a configuration error until the key is added.

## Setup

### Requirements

- Android Studio Iguana or newer.
- Android SDK 34 installed.
- JDK 17. Android Studio's bundled JBR works.
- Internet access for Gradle dependencies and external APIs.

### Open In Android Studio

1. Open this directory in Android Studio.
2. Let Gradle sync finish.
3. Select the `app` configuration.
4. Run on an emulator or physical device running Android 8.0+.

### Command Line Build

The repository includes `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and the wrapper properties. The wrapper is configured for Gradle 8.14.5.

```bash
./gradlew :app:assembleDebug
```

On Windows with JDK 17:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

In this workspace, the debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Main Screens

### Pairs

Files:

- `presentation/crypto_list/CryptoListScreen.kt`
- `presentation/crypto_list/CryptoListViewModel.kt`
- `presentation/crypto_list/components/CryptoPairItem.kt`

Behavior:

- Loads available USDT symbols from Binance (spot) and MEXC (futures).
- Adds/removes tracked pairs stored in Room.
- Refreshes prices periodically.
- Receives live Binance spot and MEXC futures prices through reconnecting WebSocket streams.
- Bell icon with unread dot opens the alerts bottom sheet (RSI divergence history, timeframe filters, monitoring toggle, mark-all-seen).
- Tapping an alert navigates to the pair chart on the alert timeframe.

### Alerts (RSI Divergences)

Files:

- `alerts/DivScanService.kt`
- `alerts/DivAlertNotifier.kt`
- `alerts/DivMonitorPrefs.kt`
- `presentation/alerts/AlertsBottomSheet.kt`
- `presentation/alerts/AlertsViewModel.kt`
- `presentation/crypto_detail/RsiDiv.kt`
- `presentation/crypto_detail/RsiCalc.kt`

Behavior:

- Foreground service scans tracked MEXC futures pairs every 60s, sequentially with small delays to respect rate limits. Requires `POST_NOTIFICATIONS`; the service notification channel is silent (`IMPORTANCE_MIN`, no status-bar icon).
- Detects TradingView-style RSI divergences twice per scan: lookback 2 (early `Pre-Bull`/`Pre-Bear`, yellow) and lookback 5 (confirmed `Bull`/`Bear`, green/red). A confirmed signal upgrades the same alert row instead of duplicating it.
- Only fresh signals notify (pivot within the last 3 closed candles); history older than 7 days is pruned.
- Push and sheet taps deep-link to `crypto_detail/{symbol}/{source}?interval={interval}`.
- Monitored timeframes are configurable and persisted in DataStore (default `5m`, `15m`, `30m`, `1h`).

### Crypto Detail

Files:

- `presentation/crypto_detail/CryptoDetailScreen.kt`
- `presentation/crypto_detail/CryptoDetailViewModel.kt`
- `presentation/crypto_detail/Analysis.kt`
- `presentation/crypto_detail/DrawOverlay.kt`
- `presentation/crypto_detail/FibDrawing.kt`
- `presentation/crypto_detail/IndicatorPrefs.kt`
- `presentation/crypto_detail/MacdCalc.kt`
- `presentation/crypto_detail/PositionOverlay.kt`
- `presentation/crypto_detail/PriceAlertSheet.kt`
- `presentation/crypto_detail/PriceFormat.kt`
- `presentation/crypto_detail/RsiCalc.kt`, `RsiDiv.kt`, `Signals.kt`, and `Smc.kt`

Behavior:

- Reads `symbol`, `source`, and optional `interval` from navigation arguments (alerts open the chart directly on their timeframe).
- Loads pair details and kline data from Binance, MEXC Futures, or TradingView depending on `source`.
- Receives Binance Futures taker buy/sell volume when the optional `C/V Taker` subchart is enabled and aligns it by candle time.
- Computes Bollinger Bands, moving averages, StochRSI, RSI, MACD, RSI divergences, SMC overlays, and Pulse inputs off the main thread where applicable.
- Paints confirmed (`Bull`/`Bear`) and early (`Pre-Bull`/`Pre-Bear`) RSI divergence marks, plus OKX-style text-only SMC tags (BOS/CHoCH/EQ/FVG) placed off candle bodies.
- Supports per-symbol drawings, Fibonacci retracements, LONG/SHORT position overlays, and price-alert configuration from the detail chart.
- Persists indicator visibility/configuration in DataStore, including moving-average type/period/timeframe/color/width, SMC toggles, Fibonacci settings, and overlay data.
- The Pulse sheet evaluates support/resistance zones, 21/50/200 MA confluence across 15m/1h/4h, fresh RSI divergence, sweep/reclaim, and latest structure.
- Uses MPAndroidChart in Compose through `AndroidView`.
- The candle countdown updates only its price-tag area; price/data refreshes remain independent.
- Chart subcomponents, synchronization, live updates, the `PriceCombinedChart` view, gesture controller, overlay renderers, and bottom sheets live in the focused `crypto_detail/chart/` and `crypto_detail/sheets/` packages; `CryptoDetailScreen.kt` keeps the main screen and Compose/AndroidView wiring.

Timeframe selection and exchange resolution use `core/Timeframes.kt`. Price formatting lives in `PriceFormat.kt`: chart, Pulse, and repository policies remain named separately because they preserve different existing output contracts. Chart and overlay colors use `ui/theme/Color.kt:ChartColors` without changing their visible values.

### Wallet Explorer

Files:

- `presentation/wallet/WalletScreen.kt`
- `presentation/wallet/WalletViewModel.kt`

Behavior:

- Saves wallets in Room.
- Loads balances from CoinStats by chain.
- Uses sequential calls with small delays to avoid API rate limits.
- Shows real API/auth/rate-limit errors instead of silently treating them as empty balances.

### Transaction History

Files:

- `presentation/transactions/TransactionsScreen.kt`
- `presentation/transactions/TransactionsViewModel.kt`
- `data/remote/dto/CoinStatsTransactionDto.kt`

Behavior:

- Uses CoinStats `/wallet/transactions`.
- Queries networks sequentially to avoid rate limits.
- Filters CoinStats `Fill` records because they are synthetic balance entries rather than useful user transactions.
- Groups transactions by network in the UI.

## Local Database

Room database:

```text
data/local/AppDatabase.kt
```

Entities:

- `TrackedPairEntity`
- `WalletEntity`
- `DivAlertEntity` (RSI divergence alerts with `PRE`/`CONFIRMED` status)
- `PriceAlertEntity` (point/range LONG/SHORT price alerts)

DAOs:

- `TrackedPairDao`
- `WalletDao`
- `DivAlertDao`
- `PriceAlertDao`

The database is version 6. `MIGRATION_5_6` adds the `price_alerts` table without changing existing data, and `AppModule.kt` registers it before the development fallback `fallbackToDestructiveMigration()`. Add an explicit migration for every future production-facing schema change.

## Dependency Injection

Hilt module:

```text
app/src/main/java/com/defitracker/app/di/AppModule.kt
```

It provides:

- Retrofit API clients.
- Binance/MEXC WebSocket ticker streams and the TradingView WebSocket client through a shared OkHttp client.
- Room database and DAOs.
- `CryptoRepositoryImpl` implementation.

## Known Notes And Caveats

- Do not make aggressive parallel calls to CoinStats; it can hit rate limits quickly.
- Keep divergence scans sequential with delays; parallelizing MEXC kline calls risks 429s. Android requires the monitor's foreground service notification; its channel stays silent and icon-free by design.
- Some third-party wallet transaction feeds include spam/fake tokens. UI should display the data clearly, but future filtering may be needed.
- `MIGRATION_5_6` preserves existing Room data while adding price alerts; the fallback can still wipe local Room data if a future schema change has no registered migration.
- API keys should not remain hard-coded for a public release.

## Troubleshooting

### Wallet balances show empty

Check:

- `COINSTATS_API_KEY` is configured in `local.properties` and is valid.
- The address is a public wallet address supported by CoinStats.
- The API did not return 401/403/429.

The app now surfaces common CoinStats auth/rate-limit errors in UI state.

### Transaction history is empty

Check:

- The wallet is saved and selected.
- CoinStats supports the selected chain/address.
- CoinStats did not return synthetic-only records. `Fill` records are intentionally filtered.

### Build cannot find Java

Set `JAVA_HOME` to JDK 17. With Android Studio:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
```

### Build cannot find Gradle

Use the versioned wrapper from the repository:

```powershell
.\gradlew.bat :app:assembleDebug
```

The project uses Gradle 8.14.5 as configured in `gradle/wrapper/gradle-wrapper.properties`.

## License

This project is for demonstration and personal development use unless a separate license is added.
