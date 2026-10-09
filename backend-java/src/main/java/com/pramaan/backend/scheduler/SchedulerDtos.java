package com.pramaan.backend.scheduler;

import com.pramaan.backend.scheduler.domain.ScheduleJob;
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

    public record CreateScheduleRequest(
            String name,
            String sourceSystem,
            String application,
            String evidenceType,
            String frequency,
            String createdBy) {}

    /** {@code version} is optional: when given, a stale value is rejected with 409 CONFLICT. */
    public record ToggleScheduleRequest(Boolean active, Long version) {}

    public record ScheduleOptions(
            List<String> sources,
            List<String> applications,
            List<String> evidenceTypes,
            List<String> frequencies) {}

    public record ScheduleView(
            String id,
            String name,
            String sourceSystem,
            String application,
            String evidenceType,
            String frequency,
            boolean active,
            String createdBy,
            Instant createdAt,
            Instant nextRunAt,
            Instant lastRunAt,
            Long version) {

        public static ScheduleView from(ScheduleJob j) {
            return new ScheduleView(j.getId().toString(), j.getName(), j.getSourceSystem(), j.getApplication(),
                    j.getEvidenceType(), j.getFrequency().name(), j.isActive(), j.getCreatedBy(),
                    j.getCreatedAt(), j.getNextRunAt(), j.getLastRunAt(), j.getVersion());
        }
    }

    public record SourceError(String errorCode, String errorMessage) {}

    public record SourceResult(String source, String status, int evidenceCount,
                               String errorCode, String errorMessage) {}

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
            Map<String, String> sourceStatus,
            String scheduleId,
            String scheduleName,
            String retryOfRunId,
            Map<String, SourceError> sourceErrors,
            String triggeredBy,
            String scheduleFrequency,
            String scheduleCreatedBy,
            Long durationMs,
            List<SourceResult> sourceResults,
            int slaSeconds,
            Long queuedMs,
            String slaStatus,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt) {

        /**
         * slaStatus covers the whole request-to-finish time (queue wait + execution): RUNNING until finished,
         * then WITHIN_SLA or BREACHED against {@code slaSeconds}.
         */
        public static RunView from(SchedulerRun r, int slaSeconds) {
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
                    connectivity(r.getSourceSummary()),
                    r.getScheduleId() == null ? null : r.getScheduleId().toString(),
                    r.getScheduleName(),
                    r.getRetryOfRunId() == null ? null : r.getRetryOfRunId().toString(),
                    errors(r.getSourceSummary()),
                    r.getTriggeredBy(), r.getScheduleFrequency(), r.getScheduleCreatedBy(),
                    r.getStartedAt() != null && r.getFinishedAt() != null
                            ? java.time.Duration.between(r.getStartedAt(), r.getFinishedAt()).toMillis() : null,
                    results(r.getSourceSummary()),
                    slaSeconds,
                    r.getStartedAt() == null ? null : java.time.Duration.between(r.getCreatedAt(), r.getStartedAt()).toMillis(),
                    r.getFinishedAt() == null ? "RUNNING"
                            : java.time.Duration.between(r.getCreatedAt(), r.getFinishedAt())
                                    .compareTo(java.time.Duration.ofSeconds(slaSeconds)) > 0 ? "BREACHED" : "WITHIN_SLA",
                    r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt());
        }

        private static final java.util.regex.Pattern INGESTED = java.util.regex.Pattern.compile("ingested=(\\d+)");

        /** One audit row per source: status, evidence count (ingested) and error code/message together. */
        private static List<SourceResult> results(Map<String, String> summary) {
            Map<String, String> status = connectivity(summary);
            Map<String, SourceError> errs = errors(summary);
            return new TreeMap<>(summary).entrySet().stream()
                    .filter(e -> status.containsKey(e.getKey()))
                    .map(e -> {
                        var n = INGESTED.matcher(e.getValue());
                        SourceError err = errs.get(e.getKey());
                        return new SourceResult(e.getKey(), status.get(e.getKey()), n.find() ? Integer.parseInt(n.group(1)) : 0,
                                err == null ? null : err.errorCode(), err == null ? null : err.errorMessage());
                    }).toList();
        }

        private static final java.util.regex.Pattern ERR_MSG = java.util.regex.Pattern.compile("error: (.*?) connectivity=");
        private static final java.util.regex.Pattern ERR_CODE = java.util.regex.Pattern.compile("errorCode=(\\w+)");

        /** errorCode + message per failed source, parsed from the executor's summary text. */
        private static Map<String, SourceError> errors(Map<String, String> summary) {
            Map<String, SourceError> out = new TreeMap<>();
            summary.forEach((source, text) -> {
                var code = text == null ? null : ERR_CODE.matcher(text);
                if (code != null && code.find()) {
                    var msg = ERR_MSG.matcher(text);
                    out.put(source, new SourceError(code.group(1), msg.find() ? msg.group(1) : null));
                }
            });
            return out;
        }

        /** CONNECTED / FAILED per source, from the {@code connectivity=} token the executor appends. */
        private static Map<String, String> connectivity(Map<String, String> summary) {
            Map<String, String> out = new TreeMap<>();
            summary.forEach((source, text) -> {
                if (text != null && text.contains("connectivity=")) {
                    out.put(source, text.contains("connectivity=CONNECTED") ? "CONNECTED" : "FAILED");
                }
            });
            return out;
        }

        private static List<String> split(String csv) {
            return csv == null || csv.isBlank() ? List.of() : List.of(csv.split("\\s*,\\s*"));
        }
    }
}
