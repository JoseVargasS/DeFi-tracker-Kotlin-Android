# Data sources: exchanges, sockets, caches and known costs

Audience: any agent (or human) touching `data/remote/**`, `data/repository/**`, the
detail tick loop, or the Pairs screen price flow. Read this before changing an
endpoint, a cadence, a cache or a fallback.

State: reflects `main` after the multi-broker scanner (2026-11-12).

> Rules live in `AGENTS.md`. This document explains them: what each source is used
> for, why an endpoint was chosen, and what it costs. If the two disagree, `AGENTS.md`
> wins and this file must be fixed in the same change.

---

## 1. Which source powers what

| Source | Powers | Transport |
|---|---|---|
| Binance spot (`source == "Binance"`, the default) | symbol discovery, 24h stats, spot price, klines | REST + WS |
| MEXC USDⓈ-M futures (`source == "MEXC"`) | futures price, futures klines | REST + WS |
| Bitunix USDⓈ-M futures (`source == "Bitunix"`) | symbol discovery, 24h stats, futures price, futures klines (USDT pairs only) | REST + WS (per symbol) |
| BingX USDⓈ-M futures (`source == "BingX"`) | symbol discovery, 24h stats, futures price, futures klines (USDT pairs only) | REST + WS (per symbol) |
| TradingView (`source == "TV"`) | chart candles for symbols the exchanges do not list | WS (own client) |
| CoinStats | wallet balances, transaction history | REST + API key |

Retro's Binance, Binance Futures, CoinStats, MEXC, Bitunix and BingX clients share the
**named REST `OkHttpClient`** from `AppModule`. The WebSocket client is a different one:
long-lived read timeout plus ping settings, because a socket that idles for minutes must
not die.

Symbols are not interchangeable: Binance and Bitunix use `BTCUSDT`, MEXC uses `BTC_USDT`
and BingX uses `BTC-USDT`. The `source` string is part of the pair identity, which is why
`tracked_pairs` is keyed by `(symbol, source)` and the live map is keyed
`"$symbol-$source"` (`BTCUSDT-Binance` and `BTCUSDT-Bitunix` are two different pairs).

`TradingViewClient` is a WebSocket client with its own timeframe→resolution mapping and
a short memo cache (the detail re-reads it every `TV_DETAIL_MS = 15s`, not every tick).

**Bar shape:** TradingView sends `[time, open, high, low, close]` for the dominance
series (`CRYPTOCAP:BTC.D`, `CRYPTOCAP:USDT.D`) and `[time, open, high, low, close, volume]`
for the market-cap series (`CRYPTOCAP:TOTAL3`, `CRYPTOCAP:OTHERS`). `tvBar()` accepts both
and fills the volume with `0.0` when it is missing. Requiring six fields dropped every
dominance bar: those pairs stayed at `0.00` with no sparkline. Covered by
`TradingViewBarTest`.

**Pair icons** are decoration, not data: `CryptoPairItem` tries CoinCap
(`assets.coincap.io/assets/icons/{base}@2x.png`), then Binance's static logo
(`bin.bnbstatic.com/static/assets/logos/{BASE}.png`), then the pair's initial, so a row
never keeps an empty circle. CoinCap has no entry for recent listings (`BIO`, `VELODROME`,
`USUAL`) or for TV indexes (`BTC.D`, `TOTAL3`, `OTHERS`); a `1000PEPE`-style symbol also
retries the bare name.

---

## 2. Live price sockets

| Stream | Endpoint | Push | Fields |
|---|---|---|---|
| `BinanceTickerStream` | `wss://stream.binance.com:9443/stream?streams=<sym>@miniTicker/…` (combined URL) | ~1 s | `close`, `open24h`, `quoteVolume` |
| `MexcTickerStream` | `wss://contract.mexc.com/edge` (`sub.ticker`, one message per symbol) | ~2 s | `lastPrice`, `riseFallRate`, `volume24` (contracts), `amount24` (quote) |
| `BitunixTickerStream` | `wss://fapi.bitunix.com/public/` (`ch: ticker` + `ch: trade`, one message with N symbols) | ~1 s | `la` (last), `o` (open 24h), `q` (quote volume); trades: `t` (time), `p` (price), `v` (base volume) |
| `BingxTickerStream` | `wss://open-api-swap.bingx.com/swap-market` (`@ticker`) | ~1 s | `c` (last), `o` (open 24h), `q` (quote volume); gzip frames |

