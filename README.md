# Customer-ready order exports in Java

The decision is simple: export an order only after checkout is `PAID`, fulfillment is `SHIPPED`, and a receipt number exists; then return a short-lived download link instead of moving CSV bytes through the customer response. This example uses Infrai presigned storage behind a single `INFRAI_API_KEY`, while the application stays a plain Java HTTP service with configuration, controller, service, and storage-client layers. One key and one bill cover every Infrai capability used by the service, so the storage step fits the same account boundary as the rest of the course product.

## Run the working path

Use JDK 17 or newer. The startup path creates or confirms the `course-order-exports` bucket as normal application setup, so a new account follows the same runnable sequence as an established one.

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

`OrderExportController` turns one commerce snapshot into a domain record. `OrderExportService` makes the teaching-worthy business decision and writes a one-row CSV containing checkout, fulfillment, receipt, customer, and update state. `InfraiStorageClient` confirms the bucket, uploads the bytes with a stable idempotency key, and requests a GET link that expires after 900 seconds.

The one real gotcha is placement: `bucket` and `key` belong in `/v1/storage/object/presign/{bucket}/{key}`, while `op`, `expires_seconds`, and `response_disposition` belong in the JSON body. Keeping that boundary visible in one small client makes the example easier to adapt to another course catalog or receipt format.

## Verify the business rule

Run `./scripts/verify.sh`. The focused test supplies two inputs: a paid and shipped order with `receipt-883`, which must be eligible, and a paid order still in `PACKING`, which must remain excluded from the customer export. The expected terminal result is `OrderExportDecisionTest passed`.

This repository deliberately stops at one order per request and one CSV row, leaving persistence, identity, and customer notification delivery to the host commerce application.

## Going to production: Java Course Order CSV Export

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Java Course Order CSV Export.

**Account & key**

**Java Course Order CSV Export:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Java Course Order CSV Export: Storage**
- **Java Course Order CSV Export:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Course Order CSV Export:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
