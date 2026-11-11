# Data sources: exchanges, sockets, caches and known costs

Audience: any agent (or human) touching `data/remote/**`, `data/repository/**`, the
detail tick loop, or the Pairs screen price flow. Read this before changing an
endpoint, a cadence, a cache or a fallback.

State: reflects `main` at `ce22db7` + the docs pass + the socket-fed detail
(72 unit tests green).

> Rules live in `AGENTS.md`. This document explains them: what each source is used
> for, why an endpoint was chosen, and what it costs. If the two disagree, `AGENTS.md`
> wins and this file must be fixed in the same change.

---

## 1. Which source powers what

| Source | Powers | Transport |
|---|---|---|
| Binance spot (`source == "Binance"`, the default) | symbol discovery, 24h stats, spot price, klines | REST + WS |
| MEXC USDⓈ-M futures (`source == "MEXC"`) | futures price, futures klines | REST + WS |
| TradingView (`source == "TV"`) | chart candles for symbols the exchanges do not list | WS (own client) |
| CoinStats | wallet balances, transaction history | REST + API key |

Retro's Binance, Binance Futures, CoinStats and MEXC clients share the **named REST
`OkHttpClient`** from `AppModule`. The WebSocket client is a different one: long-lived
read timeout plus ping settings, because a socket that idles for minutes must not die.

`TradingViewClient` is a WebSocket client with its own timeframe→resolution mapping and
a short memo cache (the detail re-reads it every `TV_DETAIL_MS = 15s`, not every tick).

---

## 2. Live price sockets

| Stream | Endpoint | Push | Fields |
|---|---|---|---|
| `BinanceTickerStream` | `wss://stream.binance.com:9443/stream?streams=!miniTicker@arr` | ~1 s | `close`, `open24h`, `quoteVolume` |
| `MexcTickerStream` | `wss://contract.mexc.com/edge` (`sub.tickers`) | ~2 s | `lastPrice`, `riseFallRate`, `volume24` (contracts) |

Both reconnect on their own with exponential backoff capped at 30 s. MEXC kills idle
connections after ~60 s, so the client pings every 15 s.

`!miniTicker@arr` is the **all-symbols** array stream: one socket carries every spot
symbol, which is why the Pairs screen can show live prices for symbols the user never
subscribed to individually.

MEXC does not publish a 24h quote volume in the ticker, so the repository approximates
it as `lastPrice * volume24`; the REST ticker corrects it later.

### 2.1 `observeLivePrices()` is SHARED — one socket pair for the whole app

```kotlin
fun observeLivePrices(): Flow<Map<String, LiveQuote>>   // CryptoRepositoryImpl
```

The upstream is built with `callbackFlow` (every *collection* would open its own pair of
sockets), so the repository shares it once:

```kotlin
private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val sharedLivePrices = buildLivePrices()
    .shareIn(liveScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
```

- **One socket pair for the whole app**, no matter how many screens collect it (the Pairs
  screen and the detail screen both do).
- The scope is the **repository's own app scope**, never `viewModelScope`: the socket must
  outlive the ViewModel, and a ViewModel scope would tear the shared flow down.
- `WhileSubscribed(5_000)` closes the sockets 5 s after the last collector leaves, so the
  app does not hold connections in the background.
- `replay = 1` hands the last map to a new collector right away (no blank first second).

The map is keyed `"$symbol-$source"` (e.g. `"BTCUSDT-Binance"`, `"BTC_USDT-MEXC"`).

Each `LiveQuote` carries two extra fields the detail needs:

| Field | Why |
|---|---|
| `priceValue: Double` | the raw push price. `price` is a **formatted** string (`formatRepositoryPrice`: 6 decimals under $1) and rounding it would move the chart's price by up to ~3 % on cheap coins |
| `receivedAtMs: Long` | when the push arrived. The freshness guard compares it against the clock; with `replay = 1` a replayed quote keeps its **old** timestamp, so a dead socket is detected instead of being mistaken for a fresh one |

### 2.2 Known cost: the merge

`combine(binance, mexc) { b, m -> b + m }` rebuilds a map of **all** symbols on every
push of either socket (~3,000 entries copied per second). The Pairs screen only needs
its tracked symbols. This is the largest remaining allocation in the list path; it is
not fixed because changing the repository contract is riskier than the gain. If you
touch it, keep the `"$symbol-$source"` key and the fallback behaviour.

---

## 3. Pairs screen price flow

- **Live:** `observeLivePrices()` (above), collected in `CryptoListViewModel.startLivePrices()`.
- **Visual cap:** at most `LIVE_PUBLISH_MS = 250ms` between two state writes (4 fps).
  The collection still runs at socket speed; only the *write* is capped, so no update is
  lost — the next push carries newer data. The first push goes through immediately.
  Do not use `sample()` here: it re-emits the last value on every tick, which would make
  the ViewModel do ~4× the work per second instead of less.
- **REST fallback:** every `PRICE_REFRESH_MS = 30s`.
- **Sparklines:** every `SPARK_REFRESH_MS = 5min`, in chunks of 3 pairs with a 60 ms
  delay, cached with a 5 min TTL (`SPARK_CACHE_MAX_ENTRIES = 60`).

`CryptoListScreen` reads `sparklines`/`volumes` through `derivedStateOf` and reads
`.value` **inside** the item, so a price tick does not invalidate the whole list.

---

## 4. Detail screen (chart) price flow

The detail reads the **same shared socket** as the Pairs screen: no second socket pair,
no REST poll for the price.