All four reconnect on their own with exponential backoff capped at 30 s. MEXC kills idle
connections after ~60 s, so the client pings every 15 s.

All four are **per-symbol**: each subscribes to the symbols of its own **tracked pairs**
and reconnects when that set changes (a pair is added or removed). A source with no
tracked pairs does not open a socket at all, and a pair that is not tracked has no live
price (the detail falls back to REST).

Measured cost of that choice (2026-11-11, same network):

| | whole market | per symbol |
|---|---|---|
| Binance | `!miniTicker@arr`, ~95 tickers/frame, **11 KB/s** | `stream?streams=…`, **0.5 KB/s** with 3 pairs |
| MEXC | `sub.tickers`, ~600 contracts/frame, **70 KB/s** | `sub.ticker` per symbol, **0.4 KB/s** with 1 pair, **0.56 KB/s** with 3 |

MEXC `sub.tickers` **ignores** `param.symbol`: the filter has to be the singular method
`sub.ticker`, one message per symbol. One socket accepts several of them (probed with 3)
and `unsub.ticker` removes one, but the app reconnects on a set change instead, like the
other three. Binance ignores a symbol that does not exist: the socket stays open and the
valid symbols keep streaming.

**Safeguard:** past 100 tracked Binance pairs the combined URL gets too long
(`streamUrl()` returns the all-market `!miniTicker@arr` URL instead). The parser handles
both shapes: `data` is an object in the combined stream and an array in the all-market one.

Measured on this network (2026-11-11): Bitunix and BingX **close the socket every 30–60 s**
with an abnormal close (`code 1006`, no close frame) — reproduced with a bare Node client
that does nothing but subscribe, so it is not the app and not the client's `pingInterval`
(removing it did not change the rate; MEXC in the same window had 0 drops). The streams
reconnect on their own, so the user-facing effect is a few seconds without a tick, not a
frozen price. Do not chase this as a bug; a `dropped` line every ~30–60 s is the baseline.

**Bitunix is the exception since 2026-11-12: it also closes at ~61 s, but a plain text
`PING` every 20 s keeps the connection open** (measured with a bare Node client: the socket
survived 75 s with PING and died at 63 s without it). That matters because the live candle
is built from the trades of the socket: every second without it is a second of missing
trades. `BitunixTickerStream` sends that `PING`; do not remove it. The backoff also resets
when a connection lasted more than 10 s, so a normal cut does not leave the socket down for
the 30 s cap.

BingX needs two things that are easy to delete by accident:

- **Ping every 4 s** (`{"ping":"<ms>"}`) or the server closes the connection.
- **One subscribe message per symbol** (`dataType: "BTC-USDT@ticker"`); a comma-separated
  list is accepted silently and then never pushes anything.

Every BingX data frame is **binary and gzip-compressed** (`1f 8b 08`); the subscription ack
arrives as plain text. `BingxTickerStream` handles both.

