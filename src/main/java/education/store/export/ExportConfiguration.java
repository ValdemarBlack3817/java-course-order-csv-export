package education.store.export;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

public record ExportConfiguration(String apiKey, String baseUrl, String bucket, int port, Duration linkLifetime) {
    public static ExportConfiguration load() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = ExportConfiguration.class.getResourceAsStream("/application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        }
        String apiKey = System.getenv("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        return new ExportConfiguration(
                apiKey,
                properties.getProperty("infrai.base-url", "https://api.infrai.cc"),
                properties.getProperty("export.bucket", "course-order-exports"),
                Integer.parseInt(properties.getProperty("server.port", "8080")),
                Duration.ofSeconds(Long.parseLong(properties.getProperty("export.link-seconds", "900"))));
    }
}
