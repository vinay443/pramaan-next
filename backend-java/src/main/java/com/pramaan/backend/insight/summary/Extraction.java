package com.pramaan.backend.insight.summary;

import com.pramaan.backend.insight.InsightDtos.SummaryFact;
import com.pramaan.backend.insight.InsightDtos.SummaryFinding;
import java.util.List;

/** The content-derived part of a summary: identical bytes always give an identical Extraction. */
public record Extraction(ContentKind kind, List<SummaryFact> facts, List<SummaryFinding> findings,
                         List<String> limits, String contentSentence) {}
