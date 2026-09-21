# Use Case Sheet — Pramaan Next (Phase 1)

This document explains, in plain business and audit language, what each Phase 1 use
case of the Evidence Compliance System (ECS) does and why it exists. It is written for
an auditor, control owner, or stakeholder encountering ECS for the first time — it
does not describe how anything is built, and it makes no claim about what has or has
not been implemented in any particular codebase.

Source: `docs/MD_Usecases (6).xlsx` (sheets `MD_Usecases` and
`Controls_Framework_Mapping`), with control descriptions cross-checked against
`docs/ECS_Control_Library.xlsx`.

**Scope: Phase 1 only — UC1 through UC5.** These are the five use cases the source
sheet marks as Phase 1. Phase 2, Phase 3, and Pan India use cases (UC6 onward) are out
of scope for this document and will be written up separately when that phase of work
is addressed.

---

## Common Frameworks (Phase 1)

PCI DSS, DPSC, ISO 27001 / IS, and C-SITE apply across all Phase 1 use cases (UC1–UC5).
ISG applies only to UC1 and UC3. VAPT does not apply to any Phase 1 use case.

## Common Controls (Phase 1)

- Encryption at Rest
- Encryption in Transit

These two are treated as baseline controls in effect across all Phase 1 use cases and
are not repeated under each individual use case below. Any other control a use case
relies on is listed under that use case's own "Controls used".

---

## UC1 — Automated scheduled evidence pull

**What it is.** This use case is ECS reaching out to a system on a recurring schedule
— say, once a day or once a week — and pulling the technical configuration evidence an
auditor would otherwise have to request by email: things like a database's TLS
settings, a server's password policy, or a network device's firewall rules. Instead of
a control owner manually exporting a screenshot or a config file each time an audit
comes around, ECS collects it automatically and keeps doing so on a set cadence.

**Why it matters.** Audit evidence collected only when someone remembers to ask for it
is inherently stale and inconsistent — different people export it in different
formats, at different times, sometimes right before a deadline under time pressure.
Automating the pull turns evidence collection into a continuous, predictable process
rather than a scramble that happens once a year, and it removes the manual effort and
error that comes with someone hand-copying configuration values.

**Why these controls are required.** Configuration data pulled straight from a live
system — database credentials context, network topology, server settings — is
sensitive by nature, so the moment ECS starts collecting it automatically and at
scale, the risk of exposure scales with it. Encrypting the data both while it travels
to ECS and once it is sitting in storage is what keeps an automated, frequent
collection process from becoming a bigger attack surface than the manual process it
replaces. The automation itself is what several frameworks' evidence-availability
expectations are actually asking for: audit evidence has to be available and traceable
on demand, and that is only realistic if it is being collected continuously rather
than reconstructed under pressure right before an audit.

---

## UC2 — Bulk evidence upload

**What it is.** Not every piece of evidence can be pulled automatically from a
system — some of it is a signed policy document, an approval email, or an exported
report that only exists as a file. This use case lets an application owner hand over
a whole batch of such files in a single action, rather than uploading them one at a
time, while every file in that batch still goes through the same checks and handling
as evidence collected any other way.

**Why it matters.** Manual, one-file-at-a-time uploads don't scale when an
application owner has dozens of documents to submit for an audit cycle, and doing it
piecemeal invites mistakes — a missed file, an inconsistent naming convention, a file
uploaded to the wrong place. Bulk upload turns a tedious, error-prone chore into a
single, auditable action, which matters especially at audit-deadline crunch time when
large volumes of evidence need to move quickly and correctly.

**Why these controls are required.** A bulk channel multiplies the consequence of any
single weakness: where a one-off upload exposes at most one document if something goes
wrong, a bulk upload can expose dozens of files from a single mishandled transfer or
unprotected storage location. Because application owners are often uploading evidence
for systems that are themselves in scope for PCI DSS or DPSC, the same protections
those frameworks require for the underlying systems have to extend to the evidence
describing them — otherwise the evidence repository becomes a weaker link than the
systems it was built to document.

---

## UC3 — Metadata tagging and naming convention

**What it is.** Every piece of evidence that comes into ECS — whether pulled
automatically, uploaded in bulk, or entered manually — gets tagged with a consistent
set of metadata (which application it belongs to, which control it supports, which
framework it maps to, what kind of evidence it is) and given a predictable, structured
name. This is what turns a pile of files into a searchable, cross-referenced evidence
repository instead of a folder of arbitrarily named documents.

**Why it matters.** The same piece of evidence frequently satisfies more than one
control across more than one framework — a single database TLS configuration
screenshot, for example, might support a PCI DSS control, a DPSC control, and an
ISO 27001 control all at once. Without consistent tagging and naming, there is no
practical way to know that, and the organization ends up collecting the same evidence
redundantly for every framework it happens to touch. Consistent metadata is what makes
evidence reuse, cross-framework mapping, and fast retrieval possible.

