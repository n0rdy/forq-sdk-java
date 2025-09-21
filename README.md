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

where `${forq-version}` is the latest version, e.g. `0.0.2`

### Producer

```java
var producer = new ForqProducer(httpClient, "http://localhost:8080", "your-auth-secret-min-32-chars-long");
```

where `httpClient` is an instance of `okhttp3.OkHttpClient` that you have to initialize with necessary timeouts, etc.

You might ask why not use `java.net.http.HttpClient` that is part of the JDK? The reason is that Forq encourages to use HTTP2 due to long-polling,
and native Java HTTP Client has a [bug with GOAWAY frames](https://bugs.openjdk.org/browse/JDK-8335181) that was fixed only in Java 24.
It is a too hard ask to require Java 24, so I decided to use OkHttp that has a solid HTTP2 support.

You can then use the producer to send messages:

```java
var newMessage = new NewMessageRequest("I am going on an adventure!", 1757875397418);

try {
    producer.sendMessage(newMessage, "my-queue");
} catch (IOException e) {
    // thrown by either Jackson while serializing the request, or by OkHttp while sending the request
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

where `httpClient` is an instance of `okhttp3.OkHttpClient` that you have to initialize with necessary timeouts, etc.

You can then use the consumer to fetch messages:

```java
try {
    var msgOptional = consumer.consumeOne("my-queue");
} catch (IOException e) {
    // thrown by either Jackson while deserializing the response, or by OkHttp while sending the request
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
    consumer.ack("my-queue", msg.id());
} catch (IOException e) {
    // thrown by either Jackson while serializing the request, or by OkHttp while sending the request
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
    consumer.nack("my-queue", msg.id());
} catch (IOException e) {
    // thrown by either Jackson while serializing the request, or by OkHttp while sending the
    // process it here
} catch (ErrorResponseException e) {
    // thrown if Forq server returned non-2xx response
    // process it here by fetching status code via `e.getHttpStatusCode()` and error
    // response body via `e.getErrorResponse()`
}
```
