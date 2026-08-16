# Java SDK for Forq - Simple Message Queue powered by SQLite

Check out the [Forq project](https://forq.sh) for more information about the server itself.

## Java SDK

The Java SDK code is available at [GitHub](https://github.com/n0rdy/forq-sdk-java)

It is available in the [Maven Central Repository](https://central.sonatype.com/artifact/sh.forq/forqsdk)

```xml
<dependency>
    <groupId>sh.forq</groupId>
    <artifactId>forqsdk</artifactId>
    <version>${forq-version}</version>
</dependency>
```

where `${forq-version}` is the latest version, e.g. `0.1.0`

### Producer

```java
var producer = new ForqProducer(httpClient, "http://localhost:8080", "your-auth-secret-min-32-chars-long");
```

where `httpClient` is an instance of Apache HttpClient 5 `CloseableHttpClient` that you have to initialize with necessary timeouts, connection pool sizes, etc.

You might ask why not use `java.net.http.HttpClient` that is part of the JDK? The native Java HTTP Client has a [bug with GOAWAY frames](https://bugs.openjdk.org/browse/JDK-8335181) that was fixed only in Java 24, which is a too hard ask.

Please note that the classic (blocking) Apache HttpClient API speaks HTTP/1.1: each in-flight long poll occupies one pooled connection.
If you run many concurrent consumers from one JVM, raise the connection pool limits accordingly
(`PoolingHttpClientConnectionManager` `setMaxTotal`/`setDefaultMaxPerRoute` - the per-route default is only 5).

You can then use the producer to send messages:

```java
var newMessage = new NewMessageRequest("I am going on an adventure!", 1757875397418);

try {
    producer.sendMessage(newMessage, "my-queue");
} catch (IOException e) {
    // thrown by either Jackson while serializing the request, or by the HTTP client while sending the request
    // process it here
} catch (ErrorResponseException e) {
    // thrown if Forq server returned non-2xx response
    // process it here by fetching status code via `e.getHttpStatusCode()` and error response body via `e.getErrorResponse()`
}
```

### Consumer

```java
var consumer = new ForqConsumer(httpClient, "http://localhost:8080", "your-auth-secret-min-32-chars-long");
```

where `httpClient` is an instance of Apache HttpClient 5 `CloseableHttpClient`. The SDK sets the response timeout
for the consume call itself (long polling needs at least 40 seconds), so no special timeout tuning is needed.

You can then use the consumer to fetch messages:

```java
try {
    var msgOptional = consumer.consumeOne("my-queue");
} catch (IOException e) {
    // thrown by either Jackson while deserializing the response, or by the HTTP client while sending the request
    // process it here
} catch (ErrorResponseException e) {
    // thrown if Forq server returned non-2xx response
    // process it here by fetching status code via `e.getHttpStatusCode()` and error response body via `e.getErrorResponse()`
}
```

`msgOptional` is `Optional<MessageResponse>`, as according to the Forq API, if there is no message available, the response will be `204 No Content`.

Then you'll process the message.
If processing is successful, you have to acknowledge the message, otherwise it will be re-delivered after the max processing time.
```java
try {
    consumer.ack("my-queue", msg);
} catch (IOException e) {
    // thrown by either Jackson while serializing the request, or by the HTTP client while sending the request
    // process it here
} catch (ErrorResponseException e) {
    // thrown if Forq server returned non-2xx response
    // process it here by fetching status code via `e.getHttpStatusCode()` and error    
    // response body via `e.getErrorResponse()`
}
```

If processing failed, you have to nack the message:
```java
try {
    consumer.nack("my-queue", msg);
} catch (IOException e) {
    // thrown by either Jackson while serializing the request, or by the HTTP client while sending the request
    // process it here
} catch (ErrorResponseException e) {
    // thrown if Forq server returned non-2xx response
    // process it here by fetching status code via `e.getHttpStatusCode()` and error
    // response body via `e.getErrorResponse()`
}
```

`ack` and `nack` take the whole `MessageResponse` (not just the ID) because the server requires the delivery
receipt from the consume response - the SDK sends it for you via the `X-Forq-Receipt` header. It fences the
ack/nack to that exact delivery, so a late ack/nack from a consumer that exceeded the max processing time
cannot affect a redelivery owned by another consumer.
