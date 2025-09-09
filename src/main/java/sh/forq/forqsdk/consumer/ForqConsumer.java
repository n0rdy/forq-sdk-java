package sh.forq.forqsdk.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import sh.forq.forqsdk.api.ErrorResponse;
import sh.forq.forqsdk.api.ErrorResponseException;
import sh.forq.forqsdk.api.MessageResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ForqConsumer {
    private static final long LONG_POLLING_MAX_DURATION_MS = 30000;

    private static final String CONSUME_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages";
    private static final String ACK_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages/%s/ack";
    private static final String NACK_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages/%s/nack";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String forqServerUrl;
    private final String authSecret;

    public ForqConsumer(OkHttpClient httpClient,
                        String forqServerUrl,
                        String authSecret) {
        Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(forqServerUrl, "forqServerUrl must not be null");
        Objects.requireNonNull(authSecret, "authSecret must not be null");

        if (forqServerUrl.isBlank()) {
            throw new IllegalArgumentException("forqServerUrl must not be blank");
        }
        if (authSecret.isBlank()) {
            throw new IllegalArgumentException("authSecret must not be blank");
        }
        if (httpClient.callTimeoutMillis() != 0 && httpClient.callTimeoutMillis() < LONG_POLLING_MAX_DURATION_MS) {
            throw new IllegalArgumentException("httpClient call timeout must be 0 (no timeout) or at least " + LONG_POLLING_MAX_DURATION_MS + " ms (+ few seconds extra buffer on top)");
        }

        if (forqServerUrl.endsWith("/")) {
            forqServerUrl = forqServerUrl.substring(0, forqServerUrl.length() - 1);
        }

        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.forqServerUrl = forqServerUrl;
        this.authSecret = authSecret;
    }

    public Optional<MessageResponse> consumeOne(String queueName) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + CONSUME_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName);

        var request = new Request.Builder()
            .url(url)
            .get()
            .addHeader("X-API-Key", authSecret)
            .addHeader("Accept", "application/json")
            .build();

        try (var response = httpClient.newCall(request).execute()) {
            switch (response.code()) {
                case 200 -> {
                    var messageResponse = objectMapper.readValue(response.body().byteStream(), MessageResponse.class);
                    return Optional.of(messageResponse);
                }
                case 204 -> {
                    // no message available
                    return Optional.empty();
                }
                default -> {
                    var errorResponse = objectMapper.readValue(response.body().byteStream(), ErrorResponse.class);
                    throw new ErrorResponseException(response.code(), errorResponse);
                }
            }
        }
    }

    public void ack(String queueName, String messageId) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + ACK_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName, messageId);

        var request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(new byte[0]))
            .addHeader("X-API-Key", authSecret)
            .addHeader("Accept", "application/json")
            .build();

        try (var response = httpClient.newCall(request).execute()) {
            if (response.code() != 204) {
                var errorResponse = objectMapper.readValue(response.body().byteStream(), ErrorResponse.class);
                throw new ErrorResponseException(response.code(), errorResponse);
            }
        }
    }

    public void nack(String queueName, String messageId) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + NACK_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName, messageId);

        var request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(new byte[0]))
            .addHeader("X-API-Key", authSecret)
            .addHeader("Accept", "application/json")
            .build();

        try (var response = httpClient.newCall(request).execute()) {
            if (response.code() != 204) {
                var errorResponse = objectMapper.readValue(response.body().byteStream(), ErrorResponse.class);
                throw new ErrorResponseException(response.code(), errorResponse);
            }
        }
    }
}
