# Design system: tokens, patterns and visual rules

Audience: agents (or humans) touching any UI, especially the chart. This app is a dark
trading UI: information density and numeric legibility come before decoration.

State: reflects `main` at `ce22db7`.

> Rules live in `AGENTS.md`. This document is the inventory behind them: which token exists,
> where it lives, what is tuned by hand, and what is known visual debt. If the two disagree,
> `AGENTS.md` wins.

---

## 1. Non-negotiables

- **Never change the font family or numeric legibility** unless the user asks for it. Prices
  use tabular figures (`GeistTnum`) so digits do not jitter while updating.
- **No new color literals** outside `ui/theme/Color.kt`. If a new tone is needed, add it there
  (Compose `Color` + a `ChartColors` hex if the Canvas needs it).
- **No decorative redesign.** Compact, dark, information-dense; improve the actual workflow.
- Nothing may break narrow phones (avoid hard-coded widths for content rows).
- Keep the visual language consistent with an exchange app (OKX/TradingView-like), not with a
  landing page.

---

## 2. Color (`ui/theme/Color.kt`)

Surface and text:

| Token | Value | Use |
|---|---|---|
| `Black` / `DarkGray` / `LightGray` | `#000000` / `#0A0A0A` / `#1A1A1A` | chart background and app backgrounds |
| `SurfaceColor` | `#121212` | generic surface |
| `CardBg` | `#1A1D23` | cards, rows, chips |
| `InputBg` | `#1E2026` | inputs |
| `SheetBg` / `sheetOverlay` | `#141518` / `#EE141518` | bottom sheets and their scrim |
| `LineDiv` | `#2A2E35` | dividers |
| `TextGray` | `#9AA0A8` | secondary text |
| `border` | `#3A3F47` | outlines |

Semantic:

| Token | Value |
|---|---|
| `AppGreen` / `APP_GREEN_HEX` | `#1ECB81` |
| `AppRed` / `APP_RED_HEX` | `#F6465D` |
| `AppGold` / `GOLD_HEX` | `#FFD60A` |
| `blue` / `BLUE_HEX` | `#2196F3` |
| `lightBlue` | `#64B5F6` |

Active/pressed states are the same hues at `CC` alpha: `activeSurface`, `activeGreen`,
`activeRed`. Overlays: `overlayBlack` (`#66000000`), `darkOverlay` (`#B3000000`).

Chart-only palette (`ChartColors`, hex because it feeds the Canvas):

| Name | Value | Where |
|---|---|---|
| `CHART_GREEN_HEX` / `CHART_RED_HEX` | `#0ECB81` / `#EF5350` | candles, volume |
| `CANDLE_SHADOW_HEX` | `#F4F4F4` | candle shadows (wicks) |
| `TEXT_SECONDARY_HEX` | `#ADB1B8` | axis labels, tags |
| `CARD_HEX` | `#1A1D23` | chart grid |
| `ORANGE_HEX` / `PINK_HEX` / `CYAN_HEX` / `PURPLE_HEX` / `WHITE_HEX` | `#FF9800` / `#E91E63` / `#22D8EB` / `#B39DDB` / `#FFFFFF` | MAs, BB, overlays |

Chart alphas are intentional and tuned; do not "normalize" them: SMC order blocks `42`,
FVG `32`, zone borders `120`, premium/discount `12`, equilibrium `170`; volume profile up
`92 / 70,222,226`, down `92 / 255,88,142`, POC `145 / 255,193,7`; measure fill `38` and
stroke `230` of `#1ECB81`.

---

## 3. Typography (`ui/theme/Type.kt`)

- Single family: **Geist** (regular/medium/semibold/bold from `res/font`), applied to the whole
  Material 3 `Typography` (`AppTypography`).
- Prices and any live number: `GeistTnum` (`fontFeatureSettings = "tnum"`). This is what keeps
  digits from jumping when the last decimal changes.
- Chart text is **not** Compose text: see §5.

---

