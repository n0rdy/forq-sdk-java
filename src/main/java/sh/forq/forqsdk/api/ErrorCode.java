package sh.forq.forqsdk.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum ErrorCode {
    BAD_REQUEST_CONTENT_EXCEEDS_LIMIT("bad_request.body.content.exceeds_limit"),
    BAD_REQUEST_PROCESS_AFTER_IN_PAST("bad_request.body.processAfter.in_past"),
    BAD_REQUEST_PROCESS_AFTER_TOO_FAR("bad_request.body.processAfter.too_far"),
    BAD_REQUEST_INVALID_BODY("bad_request.body.invalid"),
    BAD_REQUEST_DLQ_ONLY_OP("bad_request.dlq_only_operation"),
    UNAUTHORIZED("unauthorized"),
    NOT_FOUND_MESSAGE("not_found.message"),
    INTERNAL("internal");

    private static final Map<String, ErrorCode> STRING_TO_ENUM = Arrays.stream(ErrorCode.values())
        .collect(Collectors.toMap(ErrorCode::getCode, e -> e));

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    @JsonCreator
    public static ErrorCode fromString(String code) {
        return STRING_TO_ENUM.get(code);
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
