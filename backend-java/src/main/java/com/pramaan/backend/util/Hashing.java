package com.pramaan.backend.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers for evidence integrity. */
public final class Hashing {

    private Hashing() {}

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matches(byte[] content, String expectedHex) {
        return expectedHex != null && expectedHex.equalsIgnoreCase(sha256Hex(content));
    }
}
