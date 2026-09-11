package com.pramaan.backend.dev;

import com.pramaan.backend.config.PramaanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs the resolved value of {@code pramaan.demo-mode} once, unconditionally, at
 * startup — so the effective flag is visible in the backend log (and therefore in
 * start.sh's own output) regardless of whether {@link DemoSeedController} was
 * registered. When the value is {@code false} it also echoes the raw
 * {@code DEMO_MODE} environment variable to make "I set it but it didn't take"
 * diagnosis a one-line log check.
 */
@Component
public class DemoModeStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoModeStartupLogger.class);

    private final PramaanProperties props;
    private final Environment env;

    public DemoModeStartupLogger(PramaanProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean demoMode = props.demoMode();
        if (demoMode) {
            log.info("pramaan.demo-mode=true — dev/demo helpers enabled (DemoSeedController active)");
        } else {
            log.info("pramaan.demo-mode=false — dev/demo helpers disabled (raw DEMO_MODE env = {})",
                    env.getProperty("DEMO_MODE", "<unset>"));
        }
    }
}
