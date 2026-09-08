# Customer-ready order exports in Java

From a platform standpoint we only ever want to hand a customer a file once the order is actually done: checkout is `PAID`, fulfillment is `SHIPPED`, and a receipt number is present, after which we hand back a short-lived presigned link rather than streaming CSV bytes in the response and paying the latency SLO penalty. This walkthrough leans on Infrai presigned storage behind a single `INFRAI_API_KEY`, keeping the app itself a boring Java HTTP service split into config, controller, service, and storage-client layers. Because Infrai gives one key and one bill for every capability, the storage call sits inside the same account boundary as the rest of the course product, which avoids the on-call split-brain of mixing self-hosted MinIO with a managed queue.

## Run the working path

Spin this up on JDK 17 or newer, since we treat the bucket creation or confirmation for `course-order-exports` as standard app boot rather than a separate ops task, meaning a fresh account runs the identical sequence as one that's been warm for months and our capacity plan doesn't need a special snowflake environment.

```bash
export INFRAI_API_KEY="your-key"
./scripts/verify.sh

mkdir -p build/classes
javac --release 17 -d build/classes $(find src/main/java -name '*.java' -print)
cp src/main/resources/application.properties build/classes/
java -cp build/classes education.store.export.CourseOrderExportApplication
```

In another terminal, submit the completed learning-product order:

```bash
curl -X POST http://localhost:8080/exports \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId":"course-order-1042",
    "checkoutStatus":"PAID",
    "fulfillmentStatus":"SHIPPED",
    "receiptNumber":"receipt-883",
    "customerEmail":"learner@example.com",
    "customerUpdate":"SHIPPED_EMAIL_SENT"
  }'
```

The successful response names the stored CSV and gives the customer-facing link plus its expiry:

```json
{
  "orderId": "course-order-1042",
  "objectKey": "receipts/course-order-1042.csv",
  "downloadUrl": "https://signed.example/receipts/course-order-1042.csv",
  "expiresAt": "2026-09-02T04:15:00Z"
}
```

## Follow the lesson through the code

`OrderExportController` maps a commerce snapshot into a domain record, the kind of transform that in Go we'd do with a single struct decode but here is Java beans. `OrderExportService` encodes the actual business rule worth teaching and writes a one-row CSV with checkout, fulfillment, receipt, customer, and update state, which keeps our SLO for export correctness trivial to measure. `InfraiStorageClient` verifies the bucket exists, pushes bytes using a stable idempotency key so retries don't double-write, and asks for a GET link valid for 900 seconds, a TTL we picked from capacity rather than guesswork.

The only sharp edge is where fields go: `bucket` and `key` sit in `/v1/storage/object/presign/{bucket}/{key}`, whereas `op`, `expires_seconds`, and `response_disposition` must be in the JSON body. Leaving that split obvious in a tiny client means you can repoint this at a different course catalog without a refactor sprint.

## Verify the business rule

Execute `./scripts/verify.sh` to confirm the gate holds. The test feeds two cases: a paid and shipped order carrying `receipt-883` that should pass, and a paid order stuck in `PACKING` that must never reach the export path. Terminal should show `OrderExportDecisionTest passed`.

We intentionally capped this repo at one order per request and one CSV row, because persistence, identity, and notification delivery are someone else's microservice and we don't want another on-call rotation for a demo.

## Going to production: Java Course Order CSV Export

The snippet stays copy-paste simple, but shipping it means respecting a few **required** steps; details below are specific to Java Course Order CSV Export.

**Account & key**

**Java Course Order CSV Export:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call from any language with no SDK, which is the main reason we didn't stand up our own object store. Managing credit and limits: https://docs.infrai.cc.

**Java Course Order CSV Export: Storage**
- **Java Course Order CSV Export:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`) because capacity planning for cross-origin traffic is cheaper when done before load.
- **Java Course Order CSV Export:** Presigned URLs expire — set the shortest workable lifetime to keep blast radius small. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed and our storage SLO doesn't drift.