Neither exchange publishes a trustworthy 24h percentage in the socket (`Bitunix` sends
none, BingX's `P` is unreliable), so the repository computes it as
`(last - open24h) / open24h` and uses the ticker's `open` for both.

MEXC's per-symbol push does carry the real 24h quote volume in `amount24`, so the
repository uses it; `lastPrice * volume24` stays only as the fallback when `amount24`
does not arrive.

### 2.1 `observeLivePrices()` is SHARED — one socket per exchange for the whole app

```kotlin
fun observeLivePrices(): Flow<Map<String, LiveQuote>>   // CryptoRepositoryImpl
```

The upstream is built with `callbackFlow` (every *collection* would open its own set of
sockets), so the repository shares it once:

```kotlin
private val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val sharedLivePrices = buildLivePrices()
    .shareIn(liveScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
```

- **One socket per exchange for the whole app** (four in total), no matter how many screens
  collect it (the Pairs screen and the detail screen both do).
- The per-symbol streams are driven by `trackedPairDao.getAllTrackedPairs()`: only Bitunix
  pairs are subscribed on the Bitunix socket, and the same for BingX. A new tracked pair
  changes that list, which cancels and reopens that one socket.
- The scope is the **repository's own app scope**, never `viewModelScope`: the socket must
  outlive the ViewModel, and a ViewModel scope would tear the shared flow down.
- `WhileSubscribed(5_000)` closes the sockets 5 s after the last collector leaves, so the
  app does not hold connections in the background.
- `replay = 1` hands the last map to a new collector right away (no blank first second).

The map is keyed `"$symbol-$source"` (e.g. `"BTCUSDT-Binance"`, `"BTC_USDT-MEXC"`).

Each `LiveQuote` carries two extra fields the detail needs:

| Field | Why |
|---|---|
| `priceValue: Double` | the raw push price. `price` is a **formatted** string (`formatRepositoryPrice`: 6 decimals under $1, and 8 below `0.0001`) and rounding it would move the chart's price by up to ~3 % on cheap coins |
| `receivedAtMs: Long` | when the push arrived. The freshness guard compares it against the clock; with `replay = 1` a replayed quote keeps its **old** timestamp, so a dead socket is detected instead of being mistaken for a fresh one |

### 2.2 Known cost: the merge

`combine(binance, mexc, bitunix, bingx) { b, m, bu, bg -> b + m + bu + bg }` merges the
four maps on every push of any socket. Since every stream is per-symbol now, each map
carries only that source's tracked symbols, so the merge copies a handful of entries
instead of the ~3,000 it used to copy per second. Keep the `"$symbol-$source"` key and the
fallback behaviour if you touch it.

---

## 3. Pairs screen price flow

- **Live:** `observeLivePrices()` (above), collected in `CryptoListViewModel.startLivePrices()`.
- **Visual cap:** at most `LIVE_PUBLISH_MS = 250ms` between two state writes (4 fps).
  The collection still runs at socket speed; only the *write* is capped, so no update is
  lost — the next push carries newer data. The first push goes through immediately.
  Do not use `sample()` here: it re-emits the last value on every tick, which would make
  the ViewModel do ~4× the work per second instead of less.
- **REST fallback:** every `PRICE_REFRESH_MS = 30s`, but **only for what the socket does not
  cover**: TradingView pairs (they have no socket), pairs whose last tick is older than
  `STALE_TICK_MS = 30s`, and pairs still at `0.00`. In normal operation that is ~4–5
  requests per cycle instead of one per tracked pair (~20). The window is aligned with the
  cycle: one full cycle without a tick from the socket is already a hole. A pair whose
  symbol simply does not trade (an illiquid Binance spot pair, for example) lands here on
  purpose — the socket has nothing to push for it, so the REST is the only source left.
- **Add/remove:** a tracked-pair change refreshes **only the new pairs** (`fresh`), so
  adding a pair costs one REST call and removing one costs none. A removal is
  **optimistic**: the pair leaves the UI immediately and the `DELETE` runs in the
  background; if the `DELETE` fails, the pair goes back.
- **Prices are applied to the current list**, never to a captured copy: a pair deleted
  while a REST call was in flight does not come back. Room returns every pair with price
  `0.00`, so the list merges the DB rows with the prices it already has
  (`mergeTrackedPairs`) — otherwise every add/remove would blank the whole list.
- **Sparklines:** every `SPARK_REFRESH_MS = 5min`, in chunks of 5 pairs with a 60 ms
  delay, cached with a 5 min TTL (`SPARK_CACHE_MAX_ENTRIES = 60`). While any tracked pair
  still has no sparkline (cold start, a failed request), the loop retries every
  `SPARK_RETRY_MS = 3s` up to `SPARK_FAST_RETRIES = 3` times instead of waiting the full
  5 min; after that it returns to the long cadence. A TV sparkline asks the socket for
  `cleanLimit` bars (`30` for the sparkline, `45` for the daily precision check), not 1000.
  The daily check sets `dayExt` (the pair traded under `$1`), which drives the decimal band,
  so a `1d` request that comes back empty keeps the known band instead of flipping the pair
  to non-extended.

`CryptoListScreen` reads `sparklines`/`volumes` through `derivedStateOf` and reads
`.value` **inside** the item, so a price tick does not invalidate the whole list.

---

## 4. Detail screen (chart) price flow

The detail reads the **same shared socket** as the Pairs screen: no second socket,
no REST poll for the price.

The sockets are per-symbol now, so a pair that is **not tracked** has no socket entry at
all: the freshness guard sees no quote and the detail falls back to REST from the start.
In practice the detail is always opened from `My Pairs`, where the pair is tracked.

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
| Bitunix futures | `BITUNIX_KLINE_PAGE_SIZE = 200` (API cap) | `BITUNIX_KLINE_PAGES = 3` → 600 candles |
| BingX futures | `BINGX_KLINE_LIMIT = 1000` | 1 request |

Bitunix and BingX return candles **newest first**. Binance and MEXC return them oldest
first *inside* each page, so reversing the page order is enough for them. Bitunix and
BingX are the opposite: every page arrives **descending**, so reversing the page order
alone leaves the list non-chronological. Both are therefore sorted by time at the end
(`ascendingRows()` in `FuturesKlineMapping.kt`, which `toAscendingRows()` for BingX also
uses) because the app expects ascending order. Bitunix pages backwards with
`endTime = oldest - 1` (its `endTime` is inclusive; reusing it would repeat a candle).
800+ candles would need a fourth call and they are not worth it: `INITIAL_VISIBLE_CANDLES`
is 100 and the live candle comes from the socket.

Their history is shallower than Binance on purpose, and neither needs a socket. A 4th
Bitunix page is the upgrade path if a user ever asks for more history.

Pages are fetched **newest first** and the whole list is sorted by time at the end:

```kotlin
return pages.flatten().ascendingRows()
```

Do not "simplify" that to `pages.asReversed().flatten()`: it reverses the *pages*, not the
candles inside them. That was the bug that made the Bitunix chart show the time axis
backwards, saturated the SMC and shifted the chart left on every refresh (the tail sync
appended duplicates because the last candle was not the newest one).

The old `allKlines.addAll(0, page)` inserted at the head of the list, shifting every
element on every page (O(N) per page, O(N·pages) overall). If you see that pattern
again, it is a regression.

**Bitunix only publishes closed candles.** Its `market/kline` never returns the candle in
progress (Binance, MEXC and BingX do; measured 2026-11-11: the newest Bitunix row is
always the previous bucket). Without that bucket the detail's 2 s tick sees `now >= last +
duration` on every pass, calls `syncTail` each time, and after three passes falls back to a
full `loadChartData` — the chart reloads itself every ~6 s and the last *closed* candle gets
overwritten with the live price. `withLiveCandle(interval)` (`CryptoDetailViewModel.kt`)
appends the flat in-progress bucket after the load and after every tail merge, and the tick
fills it with the socket price. It is a no-op for the sources that already publish it. The
synthetic bucket keeps the series phase (steps of the same duration), and `1mo` uses the
UTC month start because months are 28-31 days. Covered by `LiveCandleBucketTest`.

**The Bitunix live bucket is built from the WS `trade` channel, not from the price.**
Filling the flat bucket with the sampled price gave a candle whose high/low were off by up
to ~95 USD (1m) and ~84 USD (5m): the price is sampled every 2 s and the exchange moves
inside the bucket. The `trade` channel of the same socket carries every fill (`t`, `p`, `v`),
and accumulating them reproduces the exchange's own candle exactly — measured 2026-11-12,
the O/H/L/C of the accumulated bucket matched the published candle of that minute to the
cent and the volume within 0.2 %:

```
22:26 ws bucket      O=71446.9 H=71491.7 L=71446.9 C=71486.4 V=6.4096
22:26 exchange candle O=71446.9 H=71491.7 L=71446.9 C=71486.4 V=6.421
```

Rules:

- The subscribe message carries **two args per symbol** (`ticker` + `trade`). The shape with
  one arg and an array of channels (`"ch":["ticker","trade"]`) is accepted silently and
  never pushes anything.
- The accumulator lives in the repository (`BitunixLiveBuckets`), app-wide, and is fed by the
  same flow that feeds the prices: it survives opening and closing the detail and there is
  no second socket.
- The bucket phase is the exchange's own phase: epoch-aligned for everything intraday,
  **Monday** for `1w` and the first day of the month (UTC) for `1mo`
  (`Timeframes.bucketStartMs`). `2w` is aggregated by the app, so it keeps the app's own
  anchor. If the phase does not match the candle's time, the tick ignores the bucket.
- The bucket only knows what the socket saw: if the app connects in the middle of a bucket,
  that bucket misses its first trades (the open falls back to the previous close and the
  high/low can miss the extremes of the missed part). From the next bucket on it is exact.

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

MEXC, Bitunix and BingX pairs are mapped to Binance by removing `_` and `-` (the taker
data is always Binance futures, weight 0, no auth). `TV` has no taker data.

That means a Bitunix `AVAXUSDT` and a BingX `AVAX-USDT` both read Binance's `AVAXUSDT`
taker series: the same underlying market, so the subchart stays meaningful without a
second taker source per broker.

**Checked 2026-11-12: none of the futures exchanges publishes its own taker buy/sell
volume**, so this stays Binance Futures for every source (Binance spot included):

| Source | Its own kline fields | Own taker endpoint |
|---|---|---|
| Bitunix | `open, high, low, close, quoteVol, baseVol, time` | none |
| BingX | `open, close, high, low, volume, time` | none (`takerBuySellVol` → `100400 this api is not exist`) |
| MEXC | no taker field | none |

Do not try to build a per-broker C/V subchart: the data does not exist in those APIs.

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
- Socket parsing and candle order are guarded: `TickerStreamParsingTest` (14),
  `FuturesKlineMappingTest` (4), `TradingViewBarTest` (4), `LiveCandleBucketTest` (5),
  `BitunixLiveBucketsTest` (5) and `TimeframesBucketTest` (3).
  Keep them green; they encode the newest-first quirks of each exchange.
- On a device: the list price moves every second, the chart candle moves every 2 s in
  1m **and** in 1d, and the taker bar shows the in-progress candle.
- **The detail's price must not freeze**: with the phone online, the header price and
  the last candle have to move within a couple of seconds. If they stop, the socket is
  dead and the REST fallback should be taking over (check the 15 s freshness window in
  §4.1).
- Socket count: `adb shell dumpsys connectivity | grep -i websocket` is not reliable;
  instead check the app does not open a second socket per exchange (see §2.1).
- `adb logcat -s BitunixStream:* BingxStream:*`: a `dropped` line every ~30–60 s is the
  baseline for BingX (§2), **not** a loop. A loop is several per minute or a drop right after
  every reconnect; there the string to grep first is the one causing the reconnect, not the
  reconnect itself. **Bitunix should not drop at all** while the PING is in place: a drop
  every ~61 s means the `PING` stopped being sent and the live candle is losing trades.
- A pair is `(symbol, source)`: adding `AVAXUSDT` on Bitunix must not overwrite or hide
  `AVAXUSDT` on Binance. Check `My Pairs` after adding the same base asset on two brokers,
  and check that deleting one leaves the other. The upsert is `OnConflictStrategy.REPLACE`
  on a composite key, so a plain `symbol` key is the regression to look for.
- The Bitunix/BingX sockets only carry tracked symbols: a newly added pair should show a
  live price within a couple of seconds (that is the reconnect) and dropping the last pair
  of a source must not spin the socket in a reconnect loop.