## 4. Shapes, spacing and components

- Corner radii in use: `4.dp`–`6.dp` for small chips/tags, `8.dp` for pills and rows,
  `10.dp`–`12.dp` for sheets and cards. Stay in that set.
- Dense rows: `8.dp` horizontal padding, `6.dp` vertical, `6.dp`–`8.dp` gaps between chips.
- Shared components live in `presentation/components/OptionPillGrid.kt`:
  `PillOption` (data), `PillGrid` (selectable grid used by the indicator sheet), and
  `SheetSectionTitle`. Sheet layout primitives (grid cells, dividers, chart resize divider) are
  in `presentation/crypto_detail/sheets/SheetPrimitives.kt`; the color picker is
  `sheets/WideColorPicker.kt`.
- Bottom sheets: title (`SheetSectionTitle`) + `PillGrid`/`PillOption` rows + actions, on
  `SheetBg`. Keep them compact; long forms scroll, they do not shrink the chart.
- Increment on charts only through `AndroidView`; do not recreate the chart views (see
  `docs/CHART_PIPELINE.md`).

---

## 5. Chart visuals (the hand-tuned part)

- Candle geometry: `barSpace = 0.18f`, `shadowWidth = 0.7f` (thin wick, fat body, OKX-like).
- Default MAs: width `1.2f`, the 200-period one `1.4f`.
- Initial window: `INITIAL_VISIBLE_CANDLES = 100`.
- **Units are mixed on purpose:**
  - Custom `Canvas` paints (`labelPaint`, tag paints, SMC tags…) use **pixels**. Fixed values
    like `24f`/`28f` are px tuned for this UI, not dp.
  - Density-dependent canvas text scales explicitly, e.g. `pinnedYLabelPaint.textSize =
    density * 10f`.
  - MPAndroidChart **axis** `textSize` (`axisLeft.textSize = 8f`, `xAxis.textSize = 8f`) is dp
    (MPAndroidChart converts it internally).
  - Do not convert one to the other; a "dp fix" on a canvas paint makes chart labels huge or
    microscopic.
- Tags are text on the chart, not boxes over the candles: chips use `argb(215–220)` dark
  backgrounds, MAs of other timeframes are labelled `1h.SMA200`-style with a dashed elbow.
- Volume profile and pinned Y labels share the right edge; they can overlap. That is
  pre-existing visual debt, not a regression (see §7).

---

## 6. Text rules inside the chart

- Price labels: `formatPriceForChart` / `formatAxisPrice` from `PriceFormat.kt`. Never
  hand-roll decimals or thousands separators: tiny tokens need adaptive precision.
- Number formatting for the Pulse/analysis: `PriceFormat.kt` too.
- The candle countdown repaints only its own tag area (`invalidateCountdownIfChanged`); do not
  move it into the general invalidation path.

---

## 7. Known visual debt (pre-existing, unrequested)

- `CryptoDetailScreen.kt` is ~2000 lines; chart code has been extracted to `chart/` and
  `sheets/`, but the screen still holds legacy wiring. Do not grow it.
- The right edge is crowded: pinned Y labels, volume profile, the POC tag, last-price tag and
  SMC/MA tags can overlap in busy configurations (user has 9 MAs + all SMC layers).
- The user's saved configuration (many MAs, all SMC toggles, divergences, taker) is dense by
  nature; the eye toggle ("Ocultar dibujos") is the supported way to get a clean chart.
- Mitigated SMC zones are intentionally **not** drawn (user requirement), so a filled zone
  disappears instead of fading out.

---

## 8. Check list before shipping a UI change

- Colors/fonts came from the tokens, no new literals.
- Numbers still tabular and readable at the smallest size used in the app.
- Nothing new hard-codes a width that a narrow phone would clip.
- Loading / empty / error states still distinguishable and unchanged in tone.
- The chart still paints with `px` paints and `dp` axes (§5).
- Screenshot before/after on the device for anything visual; the user reviews visuals closely.
