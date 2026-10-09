# Evidence summary

Deterministic, rule-based summaries of stored evidence — the "Evidence summary" panel on the evidence detail page and
the `evidence-summary` report. Backend: `backend-java/.../insight/EvidenceSummaryService` and `insight/summary/`.

## What it does

- Reads the stored bytes of one evidence version and states, with a pointer to where each fact was found, what they
  contain (check results, ticket fields, query rows, document properties, headings, dates, owners, ...).
- Adds the persisted automated-check verdict, the control's name and description, and the evidence's health
  (integrity, age, freshness, lifecycle, missing tags).
- Same evidence + same check results + same clock => the same summary (only `generatedAt` differs).

## What it does NOT do

- **No AI, no LLM, no OCR, no external calls.** Every sentence is a fixed template filled with extracted facts
  (`SummaryTemplates`). `model` is `deterministic-rules/v1`, `modelGenerated` is always `false`.
- **It does not prove compliance.** A verdict describes automated checks; a document summary says what the text
  states. "Keyword overlap with the control description" is informational, with no score.
- It never writes: a GET does not run rule evaluation.

## Pipeline (`GET /api/v1/insight/evidence/{id}/summary[?version=N]`)

1. Load record, version and control (`ControlCatalog`: LIBRARY = ECS Control Library, LEGACY = title only, UNKNOWN).
2. Verify integrity with the same code as `/evidence/{id}/verify` (INTACT | MISMATCH | UNVERIFIABLE).
3. Read the object (1 MiB; PDF and zip files up to 20 MiB). Missing/unreadable => `UNAVAILABLE` plus a limit, never a 500.
4. Detect the content kind from the bytes (magic bytes, zip entry names, text heuristics); a declared type that
   disagrees gives an INFO finding.
5. The first `ContentSummarizer` (ordered) that supports the probe produces facts, findings, limits and one content sentence.
6. Assemble verdict, headline, health, redaction and caps per call; cache only the content-derived part.

## Content kinds and extractors

| Kind | Summarizer | Reads |
|---|---|---|
| CHECK_BUNDLE | `CheckBundleSummarizer` | agent / simulated-technology bundles: check rows (`findings[]` or `checks[]`) |
| INTEGRATION_OBJECT | `IntegrationObjectSummarizer` | `objectType` objects: id, status, approver, dates, linked items |
| QUERY_RESULT | `QueryResultSummarizer` | predefined-query output: rows / result / text lines, flagged rows |
| STRUCTURED_DATA | `GenericJsonSummarizer` | top-level scalars, array lengths, status-like fields (depth <= 6) |
| TEXT | `PlainTextSummarizer` | lines, first line, dates; CSV/TSV header, rows, empty cells, ragged-row WARNING |
| DOCUMENT | `PdfSummarizer` (PDFBox), `DocxSummarizer`, `XlsxSummarizer`, `PptxSummarizer` | properties, structure, text signals |
| IMAGE | `ImageSummarizer` | width x height only |
| BINARY | `BinaryMetadataSummarizer` | type and size; legacy .doc/.xls/.ppt are not read |

Document signals (`DocumentSignals`): first heading, up to 5 dates (normalised to ISO, original kept in the pointer),
version markers, approver/owner markers, review-cycle markers (a past "next review"/"valid until" date is a WARNING),
and an INFO when a policy-type document has no approver/owner marker.

## Verdict and health

- Verdict = worst-of over persisted `check_result` rows: FAIL > WARNING > PASS > NOT_APPLICABLE; none => NOT_ASSESSED.
  For a non-current `?version=` only rows evaluated on that version count.
- Freshness uses the same definition as evidence completeness (age <= `stale-after-days` => CURRENT).
- A hash mismatch adds a FAIL finding and a headline sentence; the bytes as stored are still described.

## Caps and redaction

At most 60 facts (50 from content), values 200 chars with an ellipsis, JSON depth 6, invalid JSON falls back to text with
a WARNING, bad UTF-8 is decoded with replacement. Keys/labels matching password|secret|token|api key|private key|
credential|authorization|bearer, PEM private keys and `key=value` secrets in text are shown as `[REDACTED]`.
Office files: DOCTYPE refused (no XXE), <= 2000 entries, 20 MB/entry, 100 MB total, ratio <= 100:1 on entries > 1 MiB,
no path traversal, nothing written to disk. PDF: first 20 pages, 200 KB of text, 15 s guard, encrypted => text not read.

## The report

`GET /api/v1/reports/evidence-summary?applicationSlug=&framework=&format=json|csv` rolls the summaries up per
expected control (coverage from `CompletenessService`, so controls with no evidence appear as MISSING; verdict is the
worst-of over that control's evidence). Evidence entries come from `EvidenceSummaryService`. Capped at 2,000
evidence records (`truncated` + a limit line; `pramaan.reports.evidence-summary.max-evidence`). CSV: one row per
evidence and per MISSING control; RFC 4180 quoting; cells starting with = + - @ get a leading single quote.
Evidence not tied to an expected control is not listed. A Markdown format was not added (it would need a new rendering path).

## Adding an extractor

Implement `ContentSummarizer` (`order()` below 1000, `supports(probe)`, `summarize(ctx)`), report only through
`ctx.sink()` (caps and redaction apply), register it as a Spring `@Component`; if detection needs a new format add it
to `ContentProbe`. Add a `*SummarizerTest` with fixtures built in code. The engine does not change.

## Known limits

- A summarizer only sees what its extractor reads: DOCX headers/footers/text boxes, XLSX cell values beyond the first
  row, slide text and image pixels are not read; scanned PDFs yield no text.
- Dates like 03/04/2026 are read day-first. Quoted CSV fields spanning lines are not supported.
- Ingest always runs the general `RULE-EVIDENCE-COLLECTED` rule, so a file that matches no clause gets its default
  WARNING verdict rather than NOT_ASSESSED.
