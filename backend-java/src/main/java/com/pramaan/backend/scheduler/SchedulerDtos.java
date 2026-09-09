package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SchedulerDtos {

    private SchedulerDtos() {}

    public record RunRequest(
            List<String> applications,
            List<String> frameworks,
            List<String> sources,
            String requestedBy) {}

    public record RunView(
            String runId,
            String trigger,
            String status,
            String requestedBy,
            List<String> applications,
            List<String> frameworks,
            List<String> sources,
            int received,
            int ingested,
            int duplicates,
            int failed,
            String message,
            Map<String, String> perSource,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {

        public static RunView from(SchedulerRun r) {
            return new RunView(
                    r.getId().toString(),
                    r.getTrigger().name(),
                    r.getStatus().name(),
                    r.getRequestedBy(),
                    split(r.getApplications()),
                    split(r.getFrameworks()),
                    split(r.getSources()),
                    r.getReceived(), r.getIngested(), r.getDuplicates(), r.getFailed(),
                    r.getMessage(),
                    new TreeMap<>(r.getSourceSummary()),
                    r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt());
        }

        private static List<String> split(String csv) {
            return csv == null || csv.isBlank() ? List.of() : List.of(csv.split("\\s*,\\s*"));
        }
    }
}
