# Functional Understanding — Pramaan Next (ECS)

This document explains what Pramaan Next does as a product — how the system behaves,
who uses it, and how its parts fit together — before you read either of the other two
core docs. It is deliberately non-technical: no file paths, class names, or endpoint
signatures. For "how it's built," see the developer manual; for a per-use-case business
breakdown, see [use-cases.md](use-cases.md).

---

## 1. System overview

Pramaan Next (ECS — Evidence & Compliance System) is a compliance evidence platform
built for a banking environment, where applications and infrastructure must
continuously prove — to internal audit, regulators, and leadership — that security and
operational controls (encryption, access management, patching, configuration
baselines, and so on) are actually in place. The problem it solves is that compliance
evidence for a large bank is normally scattered: screenshots emailed around, configs
exported ad hoc, the same document re-collected for every framework that happens to
need it, with no reliable way to prove any of it hasn't been altered after the fact.
Pramaan Next centralizes evidence collection, tagging, integrity verification, and
reporting into one system, so that audit-readiness is a continuous state rather than a
frantic exercise before each audit cycle. Its users span the people who own the
systems being audited, the people who audit them, and the leadership who need a
rolled-up view of compliance posture across the bank.

## 2. User roles / personas

The system recognizes people in two related ways: a set of **personas** presented at
login, which frame how someone experiences the product, and a more granular set of
**roles** underneath that actually govern what a person is allowed to do with a piece
of evidence.

At login, a user picks one of five personas:

- **App Owner** — the person responsible for a specific application or system. This
  is the primary evidence contributor: uploading files, letting the system pull
  configuration evidence automatically, and submitting evidence for review.
- **Auditor** — reviews evidence, runs queries, checks completeness and compliance
  posture, and approves or rejects evidence that's been submitted.
- **Functional Head** and **Vertical Head** — mid-to-senior oversight roles who care
  about compliance posture across a group of applications or a business line rather
  than any single system.
- **CIO** — the most senior, enterprise-wide view: portfolio-level posture, trends,
  and cross-application comparisons rather than day-to-day evidence handling.

Underneath the persona, a more fine-grained role model governs what actually happens
to a piece of evidence as it moves through review. An **App Owner** role can submit
evidence for approval but not approve it. A generic **Auditor** role can approve or
reject evidence for any framework. More specialized reviewer roles are scoped
narrowly — for example, an officer who can only approve evidence for a specific
framework, or for a specific family of controls (such as vulnerability and
penetration-testing findings) regardless of which framework they were filed under.
This means approval authority isn't all-or-nothing: a reviewer can be trusted with
exactly the slice of evidence relevant to their responsibility, and nothing else.
There is also an administrative capability for managing which users exist, what roles
they hold, and which applications are registered in the system — the province of a
platform administrator rather than any of the day-to-day personas above.

## 3. Core evidence lifecycle

Every piece of evidence in Pramaan Next follows the same conceptual journey,
regardless of how it arrives.

**Birth — how evidence enters the system.** There are four distinct entry points,
each suited to a different situation. The most hands-off is a **scheduled pull**:
the system reaches out to source systems on a recurring cadence and retrieves
configuration evidence automatically, so evidence keeps accumulating without anyone
having to remember to collect it. When evidence can't be pulled programmatically —
a signed policy, an approval record, an exported report — an App Owner can perform a
**bulk upload**, handing over many files (including a zip archive that gets expanded
into its individual contents) in one action rather than one file at a time. For
routine, repeatable technical checks — "is TLS enabled here," "what's the password
policy there" — a **predefined query** can be defined once and then run on demand, or
across a whole catalogue of such checks at once, producing its result as evidence
rather than requiring a person to re-derive the answer each time. And for anything
that doesn't fit those patterns, evidence can be entered directly through a manual,
single-item path.

