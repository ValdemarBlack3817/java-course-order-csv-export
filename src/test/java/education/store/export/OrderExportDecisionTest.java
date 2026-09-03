package education.store.export;

public final class OrderExportDecisionTest {
    public static void main(String[] args) {
        var ready = new OrderExportService.OrderSnapshot(
                "order-1042", "PAID", "SHIPPED", "receipt-883", "learner@example.com", "SHIPPED_EMAIL_SENT");
        require(ready.readyForCustomer(), "a paid, shipped order with a receipt should be exportable");

        var awaitingShipment = new OrderExportService.OrderSnapshot(
                "order-1043", "PAID", "PACKING", "receipt-884", "learner@example.com", "PACKING_EMAIL_SENT");
        require(!awaitingShipment.readyForCustomer(), "an order still being packed should stay out of the customer export");
        System.out.println("OrderExportDecisionTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
