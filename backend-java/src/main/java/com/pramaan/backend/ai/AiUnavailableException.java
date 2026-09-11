package com.pramaan.backend.ai;

/**
 * Thrown when the configured AI/embedding HTTP endpoint cannot be reached
 * (connection refused, I/O error, timeout) — distinct from an application bug,
 * so callers can surface "AI service unavailable" instead of a generic 500.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
