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
- Do not remove APIs or DTOs just because they are not currently used; several clients are kept for fallback/future work.
- Surface API errors clearly. Do not convert auth/rate-limit errors into empty UI states.
- Never write the word "ponytail" inside code comments in any file.

## Important Commands

Preferred verification:

```bash
:app:assembleDebug
```

If wrapper scripts are unavailable in the workspace, use Android Studio or an installed Gradle distribution with JDK 17.

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
- `data/remote`: Retrofit API interfaces and DTOs.
- `data/repository`: concrete repository implementation.
- `domain/repository`: repository contract.
- `domain/model`: app/domain UI models.
- `presentation/*`: Compose screens and ViewModels.
- `di`: Hilt module.
- `ui/theme`: colors/theme.
- `widget`: Glance app widget.

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
- Price list refresh should stay conservative. Avoid very short intervals or per-item busy loops.

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

## Performance Guidelines

- Preserve coroutine cancellation. Always rethrow `CancellationException`.
- Move CPU-heavy chart calculations off the main thread.
- Avoid network loops faster than needed.
- Use cached/remembered derived UI state in Compose when grouping/filtering large lists.
- Avoid excessive widget updates.

## Database Notes

Room database version is in:

```text
app/src/main/java/com/defitracker/app/data/local/AppDatabase.kt
```

The app currently uses:

```kotlin
fallbackToDestructiveMigration()
```

Do not bump schema casually. If this becomes production-facing, add proper migrations.

## Secrets

API keys currently live in:

```text
app/src/main/java/com/defitracker/app/core/Constants.kt
```

This is acceptable for the current local/demo workflow, but not for production. If asked to prepare for release, move keys to `local.properties`, Gradle `BuildConfig`, or a backend proxy.

## Verification Checklist

Before final response after code changes:

- Run `:app:assembleDebug` when possible.
- Mention if build could not be run and why.
- Check that UI changes keep existing labels/routes intact.
- Confirm no unrelated files were reverted.
- Summarize changed files and behavior, not every small implementation detail.

## Adding A New Sub-Indicator (Chart Standard)

Follow the MACD path (`MacdCalc.kt`, `MacdChart` in `CryptoDetailScreen.kt`):

- Pure calc in `presentation/crypto_detail/<Name>Calc.kt`, aligned to candle index as `List<Pair<Long, Double>>`.
- State fields in `CryptoDetailState` + `ChartComputation`; compute inside `toChartComputation` (already off-main); copy into all three `state.copy` blocks (tick, `loadChartData`, `syncTail`).
- Prefs flag (`*Visible`, default off unless requested) in `IndicatorPrefs` + DataStore key + `prefsKey()` + `toggle*()` in the ViewModel.
- Sub chart as `AndroidView` cloning `RsiChart` (viewport/time-axis/in-place tick update). If it needs bars, use `CombinedChart` like `MacdChart`.
- Highlight rule (crash standard): never call `highlightValue(x, 0)` directly on a `CombinedChart` sub — `CombinedData` throws `IndexOutOfBoundsException` (`dataIndex -1`). Always go through `highlightXSafe(x)`, which pins `dataIndex = 0` (its `LineData`) with try/catch fallback.
- Wire into `PriceChart` refs, `sync*` helpers, layout weights/dividers, and the `IndicatorsSheet` grid (`PillGrid`).

## Common Pitfalls

- Treating API errors as empty lists.
- Parallelizing third-party API calls and hitting rate limits.
- Displaying tiny token amounts as `0.0000`; use adaptive precision.
- Breaking Wallet/History by changing DTO fields without checking actual API JSON.
- Reintroducing removed fallback clients without a clear current use.
