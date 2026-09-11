package com.pramaan.backend.common;

import com.pramaan.backend.ai.AiUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(Instant timestamp, int status, String error, String message) {
        static ErrorBody of(HttpStatus s, String msg) {
            return new ErrorBody(Instant.now(), s.value(), s.getReasonPhrase(), msg);
        }
    }

    /**
     * Same shape as {@link ErrorBody} plus {@code aiUnavailable: true} — a signal the
     * frontend can key off to show "AI service unavailable" instead of treating this
     * like the whole backend being down (see {@code withFallback} in api/endpoints.ts).
     */
    public record AiErrorBody(Instant timestamp, int status, String error, String message,
                              boolean aiUnavailable) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(ErrorBody.of(ex.status(), ex.getMessage()));
    }

    /**
     * The configured AI/embedding HTTP endpoint (e.g. a local Ollama) is unreachable.
     * 503, not 500 — the backend and evidence data are fine, only the AI feature is
     * degraded.
     */
    @ExceptionHandler(AiUnavailableException.class)
    ResponseEntity<AiErrorBody> handleAiUnavailable(AiUnavailableException ex) {
        String msg = "The configured AI/embedding service is unreachable right now. "
                + "The backend and evidence data are working normally — this only disables "
                + "AI-generated features (summaries, NL query, audit-prep narrative, similarity search). "
                + "Check that the service is running and reachable, then retry.";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new AiErrorBody(Instant.now(), HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "AI Service Unavailable", msg, true));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorBody> handleValidation(Exception ex) {
        return ResponseEntity.badRequest().body(ErrorBody.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ErrorBody.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /** An unmapped path is a 404, not a 500 (otherwise clients cannot tell "missing" from "broken"). */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorBody.of(HttpStatus.NOT_FOUND, "No endpoint " + ex.getResourcePath()));
    }

    /** Wrong HTTP verb on a known path is a 405 (with an Allow header), not a 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        var body = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        var supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            body.allow(supported.toArray(new org.springframework.http.HttpMethod[0]));
        }
        return body.body(ErrorBody.of(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("timestamp", Instant.now().toString(),
                        "status", 500,
                        "error", "Internal Server Error",
                        "message", String.valueOf(ex.getMessage())));
    }
}
