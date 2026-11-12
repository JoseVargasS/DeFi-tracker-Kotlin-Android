# Development environment, device verification and measurements

Audience: any agent (or human) that needs to build, install, measure or profile this
app. Read this before adding a temporary probe, running adb, or reporting a
performance claim.

State: reflects `main` after the Bitunix/BingX sources (2026-11-11, 127 unit tests /
22 classes green). Read the count from the test results, not from this line.

> Rules live in `AGENTS.md`. This document has the recipes.

---

## 1. Build and test

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:testDebugUnitTest --console=plain   # JVM unit tests
.\gradlew.bat :app:assembleDebug --console=plain       # debug APK
```

The wrapper is versioned in the repo. On Unix-like shells use `./gradlew`. If the
wrapper cannot run, use Android Studio or an installed Gradle with JDK 17.

**Always report the real per-class test counts** (read
`app/build/test-results/testDebugUnitTest/*.xml`), not just "BUILD SUCCESSFUL". The
suite covers the chart caches, the incremental tail, the key contracts, the alert math,
the socket parsers, the kline order and the list price merge, plus the pure indicator maths.

Unit tests run on the JVM **without Robolectric and without `returnDefaultValues`**:
`Uri`, `Settings` and `Context` throw. Keep decision logic in pure Kotlin if it has to
be tested — that is why the cache keys and the staleness guards are plain functions.

---

## 2. Install and launch on a device

```powershell
./r -Device <adb-serial>        # repo helper: r.ps1 -> run-debug.ps1
```

Always pass `-Device`: `adb devices` usually lists a wireless serial **and** an mDNS
duplicate of the same device, and installing to the wrong one wastes a build.

---

## 3. Screenshots

```powershell
adb -s <serial> shell screencap -p /sdcard/x.png
adb -s <serial> pull /sdcard/x.png "$env:TEMP\x.png"
```

Do **not** use `adb exec-out screencap -p > file.png` from PowerShell: it corrupts the
binary.

---

## 4. Measurements

Keep the screen on while measuring, and restore it afterwards:

```powershell
adb -s <serial> shell svc power stayon true
adb -s <serial> shell svc power stayon false
```

With the screen off the chart never recomposes and every counter reads zero.

### 4.1 Counting chart updates and dataset rebuilds

There is no permanent instrumentation. Add a temporary counter (a `Log.d` in the
`PriceChart` update block, one for the tick and one for the rebuild branch), run 2–3
minutes, read it with:

```powershell
adb -s <serial> logcat -s CryptoDetailVM ChartPipeline
```

Then **remove the probe and grep for leftovers before reporting**:

```powershell
Select-String -Path app/src/main/java -Pattern "probe|TEMP|counter" -Recurse
```

### 4.2 Expected steady state (1m timeframe)

| Metric | Expected |
|---|---|
| chart updates | ~20 per minute (one per 2 s tick) |
| dataset rebuilds | **1 per minute** (one per closed candle) |
| REST calls to the exchange | ~2 per minute (the 24h stats) + taker per its cadence; the price comes from the socket |

More than one rebuild per minute means the rebuild gate regressed — see
`docs/CHART_PIPELINE.md` §2.

---

## 5. Editing files safely

- **Never** rewrite Kotlin sources with PowerShell `Set-Content` / `Out-File`: it
  re-encodes UTF-8 (mojibake in the accents, plus a BOM). Use the editor tools.
- Keep the file's existing line endings; the repo is mixed CRLF/LF and Git warns about
  it on every diff.
- Files under `app/src/main/java/com/defitracker/app/presentation/crypto_detail/` must
  stay under ~600 lines. If a change would cross that, split by responsibility first.

---

## 6. Temporary probes

Probes are fine for verification, but they are not part of the deliverable:

1. Remove the probe code.
2. Grep for leftovers (see §4.1).
3. Say in the final report whether a probe was used and that it was removed.
