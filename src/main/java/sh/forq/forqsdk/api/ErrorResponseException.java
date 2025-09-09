package sh.forq.forqsdk.api;

import java.io.Serial;

public class ErrorResponseException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int httpStatusCode;
    private final ErrorResponse errorResponse;

    public ErrorResponseException(int httpStatusCode,
                                  ErrorResponse errorResponse) {
        this.httpStatusCode = httpStatusCode;
        this.errorResponse = errorResponse;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
