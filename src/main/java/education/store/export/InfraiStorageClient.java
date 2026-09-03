package education.store.export;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiStorageClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;

    public InfraiStorageClient(ExportConfiguration configuration) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = configuration.baseUrl();
        this.apiKey = configuration.apiKey();
    }

    public void ensureBucket(String bucket) throws IOException, InterruptedException {
        try {
            call("GET", "/v1/storage/bucket/get/" + segment(bucket), null);
        } catch (InfraiException error) {
            if (error.httpStatus() != 404) throw error;
            call("POST", "/v1/storage/bucket/create", Map.of("name", bucket));
        }
    }

    public void putCsv(String bucket, String key, byte[] csv, String idempotencyKey)
            throws IOException, InterruptedException {
        call("PUT", "/v1/storage/object/put/" + segment(bucket) + "/" + path(key), Map.of(
                "data_base64", java.util.Base64.getEncoder().encodeToString(csv),
                "content_type", "text/csv; charset=utf-8",
                "idempotency_key", idempotencyKey));
    }

    public String presignDownload(String bucket, String key, Duration lifetime)
            throws IOException, InterruptedException {
        Map<String, Object> data = call("POST",
                "/v1/storage/object/presign/" + segment(bucket) + "/" + path(key), Map.of(
                        "op", "get",
                        "expires_seconds", lifetime.toSeconds(),
                        "response_disposition", "attachment"));
        Object url = data.get("url");
        if (!(url instanceof String value) || value.isBlank()) {
            throw new IOException("Presign response did not contain a URL");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String endpoint, Map<String, ?> body)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + endpoint))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").method(method,
                        HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(body)));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Object decoded = JsonCodec.parse(response.body());
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) throw new IOException("Invalid response envelope");
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;

            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map : Map.of();
                throw new InfraiException(
                        String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                        String.valueOf(error.getOrDefault("message", "Request rejected")),
                        response.statusCode());
            }
            return envelope.get("data") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
        }
        throw new IOException("Retry policy exhausted");
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Long.parseLong(value) * 1000L; }
                    catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
                })
                .orElse(250L * (1L << attempt));
    }

    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String path(String key) {
        return java.util.Arrays.stream(key.split("/", -1)).map(this::segment)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    public static final class InfraiException extends IOException {
        private final String code;
        private final int httpStatus;

        InfraiException(String code, String message, int httpStatus) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }
        public String code() { return code; }
        public int httpStatus() { return httpStatus; }
    }
}
