package com.pramaan.backend.config;

import com.pramaan.backend.storage.FilesystemObjectStore;
import com.pramaan.backend.storage.InMemoryObjectStore;
import com.pramaan.backend.storage.ObjectStore;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PramaanProperties.class)
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ObjectStore objectStore(PramaanProperties props) {
        String driver = props.objectStore() == null ? "filesystem" : props.objectStore().driver();
        if ("memory".equalsIgnoreCase(driver)) {
            return new InMemoryObjectStore();
        }
        String root = props.objectStore().filesystem() != null
                ? props.objectStore().filesystem().root()
                : "./data/object-store";
        return new FilesystemObjectStore(Path.of(root));
    }
}
