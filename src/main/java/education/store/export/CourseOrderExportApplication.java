package education.store.export;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.Executors;

public final class CourseOrderExportApplication {
    private CourseOrderExportApplication() {}

    public static void main(String[] args) throws Exception {
        ExportConfiguration configuration = ExportConfiguration.load();
        InfraiStorageClient storage = new InfraiStorageClient(configuration);
        storage.ensureBucket(configuration.bucket());
        OrderExportService service = new OrderExportService(storage, configuration, Clock.systemUTC());

        HttpServer server = HttpServer.create(new InetSocketAddress(configuration.port()), 0);
        server.createContext("/exports", new OrderExportController(service));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Course order export service listening on http://localhost:" + configuration.port());
    }
}