| Piece | Source | Cadence |
|---|---|---|
| price of the in-progress candle | socket (`LiveQuote.priceValue`) | every push (~1 s), consumed by the 2 s tick |
| candle high/low | derived from the price | every tick |
| live volume bar | socket delta (`quoteVolume` delta ÷ price) | every tick |
| 24h high/low, absolute change | REST `getPairDetail` | every `DETAIL_REST_REFRESH_MS = 30s` |
| fallback if the socket dies | REST `getPairDetail` | every tick (2 s) |

The tick loop (`CryptoDetailViewModel.startUpdates`) still runs every
`DETAIL_REFRESH_MS = 2s` — **the cadence did not change**, only where the price comes
from. The 2 s cadence is a **product decision**: the candle and the price must move in
real time in every timeframe, including 4h and 1d. Do not slow it down per timeframe.

### 4.1 The freshness guard (the anti-freeze rule)

`LivePriceFeed.kt` holds the decision as **pure functions** (`LivePriceFeedTest` covers
them):

```kotlin
isSocketPriceFresh(quote, nowMs)   // quote != null, priceValue > 0, 0 <= now - receivedAtMs <= 15s
shouldFetchRest(lastRestAt, nowMs, gapMs)
socketBaseVolumeDelta(qNow, qPrev, price)
```

`LIVE_FRESH_WINDOW_MS = 15s`: if no tick arrived for this symbol in 15 s, the socket is
considered dead and the tick falls back to REST. Without this guard a dead socket would
freeze the price **silently** — the chart would keep drawing the last candle.

The window is generous on purpose: an illiquid pair may not trade for minutes, and then
the REST fallback takes over (which is correct: there is nothing to push).

### 4.2 Volume: two sources, never double counted

The socket reports 24h volume in **quote** currency, the REST in **base** currency. The
candle's volume is in base, so the socket delta is divided by the price.

When the source changes (socket dies, or comes back) both baselines are reset
(`volSource`), so the same stretch of trading is never counted twice.

### 4.3 TV is different

TV has no ticker socket: its freshest real price is the TradingView memo, so the detail
keeps reading the REST every `TV_DETAIL_MS = 15s`. `LiveQuote` has no `"$symbol-TV"` key,
so the freshness guard naturally reports "not fresh" for TV and the REST path stays.


---

## 5. Klines

| Call | Page size | Pages |
|---|---|---|
| Binance spot | `CHART_KLINE_PAGE_SIZE = 1000` | `Timeframes.chartPageCount(interval)`: 2 for 1m..1h, 1 for 2h+ |
| MEXC futures | `MEXC_KLINE_PAGE_SIZE = 2000` | same counter |

Pages are fetched **newest first** and reversed once at the end:

```kotlin
return pages.asReversed().flatten()
```

The old `allKlines.addAll(0, page)` inserted at the head of the list, shifting every
element on every page (O(N) per page, O(N·pages) overall). If you see that pattern
again, it is a regression.

`klineCache` is an LRU of 24 entries with a 30 s TTL; `sparkCache` is an LRU of 60
entries with a 5 min TTL.

`chartPageCount` / `tvCountback` / durations live in `core/Timeframes.kt`, the single
catalog. Never add a second timeframe list.

---

## 6. Taker buy/sell volume

`BinanceFuturesApi.getKlines(symbol, interval, limit)` hits `fapi/v1/klines`.

**It used to be `futures/data/takerlongshortRatio`.** That endpoint only publishes
*closed* buckets, so the in-progress candle never appeared in the C/V Taker subchart —
on 15m/1h/4h/1d the live bar was missing. The klines endpoint carries the partial
candle, so index 5 is the volume and index 9 is the taker **buy base** volume of that
(in-progress) candle; `sell = volume - buy`.

The subchart is aligned to candle **open time** (ms), not to list position: the tail
sync shifts the list, the timestamps do not. `takerCache` is keyed by candle time and
is cleared when symbol or interval changes.

**Cadence** (`Timeframes.takerRefreshMs`):

| Interval | Sync |
|---|---|
| 1m, 5m | 2 s |
| 15m | 15 s |
| 30m, 1h | 30 s |
| 2h and up | 60 s |

The bar grows **locally** on every tick (the 24h volume delta); the network call only
corrects the buy/sell split. That is why the cadence can be slow without the bar
freezing.

MEXC pairs are mapped to Binance by removing the `_` (the taker data is always Binance
futures, weight 0, no auth). `TV` has no taker data.

---

## 7. CoinStats (wallet + history)

- Balances and transaction history are called **sequentially with delays**. Do not
  parallelise them: the rate limit is the reason they are sequential.
- `401 / 403 / 429` are user-visible errors, never empty states.
- `Fill` transaction types are synthetic balance records and are filtered out.
- Transactions are grouped by network in the UI.
- The API key comes from `local.properties` → `BuildConfig.COINSTATS_API_KEY`. Never
  hard-code, log or commit it.

---

## 8. What to check after touching any of this

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
- On a device: the list price moves every second, the chart candle moves every 2 s in
  1m **and** in 1d, and the taker bar shows the in-progress candle.
- **The detail's price must not freeze**: with the phone online, the header price and
  the last candle have to move within a couple of seconds. If they stop, the socket is
  dead and the REST fallback should be taking over (check the 15 s freshness window in
  §4.1).
- Socket count: `adb shell dumpsys connectivity | grep -i websocket` is not reliable;
  instead check the app does not open a second socket pair (see §2.1).