**Why these controls are required.** Frameworks consistently require that audit
evidence be traceable to a specific control requirement — evidence that exists but
cannot be tied back to what it's supposed to prove is functionally useless in an
audit. A structured naming convention and a defined tag set are what make that
traceability enforceable at scale rather than dependent on someone's memory or a
spreadsheet maintained on the side. This same classification layer is also the
foundation that later capabilities — evidence reuse, completeness checks, predefined
querying — depend on, since none of those are possible if evidence isn't already
consistently classified and mapped to controls.

---

## UC4 — Evidence dashboard and hash integrity check

**What it is.** This use case gives auditors and control owners two things at once: a
consolidated dashboard showing what evidence exists across the whole repository (how
much, how recent, from which sources), and a cryptographic check that confirms a piece
of evidence — or the entire repository — has not been altered since it was submitted.
In practice, this is the "can I trust what I'm looking at" layer sitting on top of
everything else ECS collects.

**Why it matters.** Evidence is only as good as an auditor's confidence that it hasn't
been tampered with after the fact — a modified configuration file that looks
legitimate is worse than no evidence at all, because it creates false assurance. At
the same time, an audit team needs a bird's-eye view of the evidence repository rather
than having to inspect records one by one to understand coverage and freshness. This
use case addresses both: integrity assurance for trust, and a dashboard for
visibility.

**Why these controls are required.** An auditor's ability to rely on evidence collapses
the moment tampering becomes plausible and undetectable — cryptographic integrity
checking is the direct, mathematically verifiable way to guarantee that what's being
reviewed today is exactly what was submitted, with no reliance on trusting whoever
handled the file in between.

**Controls used (use-case-specific, beyond the Common Controls).**
- *Logging & Monitoring* — the dashboard makes compliance visibility a routine,
  always-available state rather than something reconstructed only under audit
  pressure. Frameworks that expect continuous compliance oversight (such as ISO 27001
  and C-SITE) are effectively asking for this kind of standing dashboard, not just a
  point-in-time report.

---

## UC5 — Common evidence querying – Pre-defined query

**What it is.** Auditors and technical reviewers tend to ask the same handful of
technical questions repeatedly across engagements — "is TLS enabled on this
database," "what's the password policy on this server," and similar checks. This use
case lets those recurring questions be defined once, as a predefined query — described
in the source sheet as either a SQL query or a shell script — and then run on demand,
or across a whole catalogue of such queries at once, producing the answer as evidence
rather than requiring someone to manually re-derive it each time.

**Why it matters.** Re-answering the same routine technical question by hand, every
audit cycle, for every application, is repetitive and inconsistent — different
reviewers may phrase or execute the check slightly differently, producing evidence
that isn't directly comparable. A predefined, reusable query standardizes both the
question and the way it's answered, so the same technical check produces consistent,
comparable evidence no matter who or what triggers it, and it can be reused across
many applications and audit cycles without being redefined each time.

Beyond the Common Controls above, the source sheet leaves the "Controls Used" cell
blank for this use case. The `Controls_Framework_Mapping` sheet contains one control
entry — "Evidence validation control" — that does not correspond to any other use
case's stated control set and is the closest semantic fit for a query-and-validate
capability like this one; it is presented here as the most reasonable inference, not
as a value stated directly in the source. Read that way, the relevant control is:
- *Evidence validation control* — confirming that the output of a query is treated and
  handled as genuine audit evidence (attributable, traceable, tamper-checked), not
  merely as the raw output of a diagnostic script.

Worth noting: the `Controls_Framework_Mapping` sheet's "Evidence validation control"
row (the inferred control above) lists VAPT as applicable, which conflicts with this
use case's own row in `MD_Usecases`, where VAPT is absent from the frameworks list, and
with the Common Frameworks note above that VAPT does not apply to any Phase 1 use
case. This is a genuine inconsistency between the two sheets in the source workbook,
not a judgment call made here.

**Why these controls are required.** A predefined query only has audit value if its
result is trusted as evidence in the same way any other collected artifact is — that
means it has to be attributable to a specific control, timestamped, and protected from
being silently altered after the fact, exactly like evidence gathered through
automated collection or manual upload. The sheet's own rationale is explicit that this
use case is meant to reach into a real system — running an actual SQL query against a
database or a shell script directly on a server — rather than simply replaying
something already on file, which is precisely why validating and standardizing the
output matters: a query result that isn't handled with the same rigor as other
evidence would undermine the consistency this use case is meant to provide in the
first place.

---

*Phase 2 (UC6–UC10), Phase 3 (UC11–UC15), and Pan India (UC16–UC19) use cases from the
same source workbook will be documented separately.*
