package sh.forq.forqsdk;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal in-process HTTP server for SDK tests, built on the JDK's own
 * HttpServer so the test suite adds no extra dependencies. Records the last
 * request and replies with a canned status + body.
 */
public class TestHttpServer implements AutoCloseable {

    public record RecordedRequest(String method, String path, String apiKey, String receipt, String contentType, byte[] body) {
    }

    private final HttpServer server;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();

    public TestHttpServer(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] reqBody = exchange.getRequestBody().readAllBytes();
            lastRequest.set(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("X-API-Key"),
                exchange.getRequestHeaders().getFirst("X-Forq-Receipt"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                reqBody
            ));

            byte[] respBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            // -1 means "no response body" for the JDK server; required for 204
            exchange.sendResponseHeaders(status, respBytes.length == 0 ? -1 : respBytes.length);
            if (respBytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            }
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public RecordedRequest lastRequest() {
        return lastRequest.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
