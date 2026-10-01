package com.pro.trigger.idempotency;

import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Builds bounded, user-scoped identifiers for retryable HTTP operations.
 *
 * <p>The raw request ID is intentionally not persisted or used as a database
 * key. A request ID is namespaced by the authenticated user and operation so
 * the same client value cannot cross users or draw/exchange APIs.</p>
 */
public final class RequestIdempotency {

    public static final int MAX_REQUEST_ID_LENGTH = 128;

    private RequestIdempotency() {
    }

    /**
     * Returns a 64-character key, or {@code null} for legacy requests that
     * omit requestId.
     */
    public static String businessKey(String userId, String operation, String requestId) {
        String normalized = normalize(requestId);
        if (normalized == null) return null;
        return sha256(userId + "\u0000" + operation + "\u0000" + normalized);
    }

    /**
     * Returns a bounded user/operation lock key without including payload
     * fields such as SKU or activity ID.
     */
    public static String scopeKey(String userId, String operation) {
        return sha256(userId + "\u0000" + operation);
    }

    private static String normalize(String requestId) {
        if (requestId == null) return null;
        String normalized = requestId.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > MAX_REQUEST_ID_LENGTH) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "requestId length must be at most " + MAX_REQUEST_ID_LENGTH);
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(item & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
