package com.pramaan.backend.evidence.domain;

/**
 * Use Case 13 — evidence lifecycle. A record moves
 * {@code DRAFT → SUBMITTED → APPROVED}; an approved record whose age exceeds the
 * retention window is reported as {@code EXPIRED}; a rejected record can be
 * resubmitted; a record replaced by newer evidence for the same control can be
 * {@code SUPERSEDED}. Every transition is recorded as an {@link EvidenceLifecycleEvent}.
 */
public enum EvidenceLifecycleState {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    EXPIRED,
    SUPERSEDED
}
