package com.pramaan.backend.config;

import com.pramaan.backend.storage.FilesystemObjectStore;
import com.pramaan.backend.storage.InMemoryObjectStore;
import com.pramaan.backend.storage.MinioObjectStore;
import com.pramaan.backend.storage.ObjectStore;
import io.minio.MinioClient;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PramaanProperties.class)
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ObjectStore objectStore(PramaanProperties props) {
        String driver = props.objectStore() == null ? "filesystem" : props.objectStore().driver();
        if ("memory".equalsIgnoreCase(driver)) {
            log.info("Object store: in-memory");
            return new InMemoryObjectStore();
        }
        if ("minio".equalsIgnoreCase(driver) || "s3".equalsIgnoreCase(driver)) {
            PramaanProperties.ObjectStore.Minio cfg = props.objectStore().minio() != null
                    ? props.objectStore().minio()
                    : new PramaanProperties.ObjectStore.Minio(null, null, null, null);
            MinioClient client = MinioClient.builder()
                    .endpoint(cfg.endpointOrDefault())
                    .credentials(cfg.accessKeyOrDefault(), cfg.secretKeyOrDefault())
                    .build();
            MinioObjectStore store = new MinioObjectStore(client, cfg.bucketOrDefault());
            store.ensureBucket();
            log.info("Object store: minio (endpoint={}, bucket={})", cfg.endpointOrDefault(), cfg.bucketOrDefault());
            return store;
        }
        String root = props.objectStore().filesystem() != null
                ? props.objectStore().filesystem().root()
                : "./data/object-store";
        log.info("Object store: filesystem (root={})", root);
        return new FilesystemObjectStore(Path.of(root));
    }
}
