package com.pramaan.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/** MinIO (S3-compatible) object store. Keys map 1:1 to object names in a single bucket. */
public class MinioObjectStore implements ObjectStore {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStore(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    /** Create the target bucket if it doesn't exist yet, rather than failing on first use. */
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot ensure MinIO bucket " + bucket, e);
        }
    }

    @Override
    public ObjectRef put(String key, byte[] content, String contentType) {
        if (exists(key)) {
            // Keys are content-addressed and immutable — same no-overwrite contract as
            // FilesystemObjectStore.
            return new ObjectRef(key, content.length, contentType);
        }
        try (InputStream in = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
            return new ObjectRef(key, content.length, contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("put failed for " + key, e);
        } catch (Exception e) {
            throw new ObjectStoreException("put failed for " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return Optional.of(in.readAllBytes());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new ObjectStoreException("get failed for " + key, e);
        } catch (IOException e) {
            throw new UncheckedIOException("get failed for " + key, e);
        } catch (Exception e) {
            throw new ObjectStoreException("get failed for " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchObject".equals(e.errorResponse().code())) {
                return false;
            }
            throw new ObjectStoreException("exists check failed for " + key, e);
        } catch (Exception e) {
            throw new ObjectStoreException("exists check failed for " + key, e);
        }
    }

    @Override
    public String driver() {
        return "minio";
    }

    private static final class ObjectStoreException extends RuntimeException {
        ObjectStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
