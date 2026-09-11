package com.pramaan.backend.onboarding;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanRequest;
import com.pramaan.backend.onboarding.OnboardingScanDtos.ScanView;
import com.pramaan.backend.onboarding.domain.OnboardingScan;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts and polls onboarding scans (the staged-progress-modal backend). */
@Service
public class OnboardingScanService {

    private final OnboardingScanRepository scans;
    private final OnboardingScanExecutor executor;
    private final Clock clock;
    private final boolean dispatch;

    public OnboardingScanService(OnboardingScanRepository scans, OnboardingScanExecutor executor, Clock clock,
                                 @Value("${pramaan.onboarding-scan.dispatch:true}") boolean dispatch) {
        this.scans = scans;
        this.executor = executor;
        this.clock = clock;
        this.dispatch = dispatch;
    }

    /** Create a scan and hand it to the async executor. Returns immediately with status PENDING. */
    @Transactional
    public ScanView trigger(ScanRequest req) {
        OnboardingScan scan = new OnboardingScan(UUID.randomUUID(), req.slug(), req.requestedBy(),
                req.frameworks(), req.sources(), clock.instant());
        scans.save(scan);
        if (dispatch) {
            executor.executeAsync(scan.getId(), req);
        }
        return ScanView.from(scan);
    }

    @Transactional(readOnly = true)
    public ScanView get(UUID scanId) {
        return scans.findById(scanId).map(ScanView::from)
                .orElseThrow(() -> ApiException.notFound("unknown onboarding scan: " + scanId));
    }
}
