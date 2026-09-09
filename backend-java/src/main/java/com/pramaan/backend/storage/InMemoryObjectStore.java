package com.pramaan.backend.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryObjectStore implements ObjectStore {

    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();

    @Override
    public ObjectRef put(String key, byte[] content, String contentType) {
        if (blobs.containsKey(key)) {
            return new ObjectRef(key, blobs.get(key).length, contentType);
        }
        blobs.put(key, content.clone());
        return new ObjectRef(key, content.length, contentType);
    }

    @Override
    public Optional<byte[]> get(String key) {
        byte[] b = blobs.get(key);
        return b == null ? Optional.empty() : Optional.of(b.clone());
    }

    @Override
    public boolean exists(String key) {
        return blobs.containsKey(key);
    }

    @Override
    public String driver() {
        return "memory";
    }
}
