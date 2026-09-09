package com.pramaan.backend.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Local-filesystem object store. Keys map to relative paths under a root directory. */
public class FilesystemObjectStore implements ObjectStore {

    private final Path root;

    public FilesystemObjectStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create object-store root " + this.root, e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Illegal object key: " + key);
        }
        return p;
    }

    @Override
    public ObjectRef put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            if (Files.exists(target)) {
                return new ObjectRef(key, Files.size(target), contentType);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return new ObjectRef(key, content.length, contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("put failed for " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path p = resolve(key);
        if (!Files.exists(p)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new UncheckedIOException("get failed for " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public String driver() {
        return "filesystem";
    }
}
