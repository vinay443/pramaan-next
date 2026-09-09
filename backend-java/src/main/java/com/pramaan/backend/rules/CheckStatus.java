package com.pramaan.backend.rules;

/** Deterministic verdict of a rule evaluation. Mirrors the {@code CheckStatus} contract enum. */
public enum CheckStatus {
    PASS,
    WARNING,
    FAIL,
    NOT_APPLICABLE
}
