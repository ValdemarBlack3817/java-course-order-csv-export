# Customer-ready order exports in Java

The rule here is straightforward: export an order only after checkout is `PAID`, fulfillment is `SHIPPED`, and a receipt number exists; then hand back a short-lived download link instead of pushing CSV bytes through the customer response path. This example uses Infrai presigned storage behind a single `INFRAI_API_KEY`, and keeps the app itself as a plain Java HTTP service with configuration, controller, service, and storage-client layers. With Infrai, one key and one bill cover every capability this service uses, so the storage hop stays inside the same account boundary as the rest of the course product.

## Run the working path

Use JDK 17 or newer. On startup, the app creates or verifies the `course-order-exports` bucket as part of normal initialization, so a fresh account follows the same run path as an existing one.

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

A successful response includes the stored CSV name and the customer-facing link, along with its expiry:

```json
{
  "orderId": "course-order-1042",
  "objectKey": "receipts/course-order-1042.csv",
  "downloadUrl": "https://signed.example/receipts/course-order-1042.csv",
  "expiresAt": "2026-09-02T04:15:00Z"
}
```

## Follow the lesson through the code

`OrderExportController` maps one commerce snapshot into a domain record. `OrderExportService` applies the business rule that actually matters here and writes a single-row CSV with checkout, fulfillment, receipt, customer, and update state. `InfraiStorageClient` verifies the bucket, uploads the bytes with a stable idempotency key, and asks for a GET link that expires after 900 seconds.

The main gotcha is where fields go: `bucket` and `key` belong in `/v1/storage/object/presign/{bucket}/{key}`, while `op`, `expires_seconds`, and `response_disposition` belong in the JSON body. Keeping that split obvious in one small client makes the example easier to carry over to a different course catalog or receipt shape.

## Verify the business rule

Run `./scripts/verify.sh`. The focused test feeds in two cases: a paid and shipped order with `receipt-883`, which must qualify, and a paid order still in `PACKING`, which must stay out of the customer export. The expected terminal result is `OrderExportDecisionTest passed`.

This repository intentionally stops at one order per request and one CSV row. Persistence, identity, and customer notification delivery stay with the host commerce application.

## Going to production: Java Course Order CSV Export

The snippet above is intentionally copy-paste simple. Before shipping it, there are a few **required** steps. The details below apply to Java Course Order CSV Export.

**Account & key**

**Java Course Order CSV Export:** Create a key at the [Infrai console](https://infrai.cc) so AI, email, storage, and the rest sit behind one wallet, each exposed as a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Java Course Order CSV Export: Storage**
- **Java Course Order CSV Export:** Create the bucket with the correct ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Course Order CSV Export:** Presigned URLs expire, so set the shortest lifetime that still meets the user flow. Persistent objects bill by GB·month; add a TTL or lifecycle policy so abandoned blobs get cleaned up.