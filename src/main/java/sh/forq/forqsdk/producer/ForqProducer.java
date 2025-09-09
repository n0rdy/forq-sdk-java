package sh.forq.forqsdk.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import sh.forq.forqsdk.api.ErrorResponse;
import sh.forq.forqsdk.api.ErrorResponseException;
import sh.forq.forqsdk.api.NewMessageRequest;

import java.io.IOException;
import java.util.Objects;

public class ForqProducer {
    private static final String PRODUCE_MESSAGE_ENDPOINT_URL_TEMPLATE = "/api/v1/queues/%s/messages";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String forqServerUrl;
    private final String authSecret;

    public ForqProducer(OkHttpClient httpClient,
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

        if (forqServerUrl.endsWith("/")) {
            forqServerUrl = forqServerUrl.substring(0, forqServerUrl.length() - 1);
        }

        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.forqServerUrl = forqServerUrl;
        this.authSecret = authSecret;
    }

    public void produce(NewMessageRequest newMessage, String queueName) throws IOException, ErrorResponseException {
        var url = String.format(forqServerUrl + PRODUCE_MESSAGE_ENDPOINT_URL_TEMPLATE, queueName);

        var bytes = objectMapper.writeValueAsBytes(newMessage);
        var request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(bytes))
            .addHeader("X-API-Key", authSecret)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build();

        try (var response = httpClient.newCall(request).execute()) {
            if (response.code() != 204) {
                var errorResponse = objectMapper.readValue(response.body().byteStream(), ErrorResponse.class);
                throw new ErrorResponseException(response.code(), errorResponse);
            }
        }
    }
}
