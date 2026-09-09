package com.pramaan.backend.storage;

import java.util.Optional;

/**
 * Object-storage abstraction for immutable evidence bytes.
 *
 * <p>Phase 1 ships {@link FilesystemObjectStore} and {@link InMemoryObjectStore}.
 * An S3/MinIO implementation can be added behind this same interface with no
 * changes to callers.
 */
public interface ObjectStore {

    /** Store bytes at {@code key}. Overwrites are rejected — keys are content-addressed and immutable. */
    ObjectRef put(String key, byte[] content, String contentType);

    Optional<byte[]> get(String key);

    boolean exists(String key);

    String driver();

    record ObjectRef(String key, long sizeBytes, String contentType) {}
}
