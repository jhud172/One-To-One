# W140/W141 membership price consistency — 9 October 2026

The completion goal remains active. This checkpoint advances price-change/history workflows; W140/W141 whole-page acceptance and Q01–Q10 remain open. The preceding W139 receipt remains historical evidence.

Native price submissions now carry the price displayed during review. The service locks and refreshes the owned product, checks actual enabled gym ownership, applies due changes and rejects a stale quote before creating another event or attempting delivery. Rejected requests retain the draft, display the refreshed price and require another review. Identical events are reused before the quote comparison so a sequential repeat cannot create another event or email attempt. Missing/malformed quotes are rejected. Internal five-argument callers retain their existing contract; the native controller requires a quote.

Date validation, minimum date, default date and submitted midnight consistently use Europe/London with an injected clock. The price/date preview already worked and is preserved. New explanatory text is translated in fourteen UI bundles. Due-price application locks and refreshes the stored row and rereads the latest due event under the lock; only its price changes, preserving newer stored metadata and availability. Latest due events use effective date then ID; displayed history uses creation date then ID, both descending. Oversized history requests clamp to a real page and size remains bounded to fifty.

History identifies USD explicitly, labels future entries as scheduled, states London time and shows effective/created times. It preserves event amounts, reason, actor context and affected-subscriber records; it does not claim that a past event is the currently applied price or that delivery succeeded.

## Evidence and limits

The initial focused implementation run passed **16 tests / two suites**, zero failures/errors/skipped, in **1 minute 35 seconds**. A subsequent expanded locale check initially failed because its assertion expected Western digits in correctly localised Arabic amounts; the assertion was corrected to check invariant currency/timezone/reason context, with exact amounts separately asserted on the event. Final expanded verification and final packaged browser evidence are recorded below after completion.

The local synthetic browser flow (H2, email `none`, SMS `console`, AI disabled) created a product at $25.00, scheduled $29.99 for 9 November, and repeated the identical request. History remained one event and the current price stayed $25.00. No actual subscription, notice, provider request or money was involved. The progressive before/after/date preview was observed populated; the shared confirmation dialogue appeared before submission. This initial browser run started at **12:15:25.976 Europe/London**, in 26.056 seconds; final restart evidence follows below.

Remaining: controlled notification-body preview and delivery-failure evidence, concurrent replay under PostgreSQL, complete owner/role/state browser matrix, keyboard/screen-reader/RTL/theme/zoom/no-script/failed-enhancement/back/refresh acceptance, empty/large history browser proof, launch currency policy, shared launcher collision, real devices and all remaining release gates. Full source inventory is not full acceptance.