**Early life — becoming trustworthy and traceable.** The instant a piece of evidence
enters the system, regardless of which of the four doors it came through, it goes
through the same processing: it's checked against whatever evidence already exists
for that application and control so an unchanged resubmission is recognized as a
duplicate rather than piling up as a new copy; it's given a structured, predictable
name and a consistent set of metadata tags (which application, which control, which
framework, what kind of evidence, how it was collected); and a cryptographic
fingerprint of its content is captured. This is what turns a raw file into something
an auditor can actually trust and find later — the naming and tagging make it
searchable and attributable to a specific control, and the fingerprint means any
future tampering is detectable.

**Working life — reuse, review, and ongoing verification.** Because evidence is
consistently tagged from the moment it arrives, the same piece of evidence can be
recognized as relevant to more than one control or framework at once — a single piece
of evidence collected for one purpose can be explicitly mapped to satisfy an
additional framework's requirement, instead of collecting a duplicate for every
framework that happens to touch the same underlying fact. Evidence can also move
through a review workflow — submitted by the person who owns it, then approved or
rejected by whoever holds the relevant reviewing authority — so there's a record of
not just what evidence exists, but that someone with the right authority has actually
looked at it. Throughout its life, the evidence's integrity can be re-checked at any
time, on a single record or across the whole repository at once, confirming that
what's on file today is exactly what was originally submitted.

**Later life — aging, retirement, and history.** Evidence doesn't stay relevant
forever. As it ages, the system tracks its freshness so stale evidence is visible
rather than silently trusted. Evidence can be superseded by a newer version while
retaining its full version history, and eventually retired or archived once it's past
its useful retention window — all of this leaving behind a durable trail of what
happened to it and when, so the history itself remains available as evidence even
after the record it describes has moved on.

## 4. Functional modules

The product is organized into a small number of logical groups, matching how a user
actually moves through it.

### Evidence management

This is where evidence is collected, organized, and kept trustworthy.

- **Dashboard** — the front door: a consolidated view of how much evidence exists,
  how fresh it is, and whether it has passed its integrity checks, so a user can tell
  at a glance whether the evidence repository as a whole is in good shape.
- **Repository** and **Evidence Query** — the searchable home of every evidence
  record, filterable by application, framework, control, tag, or collection method,
  answering "what evidence do we have, and does it match what I'm looking for."
- **Predefined Queries** — the catalogue of standardized technical checks that can be
  run individually or in bulk, answering routine audit questions in a repeatable,
  comparable way rather than through ad hoc investigation.
- **Bulk Upload** — where an App Owner hands over a batch of files (or a zip archive)
  in one action, for evidence that can't be collected automatically.
- **Scheduler** — where automated, recurring evidence collection runs are started,
  monitored, and retried, answering "is evidence being kept up to date without
  someone having to remember to do it."
- **Evidence Reuse** — surfaces evidence already on file that matches something new
  being requested — either because it's byte-for-byte identical or because the same
  control satisfies more than one framework — so the answer to "do we already have
  this" is available before anyone re-collects it.
- **Evidence Lifecycle** — the retention, archival, and version-history view across
  all evidence, answering "what happened to this evidence over time, and is anything
  overdue for renewal or retirement."

### Compliance insight

This is where raw evidence is turned into an answer about compliance posture.

- **Control Results** — the rule-by-rule verdicts computed from stored evidence
  against the expected controls, answering "does this specific control actually pass."
- **Completeness** — for every control a framework expects, is there current, valid
  evidence on file — checking not just presence but real substance (proper metadata,
  meaningful content, intact integrity, and freshness), answering "are we actually
  audit-ready or do we have gaps."
- **Compliance** — the rolled-up pass/fail posture across the full expected-control
  catalog, answering "where do we stand overall."
- **Audit Prep** — a checklist and narrative summary drawn from completeness,
  compliance, and lifecycle data, meant to be the thing an auditor or App Owner reads
  right before an actual audit engagement to know what's ready and what isn't.
- **Reports** — packaged views over the underlying evidence and compliance data,
  including a regulator-facing filing format, meant to be handed to someone outside
  the day-to-day system.
