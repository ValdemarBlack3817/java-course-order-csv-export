package education.store.export;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderExportController implements HttpHandler {
    private final OrderExportService service;

    public OrderExportController(OrderExportService service) {
        this.service = service;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "Use POST /exports"));
            return;
        }
        try {
            Object decoded = JsonCodec.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (!(decoded instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Request body must be an object");
            Map<String, Object> input = (Map<String, Object>) raw;
            OrderExportService.OrderSnapshot order = new OrderExportService.OrderSnapshot(
                    required(input, "orderId"), required(input, "checkoutStatus"),
                    required(input, "fulfillmentStatus"), required(input, "receiptNumber"),
                    required(input, "customerEmail"), required(input, "customerUpdate"));
            OrderExportService.ExportResult result = service.export(order);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId", result.orderId());
            response.put("objectKey", result.objectKey());
            response.put("downloadUrl", result.downloadUrl());
            response.put("expiresAt", result.expiresAt().toString());
            send(exchange, 201, response);
        } catch (OrderExportService.OrderNotReadyException | IllegalArgumentException error) {
            send(exchange, 422, Map.of("error", error.getMessage()));
        } catch (InfraiStorageClient.InfraiException error) {
            int status = error.httpStatus() >= 400 && error.httpStatus() < 500 ? error.httpStatus() : 502;
            send(exchange, status, Map.of("error", error.code(), "message", error.getMessage()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            send(exchange, 503, Map.of("error", "Request interrupted"));
        }
    }

    private String required(Map<String, Object> input, String name) {
        Object value = input.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " is required");
        return text;
    }

    private void send(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        byte[] bytes = JsonCodec.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
