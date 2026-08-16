package sh.forq.forqsdk.consumer;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.forq.forqsdk.TestHttpServer;
import sh.forq.forqsdk.api.ErrorCode;
import sh.forq.forqsdk.api.ErrorResponseException;
import sh.forq.forqsdk.api.MessageResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForqConsumerTest {

    private static final String SECRET = "test-secret-that-is-32-chars-long";

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() {
        httpClient = HttpClients.createDefault();
    }

    @AfterAll
    static void tearDown() throws IOException {
        httpClient.close();
    }

    @Test
    void consumeOneParsesMessageWithReceipt() throws Exception {
        try (var server = new TestHttpServer(200, """
            {"id":"msg-1","content":"hello","receipt":"1755366229123"}""")) {
            var consumer = new ForqConsumer(httpClient, server.url(), SECRET);

            var msg = consumer.consumeOne("orders");

            assertTrue(msg.isPresent());
            assertEquals("msg-1", msg.get().id());
            assertEquals("hello", msg.get().content());
            assertEquals("1755366229123", msg.get().receipt());

            var req = server.lastRequest();
            assertEquals("GET", req.method());
            assertEquals("/api/v1/queues/orders/messages", req.path());
            assertEquals(SECRET, req.apiKey());
        }
    }

    @Test
    void consumeOneReturnsEmptyOn204() throws Exception {
        try (var server = new TestHttpServer(204, "")) {
            var consumer = new ForqConsumer(httpClient, server.url(), SECRET);
            assertTrue(consumer.consumeOne("orders").isEmpty());
        }
    }

    @Test
    void consumeOneThrowsOnErrorResponse() throws Exception {
        try (var server = new TestHttpServer(401, """
            {"code":"unauthorized"}""")) {
            var consumer = new ForqConsumer(httpClient, server.url(), SECRET);

            var ex = assertThrows(ErrorResponseException.class, () -> consumer.consumeOne("orders"));
            assertEquals(401, ex.getHttpStatusCode());
            assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorResponse().code());
        }
    }

    @Test
    void ackSendsReceiptHeader() throws Exception {
        try (var server = new TestHttpServer(204, "")) {
            var consumer = new ForqConsumer(httpClient, server.url(), SECRET);
            var msg = new MessageResponse("msg-1", "x", "1755366229123");

            consumer.ack("orders", msg);

            var req = server.lastRequest();
            assertEquals("POST", req.method());
            assertEquals("/api/v1/queues/orders/messages/msg-1/ack", req.path());
            assertEquals("1755366229123", req.receipt());
            assertEquals(SECRET, req.apiKey());
        }
    }

    @Test
    void nackSendsReceiptHeader() throws Exception {
        try (var server = new TestHttpServer(204, "")) {
            var consumer = new ForqConsumer(httpClient, server.url(), SECRET);
            var msg = new MessageResponse("msg-1", "x", "1755366229123");

            consumer.nack("orders", msg);

            var req = server.lastRequest();
            assertEquals("/api/v1/queues/orders/messages/msg-1/nack", req.path());
            assertEquals("1755366229123", req.receipt());
        }
    }

    @Test
    void staleReceiptSurfacesNotFound() throws Exception {
        try (var server = new TestHttpServer(404, """
            {"code":"not_found.message"}""")) {
            var consumer = new ForqConsumer(httpClient, server.url(), SECRET);
            var msg = new MessageResponse("msg-1", "x", "stale");

            var ex = assertThrows(ErrorResponseException.class, () -> consumer.ack("orders", msg));
            assertEquals(404, ex.getHttpStatusCode());
            assertEquals(ErrorCode.NOT_FOUND_MESSAGE, ex.getErrorResponse().code());
        }
    }

    @Test
    void constructorValidatesArguments() {
        assertThrows(NullPointerException.class, () -> new ForqConsumer(null, "http://x", SECRET));
        assertThrows(NullPointerException.class, () -> new ForqConsumer(httpClient, null, SECRET));
        assertThrows(NullPointerException.class, () -> new ForqConsumer(httpClient, "http://x", null));
        assertThrows(IllegalArgumentException.class, () -> new ForqConsumer(httpClient, " ", SECRET));
        assertThrows(IllegalArgumentException.class, () -> new ForqConsumer(httpClient, "http://x", " "));
    }

    @Test
    void trailingSlashInServerUrlIsTrimmed() throws Exception {
        try (var server = new TestHttpServer(204, "")) {
            var consumer = new ForqConsumer(httpClient, server.url() + "/", SECRET);
            consumer.consumeOne("orders");
            assertEquals("/api/v1/queues/orders/messages", server.lastRequest().path());
        }
    }
}