- **NL Queries** — lets a user ask a plain-language question about the evidence
  repository and get a routed, evidence-grounded answer back, rather than having to
  know the exact filters to apply in Evidence Query.

### Enterprise & reporting

This is where posture is aggregated across many applications, business units, or
regions for leadership-level visibility.

- **Leadership** — a portfolio rollup of real compliance and completeness results
  across every application, meant for a Functional or Vertical Head who cares about a
  group of systems rather than one.
- **Comparison** — a side-by-side view of compliance posture and control gaps across
  applications, answering "which of our systems is behind, and on what."
- **Enterprise** — the same posture cut by business unit and criticality, for a
  view that reflects how the organization is actually structured rather than a flat
  list of applications.
- **National Rollup** — the same posture aggregated by geographic region, ranking
  regions by how far they sit from the national average, for the most senior,
  organization-wide view.
- **Trend** — tracks compliance posture and evidence-closure metrics over time,
  answering "are we improving or slipping," rather than only showing a single
  point-in-time snapshot.
- **GRC Integration** — pushes a summary of evidence and control status out to an
  external governance-risk-compliance system, for organizations that consolidate
  compliance data in a separate enterprise tool.

### Administration

- **Applications** — the registry of which systems exist in the platform at all,
  the base unit everything else (evidence, controls, dashboards) is organized around.
- **Onboarding** — brings a new application into scope: establishing its identity,
  the frameworks and controls that apply to it, its evidence sources, and running an
  initial baseline collection so it starts life with a real compliance posture rather
  than a blank slate.
- **Users & Roles** — where accounts, role assignments, and the review-authority
  scopes described in section 2 are managed.
- **Agents** — visibility into the technical collectors that can gather evidence
  directly from infrastructure (databases, operating systems, middleware, network
  configuration), showing what each is capable of collecting.

## 5. How the pieces connect

None of these modules are standalone tools — they're stages of one pipeline. Evidence
collected through the Scheduler, Bulk Upload, or Predefined Queries all lands in the
same evidence repository, tagged and verified the same way, which is what makes
everything downstream possible. Control Results and Completeness read directly from
that repository to determine, control by control, whether real evidence backs up each
requirement. Compliance rolls those individual control verdicts up into an overall
posture. Leadership, Comparison, Enterprise, and National Rollup all take that same
underlying compliance posture and re-cut it — by application, business unit,
criticality, or region — without recomputing anything from scratch; they're different
lenses on the same evaluated facts. Trend captures how that posture changes over
time, and Audit Prep and Reports package the same underlying facts for a human or a
regulator to read directly. Evidence Reuse and NL Query both work by looking back into
the same tagged, indexed evidence repository, just answering different questions
about it. In other words: evidence goes in once, through whichever door fits how it
was obtained, and every dashboard, report, and rollup in the system is a different
way of asking a question about that same evidence — not a separate, disconnected data
source.

## 6. Current phase boundaries

The system is being built in stages, and it's useful to know roughly where the line
sits so expectations match what each area is meant to deliver today.

The **foundational layer** — automated and manual evidence collection, consistent
tagging and naming, integrity checking, and predefined technical queries — is the
bedrock everything else depends on: get evidence in reliably, trustworthily, and
consistently classified, before trying to reason about it.

The **analytical layer** built on top of that foundation adds the interpretive
capabilities: detecting where evidence is missing or incomplete, finding and reusing
evidence across frameworks, generating plain-language summaries and answering
natural-language questions about the evidence, and giving leadership a rolled-up view
of posture. This layer is where the system starts telling you something about your
compliance state, rather than just holding evidence for you.

The **enterprise-scale layer** extends the same ideas across a much larger
organizational footprint — onboarding many applications at once with a standardized
process, comparing posture across the whole portfolio, integrating with external
governance tooling, and rolling everything up to an enterprise- or national-level
view for the most senior stakeholders. This layer assumes the foundational and
analytical layers are already working well for a single application and asks the
same questions again at the scale of an entire bank.
