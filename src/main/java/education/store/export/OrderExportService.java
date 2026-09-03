package education.store.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

public final class OrderExportService {
    private final InfraiStorageClient storage;
    private final ExportConfiguration configuration;
    private final Clock clock;

    public OrderExportService(InfraiStorageClient storage, ExportConfiguration configuration, Clock clock) {
        this.storage = storage;
        this.configuration = configuration;
        this.clock = clock;
    }

    public ExportResult export(OrderSnapshot order) throws IOException, InterruptedException {
        if (!order.readyForCustomer()) {
            throw new OrderNotReadyException("The order needs a paid checkout, shipped fulfillment, and receipt before export");
        }
        String key = "receipts/" + safeKey(order.orderId()) + ".csv";
        String csv = "order_id,checkout_status,fulfillment_status,receipt_number,customer_email,customer_update\n"
                + csv(order.orderId()) + "," + csv(order.checkoutStatus()) + ","
                + csv(order.fulfillmentStatus()) + "," + csv(order.receiptNumber()) + ","
                + csv(order.customerEmail()) + "," + csv(order.customerUpdate()) + "\n";
        storage.ensureBucket(configuration.bucket());
        storage.putCsv(configuration.bucket(), key, csv.getBytes(StandardCharsets.UTF_8),
                "order-export-" + safeKey(order.orderId()));
        String url = storage.presignDownload(configuration.bucket(), key, configuration.linkLifetime());
        return new ExportResult(order.orderId(), key, url, Instant.now(clock).plus(configuration.linkLifetime()));
    }

    private String safeKey(String value) {
        String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (safe.isBlank()) throw new IllegalArgumentException("orderId must contain a letter or number");
        return safe;
    }

    private String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public record OrderSnapshot(String orderId, String checkoutStatus, String fulfillmentStatus,
                                String receiptNumber, String customerEmail, String customerUpdate) {
        boolean readyForCustomer() {
            return "PAID".equals(checkoutStatus) && "SHIPPED".equals(fulfillmentStatus)
                    && receiptNumber != null && !receiptNumber.isBlank();
        }
    }

    public record ExportResult(String orderId, String objectKey, String downloadUrl, Instant expiresAt) {}
    public static final class OrderNotReadyException extends IllegalStateException {
        OrderNotReadyException(String message) { super(message); }
    }
}
