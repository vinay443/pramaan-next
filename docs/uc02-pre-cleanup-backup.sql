--
-- PostgreSQL database dump
--

\restrict Niet7IBrbSINHnOgZGwivvYN8K7pgK6M2SCVvwUOruqpndRDofhb9uLfM2E4k8T

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pramaan; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA pramaan;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: applications; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.applications (
    id uuid NOT NULL,
    slug character varying(120) NOT NULL,
    name character varying(200) NOT NULL,
    business_unit character varying(120),
    criticality character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    owner character varying(160),
    technology character varying(400),
    auto_created boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    active boolean DEFAULT true NOT NULL,
    onboarding_profile jsonb
);


--
-- Name: check_result; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.check_result (
    id uuid NOT NULL,
    evidence_record_id uuid NOT NULL,
    check_id character varying(120) NOT NULL,
    application_slug character varying(120) NOT NULL,
    control_id character varying(120) NOT NULL,
    framework character varying(120) NOT NULL,
    source_system character varying(120) NOT NULL,
    status character varying(20) NOT NULL,
    observed character varying(2000),
    expected character varying(2000),
    detail character varying(2000),
    evidence_version integer DEFAULT 0 NOT NULL,
    evaluated_at timestamp with time zone NOT NULL
);


--
-- Name: compliance_snapshot; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.compliance_snapshot (
    id uuid NOT NULL,
    taken_at timestamp with time zone NOT NULL,
    applications integer DEFAULT 0 NOT NULL,
    expected integer DEFAULT 0 NOT NULL,
    compliant integer DEFAULT 0 NOT NULL,
    compliance_pct double precision DEFAULT 0 NOT NULL,
    completeness_pct double precision DEFAULT 0 NOT NULL,
    approved_evidence integer DEFAULT 0 NOT NULL,
    open_findings integer DEFAULT 0 NOT NULL,
    evidence_count integer DEFAULT 0 NOT NULL,
    integrity_checked integer DEFAULT 0 NOT NULL,
    integrity_intact integer DEFAULT 0 NOT NULL
);


--
-- Name: evidence_approval_role; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_approval_role (
    role character varying(40) NOT NULL,
    framework_scope character varying(40),
    control_family_scope character varying(40),
    can_submit boolean DEFAULT false NOT NULL,
    can_approve boolean DEFAULT false NOT NULL,
    description character varying(200) NOT NULL
);


--
-- Name: evidence_approval_user; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_approval_user (
    username character varying(64) NOT NULL,
    display_name character varying(120) NOT NULL,
    role character varying(40) NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: evidence_lifecycle_event; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_lifecycle_event (
    id uuid NOT NULL,
    evidence_record_id uuid NOT NULL,
    from_state character varying(20),
    to_state character varying(20) NOT NULL,
    action character varying(40) NOT NULL,
    actor character varying(160),
    note character varying(2000),
    occurred_at timestamp with time zone NOT NULL
);


--
-- Name: evidence_record; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_record (
    id uuid NOT NULL,
    evidence_key character varying(600) NOT NULL,
    application_id uuid NOT NULL,
    application_slug character varying(120) NOT NULL,
    control_id character varying(120) NOT NULL,
    framework character varying(120) NOT NULL,
    source_system character varying(120) NOT NULL,
    source_object_id character varying(200),
    title character varying(400),
    current_version integer DEFAULT 0 NOT NULL,
    latest_sha256 character varying(64),
    latest_collected_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    lifecycle_state character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    reviewed_by character varying(160),
    reviewed_at timestamp with time zone,
    lifecycle_note character varying(2000)
);


--
-- Name: evidence_tag; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_tag (
    id uuid NOT NULL,
    evidence_record_id uuid NOT NULL,
    tag_key character varying(160) NOT NULL,
    tag_value character varying(600)
);


--
-- Name: evidence_version; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_version (
    id uuid NOT NULL,
    evidence_record_id uuid NOT NULL,
    version_number integer NOT NULL,
    sha256 character varying(64) NOT NULL,
    content_type character varying(120) DEFAULT 'application/octet-stream'::character varying NOT NULL,
    size_bytes bigint NOT NULL,
    object_key character varying(600) NOT NULL,
    collected_at timestamp with time zone NOT NULL,
    collected_by character varying(160),
    ingestion_run_id uuid,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: evidence_version_metadata; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.evidence_version_metadata (
    evidence_version_id uuid NOT NULL,
    meta_key character varying(160) NOT NULL,
    meta_value character varying(2000)
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: onboarding_scan; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.onboarding_scan (
    id uuid NOT NULL,
    application_slug character varying(120) NOT NULL,
    status character varying(20) NOT NULL,
    requested_by character varying(160),
    frameworks character varying(2000),
    sources character varying(2000),
    current_phase character varying(60),
    scheduler_run_id character varying(64),
    completeness_pct double precision,
    compliance_pct double precision,
    message character varying(2000),
    created_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone
);


--
-- Name: onboarding_scan_phase; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.onboarding_scan_phase (
    onboarding_scan_id uuid NOT NULL,
    phase_key character varying(60) NOT NULL,
    phase_status character varying(600)
);


--
-- Name: scheduler_run; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.scheduler_run (
    id uuid NOT NULL,
    trigger_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    requested_by character varying(160),
    applications character varying(2000),
    frameworks character varying(2000),
    sources character varying(2000),
    received integer DEFAULT 0 NOT NULL,
    ingested integer DEFAULT 0 NOT NULL,
    duplicates integer DEFAULT 0 NOT NULL,
    failed integer DEFAULT 0 NOT NULL,
    message character varying(2000),
    created_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone
);


--
-- Name: scheduler_run_source; Type: TABLE; Schema: pramaan; Owner: -
--

CREATE TABLE pramaan.scheduler_run_source (
    scheduler_run_id uuid NOT NULL,
    source_key character varying(120) NOT NULL,
    source_summary character varying(600)
);


--
-- Data for Name: applications; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.applications (id, slug, name, business_unit, criticality, owner, technology, auto_created, created_at, updated_at, active, onboarding_profile) FROM stdin;
e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	Net Banking	Retail Banking	HIGH	app.owner.netbanking	Java,Oracle,NGINX	f	2026-09-15 06:00:01.943324+00	2026-09-15 06:00:01.943324+00	t	\N
4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	Mobile Banking	Retail Banking	HIGH	app.owner.mobile	Kotlin,PostgreSQL,Kubernetes	f	2026-09-15 06:00:01.943324+00	2026-09-15 06:00:01.943324+00	t	\N
9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	Payments	Payments	CRITICAL	app.owner.payments	Go,PostgreSQL,Redis	f	2026-09-15 06:00:01.943324+00	2026-09-15 06:00:01.943324+00	t	\N
\.


--
-- Data for Name: check_result; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.check_result (id, evidence_record_id, check_id, application_slug, control_id, framework, source_system, status, observed, expected, detail, evidence_version, evaluated_at) FROM stdin;
c8932598-68e0-4a11-8adb-16cc050cc6a5	00c73d89-1be7-48e3-a90d-24575535dfe8	RULE-EVIDENCE-COLLECTED	net-banking	TLS-PROTOCOL-VERSION	PCI_DSS	AGENT_TLS	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
bb0aa800-dc03-406a-b1ec-c838b48c0004	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	RULE-EVIDENCE-COLLECTED	payments	DPSC-SDLC-04	DPSC	MOCK_GITHUB	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
fa740b06-c24f-4750-a2f4-f97a101ce569	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
c848dc95-4bd1-4050-860f-7b2ffd26d518	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	RULE-EVIDENCE-COLLECTED	mobile-banking	PCI-DSS-12.1	PCI_DSS	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
8d9bfb2b-a7a3-44b2-b1c5-38dc9fdc05d1	0b589896-b23e-452f-bb27-cc5088dadf8d	RULE-EVIDENCE-COLLECTED	mobile-banking	MW-AUTODEPLOY	C-SITE	SIM_TOMCAT	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
2b37ebe2-2d57-4f35-973e-7f39aa9d566a	654cb34d-ee4d-46f5-9f4b-966288fbee24	RULE-CHANGE-TICKET-PRESENT	mobile-banking	ITPP-CHG-02	ITPP	SERVICENOW	FAIL	no rule clause matched	objectType = change_ticket	fell through to default verdict FAIL	1	2026-09-15 06:38:45.19365+00
fd47392c-8b8a-4325-b77d-46797f70ac1a	137f0260-d7cc-432c-8ad1-4417844da923	RULE-EVIDENCE-COLLECTED	net-banking	OS-SSH-ROOT-LOGIN	C-SITE	AGENT_OS_LINUX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
8f9befab-32fe-43da-b1f4-50358ccd0476	252a8116-32db-4c3f-8bc4-0fe0eee53653	RULE-EVIDENCE-COLLECTED	payments	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
aef80cb8-418a-4307-888d-73193f19323a	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	RULE-EVIDENCE-COLLECTED	mobile-banking	MW-HSTS	DPSC	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
439af149-d5cb-49a1-af51-d8c19837722d	2777b2ba-3652-4908-9561-98a028beef82	RULE-EVIDENCE-COLLECTED	payments	ITPP-INC-01	ITPP	MOCK_SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
b7c2cebd-ed39-4044-8d82-e46ac7ecfff4	2777b2ba-3652-4908-9561-98a028beef82	RULE-INCIDENT-OPEN	payments	ITPP-INC-01	ITPP	MOCK_SERVICENOW	WARNING	matched: "objectType": "incident_record"	no open incident_record	incident record present; confirm it is resolved	1	2026-09-15 06:38:45.19365+00
affba111-3cca-4d70-b358-078a59060a92	2874aafc-3723-40bc-b524-229f1b2d7b8c	RULE-EVIDENCE-COLLECTED	mobile-banking	MW-TLS-VERSION	PCI_DSS	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
cf2a1992-ae1d-40de-adee-a5bce0601ade	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	RULE-EVIDENCE-COLLECTED	payments	PCI-DSS-6.3	PCI_DSS	MOCK_JIRA	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
526854ba-6d02-4c95-a8c3-ac2b80914c7c	2ed3b783-c765-4c35-8d28-3649d5ee5b98	RULE-EVIDENCE-COLLECTED	mobile-banking	PCI-DSS-12.10	PCI_DSS	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
aa4bea37-e490-4839-8120-ad182b1fcf43	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
88d680b4-6683-489a-9d4f-42e21f49aa29	31939122-58c2-4c45-832d-946552e91124	RULE-EVIDENCE-COLLECTED	mobile-banking	ITPP-DOC-03	ITPP	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
6349f48f-9df1-494c-abe2-7f1832371fd6	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	RULE-EVIDENCE-COLLECTED	mobile-banking	PCI-DSS-12.10	PCI_DSS	MOCK_SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
2102645c-2f7d-4a80-9d16-44621402a27e	3340f502-aac7-4e37-9498-874ce46117cf	RULE-EVIDENCE-COLLECTED	payments	DB-TLS-IN-TRANSIT	PCI_DSS	AGENT_DATABASE_POSTGRESQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
a9a6225b-864e-4423-b99f-bfb407523b6e	379fbd9a-38cd-4989-81ba-0f9bd533736d	RULE-EVIDENCE-COLLECTED	net-banking	PCI-DSS-12.1	PCI_DSS	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
2eb6b094-3b5b-47be-935e-533ab2fa7790	38f74afa-6f51-46c6-87c9-12929e812362	RULE-EVIDENCE-COLLECTED	payments	DB-AUDIT-LOGGING	PCI_DSS	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
228d5047-4170-49a8-a4ae-7ad0762d3a89	3bb02091-449e-497e-9317-427cacc54837	RULE-EVIDENCE-COLLECTED	net-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
f886ec1e-727e-4743-a5eb-3aa78343dd2d	3e71e55c-5859-4652-b492-207dac524809	RULE-EVIDENCE-COLLECTED	net-banking	PCI-DSS-12.10	PCI_DSS	MOCK_SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
3f8bb789-8f66-43ad-bab7-4b8083ff072a	3ee6901d-e417-471c-a00e-0eb95e2b8491	RULE-EVIDENCE-COLLECTED	payments	DB-AUDIT-LOGGING	PCI_DSS	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
3b531808-611c-4b17-bf8d-f47705940619	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	RULE-EVIDENCE-COLLECTED	mobile-banking	DPSC-SDLC-04	DPSC	MOCK_GITHUB	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
7a30a823-c601-4a1f-b0f0-27f8901c6104	40630923-dd28-49e1-b40f-8169dee97763	RULE-EVIDENCE-COLLECTED	net-banking	DPSC-GOV-01	DPSC	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
4440146c-99a0-42d9-8c1e-af21f6f061b5	44763b2a-ed03-42a3-b8c0-68662c2d9b67	RULE-EVIDENCE-COLLECTED	mobile-banking	ITPP-INC-01	ITPP	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
44235839-7481-4e97-8654-1184f90363fa	44763b2a-ed03-42a3-b8c0-68662c2d9b67	RULE-INCIDENT-OPEN	mobile-banking	ITPP-INC-01	ITPP	SERVICENOW	WARNING	matched: "objectType": "incident_record"	no open incident_record	incident record present; confirm it is resolved	1	2026-09-15 06:38:45.19365+00
485020da-288a-4b3d-852a-c3f0a4aaa7b6	49606c9a-050b-4562-830f-dd5e58ab6b48	RULE-EVIDENCE-COLLECTED	net-banking	abcd	PCI_DSS	BULK_UPLOAD	WARNING	no rule clause matched	every technical check in the bundle passed	fell through to default verdict WARNING	1	2026-09-15 06:38:45.19365+00
3fd40207-d8aa-46c2-bb81-598f04809550	49d5d6ba-7459-4bcf-901a-5424e414a32a	RULE-EVIDENCE-COLLECTED	payments	MW-TLS-VERSION	PCI_DSS	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
99dfa9ce-c734-46a7-a96f-d6d7019a4540	4dd11c77-bc7f-4615-b96d-796bced6077c	RULE-EVIDENCE-COLLECTED	mobile-banking	MW-HSTS	DPSC	AGENT_MIDDLEWARE_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
ed3ebded-1804-40b1-9b25-d81c3f777357	5edff7ca-679a-4517-bdda-31cc792ca6ff	RULE-EVIDENCE-COLLECTED	payments	MW-AUTODEPLOY	C-SITE	SIM_TOMCAT	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
3f53a257-4bf8-41dd-931c-ca7f2cca99bf	5ef049e2-85a0-44b1-9e80-d494a5043570	RULE-EVIDENCE-COLLECTED	payments	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
abc6041a-0259-49b6-a0ff-69d6efdf51d2	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	RULE-EVIDENCE-COLLECTED	net-banking	MW-AUTODEPLOY	C-SITE	SIM_TOMCAT	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
7d86a461-dacd-421c-863e-2b9e2b6fb9e1	62b53770-56b0-4556-9e8a-bcef611a5588	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
a699b7ca-c753-4085-b5a0-91b8f4316083	64ddd7c6-fdca-443b-8dde-fbc0d2801462	RULE-EVIDENCE-COLLECTED	mobile-banking	DPSC-GOV-01	DPSC	MOCK_CONFLUENCE	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
fad8737b-627c-4873-9848-f65a0e77d496	654cb34d-ee4d-46f5-9f4b-966288fbee24	RULE-EVIDENCE-COLLECTED	mobile-banking	ITPP-CHG-02	ITPP	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
04be926d-e647-4d0e-a0d6-861e95313307	67a16d1b-085b-4faf-8333-d4a8811667d7	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
d903b299-1dd2-439c-b806-05eb205dcf88	6881638e-3f67-4cc8-a178-e67cd76a90bc	RULE-EVIDENCE-COLLECTED	net-banking	MW-HSTS	DPSC	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
4b1d5b2c-e728-47ed-a3c9-18eb5a4d5818	68b6c810-9ce8-4c96-a653-133a4042efd6	RULE-EVIDENCE-COLLECTED	net-banking	MW-BANNER-SUPPRESSION	C-SITE	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
2f1a7ee1-9125-4dea-a51a-1177ccd758a5	6f4cc0f7-e8c8-4121-9897-7d43cd064263	RULE-EVIDENCE-COLLECTED	net-banking	PCI-DSS-6.3	PCI_DSS	MOCK_JIRA	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
82e9838f-a199-4ea3-ae4f-34e032052b24	76c9a2eb-b56b-491f-b914-3d5729bf8aae	RULE-EVIDENCE-COLLECTED	mobile-banking	ITPP-INC-01	ITPP	MOCK_SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
d8a3d882-2a05-4ae8-bfbf-64540519448d	76c9a2eb-b56b-491f-b914-3d5729bf8aae	RULE-INCIDENT-OPEN	mobile-banking	ITPP-INC-01	ITPP	MOCK_SERVICENOW	WARNING	matched: "objectType": "incident_record"	no open incident_record	incident record present; confirm it is resolved	1	2026-09-15 06:38:45.19365+00
7ff9165e-f182-4f36-a9f8-754f45f8dce1	79296e0a-4136-445f-a85b-64d2607cc1a3	RULE-EVIDENCE-COLLECTED	net-banking	PCI-DSS-12.10	PCI_DSS	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
c534aaef-b0ac-4cb0-8545-81c623642b70	7efff257-28af-43c1-8634-51e773b07948	RULE-EVIDENCE-COLLECTED	payments	PCI-DSS-6.2	PCI_DSS	MOCK_GITHUB	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
c7056ea8-0994-477b-8d94-b04b7cfdf612	81612e44-ed06-45f7-a8c1-89988c88de7b	RULE-EVIDENCE-COLLECTED	payments	ITPP-INC-01	ITPP	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
c9e5fcfb-efa7-403c-bc12-938c128bf7cf	81612e44-ed06-45f7-a8c1-89988c88de7b	RULE-INCIDENT-OPEN	payments	ITPP-INC-01	ITPP	SERVICENOW	WARNING	matched: "objectType": "incident_record"	no open incident_record	incident record present; confirm it is resolved	1	2026-09-15 06:38:45.19365+00
6d1e4351-3007-456e-b529-a994436c2045	8296014e-a49c-4866-a948-6433ac7cef44	RULE-EVIDENCE-COLLECTED	net-banking	ITPP-DOC-03	ITPP	MOCK_CONFLUENCE	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
b98195ff-d5ee-42a8-beca-776a8cac838c	82fe525f-b0e6-4630-be90-6699615e0868	RULE-EVIDENCE-COLLECTED	net-banking	PCI-DSS-6.2	PCI_DSS	MOCK_GITHUB	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
c0541355-4644-41a4-a682-7ae7e9a696b6	86a41980-cbd9-478c-a4bc-371bc159f431	RULE-EVIDENCE-COLLECTED	mobile-banking	MW-BANNER-SUPPRESSION	C-SITE	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
9c66cd2c-90fc-45ea-9e92-f9ecb3554afb	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
37e3f460-ba3a-43ec-9aed-ebb7ce009fb4	897683f8-35a9-4af6-b103-8f073087e332	RULE-EVIDENCE-COLLECTED	payments	DB-PASSWORD-STORAGE	C-SITE	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
c0e18ac6-5687-4226-bc60-46ecc9083268	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	RULE-EVIDENCE-COLLECTED	net-banking	ITPP-DOC-03	ITPP	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
2614f048-1c58-4767-bc1e-f238caa4b074	95678b8e-8ebc-49be-acf4-40c4f43c77d5	RULE-EVIDENCE-COLLECTED	mobile-banking	OS-SSH-ROOT-LOGIN	C-SITE	AGENT_OS_LINUX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
c37eb69b-8ee2-4a98-857e-e2fbd415d732	95719fec-0ab6-43d4-8097-2057cd8344fc	RULE-EVIDENCE-COLLECTED	mobile-banking	DPSC-GOV-01	DPSC	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
a2c3c03b-b3df-4409-8853-5173486cf4ee	969d7c02-eb0c-47af-a189-b94c42ce67dc	RULE-EVIDENCE-COLLECTED	payments	DPSC-GOV-01	DPSC	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
4e807a03-895b-4053-9c8c-4aee8b151b76	96ce1593-ee52-455f-ad08-64ec36bbff02	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
8ae127e7-51f6-463d-8ff2-943605afbea3	97763088-2d92-472e-8672-362cef6175a4	RULE-EVIDENCE-COLLECTED	payments	PCI-DSS-12.10	PCI_DSS	MOCK_SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
bc6e9847-ae6f-402d-87d1-2e88491632ce	9aba1297-9050-494d-a023-89166919ce50	RULE-EVIDENCE-COLLECTED	payments	MW-TLS-VERSION	PCI_DSS	SIM_TOMCAT	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
69c67592-2c46-473a-9524-057b64390eda	a0569699-2209-4fff-a8a0-af1e0ccaa227	RULE-EVIDENCE-COLLECTED	net-banking	DPSC-GOV-01	DPSC	MOCK_CONFLUENCE	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
818eb4ce-3e22-4a88-b6e6-f94cd05c0b56	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	RULE-EVIDENCE-COLLECTED	payments	MW-BANNER-SUPPRESSION	C-SITE	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
59f8c7f3-b7ab-49cc-ba6d-fff816f1a1b8	a5667964-1027-4f26-b6d5-3234f80dd54f	RULE-EVIDENCE-COLLECTED	net-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
1dd34149-c74f-4f43-bba0-392b0f643a89	a99ef1f9-2193-4c30-b4b4-743268c8003e	RULE-EVIDENCE-COLLECTED	mobile-banking	DB-PASSWORD-STORAGE	C-SITE	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
50880695-b6ff-4929-b5cb-0a59e9199c71	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	RULE-EVIDENCE-COLLECTED	payments	ITPP-DOC-03	ITPP	MOCK_CONFLUENCE	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
1f02801e-b90d-4156-b960-cfbbb40e11bd	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	RULE-EVIDENCE-COLLECTED	net-banking	ITPP-CHG-02	ITPP	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
5fd97bec-1230-4e34-a58d-91f127cc9582	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	RULE-CHANGE-TICKET-PRESENT	net-banking	ITPP-CHG-02	ITPP	SERVICENOW	FAIL	no rule clause matched	objectType = change_ticket	fell through to default verdict FAIL	1	2026-09-15 06:38:45.19365+00
d88b6227-ac79-42a7-9615-44c339f968d5	b0946cad-efb7-4bee-bb25-1626fe14c229	RULE-EVIDENCE-COLLECTED	net-banking	MW-TLS-VERSION	PCI_DSS	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
e1049359-4013-4344-92b0-78c3a131c1ba	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	RULE-EVIDENCE-COLLECTED	net-banking	DB-TLS-IN-TRANSIT	PCI_DSS	AGENT_DATABASE_POSTGRESQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
3ad954ac-7b80-4768-a94d-7d7e9f2f6bab	ba85be2a-8251-4dac-bb80-4655f5a3979a	RULE-EVIDENCE-COLLECTED	net-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
95dd4499-3757-400a-96fd-774d6fa4808f	bbe54c6f-2024-4781-beee-7bb1c4f10234	RULE-EVIDENCE-COLLECTED	net-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
1e4d2582-8066-4b63-9181-8024225f4924	bff6261f-c689-4dee-95a9-281451058e08	RULE-EVIDENCE-COLLECTED	payments	DB-AUTH-NO-TRUST	C-SITE	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
949bc8f5-51f6-4bd4-ab26-957770741add	c3c77f08-3579-4394-84b0-8592dd97bbd8	RULE-EVIDENCE-COLLECTED	net-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
47c60a7c-a620-42f7-a861-32757a537c20	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	RULE-EVIDENCE-COLLECTED	net-banking	ITPP-CHG-02	ITPP	MOCK_JIRA	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
64928b35-8d12-4bf7-ae68-e40cdb987cf6	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	RULE-CHANGE-TICKET-PRESENT	net-banking	ITPP-CHG-02	ITPP	MOCK_JIRA	PASS	matched: "objectType": "change_ticket"	objectType = change_ticket	linked change ticket found	1	2026-09-15 06:38:45.19365+00
482fd5c9-20bb-4864-a035-6e751041c851	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	RULE-EVIDENCE-COLLECTED	payments	DPSC-GOV-01	DPSC	MOCK_CONFLUENCE	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
42c80635-39c3-49e8-bd9f-3eaeb7a941d9	c5ce959e-f256-4cef-84df-90502457d64c	RULE-EVIDENCE-COLLECTED	payments	PCI-DSS-12.10	PCI_DSS	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
de9c090f-e0f9-48fe-8cf3-1ccb51a54ff4	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	RULE-EVIDENCE-COLLECTED	net-banking	ITPP-INC-01	ITPP	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
c33e74a6-9029-46b9-b34a-372e472d93a2	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	RULE-INCIDENT-OPEN	net-banking	ITPP-INC-01	ITPP	SERVICENOW	WARNING	matched: "objectType": "incident_record"	no open incident_record	incident record present; confirm it is resolved	1	2026-09-15 06:38:45.19365+00
f97cd8ec-ab2b-48ec-9e26-471d45ed8642	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	RULE-EVIDENCE-COLLECTED	mobile-banking	ITPP-CHG-02	ITPP	MOCK_JIRA	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
38f40bcd-673b-4e97-a32c-c49e2319a01f	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	RULE-CHANGE-TICKET-PRESENT	mobile-banking	ITPP-CHG-02	ITPP	MOCK_JIRA	PASS	matched: "objectType": "change_ticket"	objectType = change_ticket	linked change ticket found	1	2026-09-15 06:38:45.19365+00
b3645048-9428-4f91-9cc6-82185b5f046b	cc6d26ef-a350-4d32-9d2a-39d180443f44	RULE-EVIDENCE-COLLECTED	mobile-banking	MW-TLS-VERSION	PCI_DSS	SIM_TOMCAT	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
525c794b-adbc-4d7e-8d1e-1de8e973926d	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	RULE-EVIDENCE-COLLECTED	mobile-banking	PCI-DSS-6.2	PCI_DSS	MOCK_GITHUB	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
876771b7-6ccb-4688-8310-550b1ae43c3f	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	RULE-EVIDENCE-COLLECTED	payments	MW-HSTS	DPSC	SIM_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
928e232b-1a55-4125-9e7e-ae03a3689e6e	d3964043-ac22-4b12-8f9a-a14152017f98	RULE-EVIDENCE-COLLECTED	mobile-banking	PCI-DSS-6.3	PCI_DSS	MOCK_JIRA	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
8cc13442-b9e6-4b34-a7e7-e5450b16a455	d452f3b9-ce2c-410a-968b-4dd45948eee6	RULE-EVIDENCE-COLLECTED	payments	DB-AUTH-NO-TRUST	C-SITE	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
343562ae-c1ec-4f69-bd7e-e98871f5eaec	d5d0f39b-6c07-4161-a96c-94dc258702df	RULE-EVIDENCE-COLLECTED	net-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_MYSQL	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
5907dd77-7715-4d63-a1ba-3e960b66a590	d6f36c83-2f64-4631-ac94-fe19b89c69e7	RULE-EVIDENCE-COLLECTED	net-banking	DPSC-SDLC-04	DPSC	MOCK_GITHUB	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
cccc554e-838b-4b27-bc44-ef5c17f0d531	d9886a1d-a255-4725-935d-d62cf11b8766	RULE-EVIDENCE-COLLECTED	net-banking	DB-PASSWORD-STORAGE	C-SITE	SIM_POSTGRES	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
27bc6616-341c-4940-a294-089ca7321f5b	e3167d52-f263-417c-964f-5523c33c1d91	RULE-EVIDENCE-COLLECTED	payments	ITPP-DOC-03	ITPP	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
bdf3306b-48ef-4709-baf2-424d5aa22e1e	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	RULE-EVIDENCE-COLLECTED	mobile-banking	ITPP-DOC-03	ITPP	MOCK_CONFLUENCE	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
7219e8bf-9882-490e-b63d-a5bf927f71ab	ee1c3319-eed3-4078-8dfd-5313359b7cd5	RULE-EVIDENCE-COLLECTED	net-banking	ITPP-INC-01	ITPP	MOCK_SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
ca306ac5-410b-4318-adc9-2b1c2cd7ebd6	ee1c3319-eed3-4078-8dfd-5313359b7cd5	RULE-INCIDENT-OPEN	net-banking	ITPP-INC-01	ITPP	MOCK_SERVICENOW	WARNING	matched: "objectType": "incident_record"	no open incident_record	incident record present; confirm it is resolved	1	2026-09-15 06:38:45.19365+00
5539487a-be8b-4564-9dcf-6221d897d3e4	ef73d9bb-1f1a-4390-a202-4a056291a646	RULE-EVIDENCE-COLLECTED	payments	PCI-DSS-12.1	PCI_DSS	SHAREPOINT	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
5cfbcf18-9bbe-481a-b027-dd4ac55d2b31	f009f238-7441-477e-bac9-f38d5c34a920	RULE-EVIDENCE-COLLECTED	payments	MW-TLS-VERSION	PCI_DSS	AGENT_MIDDLEWARE_NGINX	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
0e56af58-4364-48b6-adc1-c061a0e2bcc4	f178b149-af5a-44fd-b461-cddfeb9a76d5	RULE-EVIDENCE-COLLECTED	payments	ITPP-CHG-02	ITPP	SERVICENOW	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
5a629b63-605a-44be-96e6-4bf13c325b62	f178b149-af5a-44fd-b461-cddfeb9a76d5	RULE-CHANGE-TICKET-PRESENT	payments	ITPP-CHG-02	ITPP	SERVICENOW	FAIL	no rule clause matched	objectType = change_ticket	fell through to default verdict FAIL	1	2026-09-15 06:38:45.19365+00
ef664ea3-5996-46a3-bd5e-5d446062e418	f46cd29a-37a3-48c7-b718-e18c28f766b0	RULE-EVIDENCE-COLLECTED	payments	ITPP-CHG-02	ITPP	MOCK_JIRA	PASS	matched: "status": "collected"	every technical check in the bundle passed	collection reported COLLECTED	1	2026-09-15 06:38:45.19365+00
be2ed9f1-57d4-4768-93fc-922393058c89	f46cd29a-37a3-48c7-b718-e18c28f766b0	RULE-CHANGE-TICKET-PRESENT	payments	ITPP-CHG-02	ITPP	MOCK_JIRA	PASS	matched: "objectType": "change_ticket"	objectType = change_ticket	linked change ticket found	1	2026-09-15 06:38:45.19365+00
38f284ea-42e1-4f81-81ec-c7b9a7678c9e	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	RULE-EVIDENCE-COLLECTED	net-banking	MW-TLS-VERSION	PCI_DSS	SIM_TOMCAT	PASS	matched: "status": "pass"	every technical check in the bundle passed	all technical checks in the bundle passed	1	2026-09-15 06:38:45.19365+00
\.


--
-- Data for Name: compliance_snapshot; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.compliance_snapshot (id, taken_at, applications, expected, compliant, compliance_pct, completeness_pct, approved_evidence, open_findings, evidence_count, integrity_checked, integrity_intact) FROM stdin;
fb8b3654-99d8-4587-a6fa-d179da407e5c	2026-07-21 06:21:18.672928+00	3	54	23	42	55	6	31	12	12	11
164b01c1-33f1-441a-9220-909bbcef17bd	2026-07-28 06:21:18.672928+00	3	54	25	46.9	59.3	9	29	21	21	19
b8fae0b1-0c96-4d25-888e-692b453aae3f	2026-08-04 06:21:18.672928+00	3	54	28	51.7	63.6	11	26	29	29	27
e53fe110-2af5-4f3a-ad24-8327886faa1b	2026-08-11 06:21:18.672928+00	3	54	31	56.6	67.9	14	23	38	38	36
998c1263-6df5-492e-a5f1-6944c67f2d8d	2026-08-18 06:21:18.672928+00	3	54	33	61.4	72.1	16	21	46	46	44
7dbc6661-a9eb-4cae-a010-710cd36ead87	2026-08-25 06:21:18.672928+00	3	54	36	66.3	76.4	19	18	55	55	53
76c49f39-b54e-445c-818a-24766e585a1c	2026-09-01 06:21:18.672928+00	3	54	38	71.1	80.7	21	16	63	63	62
a8deb175-338d-4440-b672-406338a9e615	2026-09-08 06:21:18.672928+00	3	54	41	76	85	24	13	72	72	72
\.


--
-- Data for Name: evidence_approval_role; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_approval_role (role, framework_scope, control_family_scope, can_submit, can_approve, description) FROM stdin;
APP_OWNER	\N	\N	t	f	Submits evidence for review; cannot approve or reject
AUDITOR	\N	\N	f	t	Approves or rejects evidence for any framework
ISG_OFFICER	ISG	VAPT	f	t	Approves or rejects ISG evidence, or VAPT-family evidence in any framework
PCIDSS_AUDITOR	PCI_DSS	\N	f	t	Approves or rejects PCI DSS evidence only
DPSC_AUDITOR	DPSC	\N	f	t	Approves or rejects DPSC evidence only
\.


--
-- Data for Name: evidence_approval_user; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_approval_user (username, display_name, role, created_at) FROM stdin;
app.owner.demo	Demo App Owner	APP_OWNER	2026-09-15 06:21:18.625435+00
auditor.demo	Demo Generic Auditor	AUDITOR	2026-09-15 06:21:18.625435+00
isg.officer.demo	Demo ISG Officer	ISG_OFFICER	2026-09-15 06:21:18.625435+00
pcidss.auditor.demo	Demo PCI DSS Auditor	PCIDSS_AUDITOR	2026-09-15 06:21:18.625435+00
dpsc.auditor.demo	Demo DPSC Auditor	DPSC_AUDITOR	2026-09-15 06:21:18.625435+00
\.


--
-- Data for Name: evidence_lifecycle_event; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_lifecycle_event (id, evidence_record_id, from_state, to_state, action, actor, note, occurred_at) FROM stdin;
c99ed216-5bdc-419f-9bf5-483b10db3d9f	137f0260-d7cc-432c-8ad1-4417844da923	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.678876+00
723a9ace-b2e5-4314-8e5e-3e0da0f09628	95678b8e-8ebc-49be-acf4-40c4f43c77d5	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.77853+00
32b24e7a-1575-4cf4-b6d7-84843563dc2e	3340f502-aac7-4e37-9498-874ce46117cf	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.8147+00
4f8e3a15-9e42-471b-b0b8-b1cc9287e099	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.848633+00
65afa0df-a86b-4d3c-9ac8-4b8ab5c77f9b	f009f238-7441-477e-bac9-f38d5c34a920	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.886523+00
0386b7dc-02f9-4126-bebe-3a003d396e6f	4dd11c77-bc7f-4615-b96d-796bced6077c	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.920094+00
972dff12-37ec-4398-a915-a65226f74219	00c73d89-1be7-48e3-a90d-24575535dfe8	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:21:18.951334+00
1c6c3b0b-bb73-4485-820f-16064e3634c6	d3964043-ac22-4b12-8f9a-a14152017f98	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.266381+00
e6b704a4-6c8d-4c08-8526-120c2a306c8d	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.426391+00
a097cfbe-de06-4a57-9606-6326edf96b86	6f4cc0f7-e8c8-4121-9897-7d43cd064263	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.542096+00
04d3ef0e-a989-4833-8793-68fea9f38c12	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.649468+00
1158d869-e58a-42d1-b688-346b461c6573	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.759826+00
0a184bcc-e52a-4a6d-ade1-ab7d0e8f2865	f46cd29a-37a3-48c7-b718-e18c28f766b0	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.859682+00
b6a19bdd-066d-4882-9ea7-53c9faff5ae8	76c9a2eb-b56b-491f-b914-3d5729bf8aae	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:28.955762+00
15df8167-f2f4-44a0-b6e6-8a9a5322ff2e	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.047494+00
dec4ccda-ab94-4bb8-95b6-f43f90ac99dc	ee1c3319-eed3-4078-8dfd-5313359b7cd5	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.143347+00
65bd9cc5-08f8-4082-9972-7ae1dc92baf3	3e71e55c-5859-4652-b492-207dac524809	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.245135+00
ff6d51f7-78c3-4e64-9b30-51cc25c4d369	2777b2ba-3652-4908-9561-98a028beef82	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.330168+00
3c826fbf-ac5b-4c62-a6c6-6b60e4220916	97763088-2d92-472e-8672-362cef6175a4	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.412748+00
08b44bbd-c66a-4130-a0d5-51360308a479	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.499429+00
d63130ad-a40a-4846-9efc-bf1cd884aadb	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.589986+00
823609d7-7b55-4840-b889-a72c5c4a21b8	82fe525f-b0e6-4630-be90-6699615e0868	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.662097+00
e97f5785-18ea-423a-845b-897934f5b286	d6f36c83-2f64-4631-ac94-fe19b89c69e7	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.744088+00
21c42aca-98d1-49de-b033-805d31e328da	7efff257-28af-43c1-8634-51e773b07948	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.821785+00
3704f1c3-5689-4869-b6bb-0731d9c9c4f0	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.894913+00
d32f179d-f4a8-467d-88e6-75ec02f00f41	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:29.984277+00
ebeee8a0-a572-4ae0-9bdc-28fff1fab57a	64ddd7c6-fdca-443b-8dde-fbc0d2801462	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.063814+00
69bfad84-0d87-4ef0-ae51-ce3ffda0df0e	8296014e-a49c-4866-a948-6433ac7cef44	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.141057+00
152dfcc1-c238-4fe1-8ac7-f22d5c7ddf5c	a0569699-2209-4fff-a8a0-af1e0ccaa227	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.22164+00
dbca310a-6393-4011-9fe3-95f4b7d92a76	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.307794+00
646d3369-69d9-4387-acc6-6a56057d2e1e	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.373883+00
3056a4e8-36cf-4292-b070-605bd82b718a	67a16d1b-085b-4faf-8333-d4a8811667d7	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.464018+00
94930eb7-69e9-4897-adc6-d297d5cac02b	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.537724+00
d8d49dcf-8ff5-45db-a8db-da2a3402778c	a99ef1f9-2193-4c30-b4b4-743268c8003e	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.606565+00
d61dc3e7-876d-4680-ae78-fab6877979fd	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.680304+00
a88fc38f-636f-4977-bd7f-a8d5cbab6743	bbe54c6f-2024-4781-beee-7bb1c4f10234	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.761748+00
7fffcefc-ad57-4dcf-ba26-98e5bea36ec9	a5667964-1027-4f26-b6d5-3234f80dd54f	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.842588+00
1630675d-ce9c-478a-890a-5c2d76623b2c	d9886a1d-a255-4725-935d-d62cf11b8766	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.906226+00
3058aa33-421f-4ea9-95ee-f0755c838d14	ba85be2a-8251-4dac-bb80-4655f5a3979a	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:30.968695+00
d7a43015-7539-47e5-8c59-72d4ab4b5cd2	252a8116-32db-4c3f-8bc4-0fe0eee53653	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.025726+00
614580a3-1604-4498-8bed-8c8e725d6477	3ee6901d-e417-471c-a00e-0eb95e2b8491	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.091645+00
8cf1cff7-eeaa-4deb-8040-0685a13a1f8d	897683f8-35a9-4af6-b103-8f073087e332	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.154248+00
78887070-b146-445d-ae5b-30265691dc3f	bff6261f-c689-4dee-95a9-281451058e08	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.221647+00
fb5ab253-5167-4e86-8c5e-580b8c1655d6	96ce1593-ee52-455f-ad08-64ec36bbff02	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.296384+00
031c04f1-a938-4964-834f-3ba32fce7567	62b53770-56b0-4556-9e8a-bcef611a5588	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.376276+00
40435785-172a-4dfa-8ddd-07ce0810f948	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.453732+00
e34df5f3-da7d-4247-9f8d-bb5dd084abfd	3bb02091-449e-497e-9317-427cacc54837	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.536426+00
a6c0302d-5cae-4113-a161-525cadafc78a	d5d0f39b-6c07-4161-a96c-94dc258702df	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.629458+00
363e668c-ed9f-4e20-8ae7-cb17c2877716	c3c77f08-3579-4394-84b0-8592dd97bbd8	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.704023+00
bf013280-cb32-4d64-acc5-02c035b0921e	5ef049e2-85a0-44b1-9e80-d494a5043570	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.770223+00
727d229f-3218-48f2-aedb-7b2746ff4144	38f74afa-6f51-46c6-87c9-12929e812362	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.861469+00
8ec785f5-91d1-4707-863c-1cada700a7b1	d452f3b9-ce2c-410a-968b-4dd45948eee6	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:31.937123+00
05b923ea-a472-4daa-aaf0-05e029e84de0	2874aafc-3723-40bc-b524-229f1b2d7b8c	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.017095+00
1e6f3dd3-1bd0-48d0-9388-595daf3f6a17	86a41980-cbd9-478c-a4bc-371bc159f431	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.077083+00
616f4026-20c4-42ee-ad33-0713b2c32935	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.143744+00
ec88c4a2-f5d3-463d-a43e-8168cd60e26d	b0946cad-efb7-4bee-bb25-1626fe14c229	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.202577+00
4aa518bd-3011-4a98-8a23-592a00795f29	68b6c810-9ce8-4c96-a653-133a4042efd6	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.274247+00
b04b67d0-7b6f-4be6-981f-3361267d850b	6881638e-3f67-4cc8-a178-e67cd76a90bc	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.338532+00
5f6da4a3-47c0-45c1-8c2c-21c82aa60786	49d5d6ba-7459-4bcf-901a-5424e414a32a	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.408136+00
0d8860b5-1a0a-428a-9db2-174cce10acbf	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.480954+00
331a1900-144f-4c23-a78e-c27bf3ba437a	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.535233+00
e78d3ad6-d032-43a2-993b-2549a319909d	cc6d26ef-a350-4d32-9d2a-39d180443f44	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.581244+00
efaeff37-fea1-43d2-aae3-e694629f41e3	0b589896-b23e-452f-bb27-cc5088dadf8d	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.627881+00
9cd36792-2f82-4b26-846e-21881894ad45	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.691518+00
847a72ae-30c5-4c61-9296-c9bcb185606a	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.749868+00
72e9909f-27b5-4675-a3b1-0fa78d407ceb	9aba1297-9050-494d-a023-89166919ce50	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.810012+00
dac12f03-eb34-49c1-9424-e577aefcaaac	5edff7ca-679a-4517-bdda-31cc792ca6ff	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.884957+00
be4fa6ad-c380-4436-9125-3c4cc984152e	31939122-58c2-4c45-832d-946552e91124	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:32.936188+00
75f17b01-8425-49ef-8a30-5d2c0b053ebb	95719fec-0ab6-43d4-8097-2057cd8344fc	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.008916+00
d92eb8d7-07aa-47a7-9cf8-7c0e79e9f3c5	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.074359+00
8d0a1325-77c2-4269-b325-696ca891bcef	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.145884+00
ad884075-d422-4e5c-b2c5-6225c58786e7	40630923-dd28-49e1-b40f-8169dee97763	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.218815+00
c86fca77-9f51-4dfe-a74a-65e88c52164f	379fbd9a-38cd-4989-81ba-0f9bd533736d	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.292715+00
8d4465de-4eee-40ca-ad82-b07dd42cb63e	e3167d52-f263-417c-964f-5523c33c1d91	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.349572+00
715741d7-7c09-4c62-9582-8ff27f60ab78	969d7c02-eb0c-47af-a189-b94c42ce67dc	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.413177+00
3019a2b2-5ecd-4185-a96e-7a2e54dd486d	ef73d9bb-1f1a-4390-a202-4a056291a646	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.486083+00
29203add-7702-4a16-8cbd-cd072636e2aa	654cb34d-ee4d-46f5-9f4b-966288fbee24	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.548329+00
25a1f9a9-c7d5-4e23-8789-2b2326eca9e2	44763b2a-ed03-42a3-b8c0-68662c2d9b67	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.625488+00
d662c2b1-346d-4136-8350-d59a186f4c9f	2ed3b783-c765-4c35-8d28-3649d5ee5b98	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.699528+00
6e4ea97d-cf5f-49be-9995-4ace1732d2c5	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.788715+00
587d1d4f-ea9b-41a5-b5e2-ddfdd4703fed	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.860465+00
2766a9ec-1226-4e8d-8f31-22fdefed9146	79296e0a-4136-445f-a85b-64d2607cc1a3	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:33.94522+00
0601c42e-a147-4788-9d65-6f4881b2a6c6	f178b149-af5a-44fd-b461-cddfeb9a76d5	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:34.035195+00
7ae18131-a9ce-4529-8127-b177022deb35	81612e44-ed06-45f7-a8c1-89988c88de7b	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:34.16039+00
901ce948-78dd-4ad1-bb54-0c11d476bd33	c5ce959e-f256-4cef-84df-90502457d64c	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:37:34.232188+00
85110d35-47b5-4bbb-a12c-561995d4c86b	49606c9a-050b-4562-830f-dd5e58ab6b48	\N	DRAFT	INGESTED	\N	evidence ingested	2026-09-15 06:38:00.784524+00
\.


--
-- Data for Name: evidence_record; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_record (id, evidence_key, application_id, application_slug, control_id, framework, source_system, source_object_id, title, current_version, latest_sha256, latest_collected_at, created_at, updated_at, lifecycle_state, reviewed_by, reviewed_at, lifecycle_note) FROM stdin;
137f0260-d7cc-432c-8ad1-4417844da923	net-banking|C-SITE|OS-SSH-ROOT-LOGIN|AGENT_OS_LINUX|demo-os-ssh-root-login	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	OS-SSH-ROOT-LOGIN	C-SITE	AGENT_OS_LINUX	demo-os-ssh-root-login	net-banking_OS-SSH-ROOT-LOGIN_HOST-CONFIG_20260814_001	1	358536887a3127eb4236fcc539a7c289332e0ee5f2a18032200bd0f6202c66ee	2026-08-14 09:00:00+00	2026-09-15 06:21:18.678876+00	2026-09-15 06:21:18.678876+00	DRAFT	\N	\N	\N
95678b8e-8ebc-49be-acf4-40c4f43c77d5	mobile-banking|C-SITE|OS-SSH-ROOT-LOGIN|AGENT_OS_LINUX|demo-os-ssh-root-login	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	OS-SSH-ROOT-LOGIN	C-SITE	AGENT_OS_LINUX	demo-os-ssh-root-login	mobile-banking_OS-SSH-ROOT-LOGIN_HOST-CONFIG_20260815_001	1	d9a422dff085e2a970f0b2509dc4a42eb21a8f191f7e088f40d3fbfed90df10e	2026-08-15 09:00:00+00	2026-09-15 06:21:18.77853+00	2026-09-15 06:21:18.77853+00	DRAFT	\N	\N	\N
3340f502-aac7-4e37-9498-874ce46117cf	payments|PCI_DSS|DB-TLS-IN-TRANSIT|AGENT_DATABASE_POSTGRESQL|demo-db-tls-in-transit	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-TLS-IN-TRANSIT	PCI_DSS	AGENT_DATABASE_POSTGRESQL	demo-db-tls-in-transit	payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260816_001	1	ac4ac55510e3a784d0c095631bb42938cc12be7b4dcea6d374a767ed058664a7	2026-08-16 09:00:00+00	2026-09-15 06:21:18.8147+00	2026-09-15 06:21:18.8147+00	DRAFT	\N	\N	\N
b722d4b5-565f-4d61-9fde-3e7f0af9a22b	net-banking|PCI_DSS|DB-TLS-IN-TRANSIT|AGENT_DATABASE_POSTGRESQL|demo-db-tls-in-transit	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-TLS-IN-TRANSIT	PCI_DSS	AGENT_DATABASE_POSTGRESQL	demo-db-tls-in-transit	net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260817_001	1	3349e22880e29ab93489ae719f407feeb7880898f2f1e0487bf6c83a14e9c6c8	2026-08-17 09:00:00+00	2026-09-15 06:21:18.848633+00	2026-09-15 06:21:18.848633+00	DRAFT	\N	\N	\N
f009f238-7441-477e-bac9-f38d5c34a920	payments|PCI_DSS|MW-TLS-VERSION|AGENT_MIDDLEWARE_NGINX|demo-mw-tls-version	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	MW-TLS-VERSION	PCI_DSS	AGENT_MIDDLEWARE_NGINX	demo-mw-tls-version	payments_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260818_001	1	ad46d609dabaa4a106e5d9658fd2a613cef49b5f8b6231bdd71d5226a7d36167	2026-08-18 09:00:00+00	2026-09-15 06:21:18.886523+00	2026-09-15 06:21:18.886523+00	DRAFT	\N	\N	\N
4dd11c77-bc7f-4615-b96d-796bced6077c	mobile-banking|DPSC|MW-HSTS|AGENT_MIDDLEWARE_NGINX|demo-mw-hsts	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	MW-HSTS	DPSC	AGENT_MIDDLEWARE_NGINX	demo-mw-hsts	mobile-banking_MW-HSTS_MIDDLEWARE-CONFIG_20260819_001	1	5a2e62b61fbbae5ada2a184b2f09e9baa3b1b16fa01880ed06290fcc97d53762	2026-08-19 09:00:00+00	2026-09-15 06:21:18.920094+00	2026-09-15 06:21:18.920094+00	DRAFT	\N	\N	\N
00c73d89-1be7-48e3-a90d-24575535dfe8	net-banking|PCI_DSS|TLS-PROTOCOL-VERSION|AGENT_TLS|demo-tls-protocol-version	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	TLS-PROTOCOL-VERSION	PCI_DSS	AGENT_TLS	demo-tls-protocol-version	net-banking_TLS-PROTOCOL-VERSION_TLS-SCAN_20260820_001	1	c1f03cd91d1022d4d4c800c3bbcea725871ad50522b6bd6209982b50e72c062f	2026-08-20 09:00:00+00	2026-09-15 06:21:18.951334+00	2026-09-15 06:21:18.951334+00	DRAFT	\N	\N	\N
d3964043-ac22-4b12-8f9a-a14152017f98	mobile-banking|PCI_DSS|PCI-DSS-6.3|MOCK_JIRA|mock_jira-pci-dss-6.3	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	PCI-DSS-6.3	PCI_DSS	MOCK_JIRA	mock_jira-pci-dss-6.3	mobile-banking_PCI-DSS-6.3_CODE-REVIEW_20260915_001	1	2773c02f2a59de78491cb7642d0456da8ef3e87c3a4238930a31736b78484c24	2026-09-15 06:37:28+00	2026-09-15 06:37:28.266381+00	2026-09-15 06:37:28.266381+00	DRAFT	\N	\N	\N
cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	mobile-banking|ITPP|ITPP-CHG-02|MOCK_JIRA|mock_jira-itpp-chg-02	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	ITPP-CHG-02	ITPP	MOCK_JIRA	mock_jira-itpp-chg-02	mobile-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001	1	9bafb81e2f356f2234f73ecc2a79d64805080d6fdcc46b7eaddbe09503eb482d	2026-09-15 06:37:28+00	2026-09-15 06:37:28.426391+00	2026-09-15 06:37:28.426391+00	DRAFT	\N	\N	\N
6f4cc0f7-e8c8-4121-9897-7d43cd064263	net-banking|PCI_DSS|PCI-DSS-6.3|MOCK_JIRA|mock_jira-pci-dss-6.3	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	PCI-DSS-6.3	PCI_DSS	MOCK_JIRA	mock_jira-pci-dss-6.3	net-banking_PCI-DSS-6.3_CODE-REVIEW_20260915_001	1	b2317796fb07ba10359280689095700c0c6105aef580ed52c6ec8c42b1990c40	2026-09-15 06:37:28+00	2026-09-15 06:37:28.542096+00	2026-09-15 06:37:28.542096+00	DRAFT	\N	\N	\N
c41d90e3-c889-47ae-91a4-0a8e678e1ff3	net-banking|ITPP|ITPP-CHG-02|MOCK_JIRA|mock_jira-itpp-chg-02	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	ITPP-CHG-02	ITPP	MOCK_JIRA	mock_jira-itpp-chg-02	net-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001	1	cda45cac5684512c30105080c516a0c3a0bcc91db487cdc091a9bd540a6d6cca	2026-09-15 06:37:28+00	2026-09-15 06:37:28.649468+00	2026-09-15 06:37:28.649468+00	DRAFT	\N	\N	\N
2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	payments|PCI_DSS|PCI-DSS-6.3|MOCK_JIRA|mock_jira-pci-dss-6.3	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	PCI-DSS-6.3	PCI_DSS	MOCK_JIRA	mock_jira-pci-dss-6.3	payments_PCI-DSS-6.3_CODE-REVIEW_20260915_001	1	90f4a5443a1324ef66471a97eb6ed12867eb6105f9d03ee7eb381ce7f234b03c	2026-09-15 06:37:28+00	2026-09-15 06:37:28.759826+00	2026-09-15 06:37:28.759826+00	DRAFT	\N	\N	\N
f46cd29a-37a3-48c7-b718-e18c28f766b0	payments|ITPP|ITPP-CHG-02|MOCK_JIRA|mock_jira-itpp-chg-02	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	ITPP-CHG-02	ITPP	MOCK_JIRA	mock_jira-itpp-chg-02	payments_ITPP-CHG-02_CHANGE-TICKET_20260915_001	1	e5bd892dbbce1b402003803b1b1a23ce6b592e57f79ef5644086de5542dc8124	2026-09-15 06:37:28+00	2026-09-15 06:37:28.859682+00	2026-09-15 06:37:28.859682+00	DRAFT	\N	\N	\N
76c9a2eb-b56b-491f-b914-3d5729bf8aae	mobile-banking|ITPP|ITPP-INC-01|MOCK_SERVICENOW|mock_servicenow-itpp-inc-01	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	ITPP-INC-01	ITPP	MOCK_SERVICENOW	mock_servicenow-itpp-inc-01	mobile-banking_ITPP-INC-01_GENERAL_20260915_001	1	54c4e03b4883f4cea54a54a8837a16d4fb880bfe7782925293061124802ff493	2026-09-15 06:37:28+00	2026-09-15 06:37:28.955762+00	2026-09-15 06:37:28.955762+00	DRAFT	\N	\N	\N
330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	mobile-banking|PCI_DSS|PCI-DSS-12.10|MOCK_SERVICENOW|mock_servicenow-pci-dss-12.10	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	PCI-DSS-12.10	PCI_DSS	MOCK_SERVICENOW	mock_servicenow-pci-dss-12.10	mobile-banking_PCI-DSS-12.10_GENERAL_20260915_001	1	83a4560f72ee065092f6635fa0838d11b18d06a37058131e3a49989c3a3599a5	2026-09-15 06:37:28+00	2026-09-15 06:37:29.047494+00	2026-09-15 06:37:29.047494+00	DRAFT	\N	\N	\N
ee1c3319-eed3-4078-8dfd-5313359b7cd5	net-banking|ITPP|ITPP-INC-01|MOCK_SERVICENOW|mock_servicenow-itpp-inc-01	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	ITPP-INC-01	ITPP	MOCK_SERVICENOW	mock_servicenow-itpp-inc-01	net-banking_ITPP-INC-01_GENERAL_20260915_001	1	c6810c7cc2a8afceb3be8e01795373e9203030b5934f59d211df35e9cab6996a	2026-09-15 06:37:29+00	2026-09-15 06:37:29.143347+00	2026-09-15 06:37:29.143347+00	DRAFT	\N	\N	\N
3e71e55c-5859-4652-b492-207dac524809	net-banking|PCI_DSS|PCI-DSS-12.10|MOCK_SERVICENOW|mock_servicenow-pci-dss-12.10	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	PCI-DSS-12.10	PCI_DSS	MOCK_SERVICENOW	mock_servicenow-pci-dss-12.10	net-banking_PCI-DSS-12.10_GENERAL_20260915_001	1	a579056be76093998aadeff079371f846005eca86528d4f0b49d9e0e0ae93864	2026-09-15 06:37:29+00	2026-09-15 06:37:29.245135+00	2026-09-15 06:37:29.245135+00	DRAFT	\N	\N	\N
2777b2ba-3652-4908-9561-98a028beef82	payments|ITPP|ITPP-INC-01|MOCK_SERVICENOW|mock_servicenow-itpp-inc-01	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	ITPP-INC-01	ITPP	MOCK_SERVICENOW	mock_servicenow-itpp-inc-01	payments_ITPP-INC-01_GENERAL_20260915_001	1	d578f160235a540b4206726cfc4a13bbf7044ead70c7f93b144c878ddd9bd68e	2026-09-15 06:37:29+00	2026-09-15 06:37:29.330168+00	2026-09-15 06:37:29.330168+00	DRAFT	\N	\N	\N
97763088-2d92-472e-8672-362cef6175a4	payments|PCI_DSS|PCI-DSS-12.10|MOCK_SERVICENOW|mock_servicenow-pci-dss-12.10	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	PCI-DSS-12.10	PCI_DSS	MOCK_SERVICENOW	mock_servicenow-pci-dss-12.10	payments_PCI-DSS-12.10_GENERAL_20260915_001	1	e71280f49f4d2d261793e829d967f126a11173fb64a2b8542810e35db05ad4e2	2026-09-15 06:37:29+00	2026-09-15 06:37:29.412748+00	2026-09-15 06:37:29.412748+00	DRAFT	\N	\N	\N
ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	mobile-banking|PCI_DSS|PCI-DSS-6.2|MOCK_GITHUB|mock_github-pci-dss-6.2	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	PCI-DSS-6.2	PCI_DSS	MOCK_GITHUB	mock_github-pci-dss-6.2	mobile-banking_PCI-DSS-6.2_CODE-REVIEW_20260915_001	1	2ef8adc1bb290627d018f597a98554a5438b19fb4f1efc3043908e53aa18dcd2	2026-09-15 06:37:29+00	2026-09-15 06:37:29.499429+00	2026-09-15 06:37:29.499429+00	DRAFT	\N	\N	\N
3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	mobile-banking|DPSC|DPSC-SDLC-04|MOCK_GITHUB|mock_github-dpsc-sdlc-04	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DPSC-SDLC-04	DPSC	MOCK_GITHUB	mock_github-dpsc-sdlc-04	mobile-banking_DPSC-SDLC-04_CODE-REVIEW_20260915_001	1	49cc556941ea4bd0fbf0525a90908b72953b5fc0a80f346b026dce3c8ec3e61a	2026-09-15 06:37:29+00	2026-09-15 06:37:29.589986+00	2026-09-15 06:37:29.589986+00	DRAFT	\N	\N	\N
82fe525f-b0e6-4630-be90-6699615e0868	net-banking|PCI_DSS|PCI-DSS-6.2|MOCK_GITHUB|mock_github-pci-dss-6.2	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	PCI-DSS-6.2	PCI_DSS	MOCK_GITHUB	mock_github-pci-dss-6.2	net-banking_PCI-DSS-6.2_CODE-REVIEW_20260915_001	1	cb76b392669f09c9635744df078441e9594d165635d557dd777b785037cdd0ee	2026-09-15 06:37:29+00	2026-09-15 06:37:29.662097+00	2026-09-15 06:37:29.662097+00	DRAFT	\N	\N	\N
d6f36c83-2f64-4631-ac94-fe19b89c69e7	net-banking|DPSC|DPSC-SDLC-04|MOCK_GITHUB|mock_github-dpsc-sdlc-04	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DPSC-SDLC-04	DPSC	MOCK_GITHUB	mock_github-dpsc-sdlc-04	net-banking_DPSC-SDLC-04_CODE-REVIEW_20260915_001	1	a4f5f767500797d964dca264ce04ea877193d59eb864f8ad212fde2d2356dc69	2026-09-15 06:37:29+00	2026-09-15 06:37:29.744088+00	2026-09-15 06:37:29.744088+00	DRAFT	\N	\N	\N
7efff257-28af-43c1-8634-51e773b07948	payments|PCI_DSS|PCI-DSS-6.2|MOCK_GITHUB|mock_github-pci-dss-6.2	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	PCI-DSS-6.2	PCI_DSS	MOCK_GITHUB	mock_github-pci-dss-6.2	payments_PCI-DSS-6.2_CODE-REVIEW_20260915_001	1	7a1fcfe9eabd29ee31d1135314b63e4e10d3678b42b00f0f15e544cfcdcdd19b	2026-09-15 06:37:29+00	2026-09-15 06:37:29.821785+00	2026-09-15 06:37:29.821785+00	DRAFT	\N	\N	\N
0eb7be56-7628-4a4c-bc9c-1c050d28c29d	payments|DPSC|DPSC-SDLC-04|MOCK_GITHUB|mock_github-dpsc-sdlc-04	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DPSC-SDLC-04	DPSC	MOCK_GITHUB	mock_github-dpsc-sdlc-04	payments_DPSC-SDLC-04_CODE-REVIEW_20260915_001	1	9c2b58d5a0b395a4f5327404cd510f15fb1bd264db417ad502ba9ec9afb57637	2026-09-15 06:37:29+00	2026-09-15 06:37:29.894913+00	2026-09-15 06:37:29.894913+00	DRAFT	\N	\N	\N
e8b82754-a27f-4c5f-a50b-8f7769ab9b55	mobile-banking|ITPP|ITPP-DOC-03|MOCK_CONFLUENCE|mock_confluence-itpp-doc-03	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	ITPP-DOC-03	ITPP	MOCK_CONFLUENCE	mock_confluence-itpp-doc-03	mobile-banking_ITPP-DOC-03_GENERAL_20260915_001	1	cb76aa5c6d41735b5ea41222efa1e2e530b86c70be95ed38984bc6ad9187ec08	2026-09-15 06:37:29+00	2026-09-15 06:37:29.984277+00	2026-09-15 06:37:29.984277+00	DRAFT	\N	\N	\N
64ddd7c6-fdca-443b-8dde-fbc0d2801462	mobile-banking|DPSC|DPSC-GOV-01|MOCK_CONFLUENCE|mock_confluence-dpsc-gov-01	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DPSC-GOV-01	DPSC	MOCK_CONFLUENCE	mock_confluence-dpsc-gov-01	mobile-banking_DPSC-GOV-01_GENERAL_20260915_001	1	5c868252c15d0d5c7be2cb9641b6f9d21b7ac4e9544342c201631205f5f9a6a8	2026-09-15 06:37:29+00	2026-09-15 06:37:30.063814+00	2026-09-15 06:37:30.063814+00	DRAFT	\N	\N	\N
8296014e-a49c-4866-a948-6433ac7cef44	net-banking|ITPP|ITPP-DOC-03|MOCK_CONFLUENCE|mock_confluence-itpp-doc-03	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	ITPP-DOC-03	ITPP	MOCK_CONFLUENCE	mock_confluence-itpp-doc-03	net-banking_ITPP-DOC-03_GENERAL_20260915_001	1	32e46f1640258217f30da94de1787da0c1a1c721f64833a0e547ba0687b95a18	2026-09-15 06:37:30+00	2026-09-15 06:37:30.141057+00	2026-09-15 06:37:30.141057+00	DRAFT	\N	\N	\N
a0569699-2209-4fff-a8a0-af1e0ccaa227	net-banking|DPSC|DPSC-GOV-01|MOCK_CONFLUENCE|mock_confluence-dpsc-gov-01	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DPSC-GOV-01	DPSC	MOCK_CONFLUENCE	mock_confluence-dpsc-gov-01	net-banking_DPSC-GOV-01_GENERAL_20260915_001	1	190a3693a422184a1a4ef5f2c4e38c52c530ba4987ce5e43b6f034ba1851c428	2026-09-15 06:37:30+00	2026-09-15 06:37:30.22164+00	2026-09-15 06:37:30.22164+00	DRAFT	\N	\N	\N
ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	payments|ITPP|ITPP-DOC-03|MOCK_CONFLUENCE|mock_confluence-itpp-doc-03	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	ITPP-DOC-03	ITPP	MOCK_CONFLUENCE	mock_confluence-itpp-doc-03	payments_ITPP-DOC-03_GENERAL_20260915_001	1	cc56f053e9a450ecbce075356598973f07665cd1cd51705d0cb6cb6e54218361	2026-09-15 06:37:30+00	2026-09-15 06:37:30.307794+00	2026-09-15 06:37:30.307794+00	DRAFT	\N	\N	\N
c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	payments|DPSC|DPSC-GOV-01|MOCK_CONFLUENCE|mock_confluence-dpsc-gov-01	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DPSC-GOV-01	DPSC	MOCK_CONFLUENCE	mock_confluence-dpsc-gov-01	payments_DPSC-GOV-01_GENERAL_20260915_001	1	67ff45ef7176065db585a5a6af69cda94f96058687f7711466b98566f9546eac	2026-09-15 06:37:30+00	2026-09-15 06:37:30.373883+00	2026-09-15 06:37:30.373883+00	DRAFT	\N	\N	\N
67a16d1b-085b-4faf-8333-d4a8811667d7	mobile-banking|PCI_DSS|DB-TLS-IN-TRANSIT|SIM_POSTGRES|postgresql/db-tls-in-transit	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_POSTGRES	postgresql/db-tls-in-transit	mobile-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001	1	475580fadf954fb9ace4ece30b9c4ee3b5e0f8bf0839249392c6f2c9ce4ed60b	2026-09-15 06:37:30+00	2026-09-15 06:37:30.464018+00	2026-09-15 06:37:30.464018+00	DRAFT	\N	\N	\N
872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	mobile-banking|PCI_DSS|DB-AUDIT-LOGGING|SIM_POSTGRES|postgresql/db-audit-logging	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_POSTGRES	postgresql/db-audit-logging	mobile-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001	1	13895ef0fd3efb72373b8a9fbfc78b61cf2c74fb91ca044f86900cb120dfe0af	2026-09-15 06:37:30+00	2026-09-15 06:37:30.537724+00	2026-09-15 06:37:30.537724+00	DRAFT	\N	\N	\N
a99ef1f9-2193-4c30-b4b4-743268c8003e	mobile-banking|C-SITE|DB-PASSWORD-STORAGE|SIM_POSTGRES|postgresql/db-password-storage	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-PASSWORD-STORAGE	C-SITE	SIM_POSTGRES	postgresql/db-password-storage	mobile-banking_DB-PASSWORD-STORAGE_DB-CONFIG_20260915_001	1	e9145c7212c290d025777184fef09273c26f8f0918d40acf1cc2acabb65fd66d	2026-09-15 06:37:30+00	2026-09-15 06:37:30.606565+00	2026-09-15 06:37:30.606565+00	DRAFT	\N	\N	\N
2fe254d9-ae0b-437d-8a53-ea71cb8043e5	mobile-banking|C-SITE|DB-AUTH-NO-TRUST|SIM_POSTGRES|postgresql/db-auth-no-trust	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_POSTGRES	postgresql/db-auth-no-trust	mobile-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001	1	e789114133c72da1c255f4bedf85dd6eff14ac41a3e5f2a2182c0a9890504a11	2026-09-15 06:37:30+00	2026-09-15 06:37:30.680304+00	2026-09-15 06:37:30.680304+00	DRAFT	\N	\N	\N
bbe54c6f-2024-4781-beee-7bb1c4f10234	net-banking|PCI_DSS|DB-TLS-IN-TRANSIT|SIM_POSTGRES|postgresql/db-tls-in-transit	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_POSTGRES	postgresql/db-tls-in-transit	net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001	1	20bfd386394c847d921199a34e88b4cc3cb13ebf3e8ba9c6c3c383f9d995a510	2026-09-15 06:37:30+00	2026-09-15 06:37:30.761748+00	2026-09-15 06:37:30.761748+00	DRAFT	\N	\N	\N
a5667964-1027-4f26-b6d5-3234f80dd54f	net-banking|PCI_DSS|DB-AUDIT-LOGGING|SIM_POSTGRES|postgresql/db-audit-logging	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_POSTGRES	postgresql/db-audit-logging	net-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001	1	5eac50333691bba0749e81593f990aafd3d57b4263ed2ea28a14f7a73263d727	2026-09-15 06:37:30+00	2026-09-15 06:37:30.842588+00	2026-09-15 06:37:30.842588+00	DRAFT	\N	\N	\N
d9886a1d-a255-4725-935d-d62cf11b8766	net-banking|C-SITE|DB-PASSWORD-STORAGE|SIM_POSTGRES|postgresql/db-password-storage	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-PASSWORD-STORAGE	C-SITE	SIM_POSTGRES	postgresql/db-password-storage	net-banking_DB-PASSWORD-STORAGE_DB-CONFIG_20260915_001	1	e403a32d91e8b38d1689c494aad1c9dd181a590aa9c13d22b98f2f87e4f29572	2026-09-15 06:37:30+00	2026-09-15 06:37:30.906226+00	2026-09-15 06:37:30.906226+00	DRAFT	\N	\N	\N
ba85be2a-8251-4dac-bb80-4655f5a3979a	net-banking|C-SITE|DB-AUTH-NO-TRUST|SIM_POSTGRES|postgresql/db-auth-no-trust	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_POSTGRES	postgresql/db-auth-no-trust	net-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001	1	12d01d9a516c6921e4e72d7ca41d28095b575d02df31efdf64431f895398dcdf	2026-09-15 06:37:30+00	2026-09-15 06:37:30.968695+00	2026-09-15 06:37:30.968695+00	DRAFT	\N	\N	\N
252a8116-32db-4c3f-8bc4-0fe0eee53653	payments|PCI_DSS|DB-TLS-IN-TRANSIT|SIM_POSTGRES|postgresql/db-tls-in-transit	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_POSTGRES	postgresql/db-tls-in-transit	payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001	1	65ec17c4c6ab9bd5ce6e2fa2e07b550649c2fe6f3e9bb9f3644e26d5497ba22e	2026-09-15 06:37:31+00	2026-09-15 06:37:31.025726+00	2026-09-15 06:37:31.025726+00	DRAFT	\N	\N	\N
3ee6901d-e417-471c-a00e-0eb95e2b8491	payments|PCI_DSS|DB-AUDIT-LOGGING|SIM_POSTGRES|postgresql/db-audit-logging	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-AUDIT-LOGGING	PCI_DSS	SIM_POSTGRES	postgresql/db-audit-logging	payments_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001	1	1dd8ea5f547f2d24c62b9a1a5626cc534ad74f85856761b66205a0d08ea46aab	2026-09-15 06:37:31+00	2026-09-15 06:37:31.091645+00	2026-09-15 06:37:31.091645+00	DRAFT	\N	\N	\N
897683f8-35a9-4af6-b103-8f073087e332	payments|C-SITE|DB-PASSWORD-STORAGE|SIM_POSTGRES|postgresql/db-password-storage	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-PASSWORD-STORAGE	C-SITE	SIM_POSTGRES	postgresql/db-password-storage	payments_DB-PASSWORD-STORAGE_DB-CONFIG_20260915_001	1	e37820414fa5d245537b9d60337e59b8104c3940e79df81844390bd3acf57066	2026-09-15 06:37:31+00	2026-09-15 06:37:31.154248+00	2026-09-15 06:37:31.154248+00	DRAFT	\N	\N	\N
bff6261f-c689-4dee-95a9-281451058e08	payments|C-SITE|DB-AUTH-NO-TRUST|SIM_POSTGRES|postgresql/db-auth-no-trust	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-AUTH-NO-TRUST	C-SITE	SIM_POSTGRES	postgresql/db-auth-no-trust	payments_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001	1	0d41509fa14e8c67cffb418318cd2dc1921e8ab9603401d6c5086512542e139d	2026-09-15 06:37:31+00	2026-09-15 06:37:31.221647+00	2026-09-15 06:37:31.221647+00	DRAFT	\N	\N	\N
96ce1593-ee52-455f-ad08-64ec36bbff02	mobile-banking|PCI_DSS|DB-TLS-IN-TRANSIT|SIM_MYSQL|mysql/db-tls-in-transit	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_MYSQL	mysql/db-tls-in-transit	mobile-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001	1	bf9f883c2967fc794c52d97e778d5982866e13a34d6acf5eda74b1e259ec1a33	2026-09-15 06:37:31+00	2026-09-15 06:37:31.296384+00	2026-09-15 06:37:31.296384+00	DRAFT	\N	\N	\N
62b53770-56b0-4556-9e8a-bcef611a5588	mobile-banking|PCI_DSS|DB-AUDIT-LOGGING|SIM_MYSQL|mysql/db-audit-logging	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_MYSQL	mysql/db-audit-logging	mobile-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001	1	8e70e6a8752d54d5e14b9d64b316b5ddc8288809ea79caba38a2c397f7408a44	2026-09-15 06:37:31+00	2026-09-15 06:37:31.376276+00	2026-09-15 06:37:31.376276+00	DRAFT	\N	\N	\N
21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	mobile-banking|C-SITE|DB-AUTH-NO-TRUST|SIM_MYSQL|mysql/db-auth-no-trust	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_MYSQL	mysql/db-auth-no-trust	mobile-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001	1	0cdd38a0c529488a5e35d1d76688fa23d3ee1da9a9fa0dfa73f60cb396d96ebc	2026-09-15 06:37:31+00	2026-09-15 06:37:31.453732+00	2026-09-15 06:37:31.453732+00	DRAFT	\N	\N	\N
3bb02091-449e-497e-9317-427cacc54837	net-banking|PCI_DSS|DB-TLS-IN-TRANSIT|SIM_MYSQL|mysql/db-tls-in-transit	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_MYSQL	mysql/db-tls-in-transit	net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001	1	328dcecb23b0f087073977be52c4fa3add48bc4f969e686798a13637890ca0a7	2026-09-15 06:37:31+00	2026-09-15 06:37:31.536426+00	2026-09-15 06:37:31.536426+00	DRAFT	\N	\N	\N
d5d0f39b-6c07-4161-a96c-94dc258702df	net-banking|PCI_DSS|DB-AUDIT-LOGGING|SIM_MYSQL|mysql/db-audit-logging	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-AUDIT-LOGGING	PCI_DSS	SIM_MYSQL	mysql/db-audit-logging	net-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001	1	fe98f6312f00f5e212bbe58d18b85389b7072c3a4665e8cf0455a6c177431a6c	2026-09-15 06:37:31+00	2026-09-15 06:37:31.629458+00	2026-09-15 06:37:31.629458+00	DRAFT	\N	\N	\N
c3c77f08-3579-4394-84b0-8592dd97bbd8	net-banking|C-SITE|DB-AUTH-NO-TRUST|SIM_MYSQL|mysql/db-auth-no-trust	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DB-AUTH-NO-TRUST	C-SITE	SIM_MYSQL	mysql/db-auth-no-trust	net-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001	1	6d292256726e57fc7daf601f0af79ac51aa96458e58bba63d74022bf888df516	2026-09-15 06:37:31+00	2026-09-15 06:37:31.704023+00	2026-09-15 06:37:31.704023+00	DRAFT	\N	\N	\N
5ef049e2-85a0-44b1-9e80-d494a5043570	payments|PCI_DSS|DB-TLS-IN-TRANSIT|SIM_MYSQL|mysql/db-tls-in-transit	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-TLS-IN-TRANSIT	PCI_DSS	SIM_MYSQL	mysql/db-tls-in-transit	payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001	1	ae8aa79a46022a5f431facdd44bb409f60e876b6ca80895d8d35ddd356e949d2	2026-09-15 06:37:31+00	2026-09-15 06:37:31.770223+00	2026-09-15 06:37:31.770223+00	DRAFT	\N	\N	\N
38f74afa-6f51-46c6-87c9-12929e812362	payments|PCI_DSS|DB-AUDIT-LOGGING|SIM_MYSQL|mysql/db-audit-logging	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-AUDIT-LOGGING	PCI_DSS	SIM_MYSQL	mysql/db-audit-logging	payments_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001	1	d0df489a1ae1ee21d43c1480f7ae2acdcc3bfa0027029a6a1c14e488f8986d7d	2026-09-15 06:37:31+00	2026-09-15 06:37:31.861469+00	2026-09-15 06:37:31.861469+00	DRAFT	\N	\N	\N
d452f3b9-ce2c-410a-968b-4dd45948eee6	payments|C-SITE|DB-AUTH-NO-TRUST|SIM_MYSQL|mysql/db-auth-no-trust	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DB-AUTH-NO-TRUST	C-SITE	SIM_MYSQL	mysql/db-auth-no-trust	payments_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001	1	a3bbf16c4db47de57b3be607f7d75261399fe4015fb7ace1df22c09819b8d8c0	2026-09-15 06:37:31+00	2026-09-15 06:37:31.937123+00	2026-09-15 06:37:31.937123+00	DRAFT	\N	\N	\N
2874aafc-3723-40bc-b524-229f1b2d7b8c	mobile-banking|PCI_DSS|MW-TLS-VERSION|SIM_NGINX|nginx/mw-tls-version	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	MW-TLS-VERSION	PCI_DSS	SIM_NGINX	nginx/mw-tls-version	mobile-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001	1	d4975be8cf3b28eeb3b007ac81a7d969fb648c6e6942c2ae5f63f626bea4fb80	2026-09-15 06:37:32+00	2026-09-15 06:37:32.017095+00	2026-09-15 06:37:32.017095+00	DRAFT	\N	\N	\N
86a41980-cbd9-478c-a4bc-371bc159f431	mobile-banking|C-SITE|MW-BANNER-SUPPRESSION|SIM_NGINX|nginx/mw-banner-suppression	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	MW-BANNER-SUPPRESSION	C-SITE	SIM_NGINX	nginx/mw-banner-suppression	mobile-banking_MW-BANNER-SUPPRESSION_MIDDLEWARE-CONFIG_20260915_001	1	1edf718538421ebac454136acbd09bbdc87792aad8ddd495c435bb5b93e5662b	2026-09-15 06:37:32+00	2026-09-15 06:37:32.077083+00	2026-09-15 06:37:32.077083+00	DRAFT	\N	\N	\N
25b18e7e-bf5d-4d43-b1df-80cce7317bdc	mobile-banking|DPSC|MW-HSTS|SIM_NGINX|nginx/mw-hsts	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	MW-HSTS	DPSC	SIM_NGINX	nginx/mw-hsts	mobile-banking_MW-HSTS_MIDDLEWARE-CONFIG_20260915_001	1	f3843299319b8e1d01c30fd7d5eb8ebad9aa3a978fd81f9a7cd3bd47efc59d03	2026-09-15 06:37:32+00	2026-09-15 06:37:32.143744+00	2026-09-15 06:37:32.143744+00	DRAFT	\N	\N	\N
b0946cad-efb7-4bee-bb25-1626fe14c229	net-banking|PCI_DSS|MW-TLS-VERSION|SIM_NGINX|nginx/mw-tls-version	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	MW-TLS-VERSION	PCI_DSS	SIM_NGINX	nginx/mw-tls-version	net-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001	1	3ed65603c5abe308fb20b34f4ebd2d03b05bb8642c6b6000003b2dca47a63e25	2026-09-15 06:37:32+00	2026-09-15 06:37:32.202577+00	2026-09-15 06:37:32.202577+00	DRAFT	\N	\N	\N
68b6c810-9ce8-4c96-a653-133a4042efd6	net-banking|C-SITE|MW-BANNER-SUPPRESSION|SIM_NGINX|nginx/mw-banner-suppression	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	MW-BANNER-SUPPRESSION	C-SITE	SIM_NGINX	nginx/mw-banner-suppression	net-banking_MW-BANNER-SUPPRESSION_MIDDLEWARE-CONFIG_20260915_001	1	98ef97151bd5ff7a539488edc1949214cfed573e9b637f93d2a9d7d8ff5f594e	2026-09-15 06:37:32+00	2026-09-15 06:37:32.274247+00	2026-09-15 06:37:32.274247+00	DRAFT	\N	\N	\N
6881638e-3f67-4cc8-a178-e67cd76a90bc	net-banking|DPSC|MW-HSTS|SIM_NGINX|nginx/mw-hsts	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	MW-HSTS	DPSC	SIM_NGINX	nginx/mw-hsts	net-banking_MW-HSTS_MIDDLEWARE-CONFIG_20260915_001	1	e2695ff8586707597839f534c1548daad70b6cc8d888610b6ac4946e591d04a7	2026-09-15 06:37:32+00	2026-09-15 06:37:32.338532+00	2026-09-15 06:37:32.338532+00	DRAFT	\N	\N	\N
49d5d6ba-7459-4bcf-901a-5424e414a32a	payments|PCI_DSS|MW-TLS-VERSION|SIM_NGINX|nginx/mw-tls-version	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	MW-TLS-VERSION	PCI_DSS	SIM_NGINX	nginx/mw-tls-version	payments_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001	1	46138b664abb1f33c33451a5019f97e248a51b08b0300ed6a9fb8fa012ef82b1	2026-09-15 06:37:32+00	2026-09-15 06:37:32.408136+00	2026-09-15 06:37:32.408136+00	DRAFT	\N	\N	\N
a50ff65e-9cb6-4eb5-bacb-d0c44b565365	payments|C-SITE|MW-BANNER-SUPPRESSION|SIM_NGINX|nginx/mw-banner-suppression	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	MW-BANNER-SUPPRESSION	C-SITE	SIM_NGINX	nginx/mw-banner-suppression	payments_MW-BANNER-SUPPRESSION_MIDDLEWARE-CONFIG_20260915_001	1	69eaaa6100649fec3dc94a09140f588bc92bbf9f5888ceea448f6296edeea47b	2026-09-15 06:37:32+00	2026-09-15 06:37:32.480954+00	2026-09-15 06:37:32.480954+00	DRAFT	\N	\N	\N
d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	payments|DPSC|MW-HSTS|SIM_NGINX|nginx/mw-hsts	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	MW-HSTS	DPSC	SIM_NGINX	nginx/mw-hsts	payments_MW-HSTS_MIDDLEWARE-CONFIG_20260915_001	1	030a1693b889f8d79fd217f29f302f8fcfa151409df31273888e66d5f52d7e85	2026-09-15 06:37:32+00	2026-09-15 06:37:32.535233+00	2026-09-15 06:37:32.535233+00	DRAFT	\N	\N	\N
cc6d26ef-a350-4d32-9d2a-39d180443f44	mobile-banking|PCI_DSS|MW-TLS-VERSION|SIM_TOMCAT|tomcat/mw-tls-version	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	MW-TLS-VERSION	PCI_DSS	SIM_TOMCAT	tomcat/mw-tls-version	mobile-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001	1	fa08d84232f4a20d90b9f1be3ee152440c8095ec59916781f2f7438b164c1221	2026-09-15 06:37:32+00	2026-09-15 06:37:32.581244+00	2026-09-15 06:37:32.581244+00	DRAFT	\N	\N	\N
0b589896-b23e-452f-bb27-cc5088dadf8d	mobile-banking|C-SITE|MW-AUTODEPLOY|SIM_TOMCAT|tomcat/mw-autodeploy	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	MW-AUTODEPLOY	C-SITE	SIM_TOMCAT	tomcat/mw-autodeploy	mobile-banking_MW-AUTODEPLOY_MIDDLEWARE-CONFIG_20260915_001	1	08291f3515799bef745bba6286182248bf981e66dd575d9522555e4da98ca2e9	2026-09-15 06:37:32+00	2026-09-15 06:37:32.627881+00	2026-09-15 06:37:32.627881+00	DRAFT	\N	\N	\N
f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	net-banking|PCI_DSS|MW-TLS-VERSION|SIM_TOMCAT|tomcat/mw-tls-version	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	MW-TLS-VERSION	PCI_DSS	SIM_TOMCAT	tomcat/mw-tls-version	net-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001	1	1a3076e2061b5f1f9a5b8ab155afe30820f20ce27385f5dec4241578bf392cc0	2026-09-15 06:37:32+00	2026-09-15 06:37:32.691518+00	2026-09-15 06:37:32.691518+00	DRAFT	\N	\N	\N
61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	net-banking|C-SITE|MW-AUTODEPLOY|SIM_TOMCAT|tomcat/mw-autodeploy	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	MW-AUTODEPLOY	C-SITE	SIM_TOMCAT	tomcat/mw-autodeploy	net-banking_MW-AUTODEPLOY_MIDDLEWARE-CONFIG_20260915_001	1	582873f51d979c92fa3137fd5f1196398fc0cba8c91ad0d534701ad6a4a1b84d	2026-09-15 06:37:32+00	2026-09-15 06:37:32.749868+00	2026-09-15 06:37:32.749868+00	DRAFT	\N	\N	\N
9aba1297-9050-494d-a023-89166919ce50	payments|PCI_DSS|MW-TLS-VERSION|SIM_TOMCAT|tomcat/mw-tls-version	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	MW-TLS-VERSION	PCI_DSS	SIM_TOMCAT	tomcat/mw-tls-version	payments_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001	1	e9fb758674261183544c5a32ef0a8363b34dba3e1a49afca89d624720d3a62e9	2026-09-15 06:37:32+00	2026-09-15 06:37:32.810012+00	2026-09-15 06:37:32.810012+00	DRAFT	\N	\N	\N
5edff7ca-679a-4517-bdda-31cc792ca6ff	payments|C-SITE|MW-AUTODEPLOY|SIM_TOMCAT|tomcat/mw-autodeploy	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	MW-AUTODEPLOY	C-SITE	SIM_TOMCAT	tomcat/mw-autodeploy	payments_MW-AUTODEPLOY_MIDDLEWARE-CONFIG_20260915_001	1	f8395725c7a0c065475c27e9276e960be97cb747cb9ae53354508890b269e4e1	2026-09-15 06:37:32+00	2026-09-15 06:37:32.884957+00	2026-09-15 06:37:32.884957+00	DRAFT	\N	\N	\N
31939122-58c2-4c45-832d-946552e91124	mobile-banking|ITPP|ITPP-DOC-03|SHAREPOINT|sharepoint-itpp-doc-03	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	ITPP-DOC-03	ITPP	SHAREPOINT	sharepoint-itpp-doc-03	mobile-banking_ITPP-DOC-03_GENERAL_20260915_001	1	de22cbdd31e125e5ba13f2a8eafa1f4cdd6a034d3bfbab708dbef04fa01ae21a	2026-09-15 06:37:32+00	2026-09-15 06:37:32.936188+00	2026-09-15 06:37:32.936188+00	DRAFT	\N	\N	\N
95719fec-0ab6-43d4-8097-2057cd8344fc	mobile-banking|DPSC|DPSC-GOV-01|SHAREPOINT|sharepoint-dpsc-gov-01	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	DPSC-GOV-01	DPSC	SHAREPOINT	sharepoint-dpsc-gov-01	mobile-banking_DPSC-GOV-01_GENERAL_20260915_001	1	bc4e083f805371f91c1f4547da0c882942adec5fe1c445a953c32f206c2985d8	2026-09-15 06:37:32+00	2026-09-15 06:37:33.008916+00	2026-09-15 06:37:33.008916+00	DRAFT	\N	\N	\N
05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	mobile-banking|PCI_DSS|PCI-DSS-12.1|SHAREPOINT|sharepoint-pci-dss-12.1	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	PCI-DSS-12.1	PCI_DSS	SHAREPOINT	sharepoint-pci-dss-12.1	mobile-banking_PCI-DSS-12.1_GENERAL_20260915_001	1	7cfd86fa87064817435d64b0aee1ca005670575cf6a60e30f9f71daab1bed2d8	2026-09-15 06:37:32+00	2026-09-15 06:37:33.074359+00	2026-09-15 06:37:33.074359+00	DRAFT	\N	\N	\N
8a4e1ff8-e3c7-445b-9766-4091f19de4e3	net-banking|ITPP|ITPP-DOC-03|SHAREPOINT|sharepoint-itpp-doc-03	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	ITPP-DOC-03	ITPP	SHAREPOINT	sharepoint-itpp-doc-03	net-banking_ITPP-DOC-03_GENERAL_20260915_001	1	1a454add48792217177475af23ffddaf1b2cb102a4167679cb685edbae95c0db	2026-09-15 06:37:33+00	2026-09-15 06:37:33.145884+00	2026-09-15 06:37:33.145884+00	DRAFT	\N	\N	\N
40630923-dd28-49e1-b40f-8169dee97763	net-banking|DPSC|DPSC-GOV-01|SHAREPOINT|sharepoint-dpsc-gov-01	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	DPSC-GOV-01	DPSC	SHAREPOINT	sharepoint-dpsc-gov-01	net-banking_DPSC-GOV-01_GENERAL_20260915_001	1	2711b9c6b79378aed13d1cb8e35c5d0508799be5141ce7ab1abd52770f8f470c	2026-09-15 06:37:33+00	2026-09-15 06:37:33.218815+00	2026-09-15 06:37:33.218815+00	DRAFT	\N	\N	\N
379fbd9a-38cd-4989-81ba-0f9bd533736d	net-banking|PCI_DSS|PCI-DSS-12.1|SHAREPOINT|sharepoint-pci-dss-12.1	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	PCI-DSS-12.1	PCI_DSS	SHAREPOINT	sharepoint-pci-dss-12.1	net-banking_PCI-DSS-12.1_GENERAL_20260915_001	1	728624ae0d33cd76a2beb57d5b84110c3b1e710f19ae877a823c95b7175bb122	2026-09-15 06:37:33+00	2026-09-15 06:37:33.292715+00	2026-09-15 06:37:33.292715+00	DRAFT	\N	\N	\N
e3167d52-f263-417c-964f-5523c33c1d91	payments|ITPP|ITPP-DOC-03|SHAREPOINT|sharepoint-itpp-doc-03	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	ITPP-DOC-03	ITPP	SHAREPOINT	sharepoint-itpp-doc-03	payments_ITPP-DOC-03_GENERAL_20260915_001	1	d6c3f9b7700024fdc75009fca7d15399eba184946b7b49fd000386b6d35aba7d	2026-09-15 06:37:33+00	2026-09-15 06:37:33.349572+00	2026-09-15 06:37:33.349572+00	DRAFT	\N	\N	\N
969d7c02-eb0c-47af-a189-b94c42ce67dc	payments|DPSC|DPSC-GOV-01|SHAREPOINT|sharepoint-dpsc-gov-01	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	DPSC-GOV-01	DPSC	SHAREPOINT	sharepoint-dpsc-gov-01	payments_DPSC-GOV-01_GENERAL_20260915_001	1	c36d58f806c41f9db8c6a6f26a2354b3c5237cafd24b9f061b236f67f313c73e	2026-09-15 06:37:33+00	2026-09-15 06:37:33.413177+00	2026-09-15 06:37:33.413177+00	DRAFT	\N	\N	\N
ef73d9bb-1f1a-4390-a202-4a056291a646	payments|PCI_DSS|PCI-DSS-12.1|SHAREPOINT|sharepoint-pci-dss-12.1	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	PCI-DSS-12.1	PCI_DSS	SHAREPOINT	sharepoint-pci-dss-12.1	payments_PCI-DSS-12.1_GENERAL_20260915_001	1	6c8adaba94e5667844f6ec0fd0f6e3969b7572b061dd1ea1519806d18449408b	2026-09-15 06:37:33+00	2026-09-15 06:37:33.486083+00	2026-09-15 06:37:33.486083+00	DRAFT	\N	\N	\N
654cb34d-ee4d-46f5-9f4b-966288fbee24	mobile-banking|ITPP|ITPP-CHG-02|SERVICENOW|servicenow-itpp-chg-02	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	ITPP-CHG-02	ITPP	SERVICENOW	servicenow-itpp-chg-02	mobile-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001	1	b175bdec976af78f3c4e59fbb3c13511ac79b1227733a23b5dec5810cec41f3c	2026-09-15 06:37:33+00	2026-09-15 06:37:33.548329+00	2026-09-15 06:37:33.548329+00	DRAFT	\N	\N	\N
44763b2a-ed03-42a3-b8c0-68662c2d9b67	mobile-banking|ITPP|ITPP-INC-01|SERVICENOW|servicenow-itpp-inc-01	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	ITPP-INC-01	ITPP	SERVICENOW	servicenow-itpp-inc-01	mobile-banking_ITPP-INC-01_GENERAL_20260915_001	1	757ebf21d27a70326dc3916bc868285cfd967e7dc05fa339c29af13b28141f80	2026-09-15 06:37:33+00	2026-09-15 06:37:33.625488+00	2026-09-15 06:37:33.625488+00	DRAFT	\N	\N	\N
2ed3b783-c765-4c35-8d28-3649d5ee5b98	mobile-banking|PCI_DSS|PCI-DSS-12.10|SERVICENOW|servicenow-pci-dss-12.10	4326ec8f-88d1-4d89-8294-84f555c9d5d6	mobile-banking	PCI-DSS-12.10	PCI_DSS	SERVICENOW	servicenow-pci-dss-12.10	mobile-banking_PCI-DSS-12.10_GENERAL_20260915_001	1	2a2311cc36524afca6d0db7e3eb7c35c74938c8ec4185be881837b2b686011ce	2026-09-15 06:37:33+00	2026-09-15 06:37:33.699528+00	2026-09-15 06:37:33.699528+00	DRAFT	\N	\N	\N
af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	net-banking|ITPP|ITPP-CHG-02|SERVICENOW|servicenow-itpp-chg-02	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	ITPP-CHG-02	ITPP	SERVICENOW	servicenow-itpp-chg-02	net-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001	1	d592c46467e2f337971219ced1ea6f377f942982e59df3cff7473798d3f16ac2	2026-09-15 06:37:33+00	2026-09-15 06:37:33.788715+00	2026-09-15 06:37:33.788715+00	DRAFT	\N	\N	\N
c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	net-banking|ITPP|ITPP-INC-01|SERVICENOW|servicenow-itpp-inc-01	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	ITPP-INC-01	ITPP	SERVICENOW	servicenow-itpp-inc-01	net-banking_ITPP-INC-01_GENERAL_20260915_001	1	2660a79ef98a809b010aafb6247c3c27f11af3611590485e3b4c126b4f0322ff	2026-09-15 06:37:33+00	2026-09-15 06:37:33.860465+00	2026-09-15 06:37:33.860465+00	DRAFT	\N	\N	\N
79296e0a-4136-445f-a85b-64d2607cc1a3	net-banking|PCI_DSS|PCI-DSS-12.10|SERVICENOW|servicenow-pci-dss-12.10	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	PCI-DSS-12.10	PCI_DSS	SERVICENOW	servicenow-pci-dss-12.10	net-banking_PCI-DSS-12.10_GENERAL_20260915_001	1	ceec4ccb8f6ba5876b688d2a8488b59ef36c0a64975f332583d0a9d071c13cd5	2026-09-15 06:37:33+00	2026-09-15 06:37:33.94522+00	2026-09-15 06:37:33.94522+00	DRAFT	\N	\N	\N
f178b149-af5a-44fd-b461-cddfeb9a76d5	payments|ITPP|ITPP-CHG-02|SERVICENOW|servicenow-itpp-chg-02	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	ITPP-CHG-02	ITPP	SERVICENOW	servicenow-itpp-chg-02	payments_ITPP-CHG-02_CHANGE-TICKET_20260915_001	1	6cdc8a6f4dce408648e17551eea1bbcd568674b00210189228baee62dca8edbe	2026-09-15 06:37:34+00	2026-09-15 06:37:34.035195+00	2026-09-15 06:37:34.035195+00	DRAFT	\N	\N	\N
81612e44-ed06-45f7-a8c1-89988c88de7b	payments|ITPP|ITPP-INC-01|SERVICENOW|servicenow-itpp-inc-01	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	ITPP-INC-01	ITPP	SERVICENOW	servicenow-itpp-inc-01	payments_ITPP-INC-01_GENERAL_20260915_001	1	ffddf055285e6b3e896876afeb4b85c203a985969849259a1c34068b1527df0a	2026-09-15 06:37:34+00	2026-09-15 06:37:34.16039+00	2026-09-15 06:37:34.16039+00	DRAFT	\N	\N	\N
c5ce959e-f256-4cef-84df-90502457d64c	payments|PCI_DSS|PCI-DSS-12.10|SERVICENOW|servicenow-pci-dss-12.10	9f63e85e-d3c8-4e36-a3b1-f941c9677535	payments	PCI-DSS-12.10	PCI_DSS	SERVICENOW	servicenow-pci-dss-12.10	payments_PCI-DSS-12.10_GENERAL_20260915_001	1	f92ed430cd5c489296e2dc0add0d072ec5212eaee923da7c5aac2b8159ad7460	2026-09-15 06:37:34+00	2026-09-15 06:37:34.232188+00	2026-09-15 06:37:34.232188+00	DRAFT	\N	\N	\N
49606c9a-050b-4562-830f-dd5e58ab6b48	net-banking|PCI_DSS|ABCD|BULK_UPLOAD|-	e559bc1f-06d9-495f-a09d-064c286c4474	net-banking	abcd	PCI_DSS	BULK_UPLOAD	\N	net-banking_ABCD_GENERAL_20260915_001	1	36bbe50ed96841d10443bcb670d6554f0a34b761be67ec9c4a8ad2c0c44ca42c	2026-09-15 06:38:00.751+00	2026-09-15 06:38:00.784524+00	2026-09-15 06:38:00.784524+00	DRAFT	\N	\N	\N
\.


--
-- Data for Name: evidence_tag; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_tag (id, evidence_record_id, tag_key, tag_value) FROM stdin;
9607fe8d-4b7c-422e-a042-8c007e602e5b	137f0260-d7cc-432c-8ad1-4417844da923	collectionMethod	demo-seed
1663ac02-1bbf-4141-a945-c67cae9f533f	137f0260-d7cc-432c-8ad1-4417844da923	application	net-banking
7ce30ff4-18b4-4b9a-93a1-4087f5d77cdf	137f0260-d7cc-432c-8ad1-4417844da923	technology	linux
72b312d9-f404-40ea-8588-7f7f599b736d	137f0260-d7cc-432c-8ad1-4417844da923	control	OS-SSH-ROOT-LOGIN
903f41e2-af4a-4068-9135-794d3ff1e774	137f0260-d7cc-432c-8ad1-4417844da923	framework	C-SITE
4dfda02d-1c58-471b-9728-ff8e8209356b	137f0260-d7cc-432c-8ad1-4417844da923	frameworks	C-SITE,PCI_DSS,ISO27001,IS
cce166f9-2800-443a-9a3d-aa5348f43785	137f0260-d7cc-432c-8ad1-4417844da923	evidenceType	HOST-CONFIG
bbe76c31-01d5-4786-baee-f29bc3bb12fa	137f0260-d7cc-432c-8ad1-4417844da923	name	net-banking_OS-SSH-ROOT-LOGIN_HOST-CONFIG_20260814_001
43f209a5-9661-4db5-a4c8-546133d99ea8	137f0260-d7cc-432c-8ad1-4417844da923	version	v1
4ea4ef68-3a2c-441b-a674-fdf4ec7e4899	95678b8e-8ebc-49be-acf4-40c4f43c77d5	collectionMethod	demo-seed
01badda4-badd-4ba4-8f2e-b0db399ef955	95678b8e-8ebc-49be-acf4-40c4f43c77d5	application	mobile-banking
644984d5-6dec-4952-8db2-f269bd8351eb	95678b8e-8ebc-49be-acf4-40c4f43c77d5	technology	linux
aa67b290-d5b7-4656-8581-67acc6b12a67	95678b8e-8ebc-49be-acf4-40c4f43c77d5	control	OS-SSH-ROOT-LOGIN
8c5fbc78-72c1-42a3-8563-ae5b05178e23	95678b8e-8ebc-49be-acf4-40c4f43c77d5	framework	C-SITE
e6d5ce85-1047-49f5-9dab-4a88fc2f38ba	95678b8e-8ebc-49be-acf4-40c4f43c77d5	frameworks	C-SITE,PCI_DSS,ISO27001,IS
55024eb1-bccf-4ef3-a4a1-97258a896dbb	95678b8e-8ebc-49be-acf4-40c4f43c77d5	evidenceType	HOST-CONFIG
bbc5d856-ebff-4ef9-9334-0403b656514d	95678b8e-8ebc-49be-acf4-40c4f43c77d5	name	mobile-banking_OS-SSH-ROOT-LOGIN_HOST-CONFIG_20260815_001
47f5e5d7-5080-4799-8dda-d1ee927502b2	95678b8e-8ebc-49be-acf4-40c4f43c77d5	version	v1
61043171-3f96-4d94-a22a-e84a0c63a65a	3340f502-aac7-4e37-9498-874ce46117cf	collectionMethod	demo-seed
32410d38-08d3-4b93-9584-6bef6d4aab20	3340f502-aac7-4e37-9498-874ce46117cf	application	payments
d861ef66-fb7f-49ab-ace1-54fcc113b5c6	3340f502-aac7-4e37-9498-874ce46117cf	technology	postgresql
1838b553-f06e-42a2-8066-dd03671b55fe	3340f502-aac7-4e37-9498-874ce46117cf	control	DB-TLS-IN-TRANSIT
aa7c16bc-34d4-44f1-8e70-315f8e9adc27	3340f502-aac7-4e37-9498-874ce46117cf	framework	PCI_DSS
57aefef4-e4d2-4b80-ad27-a199c01f7bb4	3340f502-aac7-4e37-9498-874ce46117cf	frameworks	PCI_DSS
8caea7d1-e897-4469-8bc6-ca8998b1c14b	3340f502-aac7-4e37-9498-874ce46117cf	evidenceType	DB-CONFIG
0bcf49ee-bab2-425c-8542-f0ed154b0ed4	3340f502-aac7-4e37-9498-874ce46117cf	name	payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260816_001
81734f9e-5a35-46f0-9ccd-787ad60b2dde	3340f502-aac7-4e37-9498-874ce46117cf	version	v1
65fb8d87-217b-4073-b63e-a7c249ec223e	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	collectionMethod	demo-seed
1251ad99-51bb-4a7c-9bd1-14107ac376f5	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	application	net-banking
465d7e10-a440-4660-a364-514368e14d24	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	technology	postgresql
c10c526c-8149-4343-ac66-eec07a3ab31f	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	control	DB-TLS-IN-TRANSIT
5ebe883a-0d5e-45df-8cfc-3405dfe35bc4	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	framework	PCI_DSS
91cbff99-f4d7-458e-a69c-c8bd52756520	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	frameworks	PCI_DSS,DPSC,ISO27001
3390cb39-1c03-48e8-b3a7-0c1316a62ce4	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	evidenceType	DB-CONFIG
6ded6d05-4892-4f3e-8bc0-8b16a6d8ca08	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	name	net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260817_001
d5e67a6c-4911-4c1d-aef7-4a64fcd9f437	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	version	v1
314d56ac-7a01-47b7-90a0-5880823ec7df	f009f238-7441-477e-bac9-f38d5c34a920	collectionMethod	demo-seed
e693bfd3-3500-4e07-9d5b-8dd9029a326e	f009f238-7441-477e-bac9-f38d5c34a920	application	payments
ab21244f-44ab-414e-b5d1-4cb24e2bef5c	f009f238-7441-477e-bac9-f38d5c34a920	technology	nginx
37a984b8-c9b2-41e5-8977-641ba24c005f	f009f238-7441-477e-bac9-f38d5c34a920	control	MW-TLS-VERSION
8586c1fe-20db-45b3-b108-3964fd44876b	f009f238-7441-477e-bac9-f38d5c34a920	framework	PCI_DSS
8666f7af-2c96-4bd9-aa08-d0ece30d5dc3	f009f238-7441-477e-bac9-f38d5c34a920	frameworks	PCI_DSS,DPSC,ISG
7f37d9d0-8db5-4100-88e0-9322b02647fe	f009f238-7441-477e-bac9-f38d5c34a920	evidenceType	MIDDLEWARE-CONFIG
7f33a56d-97c2-4b0f-847c-2768d43adc69	f009f238-7441-477e-bac9-f38d5c34a920	name	payments_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260818_001
0cf386a9-53fb-4f75-9c37-61e686f64dd4	f009f238-7441-477e-bac9-f38d5c34a920	version	v1
6d297d6d-e8d0-4efc-8132-82f0c455281d	4dd11c77-bc7f-4615-b96d-796bced6077c	collectionMethod	demo-seed
c0b92329-b789-4b3d-9a1c-a18272b8f472	4dd11c77-bc7f-4615-b96d-796bced6077c	application	mobile-banking
8400415f-38b6-484c-a332-b3f9224ba681	4dd11c77-bc7f-4615-b96d-796bced6077c	technology	nginx
312ca870-7bcc-43be-b481-009f864ee91f	4dd11c77-bc7f-4615-b96d-796bced6077c	control	MW-HSTS
85b929e4-b2a1-49f2-96ae-48ed8cfd80aa	4dd11c77-bc7f-4615-b96d-796bced6077c	framework	DPSC
81f97c50-86e8-49c4-82e9-ad57a6f55965	4dd11c77-bc7f-4615-b96d-796bced6077c	frameworks	DPSC,ISG,ISO27001
a5fb6bf1-2b67-497e-8514-5233a3b0957c	4dd11c77-bc7f-4615-b96d-796bced6077c	evidenceType	MIDDLEWARE-CONFIG
42465a83-5dab-4ea5-b678-4afeaa0bf14f	4dd11c77-bc7f-4615-b96d-796bced6077c	name	mobile-banking_MW-HSTS_MIDDLEWARE-CONFIG_20260819_001
73ce07b4-5f08-4374-aa6e-fdcc2cebd463	4dd11c77-bc7f-4615-b96d-796bced6077c	version	v1
b9d0582d-1d1e-46eb-9cf9-ba52f30479b5	00c73d89-1be7-48e3-a90d-24575535dfe8	collectionMethod	demo-seed
91eb8664-177e-4352-82f6-2fdf4792f256	00c73d89-1be7-48e3-a90d-24575535dfe8	application	net-banking
872c065f-d9c3-48de-8554-403f9881e322	00c73d89-1be7-48e3-a90d-24575535dfe8	technology	tls
e72c2308-a852-4124-bf7c-2c8e8cb27ffc	00c73d89-1be7-48e3-a90d-24575535dfe8	control	TLS-PROTOCOL-VERSION
ee4c26fe-ab69-4a75-9818-406f3840fa31	00c73d89-1be7-48e3-a90d-24575535dfe8	framework	PCI_DSS
05bd1ed3-4a1b-49a2-b6cf-ead6097f9f82	00c73d89-1be7-48e3-a90d-24575535dfe8	frameworks	PCI_DSS,DPSC,ISG
0d9b42f6-8b67-4a69-b103-6b76b2d8f7d4	00c73d89-1be7-48e3-a90d-24575535dfe8	evidenceType	TLS-SCAN
c44bfd02-5462-4769-b6a1-45e4437295ef	00c73d89-1be7-48e3-a90d-24575535dfe8	name	net-banking_TLS-PROTOCOL-VERSION_TLS-SCAN_20260820_001
50c806d1-5c58-4363-bacd-1d25305384f4	00c73d89-1be7-48e3-a90d-24575535dfe8	version	v1
20f25a06-c49e-4d07-99cb-248e0526d197	d3964043-ac22-4b12-8f9a-a14152017f98	objectType	change_ticket
cde50291-4c0b-4081-aa96-c9f62069d6e6	d3964043-ac22-4b12-8f9a-a14152017f98	source	MOCK_JIRA
126103aa-1434-4f16-9b06-da5de01af7f8	d3964043-ac22-4b12-8f9a-a14152017f98	application	mobile-banking
0d39df8b-b267-4da2-8ed1-1d26361f66b4	d3964043-ac22-4b12-8f9a-a14152017f98	technology	unknown
c083bb1e-ff39-4d4b-b7ce-c8191616fc61	d3964043-ac22-4b12-8f9a-a14152017f98	control	PCI-DSS-6.3
cc7bc88f-1f36-4fb3-ae90-5d4f59a31130	d3964043-ac22-4b12-8f9a-a14152017f98	framework	PCI_DSS
f4956e7f-c73a-4140-a564-709b59bcdc15	d3964043-ac22-4b12-8f9a-a14152017f98	frameworks	PCI_DSS
964085c6-f89f-4f13-91aa-cbd5c59bcf47	d3964043-ac22-4b12-8f9a-a14152017f98	evidenceType	CODE-REVIEW
98eb5989-fe59-4739-a20d-840c029283ac	d3964043-ac22-4b12-8f9a-a14152017f98	name	mobile-banking_PCI-DSS-6.3_CODE-REVIEW_20260915_001
16cb00b4-ac32-4a86-83eb-8a8cceb719ff	d3964043-ac22-4b12-8f9a-a14152017f98	sourceTitle	change_ticket for PCI-DSS-6.3
ec64b788-cf86-4a2d-8343-f690bb18edd3	d3964043-ac22-4b12-8f9a-a14152017f98	collectionMethod	scheduled
df95e343-9f32-4783-8708-ad9a0701b6ae	d3964043-ac22-4b12-8f9a-a14152017f98	version	v1
6407f3cc-b827-4809-95ad-552446e7cee9	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	objectType	change_ticket
3d6aad00-da4d-45de-8d02-76af042daa89	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	source	MOCK_JIRA
e4e2eddc-786a-42a1-b0cd-af8eee56afe9	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	application	mobile-banking
30ad9809-b16a-4c0e-8c52-2176bb84d697	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	technology	unknown
c61aa75b-d481-40e6-81c7-436d633b314a	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	control	ITPP-CHG-02
786e8e9c-d639-4714-90ac-67a93a35ba5d	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	framework	ITPP
a781fcf1-09de-460e-87ed-02778945a5ac	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	frameworks	ITPP,ISO27001,IS
aa637c35-ad22-4d97-8764-329f872752a4	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	evidenceType	CHANGE-TICKET
1bbfe9d3-773a-4083-8928-11e25431569a	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	name	mobile-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001
0987612f-21ba-4621-a2f4-779646e60e9b	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	sourceTitle	change_ticket for ITPP-CHG-02
f28f3e30-1a2d-472c-bd27-cd6e6b13e276	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	collectionMethod	scheduled
44d58c53-bba2-4b87-bbd1-ff40ac46d529	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	version	v1
c0d09554-d677-4e3c-ac41-dcccefe6eea6	6f4cc0f7-e8c8-4121-9897-7d43cd064263	objectType	change_ticket
76910638-d428-488c-bd0a-5d649f691d16	6f4cc0f7-e8c8-4121-9897-7d43cd064263	source	MOCK_JIRA
6e0a058b-9783-4561-8f3b-2cbaaa37903c	6f4cc0f7-e8c8-4121-9897-7d43cd064263	application	net-banking
a65a8f5f-1ef6-495f-a88c-8793823bc8fd	6f4cc0f7-e8c8-4121-9897-7d43cd064263	technology	unknown
894a5188-8b00-4384-8b9a-02ea542459c1	6f4cc0f7-e8c8-4121-9897-7d43cd064263	control	PCI-DSS-6.3
871ff410-346f-425f-8982-4d2afca214b1	6f4cc0f7-e8c8-4121-9897-7d43cd064263	framework	PCI_DSS
e444cb97-2f0b-447f-a284-782fc9c836d1	6f4cc0f7-e8c8-4121-9897-7d43cd064263	frameworks	PCI_DSS
175ebda5-e57d-4d40-a586-e1e5c9f4bda0	6f4cc0f7-e8c8-4121-9897-7d43cd064263	evidenceType	CODE-REVIEW
ae90babb-5e9c-4e9a-86f6-52d4dde871a0	6f4cc0f7-e8c8-4121-9897-7d43cd064263	name	net-banking_PCI-DSS-6.3_CODE-REVIEW_20260915_001
9ceea5fb-8c25-4109-a0db-b7ad523fac96	6f4cc0f7-e8c8-4121-9897-7d43cd064263	sourceTitle	change_ticket for PCI-DSS-6.3
7cb56180-3bb2-4ba7-977d-be3c289aff29	6f4cc0f7-e8c8-4121-9897-7d43cd064263	collectionMethod	scheduled
c4b6218f-825a-45af-a2ba-64df28cbba67	6f4cc0f7-e8c8-4121-9897-7d43cd064263	version	v1
6a257e5c-43ba-4fa4-bc84-6bbf18af5eb5	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	objectType	change_ticket
facb42db-5f38-4a78-a397-0ea7552e2f56	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	source	MOCK_JIRA
16762057-b7ee-4145-895f-ffc684e686b3	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	application	net-banking
cf3c7a53-7a66-4dcf-9bb4-bc21699147a7	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	technology	unknown
366cfdca-0584-4f41-b1c9-3df3f12ab030	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	control	ITPP-CHG-02
18a0bb61-4608-40b1-bb1a-d58bda78e795	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	framework	ITPP
a68a8b2c-45c6-4494-b841-3906f1df91a0	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	frameworks	ITPP,ISO27001,IS
ff932204-8fae-44a6-8157-0c46c859a2d1	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	evidenceType	CHANGE-TICKET
15fbb8d0-d648-4967-b40e-af81217cebfe	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	name	net-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001
3dea97aa-9542-4d51-9322-bfa7e3aca07c	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	sourceTitle	change_ticket for ITPP-CHG-02
a7ecd3c4-bbf0-4829-a2d3-bb6cd84d4e1d	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	collectionMethod	scheduled
0c2f7456-0513-4272-833a-092403d3ed46	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	version	v1
5b46a2b9-6070-4536-baab-c391ad1baf64	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	objectType	change_ticket
7eea325e-efa5-40e8-bbf2-619b7b2a8a60	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	source	MOCK_JIRA
bcf5aec7-a658-45d4-ae24-1146ebe227b0	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	application	payments
6ccadd94-c729-441a-ac81-6f1465be4e38	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	technology	unknown
186198d5-af0c-4a19-a0d7-4d6d7bcedcee	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	control	PCI-DSS-6.3
6d473485-6f1c-4b69-bad8-000afe337d8a	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	framework	PCI_DSS
b9da2ba5-205e-4966-823f-3a11c657767f	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	frameworks	PCI_DSS
934d59d7-e999-40ee-9cfb-d7b38f360432	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	evidenceType	CODE-REVIEW
059e434c-4d36-4ade-a676-a043cd379b06	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	name	payments_PCI-DSS-6.3_CODE-REVIEW_20260915_001
15ad100e-3453-4388-857e-8cde64c4aec4	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	sourceTitle	change_ticket for PCI-DSS-6.3
27604001-f967-495a-9ee2-b9d14f3d31c0	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	collectionMethod	scheduled
a823cf9b-8de9-4491-a91f-2dbb12339f62	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	version	v1
a633cb2b-2404-476e-974b-ca792ebfedd7	f46cd29a-37a3-48c7-b718-e18c28f766b0	objectType	change_ticket
13adbb3a-25a5-4575-91be-a75045f1a8a6	f46cd29a-37a3-48c7-b718-e18c28f766b0	source	MOCK_JIRA
6894b5ec-aa15-41e8-83a1-c5f1a98438c9	f46cd29a-37a3-48c7-b718-e18c28f766b0	application	payments
c7aa752c-ff35-4f6b-abca-62a979e8b683	f46cd29a-37a3-48c7-b718-e18c28f766b0	technology	unknown
11b93dcd-d9a9-4d12-8c4a-31d819461236	f46cd29a-37a3-48c7-b718-e18c28f766b0	control	ITPP-CHG-02
b3f4e36b-43c4-4022-8eca-f4d4c8eea6de	f46cd29a-37a3-48c7-b718-e18c28f766b0	framework	ITPP
d303da8d-6cb5-4dbc-895a-9c6fb2142b1a	f46cd29a-37a3-48c7-b718-e18c28f766b0	frameworks	ITPP,ISO27001,IS
95ad601f-fe32-4157-b64a-92750b2de23c	f46cd29a-37a3-48c7-b718-e18c28f766b0	evidenceType	CHANGE-TICKET
151b2bef-f61e-48ce-8a9b-a391ce15f8b0	f46cd29a-37a3-48c7-b718-e18c28f766b0	name	payments_ITPP-CHG-02_CHANGE-TICKET_20260915_001
b999fdf7-7666-4ed1-b24a-bd4ea8165319	f46cd29a-37a3-48c7-b718-e18c28f766b0	sourceTitle	change_ticket for ITPP-CHG-02
565f8f29-df47-4159-bb81-14d4d3813044	f46cd29a-37a3-48c7-b718-e18c28f766b0	collectionMethod	scheduled
58e6e6e1-272f-483b-9905-511d1bab8e38	f46cd29a-37a3-48c7-b718-e18c28f766b0	version	v1
6eb01c46-2e29-438c-9040-03d4402cb290	76c9a2eb-b56b-491f-b914-3d5729bf8aae	objectType	incident_record
6623e822-931c-42a1-8b5c-cc6e0b46be72	76c9a2eb-b56b-491f-b914-3d5729bf8aae	source	MOCK_SERVICENOW
853f0f12-0845-4bee-bc66-dc2b7037453e	76c9a2eb-b56b-491f-b914-3d5729bf8aae	application	mobile-banking
e634b5bf-8ee0-455e-a7b9-2288ab221136	76c9a2eb-b56b-491f-b914-3d5729bf8aae	technology	unknown
2b3756aa-6967-413a-ad25-1ef3feb95c80	76c9a2eb-b56b-491f-b914-3d5729bf8aae	control	ITPP-INC-01
b255cb3f-36cb-4121-b89d-478c5ac7b394	76c9a2eb-b56b-491f-b914-3d5729bf8aae	framework	ITPP
31b2ebe7-7284-4cb3-8584-1f1c48194596	76c9a2eb-b56b-491f-b914-3d5729bf8aae	frameworks	ITPP
0a833d90-5f96-422a-ad1b-baef5717c3ba	76c9a2eb-b56b-491f-b914-3d5729bf8aae	evidenceType	GENERAL
a2770465-dae3-48e4-8fe8-a419c412c63e	76c9a2eb-b56b-491f-b914-3d5729bf8aae	name	mobile-banking_ITPP-INC-01_GENERAL_20260915_001
03c95edc-3922-4944-a0c2-c1168e4a0e7e	76c9a2eb-b56b-491f-b914-3d5729bf8aae	sourceTitle	incident_record for ITPP-INC-01
12f39b1b-e1b4-4c5d-96e3-bb4aee82a5c3	76c9a2eb-b56b-491f-b914-3d5729bf8aae	collectionMethod	scheduled
16075004-9eb4-42b4-b11e-6df3413e1876	76c9a2eb-b56b-491f-b914-3d5729bf8aae	version	v1
9031a1da-65a7-48df-be88-fdc73eefe847	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	objectType	incident_record
223dc03d-37dc-4e13-bca9-7758b1b29e87	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	source	MOCK_SERVICENOW
3f48ddcc-9b9c-4a5b-b6c5-21c126ec69e4	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	application	mobile-banking
3eea6f6e-44fc-4527-891e-f5cd05637eb8	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	technology	unknown
73222eb7-9e93-4796-bcea-0e0ab746f316	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	control	PCI-DSS-12.10
ba704958-8461-4e62-83ac-9c20f946fa22	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	framework	PCI_DSS
b2d86d65-22cb-44c6-93cd-85c31d9969c2	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	frameworks	PCI_DSS
a5d370b2-6770-42cb-9369-6874b171c812	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	evidenceType	GENERAL
cb191bb9-780e-405f-9c5f-ac0b4edb4e4e	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	name	mobile-banking_PCI-DSS-12.10_GENERAL_20260915_001
2cdecd9a-22e0-44dd-9345-be8ff1ac98df	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	sourceTitle	incident_record for PCI-DSS-12.10
dfd7a0d5-00e6-47b3-b41e-800c1aff1e40	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	collectionMethod	scheduled
f67391bc-8fb5-4cd5-866f-28c9c5eb2ad0	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	version	v1
e7b3d379-584b-4ad2-9375-ccd6b730eece	ee1c3319-eed3-4078-8dfd-5313359b7cd5	objectType	incident_record
f16f9075-e6e5-4551-b465-df42d6d587e6	ee1c3319-eed3-4078-8dfd-5313359b7cd5	source	MOCK_SERVICENOW
e3f1b00d-b224-448e-ae8c-9f3276d07f19	ee1c3319-eed3-4078-8dfd-5313359b7cd5	application	net-banking
f3e72723-402e-4130-ab42-d6e703486caf	ee1c3319-eed3-4078-8dfd-5313359b7cd5	technology	unknown
17027a50-c369-419b-bd01-a8cca57729f9	ee1c3319-eed3-4078-8dfd-5313359b7cd5	control	ITPP-INC-01
214d0bbb-2a8d-437c-9b5d-d4485fb5feca	ee1c3319-eed3-4078-8dfd-5313359b7cd5	framework	ITPP
b38c78b7-0192-46d3-8131-01eb1d24de0f	ee1c3319-eed3-4078-8dfd-5313359b7cd5	frameworks	ITPP
4758b168-aa15-4f36-a823-1a9e133ddb11	ee1c3319-eed3-4078-8dfd-5313359b7cd5	evidenceType	GENERAL
ae081bf4-e907-4454-9f47-80cc9fe1fda4	ee1c3319-eed3-4078-8dfd-5313359b7cd5	name	net-banking_ITPP-INC-01_GENERAL_20260915_001
9ef4d2ef-4183-42d6-a64a-c9ddf77c9600	ee1c3319-eed3-4078-8dfd-5313359b7cd5	sourceTitle	incident_record for ITPP-INC-01
a160fb86-8f18-4613-a285-c0a2560088af	ee1c3319-eed3-4078-8dfd-5313359b7cd5	collectionMethod	scheduled
61ab0ebf-a30f-4621-9dfc-b3fc36d2b831	ee1c3319-eed3-4078-8dfd-5313359b7cd5	version	v1
358a0374-ce59-46e9-b544-9d3e9978e5f9	3e71e55c-5859-4652-b492-207dac524809	objectType	incident_record
eb240f9c-b4c8-432f-8587-430562a43d4d	3e71e55c-5859-4652-b492-207dac524809	source	MOCK_SERVICENOW
f5f14fd3-8b83-4279-b75f-d4fd2672094f	3e71e55c-5859-4652-b492-207dac524809	application	net-banking
5b03fde9-c684-4264-a73d-6a763be88aaf	3e71e55c-5859-4652-b492-207dac524809	technology	unknown
fd3746e0-2990-44d6-8179-182868107d5b	3e71e55c-5859-4652-b492-207dac524809	control	PCI-DSS-12.10
12cf9f34-a383-4605-9bf5-a9ebc51c9b8d	3e71e55c-5859-4652-b492-207dac524809	framework	PCI_DSS
48d1b88a-4379-4f75-9090-4c1a4ba63e58	3e71e55c-5859-4652-b492-207dac524809	frameworks	PCI_DSS
0230be54-00d4-49f6-a6d5-54b0da3e753f	3e71e55c-5859-4652-b492-207dac524809	evidenceType	GENERAL
3fd4e77a-57fa-45ec-8db1-c8bfa5842ff9	3e71e55c-5859-4652-b492-207dac524809	name	net-banking_PCI-DSS-12.10_GENERAL_20260915_001
e5094c55-a741-46a8-ad4d-7eceb862c506	3e71e55c-5859-4652-b492-207dac524809	sourceTitle	incident_record for PCI-DSS-12.10
e72029fa-8ded-4a2d-be9a-bc58b673b822	3e71e55c-5859-4652-b492-207dac524809	collectionMethod	scheduled
7c8fedbb-67dc-4215-a03b-ce9b6d729574	3e71e55c-5859-4652-b492-207dac524809	version	v1
d6f30fd2-7a38-4569-aeb1-9bb80f9bf9bf	2777b2ba-3652-4908-9561-98a028beef82	objectType	incident_record
85ebe9fa-2995-42fd-a4e6-d31543ff38d4	2777b2ba-3652-4908-9561-98a028beef82	source	MOCK_SERVICENOW
2e21db6c-a440-4195-b9a1-8323b17ed795	2777b2ba-3652-4908-9561-98a028beef82	application	payments
8bc49c2e-e03a-4c7a-94a4-15aecbde58b5	2777b2ba-3652-4908-9561-98a028beef82	technology	unknown
3788a326-e8b0-4aec-bfa3-763f1132a907	2777b2ba-3652-4908-9561-98a028beef82	control	ITPP-INC-01
28546f14-94b5-49e9-ae1f-216a8ce2028e	2777b2ba-3652-4908-9561-98a028beef82	framework	ITPP
1307d877-0280-46c6-86c5-6aea85571c61	2777b2ba-3652-4908-9561-98a028beef82	frameworks	ITPP
fd01df6f-0405-4da1-9ec5-3783f749f7bc	2777b2ba-3652-4908-9561-98a028beef82	evidenceType	GENERAL
99559da8-4ec3-4822-a693-c7216dd09596	2777b2ba-3652-4908-9561-98a028beef82	name	payments_ITPP-INC-01_GENERAL_20260915_001
16f4e4dc-446b-45ca-94db-c11f0781912f	2777b2ba-3652-4908-9561-98a028beef82	sourceTitle	incident_record for ITPP-INC-01
1c94834e-aff6-4acd-8b72-0f9a63aac294	2777b2ba-3652-4908-9561-98a028beef82	collectionMethod	scheduled
b136c915-f277-4f88-a126-eac6c1512211	2777b2ba-3652-4908-9561-98a028beef82	version	v1
30ff8c26-068b-44cc-8243-fe04fe772ec5	97763088-2d92-472e-8672-362cef6175a4	objectType	incident_record
37ee1840-4e6c-454a-b1a5-32daea51934b	97763088-2d92-472e-8672-362cef6175a4	source	MOCK_SERVICENOW
e778169c-8016-4549-abe9-2a518a625b59	97763088-2d92-472e-8672-362cef6175a4	application	payments
e09489d9-7199-4198-9fca-ec1ecd5efcb4	97763088-2d92-472e-8672-362cef6175a4	technology	unknown
9a934075-9cd4-47a6-afbf-417e73120756	97763088-2d92-472e-8672-362cef6175a4	control	PCI-DSS-12.10
a8d1fab3-0888-4fbe-b13b-ba74fef14eb8	97763088-2d92-472e-8672-362cef6175a4	framework	PCI_DSS
0c946cb0-b39b-40bc-af16-55424f06c2ec	97763088-2d92-472e-8672-362cef6175a4	frameworks	PCI_DSS
6d126d0d-8110-44ce-8fa4-0e902bdf73aa	97763088-2d92-472e-8672-362cef6175a4	evidenceType	GENERAL
2fe79d98-96a7-4ebf-9f5c-24a2e277f436	97763088-2d92-472e-8672-362cef6175a4	name	payments_PCI-DSS-12.10_GENERAL_20260915_001
da79717a-7492-4b87-90d1-b60f25b9f142	97763088-2d92-472e-8672-362cef6175a4	sourceTitle	incident_record for PCI-DSS-12.10
ba8d8bec-bc00-4b53-91c0-b8b77124c44e	97763088-2d92-472e-8672-362cef6175a4	collectionMethod	scheduled
a200793f-6dd8-4884-b42a-b4bd861910ce	97763088-2d92-472e-8672-362cef6175a4	version	v1
3affe2ee-465b-4250-b74f-8d06d15a1a69	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	objectType	branch_protection
693b2fd2-5ed2-492f-86ae-2e5dc88b3fc1	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	source	MOCK_GITHUB
0beea4bf-19f3-42d2-8579-15992c2e505d	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	application	mobile-banking
dfee76a9-34d7-4ad9-aff4-59539c2febe7	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	technology	unknown
cbc308f4-2189-4d34-beba-c56bfa8c50c6	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	control	PCI-DSS-6.2
4471d77b-8b6a-4503-bcd7-3cf2e6ff070d	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	framework	PCI_DSS
a9c6bd65-8878-4bb0-988f-682090b29400	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	frameworks	PCI_DSS,DPSC
03f40571-0443-4468-899b-7a08361d656c	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	evidenceType	CODE-REVIEW
8a0ad816-3b9d-484b-b0e3-9de6a8023fdc	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	name	mobile-banking_PCI-DSS-6.2_CODE-REVIEW_20260915_001
10c37921-1360-48a8-912c-27d92b0cd04c	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	sourceTitle	branch_protection for PCI-DSS-6.2
bcb1a2ed-c2e6-4777-ba94-6f399a8fb703	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	collectionMethod	scheduled
2371234f-b210-4428-be67-754a78164431	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	version	v1
ad91e0ed-6194-4778-a637-e37d9759acbe	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	objectType	pull_request_review
db8db9a5-6e3e-427c-bdac-e8f0f952a497	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	source	MOCK_GITHUB
3f56febd-273d-47e0-b11c-71fd805ef7fa	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	application	mobile-banking
5f067e72-59df-407e-9941-0ffbd669307a	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	technology	unknown
503b07fd-dd2c-4c94-aebe-fa9f3758d02f	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	control	DPSC-SDLC-04
f688413a-d835-4163-b6d3-a2f6a1a134ad	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	framework	DPSC
1a4055a3-6abf-4ac5-b7ea-ab29159ae9d4	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	frameworks	DPSC,ISO27001,PCI_DSS
47accca8-1eb9-4521-bd6f-aa92c24b838e	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	evidenceType	CODE-REVIEW
17dfd1bd-4870-4b46-a9b3-f77dcc3ce4bb	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	name	mobile-banking_DPSC-SDLC-04_CODE-REVIEW_20260915_001
8b0f7240-4aa3-4420-a8f6-e5d3a9ec77c9	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	sourceTitle	pull_request_review for DPSC-SDLC-04
b2173fc4-9d32-4345-b209-9fdd690e400f	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	collectionMethod	scheduled
8e0fb112-2e43-4425-adaf-08befc79db40	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	version	v1
91cd5eff-1a77-4c4e-9c23-082ff73a5fc0	82fe525f-b0e6-4630-be90-6699615e0868	objectType	branch_protection
6f34f9c9-1c46-460c-9436-fe97547374fd	82fe525f-b0e6-4630-be90-6699615e0868	source	MOCK_GITHUB
edb97cda-3a7a-4d5b-91c9-596e1f0f102d	82fe525f-b0e6-4630-be90-6699615e0868	application	net-banking
ec8a0980-a013-442b-92e1-beb1414f8e47	82fe525f-b0e6-4630-be90-6699615e0868	technology	unknown
f7bc0cb5-a657-4e80-9bd5-85ef190ba4b7	82fe525f-b0e6-4630-be90-6699615e0868	control	PCI-DSS-6.2
e2cd8afa-f0a4-4c16-9b37-0d985c7c44e4	82fe525f-b0e6-4630-be90-6699615e0868	framework	PCI_DSS
ee2c9579-60f2-4c7b-8db7-d50d828a3c4d	82fe525f-b0e6-4630-be90-6699615e0868	frameworks	PCI_DSS,DPSC
3c1e86c2-e46f-4270-bc60-499eb295b23c	82fe525f-b0e6-4630-be90-6699615e0868	evidenceType	CODE-REVIEW
5de8f9c9-d745-4648-9384-7aef0df106bd	82fe525f-b0e6-4630-be90-6699615e0868	name	net-banking_PCI-DSS-6.2_CODE-REVIEW_20260915_001
dacbe712-9efa-46d9-854d-9bf803a6a5fc	82fe525f-b0e6-4630-be90-6699615e0868	sourceTitle	branch_protection for PCI-DSS-6.2
b014d027-2723-4805-87f0-0d44418c3aff	82fe525f-b0e6-4630-be90-6699615e0868	collectionMethod	scheduled
290ba96d-986c-49d1-91d0-564ae02b4d65	82fe525f-b0e6-4630-be90-6699615e0868	version	v1
258bf939-7a8c-48c7-8d2f-03bf76826770	d6f36c83-2f64-4631-ac94-fe19b89c69e7	objectType	pull_request_review
6dfc7f72-e12c-45e6-90d3-82cad791e116	d6f36c83-2f64-4631-ac94-fe19b89c69e7	source	MOCK_GITHUB
9c9358f0-29f6-4370-adac-82d40e8d12ec	d6f36c83-2f64-4631-ac94-fe19b89c69e7	application	net-banking
c39b2276-8c19-4d1b-bb03-7524c7cb5922	d6f36c83-2f64-4631-ac94-fe19b89c69e7	technology	unknown
74ad6b7d-4374-4f80-b67c-16269ead08bd	d6f36c83-2f64-4631-ac94-fe19b89c69e7	control	DPSC-SDLC-04
96bf45dc-e84b-46dd-b0ce-caeccc7639f1	d6f36c83-2f64-4631-ac94-fe19b89c69e7	framework	DPSC
a971b5bb-ed48-4a37-a745-6e4ca76deac2	d6f36c83-2f64-4631-ac94-fe19b89c69e7	frameworks	DPSC,ISO27001,PCI_DSS
c7e960f1-5b24-402e-bb9d-c8db22a28d69	d6f36c83-2f64-4631-ac94-fe19b89c69e7	evidenceType	CODE-REVIEW
10ed08e6-ef20-4082-8963-cc7d17398eaf	d6f36c83-2f64-4631-ac94-fe19b89c69e7	name	net-banking_DPSC-SDLC-04_CODE-REVIEW_20260915_001
e6d468dc-40bc-40ee-9b34-81896a4a474e	d6f36c83-2f64-4631-ac94-fe19b89c69e7	sourceTitle	pull_request_review for DPSC-SDLC-04
e1925ec4-9e73-40f5-a77d-2bcadfe3dc52	d6f36c83-2f64-4631-ac94-fe19b89c69e7	collectionMethod	scheduled
7c78321e-ced6-49d0-8273-e7ecb135742d	d6f36c83-2f64-4631-ac94-fe19b89c69e7	version	v1
5f5ac6e8-1d23-4efc-9628-fbb4f443dda6	7efff257-28af-43c1-8634-51e773b07948	objectType	branch_protection
26e4bcc9-5222-429c-b8c2-eb45623659ea	7efff257-28af-43c1-8634-51e773b07948	source	MOCK_GITHUB
a4f7c4f2-810c-4fee-8a34-049abb51ab2c	7efff257-28af-43c1-8634-51e773b07948	application	payments
07df99f9-dbc9-4fe2-ab30-5037c35a3426	7efff257-28af-43c1-8634-51e773b07948	technology	unknown
ec1d9e15-a612-4dac-8009-1f8751cd5d40	7efff257-28af-43c1-8634-51e773b07948	control	PCI-DSS-6.2
62512667-50c5-4d91-ad39-aa0eefbf5bcf	7efff257-28af-43c1-8634-51e773b07948	framework	PCI_DSS
4acce9d8-21a8-4aea-87fa-f8a579ffc683	7efff257-28af-43c1-8634-51e773b07948	frameworks	PCI_DSS,DPSC
d2f455bd-4f44-416d-9c65-af1a83b8a96d	7efff257-28af-43c1-8634-51e773b07948	evidenceType	CODE-REVIEW
6307f44a-2ca2-49d6-a3c7-a0b777da06cc	7efff257-28af-43c1-8634-51e773b07948	name	payments_PCI-DSS-6.2_CODE-REVIEW_20260915_001
7c4228a5-1f27-4cf0-b6d0-d52763f2f27c	7efff257-28af-43c1-8634-51e773b07948	sourceTitle	branch_protection for PCI-DSS-6.2
94f0f5ac-437d-4d56-8f21-8a0931082c84	7efff257-28af-43c1-8634-51e773b07948	collectionMethod	scheduled
f2777159-479a-472a-aaa1-715788e06f4d	7efff257-28af-43c1-8634-51e773b07948	version	v1
638e9d88-7411-4efd-9ad0-2a0bcae10db2	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	objectType	pull_request_review
6dd0ed8e-fc6c-4cef-9d3d-696ec02f0345	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	source	MOCK_GITHUB
c3b6f8db-e7fd-4a3b-94be-6758a4834f26	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	application	payments
d1396995-8331-42fa-b517-37d955d7375e	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	technology	unknown
67aaf610-9680-4040-a408-502c78e51bdf	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	control	DPSC-SDLC-04
7d837069-7237-4e23-aa8d-7dd9f9d22d09	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	framework	DPSC
981a45a0-6e1a-4d9b-ab8e-5cbe7217ee12	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	frameworks	DPSC,ISO27001,PCI_DSS
e0dfea39-1a7c-4cfa-b61f-bcfdd6bf7f80	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	evidenceType	CODE-REVIEW
a586ee44-b0a4-4426-86c8-8a6f228cc2af	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	name	payments_DPSC-SDLC-04_CODE-REVIEW_20260915_001
756a125b-f38f-4b53-8fb1-a55c2142e933	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	sourceTitle	pull_request_review for DPSC-SDLC-04
c40876df-fb00-4dbd-9e91-60ddf43afdfd	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	collectionMethod	scheduled
0ffb16b5-4b7c-4613-b158-5e76993cfef5	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	version	v1
fe8d22e3-a3dd-4738-aefe-e2f7972d320b	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	objectType	policy_page
6d589e72-ebd9-46e2-b87f-6ea8736cf206	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	source	MOCK_CONFLUENCE
9dee977b-23e1-48f5-99eb-1d416aff1519	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	application	mobile-banking
8f2eac9b-df4b-474d-8d63-3e7268e53c32	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	technology	unknown
e0ee86bf-1151-4ccb-ab5c-d2856ebb9aac	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	control	ITPP-DOC-03
4a246d3b-a73c-4b25-9db7-2625c0838b25	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	framework	ITPP
c7bf78b6-71f3-4ad7-bee4-b95cffb2d808	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	frameworks	ITPP
0f995183-9323-4ebc-b677-295e2553538f	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	evidenceType	GENERAL
c32c53c9-520c-4647-a614-cfe742742955	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	name	mobile-banking_ITPP-DOC-03_GENERAL_20260915_001
d956d9eb-18fc-4602-8f15-55b37ac3b099	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	sourceTitle	policy_page for ITPP-DOC-03
97e35e0f-fdf5-4930-9ade-a85a3b0be694	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	collectionMethod	scheduled
4423de12-15a6-494e-908b-38005ca9fa2a	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	version	v1
17963821-5913-41d5-98bb-94b453a57d96	64ddd7c6-fdca-443b-8dde-fbc0d2801462	objectType	policy_page
d7fb0ba8-b640-4b55-a9df-549bb162e08a	64ddd7c6-fdca-443b-8dde-fbc0d2801462	source	MOCK_CONFLUENCE
2b5e059d-48c7-41e8-a8d5-ff5aac900607	64ddd7c6-fdca-443b-8dde-fbc0d2801462	application	mobile-banking
fb1fd20e-9cce-4b83-b229-77582f17d63d	64ddd7c6-fdca-443b-8dde-fbc0d2801462	technology	unknown
c6f5a99f-65b2-4cc5-8007-729e9682fc81	64ddd7c6-fdca-443b-8dde-fbc0d2801462	control	DPSC-GOV-01
159fa103-6728-4327-a3b3-7de1289bc9d3	64ddd7c6-fdca-443b-8dde-fbc0d2801462	framework	DPSC
8db1afed-478d-4080-871d-730ca4e49154	64ddd7c6-fdca-443b-8dde-fbc0d2801462	frameworks	DPSC
4b964bca-9907-47a9-983d-7683a8060094	64ddd7c6-fdca-443b-8dde-fbc0d2801462	evidenceType	GENERAL
dd6f7bd6-07f4-4f69-8ee4-ddf478b8faba	64ddd7c6-fdca-443b-8dde-fbc0d2801462	name	mobile-banking_DPSC-GOV-01_GENERAL_20260915_001
34393383-bc1c-497d-a496-06de48a29899	64ddd7c6-fdca-443b-8dde-fbc0d2801462	sourceTitle	policy_page for DPSC-GOV-01
2c33a260-a6ed-48f9-85d6-fbe398d4e42a	64ddd7c6-fdca-443b-8dde-fbc0d2801462	collectionMethod	scheduled
af215127-a4cb-4827-8df2-7543521b74ad	64ddd7c6-fdca-443b-8dde-fbc0d2801462	version	v1
2a375356-d2ce-47db-8a0a-5b78ad9137d5	8296014e-a49c-4866-a948-6433ac7cef44	objectType	policy_page
78c0dc13-8a4b-4fd7-a1c4-b9d917d1cd94	8296014e-a49c-4866-a948-6433ac7cef44	source	MOCK_CONFLUENCE
4c5cc93f-724e-41e9-92c7-e17090349856	8296014e-a49c-4866-a948-6433ac7cef44	application	net-banking
199a0fc5-3766-43e9-8d85-a04ad38c682f	8296014e-a49c-4866-a948-6433ac7cef44	technology	unknown
0c4ae4c4-9f68-4e13-85a4-51cab0b6c206	8296014e-a49c-4866-a948-6433ac7cef44	control	ITPP-DOC-03
665efd89-f614-495b-824c-11ffff8066c8	8296014e-a49c-4866-a948-6433ac7cef44	framework	ITPP
351b66e1-f88f-4053-8bbf-856f15c82549	8296014e-a49c-4866-a948-6433ac7cef44	frameworks	ITPP
c51e1faa-607b-45fe-9a03-a3acf6424e19	8296014e-a49c-4866-a948-6433ac7cef44	evidenceType	GENERAL
1e47de89-7cd7-4a6f-9437-b70032327a08	8296014e-a49c-4866-a948-6433ac7cef44	name	net-banking_ITPP-DOC-03_GENERAL_20260915_001
1f2489d3-30c1-497c-ac60-59aa7c11e613	8296014e-a49c-4866-a948-6433ac7cef44	sourceTitle	policy_page for ITPP-DOC-03
0d980726-5ec6-4f41-a3c2-5e27754c5866	8296014e-a49c-4866-a948-6433ac7cef44	collectionMethod	scheduled
f167c631-de9b-4bf0-a15e-796f847e2447	8296014e-a49c-4866-a948-6433ac7cef44	version	v1
40481d4b-b213-42a8-9af3-79c350b8aff5	a0569699-2209-4fff-a8a0-af1e0ccaa227	objectType	policy_page
b469a953-d5b8-4c9f-b747-a276bf829edb	a0569699-2209-4fff-a8a0-af1e0ccaa227	source	MOCK_CONFLUENCE
107c774c-fe18-45f9-ba25-344abfbe1ef8	a0569699-2209-4fff-a8a0-af1e0ccaa227	application	net-banking
641378e8-26bf-489f-a8a7-b9cc7ffefcbb	a0569699-2209-4fff-a8a0-af1e0ccaa227	technology	unknown
08d49f4e-a849-4427-9da4-60e68a9a7f40	a0569699-2209-4fff-a8a0-af1e0ccaa227	control	DPSC-GOV-01
acc72536-caaa-47c6-b4be-d793bbb8ba30	a0569699-2209-4fff-a8a0-af1e0ccaa227	framework	DPSC
ac04e15e-5ae1-449b-b670-90409fe1526b	a0569699-2209-4fff-a8a0-af1e0ccaa227	frameworks	DPSC
e2c0a4cc-7dc4-444b-8cf7-a0839bbc788b	a0569699-2209-4fff-a8a0-af1e0ccaa227	evidenceType	GENERAL
ed6b4706-bdc6-4f53-b493-862fc505b225	a0569699-2209-4fff-a8a0-af1e0ccaa227	name	net-banking_DPSC-GOV-01_GENERAL_20260915_001
44b0a60c-c0ed-40e4-b9d8-7cd635eb84d0	a0569699-2209-4fff-a8a0-af1e0ccaa227	sourceTitle	policy_page for DPSC-GOV-01
9a5b7834-ade0-425c-b963-cfa1d14eb4c3	a0569699-2209-4fff-a8a0-af1e0ccaa227	collectionMethod	scheduled
e36adf7e-0862-42ef-9e12-9c02dcabe33e	a0569699-2209-4fff-a8a0-af1e0ccaa227	version	v1
11d32759-8dde-47a5-b938-fb3880e72112	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	objectType	policy_page
a372b254-76e0-4402-8a7a-5c1c1c35b005	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	source	MOCK_CONFLUENCE
ee4a7760-4a9c-4db4-b575-470feb52ac9e	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	application	payments
a895ad2a-ab31-455d-a43f-e02198ab8c00	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	technology	unknown
f4038b0d-d208-452a-b3fd-bc7b206ce9cf	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	control	ITPP-DOC-03
d876a8ef-83b3-413a-a40a-a2844f0d61e3	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	framework	ITPP
82676de6-f1d3-4344-a928-ac7b6f57c1fc	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	frameworks	ITPP
d4e344cd-2d57-4974-8aaf-c6170736d959	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	evidenceType	GENERAL
8c451069-3aa9-4098-8989-031d5c8198a1	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	name	payments_ITPP-DOC-03_GENERAL_20260915_001
8fcb907b-b9a9-484f-b0db-f6b69b451b10	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	sourceTitle	policy_page for ITPP-DOC-03
2207735a-b1ba-4b2d-a4ce-4629e63c4803	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	collectionMethod	scheduled
10d40b67-f812-401c-af9c-cd7ddf121849	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	version	v1
6dff2839-6c23-4e00-b718-7fbf2e499902	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	objectType	policy_page
861caeea-466a-47db-a9c1-076fc7de61ac	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	source	MOCK_CONFLUENCE
a2d05cfd-b644-4a9f-ba9f-376ad5c403f5	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	application	payments
f2cab231-b31e-4dd3-a8c1-14003a5fe5ac	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	technology	unknown
d0235001-3afb-4d55-a00e-9b148cb1a90a	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	control	DPSC-GOV-01
ffe3b4c4-2526-4ec3-9d17-2c02c4b981ec	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	framework	DPSC
5adcab70-f23a-48e7-acf2-d9f40ffc014c	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	frameworks	DPSC
a0529efa-20c6-4428-b00c-dde3ec14deb0	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	evidenceType	GENERAL
671130d7-4237-4d26-8744-b46c32d43b9d	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	name	payments_DPSC-GOV-01_GENERAL_20260915_001
6754a776-75af-476c-b827-2b3d2fb961f7	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	sourceTitle	policy_page for DPSC-GOV-01
d8d6667a-b04a-43fe-ab27-941c6d96d0d2	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	collectionMethod	scheduled
2b7445a3-b924-40f1-8420-eae40eaeb63e	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	version	v1
0b819104-aae0-466d-bef3-d1018d19804f	67a16d1b-085b-4faf-8333-d4a8811667d7	technology	postgresql
ec69875f-7603-4efc-9df1-fcd5cd976a71	67a16d1b-085b-4faf-8333-d4a8811667d7	agent	SIM_POSTGRES
ec6e2259-07c5-445f-9628-05abc6b6ccb1	67a16d1b-085b-4faf-8333-d4a8811667d7	collectionMethod	scheduled
79d30982-5044-448a-9ecc-d7862d298042	67a16d1b-085b-4faf-8333-d4a8811667d7	application	mobile-banking
32ed65a0-b7e7-4e70-8c4a-48f05cbb52ce	67a16d1b-085b-4faf-8333-d4a8811667d7	control	DB-TLS-IN-TRANSIT
88c74e04-c1c2-4da3-820c-45684ba74790	67a16d1b-085b-4faf-8333-d4a8811667d7	framework	PCI_DSS
d7987549-8a8c-47ed-b6ac-8f171d40534b	67a16d1b-085b-4faf-8333-d4a8811667d7	frameworks	PCI_DSS,DPSC,ISO27001
5f6c6057-ca31-497f-b9c8-5f10141c8f50	67a16d1b-085b-4faf-8333-d4a8811667d7	evidenceType	DB-CONFIG
1e46bfd5-bbdb-45a8-809f-6e6dba3bad38	67a16d1b-085b-4faf-8333-d4a8811667d7	name	mobile-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001
13ac7696-89a7-404c-90a7-1c53c3595b6f	67a16d1b-085b-4faf-8333-d4a8811667d7	version	v1
3fd72d12-b05d-42e7-b44c-9e235ae92db2	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	technology	postgresql
7c29c48a-3135-46f7-86f3-4baa962cc115	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	agent	SIM_POSTGRES
b9ffa573-feaa-4116-afa8-1ebe819ac15e	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	collectionMethod	scheduled
84c997fe-f0cf-4548-b66a-794e2cfbd2d6	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	application	mobile-banking
11072b42-e043-4ae2-90cf-c2764fd88e05	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	control	DB-AUDIT-LOGGING
f157efbc-515f-4de9-9cd4-a4979c827dec	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	framework	PCI_DSS
9c42f557-c49c-4179-8aad-ac8e80fe0aa9	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	frameworks	PCI_DSS,ISO27001,IS
a367fb6b-fb61-477f-b085-7cf0e4349546	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	evidenceType	DB-CONFIG
909f78ea-6f8a-423b-b4af-f7b7ff77555b	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	name	mobile-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001
bb58492d-0190-4a94-86c7-817ffe2b2e95	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	version	v1
d02420cf-f14e-4e62-93e7-06b6d4383dee	a99ef1f9-2193-4c30-b4b4-743268c8003e	technology	postgresql
af2aaa3e-4d3b-4cda-aac7-4899567a79fe	a99ef1f9-2193-4c30-b4b4-743268c8003e	agent	SIM_POSTGRES
79253789-4cd7-4f52-9ad5-b09d48bdc5e1	a99ef1f9-2193-4c30-b4b4-743268c8003e	collectionMethod	scheduled
c525f336-e65f-4dd5-8435-bdcdd649bb09	a99ef1f9-2193-4c30-b4b4-743268c8003e	application	mobile-banking
ee86c37e-530f-46af-a671-3f7d8bbd2739	a99ef1f9-2193-4c30-b4b4-743268c8003e	control	DB-PASSWORD-STORAGE
7372661c-0f0f-418b-a884-4c824ee1a165	a99ef1f9-2193-4c30-b4b4-743268c8003e	framework	C-SITE
c6e8b7e5-8975-4ac4-a24f-b9cfb14c51da	a99ef1f9-2193-4c30-b4b4-743268c8003e	frameworks	C-SITE,PCI_DSS,ISO27001
17c43a33-a819-40f7-a8e5-7f519c79587d	a99ef1f9-2193-4c30-b4b4-743268c8003e	evidenceType	DB-CONFIG
32fe9c85-2feb-4a1e-9950-21eaf11ce288	a99ef1f9-2193-4c30-b4b4-743268c8003e	name	mobile-banking_DB-PASSWORD-STORAGE_DB-CONFIG_20260915_001
ea980a39-d986-4a9d-9d54-1ea395914d5a	a99ef1f9-2193-4c30-b4b4-743268c8003e	version	v1
09ac2dec-b52a-4be8-adf8-a867b744b37d	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	technology	postgresql
221ba04e-b52f-4a6b-b596-3a9d9f3b1423	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	agent	SIM_POSTGRES
0ffa281e-911c-4636-bd6e-c89016ace353	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	collectionMethod	scheduled
9099a15e-e679-4692-9a7b-0800e4ca1905	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	application	mobile-banking
362d4db7-d82c-4499-85c9-6c24a6830f60	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	control	DB-AUTH-NO-TRUST
2cde789a-83d2-4946-bc56-059af8185b91	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	framework	C-SITE
c61f96b2-7227-4ea3-8c77-cf41e57a9e95	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	frameworks	C-SITE,PCI_DSS
aecb45a4-4c10-4802-accd-87cf135686ef	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	evidenceType	DB-CONFIG
3460fe21-d44e-4e4b-9c20-85614522f9ca	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	name	mobile-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001
97e4ff40-b096-4941-9cb9-830e45ae0241	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	version	v1
3b2c466e-ba35-4c78-ae5f-9c527962636b	bbe54c6f-2024-4781-beee-7bb1c4f10234	technology	postgresql
7e693fe5-d3f9-43d5-88da-12373d6ba026	bbe54c6f-2024-4781-beee-7bb1c4f10234	agent	SIM_POSTGRES
de4819ce-13ce-404d-8d53-b13f905bf2fb	bbe54c6f-2024-4781-beee-7bb1c4f10234	collectionMethod	scheduled
985ef579-9979-4c86-8b22-511598dd8447	bbe54c6f-2024-4781-beee-7bb1c4f10234	application	net-banking
91d6931a-67d7-419f-8993-e0d5d25b15d6	bbe54c6f-2024-4781-beee-7bb1c4f10234	control	DB-TLS-IN-TRANSIT
6616f0df-ca5f-4916-8a2f-282e98f8a853	bbe54c6f-2024-4781-beee-7bb1c4f10234	framework	PCI_DSS
033cb254-6237-41d3-a9c7-039926708ddf	bbe54c6f-2024-4781-beee-7bb1c4f10234	frameworks	PCI_DSS,DPSC,ISO27001
ffb35782-5687-41ef-8f52-fafed7a5f9d8	bbe54c6f-2024-4781-beee-7bb1c4f10234	evidenceType	DB-CONFIG
8a1c0937-6e42-4dad-b7c9-fa420f0158ed	bbe54c6f-2024-4781-beee-7bb1c4f10234	name	net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001
fdba56e6-eb71-4af9-bcaf-0c95a5c9eaf4	bbe54c6f-2024-4781-beee-7bb1c4f10234	version	v1
1289bb2a-71b4-4012-ac90-8ec3310e8261	a5667964-1027-4f26-b6d5-3234f80dd54f	technology	postgresql
4370f991-20a1-4ac0-95eb-ac84cd617e71	a5667964-1027-4f26-b6d5-3234f80dd54f	agent	SIM_POSTGRES
11af9977-9a7f-40ae-baf1-c824d0edbbb5	a5667964-1027-4f26-b6d5-3234f80dd54f	collectionMethod	scheduled
32d17e5d-685a-40c4-be3e-18a876ff28d0	a5667964-1027-4f26-b6d5-3234f80dd54f	application	net-banking
aba70012-dc40-492b-817d-1c23e8320dd0	a5667964-1027-4f26-b6d5-3234f80dd54f	control	DB-AUDIT-LOGGING
e0fa1910-ecf1-47e7-aea8-838e08a335b3	a5667964-1027-4f26-b6d5-3234f80dd54f	framework	PCI_DSS
2876b05b-2fec-4328-a8b9-dad8e91a6dcd	a5667964-1027-4f26-b6d5-3234f80dd54f	frameworks	PCI_DSS,ISO27001,IS
65fe5c09-0659-42f1-9aae-81fcc020ed08	a5667964-1027-4f26-b6d5-3234f80dd54f	evidenceType	DB-CONFIG
7b14ef18-5065-4ab4-a99b-9135564a41b4	a5667964-1027-4f26-b6d5-3234f80dd54f	name	net-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001
68ab743a-a653-4278-a703-220926e2c5d0	a5667964-1027-4f26-b6d5-3234f80dd54f	version	v1
5a3538e8-db6d-41d0-bf89-25c7ddc2b973	d9886a1d-a255-4725-935d-d62cf11b8766	technology	postgresql
f2c58c4e-3d2e-4d4b-96ec-696bca436985	d9886a1d-a255-4725-935d-d62cf11b8766	agent	SIM_POSTGRES
25c51570-d68e-4dee-b407-ddc6974c8a7a	d9886a1d-a255-4725-935d-d62cf11b8766	collectionMethod	scheduled
89c3a9a0-16a3-4334-b5f8-37b797472a9d	d9886a1d-a255-4725-935d-d62cf11b8766	application	net-banking
97c750b7-8667-4eb4-af36-e2de2da21526	d9886a1d-a255-4725-935d-d62cf11b8766	control	DB-PASSWORD-STORAGE
89e6cb42-8ce9-4f36-aba1-97fbca695c20	d9886a1d-a255-4725-935d-d62cf11b8766	framework	C-SITE
bd5a63f9-06a3-4784-a3e4-c94a71568f0f	d9886a1d-a255-4725-935d-d62cf11b8766	frameworks	C-SITE,PCI_DSS,ISO27001
73f2170d-76b1-4323-949a-dae71eea98b0	d9886a1d-a255-4725-935d-d62cf11b8766	evidenceType	DB-CONFIG
ed1b7bf5-5dd9-4315-adc2-b31fb88b8296	d9886a1d-a255-4725-935d-d62cf11b8766	name	net-banking_DB-PASSWORD-STORAGE_DB-CONFIG_20260915_001
40a93299-f487-4664-87e1-f594bf0d053b	d9886a1d-a255-4725-935d-d62cf11b8766	version	v1
e00b6431-6117-4a9c-9829-3e2cffb6c4b1	ba85be2a-8251-4dac-bb80-4655f5a3979a	technology	postgresql
c1db324d-e033-4dd6-9b8f-88a22a47cdd6	ba85be2a-8251-4dac-bb80-4655f5a3979a	agent	SIM_POSTGRES
ad65a681-a1f4-4c28-8084-f0749d1ad8da	ba85be2a-8251-4dac-bb80-4655f5a3979a	collectionMethod	scheduled
458fa7ca-0a4f-4247-8687-36e519b37e65	ba85be2a-8251-4dac-bb80-4655f5a3979a	application	net-banking
8eb1429d-52ba-4f8c-b55c-0f879257a459	ba85be2a-8251-4dac-bb80-4655f5a3979a	control	DB-AUTH-NO-TRUST
6e52b189-7579-46d9-b290-7efc099994d5	ba85be2a-8251-4dac-bb80-4655f5a3979a	framework	C-SITE
d0d8dbc4-5da1-43c9-9642-1d88fef70570	ba85be2a-8251-4dac-bb80-4655f5a3979a	frameworks	C-SITE,PCI_DSS
c68cbba5-bb7a-449f-9bbd-bb55b50e05d2	ba85be2a-8251-4dac-bb80-4655f5a3979a	evidenceType	DB-CONFIG
424e18c2-a937-4520-9d37-6bbbb91474b9	ba85be2a-8251-4dac-bb80-4655f5a3979a	name	net-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001
29190b8b-4af3-43ca-aa44-95ed1c7c3c16	ba85be2a-8251-4dac-bb80-4655f5a3979a	version	v1
ae507c45-1e67-4aad-b7cd-f5e2ac356fe6	252a8116-32db-4c3f-8bc4-0fe0eee53653	technology	postgresql
2b197fad-c43f-4aa0-81a6-7fd12fa3b150	252a8116-32db-4c3f-8bc4-0fe0eee53653	agent	SIM_POSTGRES
9e223383-6017-4f7c-955b-9d94295cc1ea	252a8116-32db-4c3f-8bc4-0fe0eee53653	collectionMethod	scheduled
7fb2c281-8e99-4deb-9fcc-e3393e7ee85a	252a8116-32db-4c3f-8bc4-0fe0eee53653	application	payments
6fcbad2d-2c23-445c-9212-e00c0c347b1d	252a8116-32db-4c3f-8bc4-0fe0eee53653	control	DB-TLS-IN-TRANSIT
4d64a28d-b147-4bf6-9bde-06b7badb0fdc	252a8116-32db-4c3f-8bc4-0fe0eee53653	framework	PCI_DSS
e3d45833-52d6-4b27-9907-c8da834b34e8	252a8116-32db-4c3f-8bc4-0fe0eee53653	frameworks	PCI_DSS,DPSC,ISO27001
768d38f7-170f-4fc8-9a67-3a509d326b26	252a8116-32db-4c3f-8bc4-0fe0eee53653	evidenceType	DB-CONFIG
b0a0e2f9-0ae7-4ed0-9960-4e18eb355c0e	252a8116-32db-4c3f-8bc4-0fe0eee53653	name	payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001
61f0b6c4-36cd-4f31-bad5-ffff5cefa504	252a8116-32db-4c3f-8bc4-0fe0eee53653	version	v1
b90af906-8cb9-4bc8-a8bb-d5f5adac0ccf	3ee6901d-e417-471c-a00e-0eb95e2b8491	technology	postgresql
e920e0af-a173-472f-bd45-8a863b3d9567	3ee6901d-e417-471c-a00e-0eb95e2b8491	agent	SIM_POSTGRES
4943b246-6b71-4561-ae97-2e3e7f022917	3ee6901d-e417-471c-a00e-0eb95e2b8491	collectionMethod	scheduled
f8ddf3c5-7c71-4cae-8624-2096a00446f3	3ee6901d-e417-471c-a00e-0eb95e2b8491	application	payments
f9445410-2660-436b-b380-c3e6ed54f1ff	3ee6901d-e417-471c-a00e-0eb95e2b8491	control	DB-AUDIT-LOGGING
dba3797e-0f2a-4437-b4f0-758de5189468	3ee6901d-e417-471c-a00e-0eb95e2b8491	framework	PCI_DSS
af06f995-333b-4457-908a-1551f3d8b88e	3ee6901d-e417-471c-a00e-0eb95e2b8491	frameworks	PCI_DSS,ISO27001,IS
895a0012-8d89-4e2a-a81c-18c219340abb	3ee6901d-e417-471c-a00e-0eb95e2b8491	evidenceType	DB-CONFIG
7bafd397-698b-4786-93f1-aa3d21f65d42	3ee6901d-e417-471c-a00e-0eb95e2b8491	name	payments_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001
b681c99c-f2a9-47cd-82f2-cbce0e83f5ba	3ee6901d-e417-471c-a00e-0eb95e2b8491	version	v1
e5f10660-ca20-4fca-876c-4a3c94db5744	897683f8-35a9-4af6-b103-8f073087e332	technology	postgresql
38d5cfdb-fe8c-495d-b1e7-cb8d98eae636	897683f8-35a9-4af6-b103-8f073087e332	agent	SIM_POSTGRES
dccd6422-6a9f-48b9-ab49-752cf27c677e	897683f8-35a9-4af6-b103-8f073087e332	collectionMethod	scheduled
4ba24fe9-25ef-465c-bdfb-4a8df6bdc9d4	897683f8-35a9-4af6-b103-8f073087e332	application	payments
77038f94-2911-480e-b125-0c220c5a82c2	897683f8-35a9-4af6-b103-8f073087e332	control	DB-PASSWORD-STORAGE
6c3fc6f4-14fc-4044-be06-23e95e115adf	897683f8-35a9-4af6-b103-8f073087e332	framework	C-SITE
82d92ba8-1227-4937-ae80-93a313209623	897683f8-35a9-4af6-b103-8f073087e332	frameworks	C-SITE,PCI_DSS,ISO27001
eac9916a-984f-43cd-9214-af2e83e0b05a	897683f8-35a9-4af6-b103-8f073087e332	evidenceType	DB-CONFIG
635eb839-c9df-442f-a59a-7659070c0970	897683f8-35a9-4af6-b103-8f073087e332	name	payments_DB-PASSWORD-STORAGE_DB-CONFIG_20260915_001
8a671318-27f1-4107-8482-ef2e34dd301b	897683f8-35a9-4af6-b103-8f073087e332	version	v1
dc44b544-6b22-45e8-9852-434b24b45684	bff6261f-c689-4dee-95a9-281451058e08	technology	postgresql
04f97264-14fe-4ba9-bc42-8fb8e91aefb0	bff6261f-c689-4dee-95a9-281451058e08	agent	SIM_POSTGRES
bb590878-2ac4-47f9-a3fa-c0bad369bcd1	bff6261f-c689-4dee-95a9-281451058e08	collectionMethod	scheduled
fe24317a-3b27-4d3e-8316-319cadf9184c	bff6261f-c689-4dee-95a9-281451058e08	application	payments
106504d5-8945-409f-951e-ddb3427589e7	bff6261f-c689-4dee-95a9-281451058e08	control	DB-AUTH-NO-TRUST
be74fffa-f6bf-4ec7-893d-e841f27fe4e0	bff6261f-c689-4dee-95a9-281451058e08	framework	C-SITE
09d87728-db9c-4c58-9f2b-f7519f359148	bff6261f-c689-4dee-95a9-281451058e08	frameworks	C-SITE,PCI_DSS
884d993e-bd50-4f17-a685-4afba878c721	bff6261f-c689-4dee-95a9-281451058e08	evidenceType	DB-CONFIG
2981701f-f565-4425-b258-d3e68af0ffb1	bff6261f-c689-4dee-95a9-281451058e08	name	payments_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001
4fe69902-d025-494f-bee5-e0d13b2e3afd	bff6261f-c689-4dee-95a9-281451058e08	version	v1
38256098-ae11-491a-ac86-2f2dd94f1f4b	96ce1593-ee52-455f-ad08-64ec36bbff02	technology	mysql
0e17fb3e-34e8-404e-9d6f-55694a4e416b	96ce1593-ee52-455f-ad08-64ec36bbff02	agent	SIM_MYSQL
d4f55e1e-d84a-4537-990e-c464c77a4661	96ce1593-ee52-455f-ad08-64ec36bbff02	collectionMethod	scheduled
04cae1f9-b664-438a-9e49-17d7e8f01d33	96ce1593-ee52-455f-ad08-64ec36bbff02	application	mobile-banking
e74a8061-045e-485a-9de2-a08d000ed504	96ce1593-ee52-455f-ad08-64ec36bbff02	control	DB-TLS-IN-TRANSIT
716e2d18-d364-4978-8d01-510458b72fc3	96ce1593-ee52-455f-ad08-64ec36bbff02	framework	PCI_DSS
4e0e3ba8-243c-4abd-8e07-e2bf6e15f69e	96ce1593-ee52-455f-ad08-64ec36bbff02	frameworks	PCI_DSS,DPSC,ISO27001
811736d8-e70e-436a-96ca-73a74090f603	96ce1593-ee52-455f-ad08-64ec36bbff02	evidenceType	DB-CONFIG
1b3fd781-bab3-4137-beae-8a44997efaf5	96ce1593-ee52-455f-ad08-64ec36bbff02	name	mobile-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001
c9738309-fe39-4f19-8533-bdf5dd0bdf1b	96ce1593-ee52-455f-ad08-64ec36bbff02	version	v1
e9c46f2d-f9ba-4f6c-96d8-d9c25f883b5a	62b53770-56b0-4556-9e8a-bcef611a5588	technology	mysql
bf23c15f-0c16-4b66-9121-236985b6f7a2	62b53770-56b0-4556-9e8a-bcef611a5588	agent	SIM_MYSQL
91456e67-5a13-4449-b618-29a87cf74bb5	62b53770-56b0-4556-9e8a-bcef611a5588	collectionMethod	scheduled
8f2b7a3b-ffea-45bb-89cc-232c04cad1d5	62b53770-56b0-4556-9e8a-bcef611a5588	application	mobile-banking
4af72959-f6c5-4e72-aee8-3f84f9c7bbcf	62b53770-56b0-4556-9e8a-bcef611a5588	control	DB-AUDIT-LOGGING
9df0a985-3661-4e4b-b808-2bf546f48b85	62b53770-56b0-4556-9e8a-bcef611a5588	framework	PCI_DSS
97cab741-cc6e-4064-9033-80b0e1283012	62b53770-56b0-4556-9e8a-bcef611a5588	frameworks	PCI_DSS,ISO27001,IS
6013763c-6bea-4e8f-b327-006eaefb6197	62b53770-56b0-4556-9e8a-bcef611a5588	evidenceType	DB-CONFIG
1f3d5870-be7f-4fb6-950b-cc3f23e709ec	62b53770-56b0-4556-9e8a-bcef611a5588	name	mobile-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001
10e48553-00ad-46bb-a80d-0622180900d1	62b53770-56b0-4556-9e8a-bcef611a5588	version	v1
e7f87960-48d3-46b0-a880-e01273525f72	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	technology	mysql
2c0a17c8-57ae-4fa6-a474-365bea43ce13	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	agent	SIM_MYSQL
c0d3642d-6458-49bb-b9d5-7cc45b4feb1c	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	collectionMethod	scheduled
0dfbdca0-505c-4ffa-88ed-9c9ed5342e31	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	application	mobile-banking
5528be68-486e-4b3a-817e-d77c1260d840	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	control	DB-AUTH-NO-TRUST
c402f774-2218-4823-9990-234c5614a460	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	framework	C-SITE
ab62f5cf-e9bd-4030-88d2-de4851b432d6	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	frameworks	C-SITE,PCI_DSS
757daf8f-fc3f-4c30-b71e-b6cbbb8740c1	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	evidenceType	DB-CONFIG
84feb43c-0565-45c4-b599-4744d5d7b524	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	name	mobile-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001
e751c88d-065c-4543-b508-c2736c1dd653	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	version	v1
af4ed02c-d641-409f-b142-464b4be3e257	3bb02091-449e-497e-9317-427cacc54837	technology	mysql
3d4f9b1d-0302-42d7-aa54-78729b888460	3bb02091-449e-497e-9317-427cacc54837	agent	SIM_MYSQL
eefc6357-2130-419d-82bc-75e48213577c	3bb02091-449e-497e-9317-427cacc54837	collectionMethod	scheduled
a5467b96-45d4-4c3f-82fc-c3e4bc6400a9	3bb02091-449e-497e-9317-427cacc54837	application	net-banking
a1ff714f-948a-4a0c-863c-a3712623564f	3bb02091-449e-497e-9317-427cacc54837	control	DB-TLS-IN-TRANSIT
8e144bbf-9746-4f90-8f31-e86ecd0bf8d1	3bb02091-449e-497e-9317-427cacc54837	framework	PCI_DSS
3de2c100-2df3-48d0-8948-d1f2b58d2b19	3bb02091-449e-497e-9317-427cacc54837	frameworks	PCI_DSS,DPSC,ISO27001
baefe74f-51f9-49bd-b24f-3efa984035f8	3bb02091-449e-497e-9317-427cacc54837	evidenceType	DB-CONFIG
95e0b5f7-ce3d-4d1b-b460-fd9b5fab4f3c	3bb02091-449e-497e-9317-427cacc54837	name	net-banking_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001
00d2c7b4-471b-4319-88c9-abe457949c69	3bb02091-449e-497e-9317-427cacc54837	version	v1
9ed8c11e-053f-46e5-aefe-3404a5700baa	d5d0f39b-6c07-4161-a96c-94dc258702df	technology	mysql
dc4dd7d8-7529-4f92-a95c-5182c5f22e00	d5d0f39b-6c07-4161-a96c-94dc258702df	agent	SIM_MYSQL
bd633ec7-b73f-4291-abe4-e29b0a7b7f75	d5d0f39b-6c07-4161-a96c-94dc258702df	collectionMethod	scheduled
c2a8c8fc-7390-4701-8590-2a8d541ce23f	d5d0f39b-6c07-4161-a96c-94dc258702df	application	net-banking
0e5b795f-56ef-40b3-817a-b1c4fd1d4397	d5d0f39b-6c07-4161-a96c-94dc258702df	control	DB-AUDIT-LOGGING
070ed45d-663a-4966-a2b8-c69e8e86f402	d5d0f39b-6c07-4161-a96c-94dc258702df	framework	PCI_DSS
b22eba4e-c51f-4c61-8c9a-41b03604e96f	d5d0f39b-6c07-4161-a96c-94dc258702df	frameworks	PCI_DSS,ISO27001,IS
eeee0fa7-7188-4167-bb6c-9e339b283a38	d5d0f39b-6c07-4161-a96c-94dc258702df	evidenceType	DB-CONFIG
3874f3b3-ccc3-4161-9286-1c364342d736	d5d0f39b-6c07-4161-a96c-94dc258702df	name	net-banking_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001
fa3fed8b-31c5-4b4f-9148-e6d9bccc7aa1	d5d0f39b-6c07-4161-a96c-94dc258702df	version	v1
98ce2bd4-e0f3-4885-a8e8-a93c7ba98eb6	c3c77f08-3579-4394-84b0-8592dd97bbd8	technology	mysql
e8108474-02e6-4a15-9ddf-3a337a497edc	c3c77f08-3579-4394-84b0-8592dd97bbd8	agent	SIM_MYSQL
d34ac155-82c4-4037-84de-c5c87905ffac	c3c77f08-3579-4394-84b0-8592dd97bbd8	collectionMethod	scheduled
5e81dfe4-a3af-4aed-8c4c-c9d7c14c6d48	c3c77f08-3579-4394-84b0-8592dd97bbd8	application	net-banking
edb305c8-0022-45c6-8545-e6a5ef01839f	c3c77f08-3579-4394-84b0-8592dd97bbd8	control	DB-AUTH-NO-TRUST
80b1429f-7eb2-49f3-8759-f3ad38981c69	c3c77f08-3579-4394-84b0-8592dd97bbd8	framework	C-SITE
0cd9c24a-9b87-43bd-9714-d9e4338d72a6	c3c77f08-3579-4394-84b0-8592dd97bbd8	frameworks	C-SITE,PCI_DSS
de44892c-8ba1-481f-bf7b-78db86aa90bf	c3c77f08-3579-4394-84b0-8592dd97bbd8	evidenceType	DB-CONFIG
f7928e9a-68b7-484f-aede-ab371189c7da	c3c77f08-3579-4394-84b0-8592dd97bbd8	name	net-banking_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001
34ca26e0-1b3c-419b-9d9d-1bd5beed113c	c3c77f08-3579-4394-84b0-8592dd97bbd8	version	v1
a2dc0088-a34c-4c92-8334-eaa513030aa9	5ef049e2-85a0-44b1-9e80-d494a5043570	technology	mysql
b88ef171-a61a-4c90-a0fe-cbc142e3ea8d	5ef049e2-85a0-44b1-9e80-d494a5043570	agent	SIM_MYSQL
14c66eb5-5748-4668-8f2b-0e987e79b80a	5ef049e2-85a0-44b1-9e80-d494a5043570	collectionMethod	scheduled
e56e3cd7-152a-4b88-9ee8-536710429002	5ef049e2-85a0-44b1-9e80-d494a5043570	application	payments
6edeca96-c4ed-43d1-9025-d8ff58ecf8ed	5ef049e2-85a0-44b1-9e80-d494a5043570	control	DB-TLS-IN-TRANSIT
d2d833fd-43fe-4b3f-9036-de690fa3c8f7	5ef049e2-85a0-44b1-9e80-d494a5043570	framework	PCI_DSS
890ff3a3-1e9d-4b90-a75c-8dc7b40e984d	5ef049e2-85a0-44b1-9e80-d494a5043570	frameworks	PCI_DSS,DPSC,ISO27001
48202591-f5df-425c-880f-9c5190c3985d	5ef049e2-85a0-44b1-9e80-d494a5043570	evidenceType	DB-CONFIG
9351f76f-889d-44a6-abec-e73d98ee5b88	5ef049e2-85a0-44b1-9e80-d494a5043570	name	payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260915_001
8daf90f2-f0b4-4e46-9bfe-1533199a5d5a	5ef049e2-85a0-44b1-9e80-d494a5043570	version	v1
67deaf92-6a0b-4ab5-935f-18efee3d323b	38f74afa-6f51-46c6-87c9-12929e812362	technology	mysql
47246eae-3ce6-4b75-a787-bf6edbb6aae7	38f74afa-6f51-46c6-87c9-12929e812362	agent	SIM_MYSQL
2bac4fd2-98ca-4ad2-a8ef-7c36fc0a398a	38f74afa-6f51-46c6-87c9-12929e812362	collectionMethod	scheduled
e388c9c0-9f53-479a-a28e-570e70ffcf76	38f74afa-6f51-46c6-87c9-12929e812362	application	payments
98ffd948-be9f-4407-9f44-c83c35cace72	38f74afa-6f51-46c6-87c9-12929e812362	control	DB-AUDIT-LOGGING
875b5a22-4e0d-4410-a90d-809093655978	38f74afa-6f51-46c6-87c9-12929e812362	framework	PCI_DSS
1a0cb244-40d3-4b3e-a1cd-ec5689cc8c7a	38f74afa-6f51-46c6-87c9-12929e812362	frameworks	PCI_DSS,ISO27001,IS
835e1329-b868-4c20-9b37-1c8c64adb624	38f74afa-6f51-46c6-87c9-12929e812362	evidenceType	DB-CONFIG
d2ab2dfd-c66f-40b4-967c-ec1ceb6a52ae	38f74afa-6f51-46c6-87c9-12929e812362	name	payments_DB-AUDIT-LOGGING_DB-CONFIG_20260915_001
b9618082-6964-4c2f-9183-8b551415e8ca	38f74afa-6f51-46c6-87c9-12929e812362	version	v1
cf291282-6b6a-4563-95d4-d81c110ac8f4	d452f3b9-ce2c-410a-968b-4dd45948eee6	technology	mysql
48b4cc20-d6c2-4b9c-885b-cba6fd888b91	d452f3b9-ce2c-410a-968b-4dd45948eee6	agent	SIM_MYSQL
8e52ef24-c14e-420b-8802-ab3a9bdc376a	d452f3b9-ce2c-410a-968b-4dd45948eee6	collectionMethod	scheduled
427460f9-4790-4ad6-9cf0-9fcf9f8f2016	d452f3b9-ce2c-410a-968b-4dd45948eee6	application	payments
29cb40cd-757a-4894-86cb-21c0ca341d4b	d452f3b9-ce2c-410a-968b-4dd45948eee6	control	DB-AUTH-NO-TRUST
e54e25a7-92d7-4e5b-99a7-ce1ebbdf20a0	d452f3b9-ce2c-410a-968b-4dd45948eee6	framework	C-SITE
b695c0dd-7615-4f18-86eb-5050738e970f	d452f3b9-ce2c-410a-968b-4dd45948eee6	frameworks	C-SITE,PCI_DSS
b5db6a5f-0026-4604-a07c-1769458978f9	d452f3b9-ce2c-410a-968b-4dd45948eee6	evidenceType	DB-CONFIG
c1192237-4d1f-4b40-8ad4-bda584b094be	d452f3b9-ce2c-410a-968b-4dd45948eee6	name	payments_DB-AUTH-NO-TRUST_DB-CONFIG_20260915_001
6619cfe7-1ed4-412d-b4d2-82ea698a8e75	d452f3b9-ce2c-410a-968b-4dd45948eee6	version	v1
fe2083aa-2478-49a5-8bbf-70a488304bc4	2874aafc-3723-40bc-b524-229f1b2d7b8c	technology	nginx
5d222fbd-a4b3-4363-9e51-92d1e9d5662a	2874aafc-3723-40bc-b524-229f1b2d7b8c	agent	SIM_NGINX
65fdde5e-e022-4892-9e7e-6dbb361a5438	2874aafc-3723-40bc-b524-229f1b2d7b8c	collectionMethod	scheduled
ae8ad757-4593-4c82-9b60-465d3e4f647a	2874aafc-3723-40bc-b524-229f1b2d7b8c	application	mobile-banking
e0f4e02e-86f0-4fb6-a796-6e4ac24e6c4c	2874aafc-3723-40bc-b524-229f1b2d7b8c	control	MW-TLS-VERSION
fb013e53-76a5-4ce4-b9a9-91458acb9e30	2874aafc-3723-40bc-b524-229f1b2d7b8c	framework	PCI_DSS
5428f226-399d-4826-8c55-afe851054622	2874aafc-3723-40bc-b524-229f1b2d7b8c	frameworks	PCI_DSS,DPSC,ISG
57a2660d-2ec2-49de-bd0e-f29a95bef67f	2874aafc-3723-40bc-b524-229f1b2d7b8c	evidenceType	MIDDLEWARE-CONFIG
01b039c0-72e2-4401-bbb5-aa273139e804	2874aafc-3723-40bc-b524-229f1b2d7b8c	name	mobile-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001
12461a3f-07db-4e81-a842-2c63ae0467fd	2874aafc-3723-40bc-b524-229f1b2d7b8c	version	v1
d52ee9e2-eb41-4d90-864c-a592ee37fb21	86a41980-cbd9-478c-a4bc-371bc159f431	technology	nginx
d734967a-1806-4437-bf87-bba0b29ae751	86a41980-cbd9-478c-a4bc-371bc159f431	agent	SIM_NGINX
2c1cd694-8c2b-4a87-bafa-0aa774c844cd	86a41980-cbd9-478c-a4bc-371bc159f431	collectionMethod	scheduled
4bfb8128-578d-4f72-a893-50d54a034899	86a41980-cbd9-478c-a4bc-371bc159f431	application	mobile-banking
dea3a0c3-a5ad-445e-b9f9-0f36d28d4262	86a41980-cbd9-478c-a4bc-371bc159f431	control	MW-BANNER-SUPPRESSION
9905c6c5-7bda-4b3f-a36e-32942e75bc47	86a41980-cbd9-478c-a4bc-371bc159f431	framework	C-SITE
7f54d25f-d9f1-4f4e-82f3-25f73b66da0f	86a41980-cbd9-478c-a4bc-371bc159f431	frameworks	C-SITE,ISG
d7d46000-56e2-4f44-85a4-d7c438a5a03b	86a41980-cbd9-478c-a4bc-371bc159f431	evidenceType	MIDDLEWARE-CONFIG
4ef0db33-5b84-4b6b-8b1b-8966217e418b	86a41980-cbd9-478c-a4bc-371bc159f431	name	mobile-banking_MW-BANNER-SUPPRESSION_MIDDLEWARE-CONFIG_20260915_001
85dc4dfa-447e-4d2a-8167-703a0b9941c6	86a41980-cbd9-478c-a4bc-371bc159f431	version	v1
482ffd8a-14d1-44a1-8f8b-e5217af3ccc7	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	technology	nginx
f3d27d00-96fa-49a2-8102-4e4b087c64cb	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	agent	SIM_NGINX
62ceea37-d4db-42f4-a18b-ef8b33e67c21	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	collectionMethod	scheduled
a436bc14-f480-4075-9b61-de997a5fbcb3	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	application	mobile-banking
17b01073-6b78-4da4-82a4-b15ae08153c1	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	control	MW-HSTS
1dfd2db3-1171-4d06-b593-4bb6a0e71f61	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	framework	DPSC
edc1d69e-43a8-49aa-aa9c-6b45f4373cbd	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	frameworks	DPSC,ISG,ISO27001
75f81176-6e65-4e2f-99e4-f425279e674e	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	evidenceType	MIDDLEWARE-CONFIG
140049ac-fcea-4a8c-8095-d940826171ef	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	name	mobile-banking_MW-HSTS_MIDDLEWARE-CONFIG_20260915_001
eb337d99-e304-460e-9532-088de619a362	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	version	v1
a6200913-0608-43e7-a057-f54c9e742004	b0946cad-efb7-4bee-bb25-1626fe14c229	technology	nginx
906bc3e1-ac0a-4981-909e-2324a99ff498	b0946cad-efb7-4bee-bb25-1626fe14c229	agent	SIM_NGINX
5dc07faf-67f5-4738-98cf-d66fc0af6c57	b0946cad-efb7-4bee-bb25-1626fe14c229	collectionMethod	scheduled
a38c72d6-c2da-4cfa-8473-7525ad3f82a4	b0946cad-efb7-4bee-bb25-1626fe14c229	application	net-banking
31058dbe-6348-471e-b69a-d9efce2a30e6	b0946cad-efb7-4bee-bb25-1626fe14c229	control	MW-TLS-VERSION
811d7550-3171-4378-8061-4a2fe4a2e559	b0946cad-efb7-4bee-bb25-1626fe14c229	framework	PCI_DSS
05aa49e3-109d-4c1a-b47a-239fe72c339b	b0946cad-efb7-4bee-bb25-1626fe14c229	frameworks	PCI_DSS,DPSC,ISG
a1f17427-2b8f-431c-a2cf-c6e083a9167e	b0946cad-efb7-4bee-bb25-1626fe14c229	evidenceType	MIDDLEWARE-CONFIG
5fcde995-2ca9-439e-ab23-8ace05a53cc6	b0946cad-efb7-4bee-bb25-1626fe14c229	name	net-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001
31c68d93-90de-4dbd-adf1-d86c80bb00d9	b0946cad-efb7-4bee-bb25-1626fe14c229	version	v1
639ed5d2-afa6-4725-9858-f98e223f7a13	68b6c810-9ce8-4c96-a653-133a4042efd6	technology	nginx
845a68ec-c62c-4549-8f62-aedcac9e0830	68b6c810-9ce8-4c96-a653-133a4042efd6	agent	SIM_NGINX
8b9c7f2b-118f-447c-a6c1-a8bea92f4471	68b6c810-9ce8-4c96-a653-133a4042efd6	collectionMethod	scheduled
9083537d-8d0f-469b-9823-839d6487a1f6	68b6c810-9ce8-4c96-a653-133a4042efd6	application	net-banking
9d933721-0fbd-433c-8715-870a74285c2a	68b6c810-9ce8-4c96-a653-133a4042efd6	control	MW-BANNER-SUPPRESSION
3ae087d5-4caf-4f4f-a380-ec8d8f5b20d1	68b6c810-9ce8-4c96-a653-133a4042efd6	framework	C-SITE
3d695f0c-69e4-478a-a9e3-f61ad355ff6b	68b6c810-9ce8-4c96-a653-133a4042efd6	frameworks	C-SITE,ISG
b3d6d3bb-ceb1-4341-862a-1de9bc33038c	68b6c810-9ce8-4c96-a653-133a4042efd6	evidenceType	MIDDLEWARE-CONFIG
dbe7a926-0b9f-4f4a-a10a-1f5eea4eb3a6	68b6c810-9ce8-4c96-a653-133a4042efd6	name	net-banking_MW-BANNER-SUPPRESSION_MIDDLEWARE-CONFIG_20260915_001
9439697f-3386-4d45-8b2e-22195508df04	68b6c810-9ce8-4c96-a653-133a4042efd6	version	v1
0188de01-5fef-445b-ae8a-9d43026eda61	6881638e-3f67-4cc8-a178-e67cd76a90bc	technology	nginx
1b1571ea-9c41-4571-b4ad-1455aa704cf8	6881638e-3f67-4cc8-a178-e67cd76a90bc	agent	SIM_NGINX
259bd183-8519-42e7-a28f-a0721cd67ac6	6881638e-3f67-4cc8-a178-e67cd76a90bc	collectionMethod	scheduled
f58aa3f8-058b-4d0c-8854-291de8074fee	6881638e-3f67-4cc8-a178-e67cd76a90bc	application	net-banking
bcdb1977-8374-48ef-aa23-9465f8a9c79a	6881638e-3f67-4cc8-a178-e67cd76a90bc	control	MW-HSTS
8b902a29-6458-4d02-8432-62e5060b778b	6881638e-3f67-4cc8-a178-e67cd76a90bc	framework	DPSC
d2532047-f783-44c9-9ce4-de6571e3a675	6881638e-3f67-4cc8-a178-e67cd76a90bc	frameworks	DPSC,ISG,ISO27001
9d0453fe-7e37-48df-92e5-b044c822cb96	6881638e-3f67-4cc8-a178-e67cd76a90bc	evidenceType	MIDDLEWARE-CONFIG
5df89716-4973-4669-bada-bdc46f8b4cb2	6881638e-3f67-4cc8-a178-e67cd76a90bc	name	net-banking_MW-HSTS_MIDDLEWARE-CONFIG_20260915_001
87ec6c84-c9c0-448c-921f-09b18686d7a3	6881638e-3f67-4cc8-a178-e67cd76a90bc	version	v1
63f7850e-1c1e-46e3-bfcc-f70336717644	49d5d6ba-7459-4bcf-901a-5424e414a32a	technology	nginx
1885002f-c6ce-46e5-b332-f425e7489f51	49d5d6ba-7459-4bcf-901a-5424e414a32a	agent	SIM_NGINX
db9e3c8b-806c-4aa8-b1cc-0f9fdaf45b1e	49d5d6ba-7459-4bcf-901a-5424e414a32a	collectionMethod	scheduled
6ecd0af7-897e-413e-a8ed-c90077478ea0	49d5d6ba-7459-4bcf-901a-5424e414a32a	application	payments
c5976068-ee95-418b-84a6-0919a101405e	49d5d6ba-7459-4bcf-901a-5424e414a32a	control	MW-TLS-VERSION
42fe0798-74c8-4d58-9983-0e0f457c159b	49d5d6ba-7459-4bcf-901a-5424e414a32a	framework	PCI_DSS
06cd089e-05a9-4f1f-8726-55f4c65e45b1	49d5d6ba-7459-4bcf-901a-5424e414a32a	frameworks	PCI_DSS,DPSC,ISG
eaa33a12-19db-47df-b5a6-948c87b267c6	49d5d6ba-7459-4bcf-901a-5424e414a32a	evidenceType	MIDDLEWARE-CONFIG
40c912c4-4465-4c62-bb39-d2a926e3c92b	49d5d6ba-7459-4bcf-901a-5424e414a32a	name	payments_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001
41872ec2-8ae7-44ed-9fa3-bc703de4f598	49d5d6ba-7459-4bcf-901a-5424e414a32a	version	v1
778b6327-a244-4b5e-bee3-d8bbf5bc7e6a	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	technology	nginx
dd99d2eb-a888-45d9-a6eb-2ab0dbee1f10	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	agent	SIM_NGINX
df3dc9f8-be00-4655-98a5-efc3bf434522	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	collectionMethod	scheduled
e13e3efa-519a-41ba-8021-aabb357bfff2	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	application	payments
32076d96-8134-45f3-9e67-4b700829bc4a	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	control	MW-BANNER-SUPPRESSION
abb471a0-0a3a-402b-8750-042e8704e920	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	framework	C-SITE
73ec44af-83cd-4ad9-b974-f5f9b0457bef	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	frameworks	C-SITE,ISG
7b0e198c-133d-4c54-9da5-0a7879114d58	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	evidenceType	MIDDLEWARE-CONFIG
1b7c4150-f6a8-449b-8760-2e81be42401e	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	name	payments_MW-BANNER-SUPPRESSION_MIDDLEWARE-CONFIG_20260915_001
85115a99-f1bc-4482-8add-43af3a27bcc9	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	version	v1
a0c2a800-35ea-4989-956e-27b88860a54a	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	technology	nginx
66b578d8-c9f5-41ac-8e56-114a8a3d7fe5	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	agent	SIM_NGINX
a193466b-57ec-459c-aaa6-a0e574df9d9b	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	collectionMethod	scheduled
ebe5699a-f8ad-4245-ae6e-d553cb2b209d	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	application	payments
34ead043-58b5-4247-8921-1aa42aefdb8e	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	control	MW-HSTS
29dd53b7-f081-4280-878d-cd62646f8403	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	framework	DPSC
985cc434-b585-4495-9140-5bebaf0bdc5c	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	frameworks	DPSC,ISG,ISO27001
16721ee6-ee28-413c-a4fb-f0f66fe8805a	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	evidenceType	MIDDLEWARE-CONFIG
7cea05c9-a956-4dda-aa8f-0f0e582cbe59	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	name	payments_MW-HSTS_MIDDLEWARE-CONFIG_20260915_001
206d0a5e-5ef8-4f6c-93da-f155a49a5599	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	version	v1
9bc66c1f-2855-4215-b3d0-66fbf23353bc	cc6d26ef-a350-4d32-9d2a-39d180443f44	technology	tomcat
a4b82a7b-718d-4f86-8c84-1457e53d9842	cc6d26ef-a350-4d32-9d2a-39d180443f44	agent	SIM_TOMCAT
3222c4ae-4b51-4bde-b95b-2a8a61e0c4a4	cc6d26ef-a350-4d32-9d2a-39d180443f44	collectionMethod	scheduled
2a6770a0-4cf6-4376-a8f3-b560e71ca418	cc6d26ef-a350-4d32-9d2a-39d180443f44	application	mobile-banking
7a6580b5-a3f7-4b49-b77a-80e716790c82	cc6d26ef-a350-4d32-9d2a-39d180443f44	control	MW-TLS-VERSION
e225e0a8-9264-4b49-b0d7-d8ed03e7da71	cc6d26ef-a350-4d32-9d2a-39d180443f44	framework	PCI_DSS
d8849477-6e79-4b43-9ca2-d9cd05bcb5d6	cc6d26ef-a350-4d32-9d2a-39d180443f44	frameworks	PCI_DSS,DPSC,ISG
f2cf01ff-cc57-41c1-87a0-a7fb3f69cfba	cc6d26ef-a350-4d32-9d2a-39d180443f44	evidenceType	MIDDLEWARE-CONFIG
b34fd5d0-fcf5-4471-9fb5-16049e6dca7c	cc6d26ef-a350-4d32-9d2a-39d180443f44	name	mobile-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001
5813799b-0aea-4573-b8e2-4b8420874902	cc6d26ef-a350-4d32-9d2a-39d180443f44	version	v1
325909b8-7ff4-446f-8b6e-a254995e8fa3	0b589896-b23e-452f-bb27-cc5088dadf8d	technology	tomcat
b02e8a78-2ac1-40ae-8e31-a2f884a9a226	0b589896-b23e-452f-bb27-cc5088dadf8d	agent	SIM_TOMCAT
e7bc1533-5d45-471e-a5f5-3c7f74b41110	0b589896-b23e-452f-bb27-cc5088dadf8d	collectionMethod	scheduled
cd1ddcf1-4e4e-49a9-889c-462a31e9f158	0b589896-b23e-452f-bb27-cc5088dadf8d	application	mobile-banking
1b90c9a7-d03a-41d3-a1af-54150d3335e6	0b589896-b23e-452f-bb27-cc5088dadf8d	control	MW-AUTODEPLOY
39420261-8658-4edc-998a-77482224d396	0b589896-b23e-452f-bb27-cc5088dadf8d	framework	C-SITE
40f5061c-05e6-4975-84f3-2e9bd87e4af1	0b589896-b23e-452f-bb27-cc5088dadf8d	frameworks	C-SITE
94e04950-5cae-4f93-8e5f-d261e0f019f8	0b589896-b23e-452f-bb27-cc5088dadf8d	evidenceType	MIDDLEWARE-CONFIG
92a71f38-8d10-4af7-98ea-ac307250ec96	0b589896-b23e-452f-bb27-cc5088dadf8d	name	mobile-banking_MW-AUTODEPLOY_MIDDLEWARE-CONFIG_20260915_001
6848e5b8-c91a-4558-ae79-e2906012db8b	0b589896-b23e-452f-bb27-cc5088dadf8d	version	v1
586185e0-6848-4f1f-aaa5-78a79c67e744	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	technology	tomcat
4736b995-9e86-4bec-9f73-318c818e5b91	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	agent	SIM_TOMCAT
8dd63e05-af4d-4cff-a899-7b96a5f5fe15	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	collectionMethod	scheduled
4c57ed37-458d-48d8-b3f5-27b48aed1994	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	application	net-banking
cf18d1ae-a2c8-4e77-b6b2-23edc6132a1b	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	control	MW-TLS-VERSION
c6e8d272-9b3d-4c23-a827-5eaa0194e9c2	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	framework	PCI_DSS
0e675675-db04-49ff-af1e-633c0fdbe16f	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	frameworks	PCI_DSS,DPSC,ISG
2f0ff494-30df-4111-a215-adb150fe48da	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	evidenceType	MIDDLEWARE-CONFIG
52b32a40-5467-4b45-8373-bda72d04dbef	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	name	net-banking_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001
27bc8204-b5ed-49d5-b835-d3ee7f8e7597	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	version	v1
5ee433b9-4cb1-43d3-a219-e0333ede1064	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	technology	tomcat
41e2349b-1283-4049-92d9-dc4f11dea9f2	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	agent	SIM_TOMCAT
1e092b08-59dd-4004-b020-5a23b9f8ede1	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	collectionMethod	scheduled
74e832fa-e81f-4ccd-931e-31c7d55261b1	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	application	net-banking
72542aa3-ed2a-4243-81c8-0e3f795ffb87	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	control	MW-AUTODEPLOY
58c7fc74-31cc-413b-b901-f629e1d84a20	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	framework	C-SITE
f4d1455d-96d8-448b-aa4c-f6968b088a31	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	frameworks	C-SITE
dcf57e04-0d1f-4132-8e1d-51cf10035e2a	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	evidenceType	MIDDLEWARE-CONFIG
135517b9-4777-496b-a3b9-d20df348d6bd	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	name	net-banking_MW-AUTODEPLOY_MIDDLEWARE-CONFIG_20260915_001
43a75385-40db-435d-886b-927627c552ca	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	version	v1
cb87202f-5e17-476d-9dbb-b8ddee4ab3d6	9aba1297-9050-494d-a023-89166919ce50	technology	tomcat
4687eae1-04c7-4c90-88ab-88e6d32d0524	9aba1297-9050-494d-a023-89166919ce50	agent	SIM_TOMCAT
94127848-5c5c-4245-813e-c212c093abe9	9aba1297-9050-494d-a023-89166919ce50	collectionMethod	scheduled
76aeca0d-7399-43ff-a2b8-82097619cfa7	9aba1297-9050-494d-a023-89166919ce50	application	payments
07eaff77-6efd-4d4d-8d60-ba877a3bd8f2	9aba1297-9050-494d-a023-89166919ce50	control	MW-TLS-VERSION
0a5e6f2a-2173-4b31-9587-e26507930e45	9aba1297-9050-494d-a023-89166919ce50	framework	PCI_DSS
70694976-8e1d-4622-879f-be2e0d8e65a1	9aba1297-9050-494d-a023-89166919ce50	frameworks	PCI_DSS,DPSC,ISG
9b8c2f53-c12a-45fe-82f3-bc0944f77046	9aba1297-9050-494d-a023-89166919ce50	evidenceType	MIDDLEWARE-CONFIG
8ea4de24-68fb-4d53-bd1b-15b7d3003e26	9aba1297-9050-494d-a023-89166919ce50	name	payments_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260915_001
7550fc9d-04ff-484b-9710-4da3da166cd3	9aba1297-9050-494d-a023-89166919ce50	version	v1
e04401ea-0a5a-4422-9e46-dede7c0700b2	5edff7ca-679a-4517-bdda-31cc792ca6ff	technology	tomcat
c50a4e67-4844-4014-9e34-0542dae906e0	5edff7ca-679a-4517-bdda-31cc792ca6ff	agent	SIM_TOMCAT
85693a59-2cc8-4c04-a39f-ce1cae41612c	5edff7ca-679a-4517-bdda-31cc792ca6ff	collectionMethod	scheduled
2dc285f8-0487-4833-ad0b-33161e7cceff	5edff7ca-679a-4517-bdda-31cc792ca6ff	application	payments
a5230f31-dcb1-4705-87e3-d9bdd5017d60	5edff7ca-679a-4517-bdda-31cc792ca6ff	control	MW-AUTODEPLOY
9e4b61f7-26db-42d8-bb3f-8d9a19fc7ce9	5edff7ca-679a-4517-bdda-31cc792ca6ff	framework	C-SITE
cf90b364-66b1-420b-86f7-3954ad201c2b	5edff7ca-679a-4517-bdda-31cc792ca6ff	frameworks	C-SITE
f19d555b-4c2b-4eba-a119-ff3ced41b505	5edff7ca-679a-4517-bdda-31cc792ca6ff	evidenceType	MIDDLEWARE-CONFIG
b6f85bbc-619d-4e0f-a669-f5899397a93e	5edff7ca-679a-4517-bdda-31cc792ca6ff	name	payments_MW-AUTODEPLOY_MIDDLEWARE-CONFIG_20260915_001
2e4e3da6-47d5-4b67-8ae6-3a099754ef7d	5edff7ca-679a-4517-bdda-31cc792ca6ff	version	v1
67a67b59-4063-4e45-a41a-2bef9179e157	31939122-58c2-4c45-832d-946552e91124	objectType	policy_document
6346e358-a179-4881-8238-2fa7d9a3f2ae	31939122-58c2-4c45-832d-946552e91124	source	SHAREPOINT
74ec1c34-0e91-4a49-9fbd-1a893b70b199	31939122-58c2-4c45-832d-946552e91124	application	mobile-banking
999bb948-151b-469a-ae4d-e60886a10de1	31939122-58c2-4c45-832d-946552e91124	technology	unknown
109f141e-a142-44b8-bbf4-dc341532ee27	31939122-58c2-4c45-832d-946552e91124	control	ITPP-DOC-03
0741f909-d2ea-42b5-b95e-f402afe5921d	31939122-58c2-4c45-832d-946552e91124	framework	ITPP
6421f65d-3427-4e8e-b74f-98c41615a0f6	31939122-58c2-4c45-832d-946552e91124	frameworks	ITPP
825eb7da-c2ac-4adb-9fac-c895cd75fcaa	31939122-58c2-4c45-832d-946552e91124	evidenceType	GENERAL
02175779-2ccb-43e6-85cc-7ae52dbdb3bd	31939122-58c2-4c45-832d-946552e91124	name	mobile-banking_ITPP-DOC-03_GENERAL_20260915_001
67600d4f-67be-4a3e-910a-3c6214b29f10	31939122-58c2-4c45-832d-946552e91124	sourceTitle	policy_document for ITPP-DOC-03
23715e41-0904-4f2d-b8ab-be936976f3bb	31939122-58c2-4c45-832d-946552e91124	collectionMethod	scheduled
1272c980-8a23-4377-a743-62dc01776334	31939122-58c2-4c45-832d-946552e91124	version	v1
aca2c7dc-54af-450a-a0e6-92162c5d6c71	95719fec-0ab6-43d4-8097-2057cd8344fc	objectType	governance_pack
70a88cff-efba-4455-83fd-590c5470d158	95719fec-0ab6-43d4-8097-2057cd8344fc	source	SHAREPOINT
f0c03adc-fd9d-42fe-b211-374de46efe78	95719fec-0ab6-43d4-8097-2057cd8344fc	application	mobile-banking
ffc93243-acab-4722-9a8f-ccef9d657d65	95719fec-0ab6-43d4-8097-2057cd8344fc	technology	unknown
475675d6-ef67-43ec-acc0-75761e141e0b	95719fec-0ab6-43d4-8097-2057cd8344fc	control	DPSC-GOV-01
f5dc5a14-d660-4250-86db-8d1241b2e1e1	95719fec-0ab6-43d4-8097-2057cd8344fc	framework	DPSC
0ebb189e-fd6f-4df3-9319-96ce2f10f974	95719fec-0ab6-43d4-8097-2057cd8344fc	frameworks	DPSC
4f586982-224e-4e09-b31a-b4c95bfc1667	95719fec-0ab6-43d4-8097-2057cd8344fc	evidenceType	GENERAL
8220063c-5e64-47ce-8e3f-f071ce461225	95719fec-0ab6-43d4-8097-2057cd8344fc	name	mobile-banking_DPSC-GOV-01_GENERAL_20260915_001
f85a3177-95c5-4779-9d6b-6e6cc784d2cd	95719fec-0ab6-43d4-8097-2057cd8344fc	sourceTitle	governance_pack for DPSC-GOV-01
2f905d36-7999-4e97-8072-07b5f486ee05	95719fec-0ab6-43d4-8097-2057cd8344fc	collectionMethod	scheduled
44e41ef8-b405-42f8-88ed-3e25aa4c9f6f	95719fec-0ab6-43d4-8097-2057cd8344fc	version	v1
d3e0e07d-c79b-4113-ab2b-288ab7021ed6	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	objectType	security_policy
392019e5-dca0-4c6e-85b8-a64df91c15ec	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	source	SHAREPOINT
174e1baa-bb80-47f3-83cb-080d21a2d5fa	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	application	mobile-banking
812e83bf-c350-4d15-9e84-ee28a58faf52	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	technology	unknown
eeb1f442-b930-420b-afc1-c9fd0c4deafd	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	control	PCI-DSS-12.1
084b5309-fd88-4056-9281-e0e93ffa0b74	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	framework	PCI_DSS
c17feb85-a89f-4f2b-934d-72a0945510d1	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	frameworks	PCI_DSS
53b73d92-186b-4e2e-bfcb-2a7a0aecc5ad	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	evidenceType	GENERAL
21b351d8-ac5b-40c4-9b1a-d4350d332bf1	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	name	mobile-banking_PCI-DSS-12.1_GENERAL_20260915_001
a4c5bea4-8d9a-4b60-b44d-7657fe94f4b8	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	sourceTitle	security_policy for PCI-DSS-12.1
96cf454e-a77c-4555-ac5f-7455ccc1b133	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	collectionMethod	scheduled
3e8f3d05-fc07-4407-a34d-ebbf0d0c5195	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	version	v1
1ed98407-69fe-4239-a4b1-26e394ff0946	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	objectType	policy_document
7af52a66-9086-4481-af4e-6053d60db467	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	source	SHAREPOINT
ea7878d1-55aa-4e53-989e-2931fe269982	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	application	net-banking
fbf7e2e5-208a-4eaa-b684-cb4828f23e86	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	technology	unknown
9211a0f6-ccb6-49b6-aded-cc5fe3b865bb	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	control	ITPP-DOC-03
a64d972c-329d-4f29-a4c3-7496cdae1359	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	framework	ITPP
dbc2feee-63bc-4432-ad63-aa5a77d0ec73	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	frameworks	ITPP
b8d37e1b-916f-4775-b62d-2446df113d8e	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	evidenceType	GENERAL
fb8de8ea-5305-40d1-b3cf-60644d033b9e	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	name	net-banking_ITPP-DOC-03_GENERAL_20260915_001
5557c8e2-5eeb-4970-8586-f0b45cb6d6c3	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	sourceTitle	policy_document for ITPP-DOC-03
e0f38644-eebd-4ae2-bbfe-c00d8e1b19f8	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	collectionMethod	scheduled
2a6d0db1-1f6c-4867-ad3f-09e338d7b068	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	version	v1
9912302c-8161-49e6-a941-6e66b46461c5	40630923-dd28-49e1-b40f-8169dee97763	objectType	governance_pack
3e0e73d6-29fc-4217-a924-bf2981c38a59	40630923-dd28-49e1-b40f-8169dee97763	source	SHAREPOINT
360e95d4-7973-420e-b28b-cbfce06ff489	40630923-dd28-49e1-b40f-8169dee97763	application	net-banking
6682bdfc-3ff6-439c-aa3e-7874b46f3602	40630923-dd28-49e1-b40f-8169dee97763	technology	unknown
799baa20-9ff7-46cf-95ca-bf0c51621057	40630923-dd28-49e1-b40f-8169dee97763	control	DPSC-GOV-01
2e377da5-1d63-4030-b5b0-ffc6e19d3b6d	40630923-dd28-49e1-b40f-8169dee97763	framework	DPSC
eff3ccde-9f8a-492f-9a6e-352136c2d628	40630923-dd28-49e1-b40f-8169dee97763	frameworks	DPSC
8688de95-6162-4b10-89d7-d0e22b3022d4	40630923-dd28-49e1-b40f-8169dee97763	evidenceType	GENERAL
d82e318e-8fba-4226-a89a-452ffc9a3463	40630923-dd28-49e1-b40f-8169dee97763	name	net-banking_DPSC-GOV-01_GENERAL_20260915_001
2a82d93e-6111-42b9-ba43-566ce2abd138	40630923-dd28-49e1-b40f-8169dee97763	sourceTitle	governance_pack for DPSC-GOV-01
96256f74-2965-4b2c-aeb9-4c56906b2b70	40630923-dd28-49e1-b40f-8169dee97763	collectionMethod	scheduled
1f4a9362-4be9-41b2-ac50-319504baf8eb	40630923-dd28-49e1-b40f-8169dee97763	version	v1
6f63ce0b-2059-47ee-8f8a-0ee7ffe9ebc5	379fbd9a-38cd-4989-81ba-0f9bd533736d	objectType	security_policy
28eb520f-82cc-4cd6-8d67-1d1326526906	379fbd9a-38cd-4989-81ba-0f9bd533736d	source	SHAREPOINT
76cb7fb8-cf4f-4f8c-af67-bf026516eda4	379fbd9a-38cd-4989-81ba-0f9bd533736d	application	net-banking
639334e4-b881-47f9-962b-aa0ea3881662	379fbd9a-38cd-4989-81ba-0f9bd533736d	technology	unknown
4fa74bcf-e969-41e2-a1ff-3fd7b4fe1976	379fbd9a-38cd-4989-81ba-0f9bd533736d	control	PCI-DSS-12.1
d8484641-dd54-4218-8cb1-481b92f8af46	379fbd9a-38cd-4989-81ba-0f9bd533736d	framework	PCI_DSS
16ead454-9366-4de2-8aac-5c89583b7a08	379fbd9a-38cd-4989-81ba-0f9bd533736d	frameworks	PCI_DSS
fe9ddb2e-c478-4e25-ba00-aea74f18e403	379fbd9a-38cd-4989-81ba-0f9bd533736d	evidenceType	GENERAL
5cb29354-d1a0-4fad-a4ed-806756220147	379fbd9a-38cd-4989-81ba-0f9bd533736d	name	net-banking_PCI-DSS-12.1_GENERAL_20260915_001
4b811817-d5e4-4e4d-8d43-880d94e77f13	379fbd9a-38cd-4989-81ba-0f9bd533736d	sourceTitle	security_policy for PCI-DSS-12.1
0a0c4dab-58ed-4ca2-aee2-a9dfef4f72d1	379fbd9a-38cd-4989-81ba-0f9bd533736d	collectionMethod	scheduled
00e40cbf-548c-431c-ad94-60b4df245389	379fbd9a-38cd-4989-81ba-0f9bd533736d	version	v1
c9486a55-103d-489b-9938-bccacc39d7c2	e3167d52-f263-417c-964f-5523c33c1d91	objectType	policy_document
5fa11426-559e-4cc5-a4f9-54f1368341e2	e3167d52-f263-417c-964f-5523c33c1d91	source	SHAREPOINT
88c5a43c-d438-4593-b939-70034a29bfcd	e3167d52-f263-417c-964f-5523c33c1d91	application	payments
00f78499-59f6-45a6-b2b0-e6a2d6ef8f43	e3167d52-f263-417c-964f-5523c33c1d91	technology	unknown
74524336-9ce5-4792-ba96-9c64d1b0240c	e3167d52-f263-417c-964f-5523c33c1d91	control	ITPP-DOC-03
c0e9bb4f-0476-42f3-8695-e8c102e1e01b	e3167d52-f263-417c-964f-5523c33c1d91	framework	ITPP
6de49a3a-a8fa-4409-80ac-c51274e50fe5	e3167d52-f263-417c-964f-5523c33c1d91	frameworks	ITPP
19c9a78c-7b5c-443d-b5f2-16ab00cb4cce	e3167d52-f263-417c-964f-5523c33c1d91	evidenceType	GENERAL
3b88efd0-98f1-4732-84fa-762a89252291	e3167d52-f263-417c-964f-5523c33c1d91	name	payments_ITPP-DOC-03_GENERAL_20260915_001
f091c0c1-fe46-43cc-91fd-2200a98f5ee1	e3167d52-f263-417c-964f-5523c33c1d91	sourceTitle	policy_document for ITPP-DOC-03
f2f1dd60-5d8d-487f-a2e4-b7fdaacc68e4	e3167d52-f263-417c-964f-5523c33c1d91	collectionMethod	scheduled
f2484c4e-075d-40d8-a121-491087eade09	e3167d52-f263-417c-964f-5523c33c1d91	version	v1
b10d8b96-d34f-4961-ac49-d6abc4dd6d25	969d7c02-eb0c-47af-a189-b94c42ce67dc	objectType	governance_pack
2c528a98-983e-48e0-b019-58e8977037d5	969d7c02-eb0c-47af-a189-b94c42ce67dc	source	SHAREPOINT
3561cdfe-a67b-4463-8cd7-f3d62883a725	969d7c02-eb0c-47af-a189-b94c42ce67dc	application	payments
600b93ac-7653-42f0-a71a-07c2c072e17f	969d7c02-eb0c-47af-a189-b94c42ce67dc	technology	unknown
8b21e08e-f7c0-4a25-b3c7-8951cc138ac6	969d7c02-eb0c-47af-a189-b94c42ce67dc	control	DPSC-GOV-01
899c55ec-1e3b-43c9-b3cc-16a7bb31d90a	969d7c02-eb0c-47af-a189-b94c42ce67dc	framework	DPSC
a2bed7bf-c1b3-4975-bc92-0d5483961236	969d7c02-eb0c-47af-a189-b94c42ce67dc	frameworks	DPSC
20245ca9-cbc6-4790-bd1a-691fd4e5f3ce	969d7c02-eb0c-47af-a189-b94c42ce67dc	evidenceType	GENERAL
f6010cd1-aba0-4f6f-8ec2-c2c8bda3063e	969d7c02-eb0c-47af-a189-b94c42ce67dc	name	payments_DPSC-GOV-01_GENERAL_20260915_001
0527a67d-5607-4333-b1c8-a418886b1a91	969d7c02-eb0c-47af-a189-b94c42ce67dc	sourceTitle	governance_pack for DPSC-GOV-01
b562c32a-c4fe-4f92-83c5-b88d86b351ec	969d7c02-eb0c-47af-a189-b94c42ce67dc	collectionMethod	scheduled
ba510dae-8b6e-4a56-80ff-fff8a8384268	969d7c02-eb0c-47af-a189-b94c42ce67dc	version	v1
a1675725-5cb4-4f56-88f1-e380425565b6	ef73d9bb-1f1a-4390-a202-4a056291a646	objectType	security_policy
88fa7239-0145-4fed-ad03-85b4f084823f	ef73d9bb-1f1a-4390-a202-4a056291a646	source	SHAREPOINT
fe1703d3-1186-4c94-b6a9-e16200f55155	ef73d9bb-1f1a-4390-a202-4a056291a646	application	payments
4bd3e293-91ee-483c-93ef-36b1a82edb80	ef73d9bb-1f1a-4390-a202-4a056291a646	technology	unknown
134b44e5-92e4-4e76-8cb7-d90fcd014533	ef73d9bb-1f1a-4390-a202-4a056291a646	control	PCI-DSS-12.1
ac005c76-cc8a-40cd-8c08-e9378392c996	ef73d9bb-1f1a-4390-a202-4a056291a646	framework	PCI_DSS
937cd94f-db68-48dc-ae92-5d7fa7bfb52c	ef73d9bb-1f1a-4390-a202-4a056291a646	frameworks	PCI_DSS
7381bd67-e93e-4ca3-bc81-03f45803d64f	ef73d9bb-1f1a-4390-a202-4a056291a646	evidenceType	GENERAL
f7db1ec4-9035-492e-99ec-f34ff5dc3c7e	ef73d9bb-1f1a-4390-a202-4a056291a646	name	payments_PCI-DSS-12.1_GENERAL_20260915_001
b25439f8-dd76-4322-a5fb-95c988ce3397	ef73d9bb-1f1a-4390-a202-4a056291a646	sourceTitle	security_policy for PCI-DSS-12.1
fa71a754-34ea-4e54-8e24-d6254cd460e2	ef73d9bb-1f1a-4390-a202-4a056291a646	collectionMethod	scheduled
1727a552-b049-4063-ad03-f6c1e142b5de	ef73d9bb-1f1a-4390-a202-4a056291a646	version	v1
c05e14b2-7326-4886-8049-f5a78e5d7b7c	654cb34d-ee4d-46f5-9f4b-966288fbee24	objectType	change_request
581b587e-47d2-4b3c-8ac0-4c9d1b97664f	654cb34d-ee4d-46f5-9f4b-966288fbee24	source	SERVICENOW
6f1c67c3-8eed-41da-963c-fe94e59fc29c	654cb34d-ee4d-46f5-9f4b-966288fbee24	application	mobile-banking
44778449-a739-4745-948e-d680d27e30a2	654cb34d-ee4d-46f5-9f4b-966288fbee24	technology	unknown
089ff01f-e834-4e41-98b8-884ee5c28b33	654cb34d-ee4d-46f5-9f4b-966288fbee24	control	ITPP-CHG-02
be538b31-715e-44ad-b96a-95fb7beda7d4	654cb34d-ee4d-46f5-9f4b-966288fbee24	framework	ITPP
bb87c10d-5a3c-451a-83ae-64bbd3e24944	654cb34d-ee4d-46f5-9f4b-966288fbee24	frameworks	ITPP,ISO27001,IS
7cd75de8-3ceb-45a3-b1bb-291304869161	654cb34d-ee4d-46f5-9f4b-966288fbee24	evidenceType	CHANGE-TICKET
872ddc92-d3ea-43c2-bc1b-555e948e18f4	654cb34d-ee4d-46f5-9f4b-966288fbee24	name	mobile-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001
825932de-5957-4470-97ef-46aa224c9648	654cb34d-ee4d-46f5-9f4b-966288fbee24	sourceTitle	change_request for ITPP-CHG-02
1ec67c65-ea86-4553-a42d-2320579d3c7a	654cb34d-ee4d-46f5-9f4b-966288fbee24	collectionMethod	scheduled
068d2891-277f-420d-b2ba-b23792ad6a42	654cb34d-ee4d-46f5-9f4b-966288fbee24	version	v1
e7d3b49d-1e38-4c7d-a2f8-db5d19299fc0	44763b2a-ed03-42a3-b8c0-68662c2d9b67	objectType	incident_record
52918052-7579-475e-bb8d-e3096543985a	44763b2a-ed03-42a3-b8c0-68662c2d9b67	source	SERVICENOW
b7b18b20-fc54-4f54-b768-a216abab9e35	44763b2a-ed03-42a3-b8c0-68662c2d9b67	application	mobile-banking
551fe998-08db-4c15-a360-51363a3c6bef	44763b2a-ed03-42a3-b8c0-68662c2d9b67	technology	unknown
ccbc6e9d-5482-49ce-bbc1-c2b87f998c78	44763b2a-ed03-42a3-b8c0-68662c2d9b67	control	ITPP-INC-01
638beed2-9534-4302-bfa4-ba509fa18845	44763b2a-ed03-42a3-b8c0-68662c2d9b67	framework	ITPP
48861f75-9849-4b71-8702-575684690462	44763b2a-ed03-42a3-b8c0-68662c2d9b67	frameworks	ITPP
510de190-0072-4b46-ba72-ba9e828678df	44763b2a-ed03-42a3-b8c0-68662c2d9b67	evidenceType	GENERAL
d51c52ec-aee6-4dad-9d2c-ac0e6842ff48	44763b2a-ed03-42a3-b8c0-68662c2d9b67	name	mobile-banking_ITPP-INC-01_GENERAL_20260915_001
8b647457-6406-407e-b9fd-17a5ec54c411	44763b2a-ed03-42a3-b8c0-68662c2d9b67	sourceTitle	incident_record for ITPP-INC-01
e1f74a60-d734-4277-bed3-da951ddf1c09	44763b2a-ed03-42a3-b8c0-68662c2d9b67	collectionMethod	scheduled
edb06a8c-480d-41d5-ace9-1931a0a2409a	44763b2a-ed03-42a3-b8c0-68662c2d9b67	version	v1
9efb3c9a-9be0-46b4-96b2-e7d42cbfbf8e	2ed3b783-c765-4c35-8d28-3649d5ee5b98	objectType	incident_response_plan
faf6cf1f-9746-408f-ade2-0c91fc62b9c5	2ed3b783-c765-4c35-8d28-3649d5ee5b98	source	SERVICENOW
88316521-4eb6-4ece-a6dd-da33d4ab931d	2ed3b783-c765-4c35-8d28-3649d5ee5b98	application	mobile-banking
67d52fb3-8ab5-4d1a-bedb-45e690e30ff3	2ed3b783-c765-4c35-8d28-3649d5ee5b98	technology	unknown
21a74e4d-e120-4b35-8b9e-a19ab96d4865	2ed3b783-c765-4c35-8d28-3649d5ee5b98	control	PCI-DSS-12.10
0d2a1236-c509-4baa-b534-10853d792532	2ed3b783-c765-4c35-8d28-3649d5ee5b98	framework	PCI_DSS
5fc60d04-9d0d-4423-941b-f99dd99e46b9	2ed3b783-c765-4c35-8d28-3649d5ee5b98	frameworks	PCI_DSS
9db2657e-c65e-4aef-bdc2-daf98fce5514	2ed3b783-c765-4c35-8d28-3649d5ee5b98	evidenceType	GENERAL
8fd4507b-96e6-41bc-ac99-b0ffc9008423	2ed3b783-c765-4c35-8d28-3649d5ee5b98	name	mobile-banking_PCI-DSS-12.10_GENERAL_20260915_001
8504d79a-b579-49b0-ab26-c57dcf0d169c	2ed3b783-c765-4c35-8d28-3649d5ee5b98	sourceTitle	incident_response_plan for PCI-DSS-12.10
cb35e538-10fe-4de7-93e8-f33a813b6039	2ed3b783-c765-4c35-8d28-3649d5ee5b98	collectionMethod	scheduled
d1489ca3-6d06-48a4-b53b-da09a930dbe5	2ed3b783-c765-4c35-8d28-3649d5ee5b98	version	v1
5ce89fb7-4538-485d-ac8f-a134dee5b0e7	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	objectType	change_request
5057269c-2326-4838-8c77-3c1a1caba6ce	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	source	SERVICENOW
afe1ed69-d4ab-484f-8820-71c6be850854	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	application	net-banking
f8df29ad-328e-4990-8e67-bb1ae591f19d	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	technology	unknown
77b66dbc-c023-4366-ab7a-99313e1d345f	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	control	ITPP-CHG-02
6c4ff9cf-8f07-4e6a-843e-ff79f15e21a2	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	framework	ITPP
06c5a5e9-d32d-4b35-9949-089457f68b89	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	frameworks	ITPP,ISO27001,IS
a9e0d5f9-0e07-4ba0-b7eb-954ea1dbeca7	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	evidenceType	CHANGE-TICKET
14f463f3-1291-41c5-b4de-9d1788a7f482	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	name	net-banking_ITPP-CHG-02_CHANGE-TICKET_20260915_001
5acfdd8e-8cd8-4fe2-8324-411b5b5a5984	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	sourceTitle	change_request for ITPP-CHG-02
1ac00828-b922-45d4-beb1-80095455f92c	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	collectionMethod	scheduled
c8c415a2-394a-456f-8c32-6cc68546fa88	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	version	v1
a1338f49-31f3-422f-ae76-bde22f09114c	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	objectType	incident_record
4bd8889a-d433-4323-a935-c69d26becf3f	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	source	SERVICENOW
ab79d122-b622-4764-a8d5-6491df47a9a1	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	application	net-banking
aeeb6680-e6d9-4227-9139-8d53dd5aba42	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	technology	unknown
849baf42-c75c-449d-b68d-38c611eccdca	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	control	ITPP-INC-01
017dbb95-442a-4580-9192-f692b05385e0	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	framework	ITPP
a7860902-ab00-4993-96e6-1e8037f85263	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	frameworks	ITPP
bae5420d-9410-4175-aad7-5724d59078e0	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	evidenceType	GENERAL
36e6b20f-21a3-4e84-a3b7-7cc8dd6bfc0d	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	name	net-banking_ITPP-INC-01_GENERAL_20260915_001
64b98b5f-c710-427d-a00a-51d8d22345f4	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	sourceTitle	incident_record for ITPP-INC-01
647c2c3d-7eef-4331-9a44-d725a74159c3	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	collectionMethod	scheduled
ac30f6da-228d-46e1-9e73-65260cf4a22b	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	version	v1
1da38adf-152b-4c30-a5e8-7a68b60692d1	79296e0a-4136-445f-a85b-64d2607cc1a3	objectType	incident_response_plan
b018431f-76ff-4435-943d-c76b61693a1a	79296e0a-4136-445f-a85b-64d2607cc1a3	source	SERVICENOW
6af8492e-761d-4118-9720-0f94704b8b55	79296e0a-4136-445f-a85b-64d2607cc1a3	application	net-banking
39d36219-2913-429c-bbe9-bfd533d73f02	79296e0a-4136-445f-a85b-64d2607cc1a3	technology	unknown
b1be297b-c5c9-44f0-81fe-37d61ea80c4b	79296e0a-4136-445f-a85b-64d2607cc1a3	control	PCI-DSS-12.10
f8d018a0-813c-4975-b582-a94dc6d54a3c	79296e0a-4136-445f-a85b-64d2607cc1a3	framework	PCI_DSS
2d4fb6da-b69a-41fd-99ba-a8f241106eb5	79296e0a-4136-445f-a85b-64d2607cc1a3	frameworks	PCI_DSS
efb138cf-0d8b-470e-beb2-e802c76b6fcb	79296e0a-4136-445f-a85b-64d2607cc1a3	evidenceType	GENERAL
3a5b7c8f-b44c-418b-8038-2d48671427e5	79296e0a-4136-445f-a85b-64d2607cc1a3	name	net-banking_PCI-DSS-12.10_GENERAL_20260915_001
a6decc81-11e0-4353-a0f2-387b12815196	79296e0a-4136-445f-a85b-64d2607cc1a3	sourceTitle	incident_response_plan for PCI-DSS-12.10
092a69a1-a821-4f2b-b379-0c9753073067	79296e0a-4136-445f-a85b-64d2607cc1a3	collectionMethod	scheduled
cc96f42d-029f-4fd2-bc58-265f9c603841	79296e0a-4136-445f-a85b-64d2607cc1a3	version	v1
520c7c53-7b07-4353-8e95-0a2b98e1cda5	f178b149-af5a-44fd-b461-cddfeb9a76d5	objectType	change_request
26f34793-092b-41ce-9645-e90554825011	f178b149-af5a-44fd-b461-cddfeb9a76d5	source	SERVICENOW
deced808-6163-4d01-8ad5-c4639f1d76b5	f178b149-af5a-44fd-b461-cddfeb9a76d5	application	payments
1bdf4686-0227-4ce7-b5ec-387c3cb081cd	f178b149-af5a-44fd-b461-cddfeb9a76d5	technology	unknown
1d55a821-1f00-43af-8093-4a3e81325c03	f178b149-af5a-44fd-b461-cddfeb9a76d5	control	ITPP-CHG-02
c2559a0a-26db-45d0-8aee-cb155c7bab9e	f178b149-af5a-44fd-b461-cddfeb9a76d5	framework	ITPP
4e7a79a1-f2b9-48cb-8734-6377197a5463	f178b149-af5a-44fd-b461-cddfeb9a76d5	frameworks	ITPP,ISO27001,IS
28792eab-9453-404e-b8ca-eb19d66c5081	f178b149-af5a-44fd-b461-cddfeb9a76d5	evidenceType	CHANGE-TICKET
a27417d7-6fa3-43af-a387-ab05f149a53e	f178b149-af5a-44fd-b461-cddfeb9a76d5	name	payments_ITPP-CHG-02_CHANGE-TICKET_20260915_001
03b3c84a-491e-46d2-9106-2e5fa9c156ad	f178b149-af5a-44fd-b461-cddfeb9a76d5	sourceTitle	change_request for ITPP-CHG-02
f1dd9745-8a71-4e7b-b441-bc3af1450e1b	f178b149-af5a-44fd-b461-cddfeb9a76d5	collectionMethod	scheduled
748ab6a5-92b7-44b9-8a4b-6dd249a71bd0	f178b149-af5a-44fd-b461-cddfeb9a76d5	version	v1
6edf8585-aaf1-4800-af61-a6003e308ee5	81612e44-ed06-45f7-a8c1-89988c88de7b	objectType	incident_record
e6a3c0e5-4044-48b6-8546-9e35e537bab8	81612e44-ed06-45f7-a8c1-89988c88de7b	source	SERVICENOW
cb0bdd40-845d-4429-b1db-e2a11c35635b	81612e44-ed06-45f7-a8c1-89988c88de7b	application	payments
62d23c66-a140-4b46-8e79-d7bc0fdc073a	81612e44-ed06-45f7-a8c1-89988c88de7b	technology	unknown
22989615-cd44-4823-a094-3813d327b378	81612e44-ed06-45f7-a8c1-89988c88de7b	control	ITPP-INC-01
4837a2eb-2ae8-4039-8bd3-b4044f843c3f	81612e44-ed06-45f7-a8c1-89988c88de7b	framework	ITPP
aafff33e-e537-44ef-b932-c00cd0650f33	81612e44-ed06-45f7-a8c1-89988c88de7b	frameworks	ITPP
1f540b2f-74d6-410e-9175-ea45aa9cd877	81612e44-ed06-45f7-a8c1-89988c88de7b	evidenceType	GENERAL
42618431-06f5-4257-86df-9ef2ff97fd0e	81612e44-ed06-45f7-a8c1-89988c88de7b	name	payments_ITPP-INC-01_GENERAL_20260915_001
0b89a30c-5078-4770-9f5b-fd169c28e5be	81612e44-ed06-45f7-a8c1-89988c88de7b	sourceTitle	incident_record for ITPP-INC-01
0c70d1d8-72a3-4b06-af88-a6b8913bca33	81612e44-ed06-45f7-a8c1-89988c88de7b	collectionMethod	scheduled
7122bfab-1b3d-45ce-b45e-fce843b7b862	81612e44-ed06-45f7-a8c1-89988c88de7b	version	v1
bd90b91f-7106-432f-b467-fe1b8c5a05cc	c5ce959e-f256-4cef-84df-90502457d64c	objectType	incident_response_plan
d7e9c5cf-d438-4695-a1b8-776d26686a7b	c5ce959e-f256-4cef-84df-90502457d64c	source	SERVICENOW
324d4b2c-8d71-434c-8a9b-05f4f18907a3	c5ce959e-f256-4cef-84df-90502457d64c	application	payments
c10c26f6-d417-44c4-a854-a2db3a231054	c5ce959e-f256-4cef-84df-90502457d64c	technology	unknown
3bfa9dfa-9b5e-42a6-93c3-66c46b781207	c5ce959e-f256-4cef-84df-90502457d64c	control	PCI-DSS-12.10
725b3cf3-6d32-4439-be07-eed744e8f133	c5ce959e-f256-4cef-84df-90502457d64c	framework	PCI_DSS
5519b835-083e-4796-a88e-cb0ec696c714	c5ce959e-f256-4cef-84df-90502457d64c	frameworks	PCI_DSS
930b576f-61f5-43bc-b885-3fe56d907610	c5ce959e-f256-4cef-84df-90502457d64c	evidenceType	GENERAL
84fb132c-febe-416b-8916-0339c31ae4de	c5ce959e-f256-4cef-84df-90502457d64c	name	payments_PCI-DSS-12.10_GENERAL_20260915_001
dcb34c84-437e-4a09-b55e-7a52319d6b33	c5ce959e-f256-4cef-84df-90502457d64c	sourceTitle	incident_response_plan for PCI-DSS-12.10
c8c8a115-7bf4-4cd7-a1ae-5dce2704564e	c5ce959e-f256-4cef-84df-90502457d64c	collectionMethod	scheduled
e534e6e2-2148-4060-89dc-ca4ff6ced170	c5ce959e-f256-4cef-84df-90502457d64c	version	v1
be34df8a-9009-411a-ad00-b16570170404	49606c9a-050b-4562-830f-dd5e58ab6b48	ingest.channel	bulk-upload
cdda3e26-9125-4902-b161-f7b1156f8b4d	49606c9a-050b-4562-830f-dd5e58ab6b48	application	net-banking
1b9e1ac9-fa59-4476-a2b7-9b1624d2c6c6	49606c9a-050b-4562-830f-dd5e58ab6b48	technology	unknown
d4775929-24c1-422b-a6ec-3687bfb514b8	49606c9a-050b-4562-830f-dd5e58ab6b48	control	ABCD
67cb158c-de50-47ee-ad40-09500c009412	49606c9a-050b-4562-830f-dd5e58ab6b48	framework	PCI_DSS
0782e7b9-d7ad-4292-bbc2-fd6f04f9aa2a	49606c9a-050b-4562-830f-dd5e58ab6b48	frameworks	PCI_DSS
73fcca87-274a-47f5-ae11-db63626afe84	49606c9a-050b-4562-830f-dd5e58ab6b48	evidenceType	GENERAL
2ea4fd0a-5316-4a94-bfad-8932eb25ff98	49606c9a-050b-4562-830f-dd5e58ab6b48	name	net-banking_ABCD_GENERAL_20260915_001
99dd4b83-a7a9-40bf-a8a8-de7c87f8e75d	49606c9a-050b-4562-830f-dd5e58ab6b48	collectionMethod	bulk
6f51b3e6-00c0-4fc4-a31d-b9f2333db340	49606c9a-050b-4562-830f-dd5e58ab6b48	version	v1
\.


--
-- Data for Name: evidence_version; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_version (id, evidence_record_id, version_number, sha256, content_type, size_bytes, object_key, collected_at, collected_by, ingestion_run_id, created_at) FROM stdin;
05c18c74-831a-42b5-8854-e8b2963b9086	137f0260-d7cc-432c-8ad1-4417844da923	1	358536887a3127eb4236fcc539a7c289332e0ee5f2a18032200bd0f6202c66ee	application/json	102	evidence/137f0260-d7cc-432c-8ad1-4417844da923/v1/358536887a3127eb4236fcc539a7c289332e0ee5f2a18032200bd0f6202c66ee	2026-08-14 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.678876+00
6ccdff22-7076-472c-a689-1ac4888864c5	95678b8e-8ebc-49be-acf4-40c4f43c77d5	1	d9a422dff085e2a970f0b2509dc4a42eb21a8f191f7e088f40d3fbfed90df10e	application/json	105	evidence/95678b8e-8ebc-49be-acf4-40c4f43c77d5/v1/d9a422dff085e2a970f0b2509dc4a42eb21a8f191f7e088f40d3fbfed90df10e	2026-08-15 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.77853+00
4c0a8d86-2d43-45ac-a0e4-0b0e8b5d99e3	3340f502-aac7-4e37-9498-874ce46117cf	1	ac4ac55510e3a784d0c095631bb42938cc12be7b4dcea6d374a767ed058664a7	application/json	99	evidence/3340f502-aac7-4e37-9498-874ce46117cf/v1/ac4ac55510e3a784d0c095631bb42938cc12be7b4dcea6d374a767ed058664a7	2026-08-16 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.8147+00
e958f284-13f6-4231-8b0a-0f4d303e6d0f	b722d4b5-565f-4d61-9fde-3e7f0af9a22b	1	3349e22880e29ab93489ae719f407feeb7880898f2f1e0487bf6c83a14e9c6c8	application/json	102	evidence/b722d4b5-565f-4d61-9fde-3e7f0af9a22b/v1/3349e22880e29ab93489ae719f407feeb7880898f2f1e0487bf6c83a14e9c6c8	2026-08-17 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.848633+00
6980c1c3-b0e2-4858-8018-1671a66906c3	f009f238-7441-477e-bac9-f38d5c34a920	1	ad46d609dabaa4a106e5d9658fd2a613cef49b5f8b6231bdd71d5226a7d36167	application/json	96	evidence/f009f238-7441-477e-bac9-f38d5c34a920/v1/ad46d609dabaa4a106e5d9658fd2a613cef49b5f8b6231bdd71d5226a7d36167	2026-08-18 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.886523+00
7bfed156-21ac-4587-98ac-00a01ae9f652	4dd11c77-bc7f-4615-b96d-796bced6077c	1	5a2e62b61fbbae5ada2a184b2f09e9baa3b1b16fa01880ed06290fcc97d53762	application/json	95	evidence/4dd11c77-bc7f-4615-b96d-796bced6077c/v1/5a2e62b61fbbae5ada2a184b2f09e9baa3b1b16fa01880ed06290fcc97d53762	2026-08-19 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.920094+00
e4b81cd7-7566-49e8-8bc1-ed760ad39725	00c73d89-1be7-48e3-a90d-24575535dfe8	1	c1f03cd91d1022d4d4c800c3bbcea725871ad50522b6bd6209982b50e72c062f	application/json	105	evidence/00c73d89-1be7-48e3-a90d-24575535dfe8/v1/c1f03cd91d1022d4d4c800c3bbcea725871ad50522b6bd6209982b50e72c062f	2026-08-20 09:00:00+00	demo-seed	\N	2026-09-15 06:21:18.951334+00
612a67b6-e6ab-4eb0-ae0b-e6435e6762ea	d3964043-ac22-4b12-8f9a-a14152017f98	1	2773c02f2a59de78491cb7642d0456da8ef3e87c3a4238930a31736b78484c24	application/json	245	evidence/d3964043-ac22-4b12-8f9a-a14152017f98/v1/2773c02f2a59de78491cb7642d0456da8ef3e87c3a4238930a31736b78484c24	2026-09-15 06:37:28+00	MOCK_JIRA	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.266381+00
61e312e8-94e8-47cf-b7d3-c462b988cf2c	cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a	1	9bafb81e2f356f2234f73ecc2a79d64805080d6fdcc46b7eaddbe09503eb482d	application/json	242	evidence/cb23a2a4-e17a-4c90-8f8e-f29fb98b9d7a/v1/9bafb81e2f356f2234f73ecc2a79d64805080d6fdcc46b7eaddbe09503eb482d	2026-09-15 06:37:28+00	MOCK_JIRA	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.426391+00
1fe98d20-896d-434c-ad8d-480d71b0752d	6f4cc0f7-e8c8-4121-9897-7d43cd064263	1	b2317796fb07ba10359280689095700c0c6105aef580ed52c6ec8c42b1990c40	application/json	242	evidence/6f4cc0f7-e8c8-4121-9897-7d43cd064263/v1/b2317796fb07ba10359280689095700c0c6105aef580ed52c6ec8c42b1990c40	2026-09-15 06:37:28+00	MOCK_JIRA	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.542096+00
63e2f8da-ad19-4dfe-a3b5-269d524615e4	c41d90e3-c889-47ae-91a4-0a8e678e1ff3	1	cda45cac5684512c30105080c516a0c3a0bcc91db487cdc091a9bd540a6d6cca	application/json	239	evidence/c41d90e3-c889-47ae-91a4-0a8e678e1ff3/v1/cda45cac5684512c30105080c516a0c3a0bcc91db487cdc091a9bd540a6d6cca	2026-09-15 06:37:28+00	MOCK_JIRA	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.649468+00
1d48d771-d471-412a-ae68-41e15bc4be0e	2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e	1	90f4a5443a1324ef66471a97eb6ed12867eb6105f9d03ee7eb381ce7f234b03c	application/json	239	evidence/2d5cb98a-54ac-4ac9-9f8a-91d5ca84ba8e/v1/90f4a5443a1324ef66471a97eb6ed12867eb6105f9d03ee7eb381ce7f234b03c	2026-09-15 06:37:28+00	MOCK_JIRA	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.759826+00
831c1efb-ad04-4470-833a-540dcd2103e2	f46cd29a-37a3-48c7-b718-e18c28f766b0	1	e5bd892dbbce1b402003803b1b1a23ce6b592e57f79ef5644086de5542dc8124	application/json	236	evidence/f46cd29a-37a3-48c7-b718-e18c28f766b0/v1/e5bd892dbbce1b402003803b1b1a23ce6b592e57f79ef5644086de5542dc8124	2026-09-15 06:37:28+00	MOCK_JIRA	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.859682+00
7463f8fa-0d15-4c84-9da7-b341d2c7e004	76c9a2eb-b56b-491f-b914-3d5729bf8aae	1	54c4e03b4883f4cea54a54a8837a16d4fb880bfe7782925293061124802ff493	application/json	256	evidence/76c9a2eb-b56b-491f-b914-3d5729bf8aae/v1/54c4e03b4883f4cea54a54a8837a16d4fb880bfe7782925293061124802ff493	2026-09-15 06:37:28+00	MOCK_SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:28.955762+00
75022dc6-7f68-463b-b311-4dc3db75b929	330cbcce-cd3d-42c1-9106-04a1ec1fc4b9	1	83a4560f72ee065092f6635fa0838d11b18d06a37058131e3a49989c3a3599a5	application/json	263	evidence/330cbcce-cd3d-42c1-9106-04a1ec1fc4b9/v1/83a4560f72ee065092f6635fa0838d11b18d06a37058131e3a49989c3a3599a5	2026-09-15 06:37:28+00	MOCK_SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.047494+00
2df50382-7d87-4a1f-8d0d-2a09b419536f	ee1c3319-eed3-4078-8dfd-5313359b7cd5	1	c6810c7cc2a8afceb3be8e01795373e9203030b5934f59d211df35e9cab6996a	application/json	253	evidence/ee1c3319-eed3-4078-8dfd-5313359b7cd5/v1/c6810c7cc2a8afceb3be8e01795373e9203030b5934f59d211df35e9cab6996a	2026-09-15 06:37:29+00	MOCK_SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.143347+00
d5b4ef61-7f7a-40a6-b3f8-1a91078bb9dd	3e71e55c-5859-4652-b492-207dac524809	1	a579056be76093998aadeff079371f846005eca86528d4f0b49d9e0e0ae93864	application/json	260	evidence/3e71e55c-5859-4652-b492-207dac524809/v1/a579056be76093998aadeff079371f846005eca86528d4f0b49d9e0e0ae93864	2026-09-15 06:37:29+00	MOCK_SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.245135+00
6ea84455-c871-43a1-9a30-4fe2bc7b491b	2777b2ba-3652-4908-9561-98a028beef82	1	d578f160235a540b4206726cfc4a13bbf7044ead70c7f93b144c878ddd9bd68e	application/json	250	evidence/2777b2ba-3652-4908-9561-98a028beef82/v1/d578f160235a540b4206726cfc4a13bbf7044ead70c7f93b144c878ddd9bd68e	2026-09-15 06:37:29+00	MOCK_SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.330168+00
631f064a-6c35-4867-b300-069b241c09a9	97763088-2d92-472e-8672-362cef6175a4	1	e71280f49f4d2d261793e829d967f126a11173fb64a2b8542810e35db05ad4e2	application/json	257	evidence/97763088-2d92-472e-8672-362cef6175a4/v1/e71280f49f4d2d261793e829d967f126a11173fb64a2b8542810e35db05ad4e2	2026-09-15 06:37:29+00	MOCK_SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.412748+00
07253b4b-34bc-4384-8da5-43a35ada05cb	ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1	1	2ef8adc1bb290627d018f597a98554a5438b19fb4f1efc3043908e53aa18dcd2	application/json	253	evidence/ceda8b12-711d-4941-bf0b-cbd2b3bf5bb1/v1/2ef8adc1bb290627d018f597a98554a5438b19fb4f1efc3043908e53aa18dcd2	2026-09-15 06:37:29+00	MOCK_GITHUB	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.499429+00
c138132b-9ab0-4ec7-84c7-b0a30c9964b9	3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80	1	49cc556941ea4bd0fbf0525a90908b72953b5fc0a80f346b026dce3c8ec3e61a	application/json	254	evidence/3f99d8ee-f1f5-48f1-a58f-59df4a5b0d80/v1/49cc556941ea4bd0fbf0525a90908b72953b5fc0a80f346b026dce3c8ec3e61a	2026-09-15 06:37:29+00	MOCK_GITHUB	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.589986+00
afbba79b-e661-4fe7-bbd6-118ee8816a09	82fe525f-b0e6-4630-be90-6699615e0868	1	cb76b392669f09c9635744df078441e9594d165635d557dd777b785037cdd0ee	application/json	250	evidence/82fe525f-b0e6-4630-be90-6699615e0868/v1/cb76b392669f09c9635744df078441e9594d165635d557dd777b785037cdd0ee	2026-09-15 06:37:29+00	MOCK_GITHUB	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.662097+00
64c5905e-2f6d-483a-ac1d-2b5b33093a97	d6f36c83-2f64-4631-ac94-fe19b89c69e7	1	a4f5f767500797d964dca264ce04ea877193d59eb864f8ad212fde2d2356dc69	application/json	251	evidence/d6f36c83-2f64-4631-ac94-fe19b89c69e7/v1/a4f5f767500797d964dca264ce04ea877193d59eb864f8ad212fde2d2356dc69	2026-09-15 06:37:29+00	MOCK_GITHUB	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.744088+00
6a8a71a8-7882-4158-a050-a59e1fd41343	7efff257-28af-43c1-8634-51e773b07948	1	7a1fcfe9eabd29ee31d1135314b63e4e10d3678b42b00f0f15e544cfcdcdd19b	application/json	247	evidence/7efff257-28af-43c1-8634-51e773b07948/v1/7a1fcfe9eabd29ee31d1135314b63e4e10d3678b42b00f0f15e544cfcdcdd19b	2026-09-15 06:37:29+00	MOCK_GITHUB	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.821785+00
c95767f9-cc99-4b78-aeba-0298ce4f05cc	0eb7be56-7628-4a4c-bc9c-1c050d28c29d	1	9c2b58d5a0b395a4f5327404cd510f15fb1bd264db417ad502ba9ec9afb57637	application/json	248	evidence/0eb7be56-7628-4a4c-bc9c-1c050d28c29d/v1/9c2b58d5a0b395a4f5327404cd510f15fb1bd264db417ad502ba9ec9afb57637	2026-09-15 06:37:29+00	MOCK_GITHUB	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.894913+00
4d503d2a-9e1f-41cb-b9a5-b9446548157d	e8b82754-a27f-4c5f-a50b-8f7769ab9b55	1	cb76aa5c6d41735b5ea41222efa1e2e530b86c70be95ed38984bc6ad9187ec08	application/json	252	evidence/e8b82754-a27f-4c5f-a50b-8f7769ab9b55/v1/cb76aa5c6d41735b5ea41222efa1e2e530b86c70be95ed38984bc6ad9187ec08	2026-09-15 06:37:29+00	MOCK_CONFLUENCE	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:29.984277+00
925fbbfd-2442-4a7b-87f7-1e7cb2963bb7	64ddd7c6-fdca-443b-8dde-fbc0d2801462	1	5c868252c15d0d5c7be2cb9641b6f9d21b7ac4e9544342c201631205f5f9a6a8	application/json	252	evidence/64ddd7c6-fdca-443b-8dde-fbc0d2801462/v1/5c868252c15d0d5c7be2cb9641b6f9d21b7ac4e9544342c201631205f5f9a6a8	2026-09-15 06:37:29+00	MOCK_CONFLUENCE	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.063814+00
95892f53-fb69-4ed5-9e8d-9302a77b744e	8296014e-a49c-4866-a948-6433ac7cef44	1	32e46f1640258217f30da94de1787da0c1a1c721f64833a0e547ba0687b95a18	application/json	249	evidence/8296014e-a49c-4866-a948-6433ac7cef44/v1/32e46f1640258217f30da94de1787da0c1a1c721f64833a0e547ba0687b95a18	2026-09-15 06:37:30+00	MOCK_CONFLUENCE	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.141057+00
58564bad-5f77-485d-8c31-fa54ccedd45a	a0569699-2209-4fff-a8a0-af1e0ccaa227	1	190a3693a422184a1a4ef5f2c4e38c52c530ba4987ce5e43b6f034ba1851c428	application/json	249	evidence/a0569699-2209-4fff-a8a0-af1e0ccaa227/v1/190a3693a422184a1a4ef5f2c4e38c52c530ba4987ce5e43b6f034ba1851c428	2026-09-15 06:37:30+00	MOCK_CONFLUENCE	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.22164+00
e3dce241-04d8-45d6-aa41-b005bc76060b	ab954975-e6d7-4d8f-b54e-67aecdf5d9ff	1	cc56f053e9a450ecbce075356598973f07665cd1cd51705d0cb6cb6e54218361	application/json	246	evidence/ab954975-e6d7-4d8f-b54e-67aecdf5d9ff/v1/cc56f053e9a450ecbce075356598973f07665cd1cd51705d0cb6cb6e54218361	2026-09-15 06:37:30+00	MOCK_CONFLUENCE	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.307794+00
574bd101-ef2d-4cca-9f96-ad1e4be92868	c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf	1	67ff45ef7176065db585a5a6af69cda94f96058687f7711466b98566f9546eac	application/json	246	evidence/c5b8f99d-a242-4f0f-9a70-8a22d2f5f8bf/v1/67ff45ef7176065db585a5a6af69cda94f96058687f7711466b98566f9546eac	2026-09-15 06:37:30+00	MOCK_CONFLUENCE	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.373883+00
b15acf59-d3ab-4881-81e1-8093709a9477	67a16d1b-085b-4faf-8333-d4a8811667d7	1	475580fadf954fb9ace4ece30b9c4ee3b5e0f8bf0839249392c6f2c9ce4ed60b	application/json	407	evidence/67a16d1b-085b-4faf-8333-d4a8811667d7/v1/475580fadf954fb9ace4ece30b9c4ee3b5e0f8bf0839249392c6f2c9ce4ed60b	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.464018+00
6e44556f-b5ba-4bab-bfc4-cd43a77e0282	872d2bd1-a91d-48f2-8fd2-f0b6541f6da7	1	13895ef0fd3efb72373b8a9fbfc78b61cf2c74fb91ca044f86900cb120dfe0af	application/json	406	evidence/872d2bd1-a91d-48f2-8fd2-f0b6541f6da7/v1/13895ef0fd3efb72373b8a9fbfc78b61cf2c74fb91ca044f86900cb120dfe0af	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.537724+00
1253de3a-738f-471b-b022-b7059a871b0b	a99ef1f9-2193-4c30-b4b4-743268c8003e	1	e9145c7212c290d025777184fef09273c26f8f0918d40acf1cc2acabb65fd66d	application/json	418	evidence/a99ef1f9-2193-4c30-b4b4-743268c8003e/v1/e9145c7212c290d025777184fef09273c26f8f0918d40acf1cc2acabb65fd66d	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.606565+00
e223babe-2fa1-4e27-942b-80802c13a564	2fe254d9-ae0b-437d-8a53-ea71cb8043e5	1	e789114133c72da1c255f4bedf85dd6eff14ac41a3e5f2a2182c0a9890504a11	application/json	388	evidence/2fe254d9-ae0b-437d-8a53-ea71cb8043e5/v1/e789114133c72da1c255f4bedf85dd6eff14ac41a3e5f2a2182c0a9890504a11	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.680304+00
885ca079-e48f-4da9-9359-d6a3409b33bb	bbe54c6f-2024-4781-beee-7bb1c4f10234	1	20bfd386394c847d921199a34e88b4cc3cb13ebf3e8ba9c6c3c383f9d995a510	application/json	404	evidence/bbe54c6f-2024-4781-beee-7bb1c4f10234/v1/20bfd386394c847d921199a34e88b4cc3cb13ebf3e8ba9c6c3c383f9d995a510	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.761748+00
8be8fa24-d6a9-47b8-baf6-735afb9af30a	a5667964-1027-4f26-b6d5-3234f80dd54f	1	5eac50333691bba0749e81593f990aafd3d57b4263ed2ea28a14f7a73263d727	application/json	403	evidence/a5667964-1027-4f26-b6d5-3234f80dd54f/v1/5eac50333691bba0749e81593f990aafd3d57b4263ed2ea28a14f7a73263d727	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.842588+00
a3d93258-91b6-4b9c-bac8-ab8e4b88d567	d9886a1d-a255-4725-935d-d62cf11b8766	1	e403a32d91e8b38d1689c494aad1c9dd181a590aa9c13d22b98f2f87e4f29572	application/json	415	evidence/d9886a1d-a255-4725-935d-d62cf11b8766/v1/e403a32d91e8b38d1689c494aad1c9dd181a590aa9c13d22b98f2f87e4f29572	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.906226+00
bab14424-a412-420b-8a37-83d7fa654b42	ba85be2a-8251-4dac-bb80-4655f5a3979a	1	12d01d9a516c6921e4e72d7ca41d28095b575d02df31efdf64431f895398dcdf	application/json	385	evidence/ba85be2a-8251-4dac-bb80-4655f5a3979a/v1/12d01d9a516c6921e4e72d7ca41d28095b575d02df31efdf64431f895398dcdf	2026-09-15 06:37:30+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:30.968695+00
79bd95a6-f0bd-4188-88b2-0b0dd6f38bdc	252a8116-32db-4c3f-8bc4-0fe0eee53653	1	65ec17c4c6ab9bd5ce6e2fa2e07b550649c2fe6f3e9bb9f3644e26d5497ba22e	application/json	401	evidence/252a8116-32db-4c3f-8bc4-0fe0eee53653/v1/65ec17c4c6ab9bd5ce6e2fa2e07b550649c2fe6f3e9bb9f3644e26d5497ba22e	2026-09-15 06:37:31+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.025726+00
f94a1bef-b24a-40be-9057-ede21f9008f4	3ee6901d-e417-471c-a00e-0eb95e2b8491	1	1dd8ea5f547f2d24c62b9a1a5626cc534ad74f85856761b66205a0d08ea46aab	application/json	400	evidence/3ee6901d-e417-471c-a00e-0eb95e2b8491/v1/1dd8ea5f547f2d24c62b9a1a5626cc534ad74f85856761b66205a0d08ea46aab	2026-09-15 06:37:31+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.091645+00
8ef854b5-b3af-4ab3-af01-814b5d8dd4be	897683f8-35a9-4af6-b103-8f073087e332	1	e37820414fa5d245537b9d60337e59b8104c3940e79df81844390bd3acf57066	application/json	412	evidence/897683f8-35a9-4af6-b103-8f073087e332/v1/e37820414fa5d245537b9d60337e59b8104c3940e79df81844390bd3acf57066	2026-09-15 06:37:31+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.154248+00
ec4f8316-8e88-4b56-b957-ff2c32bba606	bff6261f-c689-4dee-95a9-281451058e08	1	0d41509fa14e8c67cffb418318cd2dc1921e8ab9603401d6c5086512542e139d	application/json	382	evidence/bff6261f-c689-4dee-95a9-281451058e08/v1/0d41509fa14e8c67cffb418318cd2dc1921e8ab9603401d6c5086512542e139d	2026-09-15 06:37:31+00	SIM_POSTGRES	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.221647+00
ae0bfa1f-0870-432b-b8d3-52eb1109f3e6	96ce1593-ee52-455f-ad08-64ec36bbff02	1	bf9f883c2967fc794c52d97e778d5982866e13a34d6acf5eda74b1e259ec1a33	application/json	400	evidence/96ce1593-ee52-455f-ad08-64ec36bbff02/v1/bf9f883c2967fc794c52d97e778d5982866e13a34d6acf5eda74b1e259ec1a33	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.296384+00
a0db63e7-02d1-4c58-8808-9ff89f73591c	62b53770-56b0-4556-9e8a-bcef611a5588	1	8e70e6a8752d54d5e14b9d64b316b5ddc8288809ea79caba38a2c397f7408a44	application/json	403	evidence/62b53770-56b0-4556-9e8a-bcef611a5588/v1/8e70e6a8752d54d5e14b9d64b316b5ddc8288809ea79caba38a2c397f7408a44	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.376276+00
bea7e95d-bb1e-425e-9559-0f4ecd18b3d4	21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed	1	0cdd38a0c529488a5e35d1d76688fa23d3ee1da9a9fa0dfa73f60cb396d96ebc	application/json	390	evidence/21b00e90-d5ab-49ff-ac65-dd5f2f5ff0ed/v1/0cdd38a0c529488a5e35d1d76688fa23d3ee1da9a9fa0dfa73f60cb396d96ebc	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.453732+00
89fd89cf-733f-45e0-98f9-7b5f9e1bf18b	3bb02091-449e-497e-9317-427cacc54837	1	328dcecb23b0f087073977be52c4fa3add48bc4f969e686798a13637890ca0a7	application/json	397	evidence/3bb02091-449e-497e-9317-427cacc54837/v1/328dcecb23b0f087073977be52c4fa3add48bc4f969e686798a13637890ca0a7	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.536426+00
864463d9-38da-465c-88ff-ee973858a746	d5d0f39b-6c07-4161-a96c-94dc258702df	1	fe98f6312f00f5e212bbe58d18b85389b7072c3a4665e8cf0455a6c177431a6c	application/json	400	evidence/d5d0f39b-6c07-4161-a96c-94dc258702df/v1/fe98f6312f00f5e212bbe58d18b85389b7072c3a4665e8cf0455a6c177431a6c	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.629458+00
395d98db-d29f-452c-abef-c7ac4a70a5f8	c3c77f08-3579-4394-84b0-8592dd97bbd8	1	6d292256726e57fc7daf601f0af79ac51aa96458e58bba63d74022bf888df516	application/json	387	evidence/c3c77f08-3579-4394-84b0-8592dd97bbd8/v1/6d292256726e57fc7daf601f0af79ac51aa96458e58bba63d74022bf888df516	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.704023+00
c9d18c4a-02c9-405d-aaf0-42159374d696	5ef049e2-85a0-44b1-9e80-d494a5043570	1	ae8aa79a46022a5f431facdd44bb409f60e876b6ca80895d8d35ddd356e949d2	application/json	394	evidence/5ef049e2-85a0-44b1-9e80-d494a5043570/v1/ae8aa79a46022a5f431facdd44bb409f60e876b6ca80895d8d35ddd356e949d2	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.770223+00
e9734d01-7b81-49fd-93c6-784d8daffbea	38f74afa-6f51-46c6-87c9-12929e812362	1	d0df489a1ae1ee21d43c1480f7ae2acdcc3bfa0027029a6a1c14e488f8986d7d	application/json	397	evidence/38f74afa-6f51-46c6-87c9-12929e812362/v1/d0df489a1ae1ee21d43c1480f7ae2acdcc3bfa0027029a6a1c14e488f8986d7d	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.861469+00
bc7e70d5-1afe-43f8-9f52-4d1356d87a3c	d452f3b9-ce2c-410a-968b-4dd45948eee6	1	a3bbf16c4db47de57b3be607f7d75261399fe4015fb7ace1df22c09819b8d8c0	application/json	384	evidence/d452f3b9-ce2c-410a-968b-4dd45948eee6/v1/a3bbf16c4db47de57b3be607f7d75261399fe4015fb7ace1df22c09819b8d8c0	2026-09-15 06:37:31+00	SIM_MYSQL	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:31.937123+00
0367f406-3de0-4393-bfdb-dca91f3d8efc	2874aafc-3723-40bc-b524-229f1b2d7b8c	1	d4975be8cf3b28eeb3b007ac81a7d969fb648c6e6942c2ae5f63f626bea4fb80	application/json	414	evidence/2874aafc-3723-40bc-b524-229f1b2d7b8c/v1/d4975be8cf3b28eeb3b007ac81a7d969fb648c6e6942c2ae5f63f626bea4fb80	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.017095+00
309ae49e-bec3-4a7f-a848-29d018957a39	86a41980-cbd9-478c-a4bc-371bc159f431	1	1edf718538421ebac454136acbd09bbdc87792aad8ddd495c435bb5b93e5662b	application/json	419	evidence/86a41980-cbd9-478c-a4bc-371bc159f431/v1/1edf718538421ebac454136acbd09bbdc87792aad8ddd495c435bb5b93e5662b	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.077083+00
4c93075c-312b-4120-bff4-53dde5a9721f	25b18e7e-bf5d-4d43-b1df-80cce7317bdc	1	f3843299319b8e1d01c30fd7d5eb8ebad9aa3a978fd81f9a7cd3bd47efc59d03	application/json	380	evidence/25b18e7e-bf5d-4d43-b1df-80cce7317bdc/v1/f3843299319b8e1d01c30fd7d5eb8ebad9aa3a978fd81f9a7cd3bd47efc59d03	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.143744+00
ab713e9b-6bff-49d5-a930-7ae59aa50535	b0946cad-efb7-4bee-bb25-1626fe14c229	1	3ed65603c5abe308fb20b34f4ebd2d03b05bb8642c6b6000003b2dca47a63e25	application/json	411	evidence/b0946cad-efb7-4bee-bb25-1626fe14c229/v1/3ed65603c5abe308fb20b34f4ebd2d03b05bb8642c6b6000003b2dca47a63e25	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.202577+00
160b9064-cea6-459c-a135-f108ebcbbc9e	68b6c810-9ce8-4c96-a653-133a4042efd6	1	98ef97151bd5ff7a539488edc1949214cfed573e9b637f93d2a9d7d8ff5f594e	application/json	416	evidence/68b6c810-9ce8-4c96-a653-133a4042efd6/v1/98ef97151bd5ff7a539488edc1949214cfed573e9b637f93d2a9d7d8ff5f594e	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.274247+00
eaf35f27-869f-432c-b67b-9ddacea9c3d4	6881638e-3f67-4cc8-a178-e67cd76a90bc	1	e2695ff8586707597839f534c1548daad70b6cc8d888610b6ac4946e591d04a7	application/json	377	evidence/6881638e-3f67-4cc8-a178-e67cd76a90bc/v1/e2695ff8586707597839f534c1548daad70b6cc8d888610b6ac4946e591d04a7	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.338532+00
80724f41-8cf1-4cd8-b19c-6f2ae1b56dce	49d5d6ba-7459-4bcf-901a-5424e414a32a	1	46138b664abb1f33c33451a5019f97e248a51b08b0300ed6a9fb8fa012ef82b1	application/json	408	evidence/49d5d6ba-7459-4bcf-901a-5424e414a32a/v1/46138b664abb1f33c33451a5019f97e248a51b08b0300ed6a9fb8fa012ef82b1	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.408136+00
55ede7de-f386-44c5-886b-79053a474390	a50ff65e-9cb6-4eb5-bacb-d0c44b565365	1	69eaaa6100649fec3dc94a09140f588bc92bbf9f5888ceea448f6296edeea47b	application/json	413	evidence/a50ff65e-9cb6-4eb5-bacb-d0c44b565365/v1/69eaaa6100649fec3dc94a09140f588bc92bbf9f5888ceea448f6296edeea47b	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.480954+00
cce57c3a-3418-40d7-9baf-8f70580c3131	d1e3fe4c-e868-4ac0-b8e8-37cf16149a08	1	030a1693b889f8d79fd217f29f302f8fcfa151409df31273888e66d5f52d7e85	application/json	374	evidence/d1e3fe4c-e868-4ac0-b8e8-37cf16149a08/v1/030a1693b889f8d79fd217f29f302f8fcfa151409df31273888e66d5f52d7e85	2026-09-15 06:37:32+00	SIM_NGINX	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.535233+00
0a930117-e3eb-496f-a81f-06726bea54d7	cc6d26ef-a350-4d32-9d2a-39d180443f44	1	fa08d84232f4a20d90b9f1be3ee152440c8095ec59916781f2f7438b164c1221	application/json	415	evidence/cc6d26ef-a350-4d32-9d2a-39d180443f44/v1/fa08d84232f4a20d90b9f1be3ee152440c8095ec59916781f2f7438b164c1221	2026-09-15 06:37:32+00	SIM_TOMCAT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.581244+00
445e0093-8033-4c6b-9a91-169637233c7c	0b589896-b23e-452f-bb27-cc5088dadf8d	1	08291f3515799bef745bba6286182248bf981e66dd575d9522555e4da98ca2e9	application/json	386	evidence/0b589896-b23e-452f-bb27-cc5088dadf8d/v1/08291f3515799bef745bba6286182248bf981e66dd575d9522555e4da98ca2e9	2026-09-15 06:37:32+00	SIM_TOMCAT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.627881+00
84d53274-8635-43a4-ab9b-7944d33daf09	f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4	1	1a3076e2061b5f1f9a5b8ab155afe30820f20ce27385f5dec4241578bf392cc0	application/json	412	evidence/f7a5a2c8-97e5-4ec1-8599-6a4602a8b2f4/v1/1a3076e2061b5f1f9a5b8ab155afe30820f20ce27385f5dec4241578bf392cc0	2026-09-15 06:37:32+00	SIM_TOMCAT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.691518+00
01995ecc-4171-46cc-9e70-02456a989c3e	61cc8dc0-0076-49e0-b694-2e9b3bb0bd42	1	582873f51d979c92fa3137fd5f1196398fc0cba8c91ad0d534701ad6a4a1b84d	application/json	383	evidence/61cc8dc0-0076-49e0-b694-2e9b3bb0bd42/v1/582873f51d979c92fa3137fd5f1196398fc0cba8c91ad0d534701ad6a4a1b84d	2026-09-15 06:37:32+00	SIM_TOMCAT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.749868+00
45ebc468-9f74-4ecb-935b-13580e99d4f7	9aba1297-9050-494d-a023-89166919ce50	1	e9fb758674261183544c5a32ef0a8363b34dba3e1a49afca89d624720d3a62e9	application/json	409	evidence/9aba1297-9050-494d-a023-89166919ce50/v1/e9fb758674261183544c5a32ef0a8363b34dba3e1a49afca89d624720d3a62e9	2026-09-15 06:37:32+00	SIM_TOMCAT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.810012+00
28cf658e-3cb4-4de2-b849-cac5b16f58e8	5edff7ca-679a-4517-bdda-31cc792ca6ff	1	f8395725c7a0c065475c27e9276e960be97cb747cb9ae53354508890b269e4e1	application/json	380	evidence/5edff7ca-679a-4517-bdda-31cc792ca6ff/v1/f8395725c7a0c065475c27e9276e960be97cb747cb9ae53354508890b269e4e1	2026-09-15 06:37:32+00	SIM_TOMCAT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.884957+00
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	31939122-58c2-4c45-832d-946552e91124	1	de22cbdd31e125e5ba13f2a8eafa1f4cdd6a034d3bfbab708dbef04fa01ae21a	application/json	246	evidence/31939122-58c2-4c45-832d-946552e91124/v1/de22cbdd31e125e5ba13f2a8eafa1f4cdd6a034d3bfbab708dbef04fa01ae21a	2026-09-15 06:37:32+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:32.936188+00
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	95719fec-0ab6-43d4-8097-2057cd8344fc	1	bc4e083f805371f91c1f4547da0c882942adec5fe1c445a953c32f206c2985d8	application/json	246	evidence/95719fec-0ab6-43d4-8097-2057cd8344fc/v1/bc4e083f805371f91c1f4547da0c882942adec5fe1c445a953c32f206c2985d8	2026-09-15 06:37:32+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.008916+00
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	05dc3d13-b4b5-4179-8c3b-dfc391fde2c3	1	7cfd86fa87064817435d64b0aee1ca005670575cf6a60e30f9f71daab1bed2d8	application/json	251	evidence/05dc3d13-b4b5-4179-8c3b-dfc391fde2c3/v1/7cfd86fa87064817435d64b0aee1ca005670575cf6a60e30f9f71daab1bed2d8	2026-09-15 06:37:32+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.074359+00
690e4e3e-b1bd-4171-9edb-d7b64f853184	8a4e1ff8-e3c7-445b-9766-4091f19de4e3	1	1a454add48792217177475af23ffddaf1b2cb102a4167679cb685edbae95c0db	application/json	243	evidence/8a4e1ff8-e3c7-445b-9766-4091f19de4e3/v1/1a454add48792217177475af23ffddaf1b2cb102a4167679cb685edbae95c0db	2026-09-15 06:37:33+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.145884+00
d41d7a44-bfe2-476b-b3f2-e0752faabbac	40630923-dd28-49e1-b40f-8169dee97763	1	2711b9c6b79378aed13d1cb8e35c5d0508799be5141ce7ab1abd52770f8f470c	application/json	243	evidence/40630923-dd28-49e1-b40f-8169dee97763/v1/2711b9c6b79378aed13d1cb8e35c5d0508799be5141ce7ab1abd52770f8f470c	2026-09-15 06:37:33+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.218815+00
51dafa36-0f44-4937-a61c-45c12f04189e	379fbd9a-38cd-4989-81ba-0f9bd533736d	1	728624ae0d33cd76a2beb57d5b84110c3b1e710f19ae877a823c95b7175bb122	application/json	248	evidence/379fbd9a-38cd-4989-81ba-0f9bd533736d/v1/728624ae0d33cd76a2beb57d5b84110c3b1e710f19ae877a823c95b7175bb122	2026-09-15 06:37:33+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.292715+00
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	e3167d52-f263-417c-964f-5523c33c1d91	1	d6c3f9b7700024fdc75009fca7d15399eba184946b7b49fd000386b6d35aba7d	application/json	240	evidence/e3167d52-f263-417c-964f-5523c33c1d91/v1/d6c3f9b7700024fdc75009fca7d15399eba184946b7b49fd000386b6d35aba7d	2026-09-15 06:37:33+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.349572+00
c6dd588f-a2c2-489a-bb73-83e937339ecf	969d7c02-eb0c-47af-a189-b94c42ce67dc	1	c36d58f806c41f9db8c6a6f26a2354b3c5237cafd24b9f061b236f67f313c73e	application/json	240	evidence/969d7c02-eb0c-47af-a189-b94c42ce67dc/v1/c36d58f806c41f9db8c6a6f26a2354b3c5237cafd24b9f061b236f67f313c73e	2026-09-15 06:37:33+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.413177+00
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	ef73d9bb-1f1a-4390-a202-4a056291a646	1	6c8adaba94e5667844f6ec0fd0f6e3969b7572b061dd1ea1519806d18449408b	application/json	245	evidence/ef73d9bb-1f1a-4390-a202-4a056291a646/v1/6c8adaba94e5667844f6ec0fd0f6e3969b7572b061dd1ea1519806d18449408b	2026-09-15 06:37:33+00	SHAREPOINT	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.486083+00
db0d26ec-bd9d-4224-a01f-b7f255d65828	654cb34d-ee4d-46f5-9f4b-966288fbee24	1	b175bdec976af78f3c4e59fbb3c13511ac79b1227733a23b5dec5810cec41f3c	application/json	245	evidence/654cb34d-ee4d-46f5-9f4b-966288fbee24/v1/b175bdec976af78f3c4e59fbb3c13511ac79b1227733a23b5dec5810cec41f3c	2026-09-15 06:37:33+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.548329+00
03677143-fbde-4bf5-a1a3-33f1535eac0f	44763b2a-ed03-42a3-b8c0-68662c2d9b67	1	757ebf21d27a70326dc3916bc868285cfd967e7dc05fa339c29af13b28141f80	application/json	246	evidence/44763b2a-ed03-42a3-b8c0-68662c2d9b67/v1/757ebf21d27a70326dc3916bc868285cfd967e7dc05fa339c29af13b28141f80	2026-09-15 06:37:33+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.625488+00
6daadb8a-86eb-439f-848e-cdcaf87020f9	2ed3b783-c765-4c35-8d28-3649d5ee5b98	1	2a2311cc36524afca6d0db7e3eb7c35c74938c8ec4185be881837b2b686011ce	application/json	260	evidence/2ed3b783-c765-4c35-8d28-3649d5ee5b98/v1/2a2311cc36524afca6d0db7e3eb7c35c74938c8ec4185be881837b2b686011ce	2026-09-15 06:37:33+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.699528+00
27c683ed-20c7-4cfb-aa36-d9314a46f82a	af1c6d8d-9cbd-4df4-97a7-21cf071ed37b	1	d592c46467e2f337971219ced1ea6f377f942982e59df3cff7473798d3f16ac2	application/json	242	evidence/af1c6d8d-9cbd-4df4-97a7-21cf071ed37b/v1/d592c46467e2f337971219ced1ea6f377f942982e59df3cff7473798d3f16ac2	2026-09-15 06:37:33+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.788715+00
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	c9c5a1fd-a7aa-4b30-96c6-efd0e681a745	1	2660a79ef98a809b010aafb6247c3c27f11af3611590485e3b4c126b4f0322ff	application/json	243	evidence/c9c5a1fd-a7aa-4b30-96c6-efd0e681a745/v1/2660a79ef98a809b010aafb6247c3c27f11af3611590485e3b4c126b4f0322ff	2026-09-15 06:37:33+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.860465+00
1870e402-051a-433c-ba5b-f7a0b07e225a	79296e0a-4136-445f-a85b-64d2607cc1a3	1	ceec4ccb8f6ba5876b688d2a8488b59ef36c0a64975f332583d0a9d071c13cd5	application/json	257	evidence/79296e0a-4136-445f-a85b-64d2607cc1a3/v1/ceec4ccb8f6ba5876b688d2a8488b59ef36c0a64975f332583d0a9d071c13cd5	2026-09-15 06:37:33+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:33.94522+00
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	f178b149-af5a-44fd-b461-cddfeb9a76d5	1	6cdc8a6f4dce408648e17551eea1bbcd568674b00210189228baee62dca8edbe	application/json	239	evidence/f178b149-af5a-44fd-b461-cddfeb9a76d5/v1/6cdc8a6f4dce408648e17551eea1bbcd568674b00210189228baee62dca8edbe	2026-09-15 06:37:34+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:34.035195+00
d820f730-a2eb-45c2-8aee-759153e7763d	81612e44-ed06-45f7-a8c1-89988c88de7b	1	ffddf055285e6b3e896876afeb4b85c203a985969849259a1c34068b1527df0a	application/json	240	evidence/81612e44-ed06-45f7-a8c1-89988c88de7b/v1/ffddf055285e6b3e896876afeb4b85c203a985969849259a1c34068b1527df0a	2026-09-15 06:37:34+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:34.16039+00
4efe5e01-9731-4b75-960e-28e15a43022c	c5ce959e-f256-4cef-84df-90502457d64c	1	f92ed430cd5c489296e2dc0add0d072ec5212eaee923da7c5aac2b8159ad7460	application/json	254	evidence/c5ce959e-f256-4cef-84df-90502457d64c/v1/f92ed430cd5c489296e2dc0add0d072ec5212eaee923da7c5aac2b8159ad7460	2026-09-15 06:37:34+00	SERVICENOW	a927fcf0-8177-4266-8e00-775b3a45ab77	2026-09-15 06:37:34.232188+00
2a0d655c-d8d7-442e-a5cb-3ec4a8103798	49606c9a-050b-4562-830f-dd5e58ab6b48	1	36bbe50ed96841d10443bcb670d6554f0a34b761be67ec9c4a8ad2c0c44ca42c	text/plain	5	evidence/49606c9a-050b-4562-830f-dd5e58ab6b48/v1/36bbe50ed96841d10443bcb670d6554f0a34b761be67ec9c4a8ad2c0c44ca42c	2026-09-15 06:38:00.751+00	ui-user	\N	2026-09-15 06:38:00.784524+00
\.


--
-- Data for Name: evidence_version_metadata; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.evidence_version_metadata (evidence_version_id, meta_key, meta_value) FROM stdin;
612a67b6-e6ab-4eb0-ae0b-e6435e6762ea	mock	true
612a67b6-e6ab-4eb0-ae0b-e6435e6762ea	digest.short	2773c02f2a59
612a67b6-e6ab-4eb0-ae0b-e6435e6762ea	collector	MOCK_JIRA
61e312e8-94e8-47cf-b7d3-c462b988cf2c	mock	true
61e312e8-94e8-47cf-b7d3-c462b988cf2c	digest.short	9bafb81e2f35
61e312e8-94e8-47cf-b7d3-c462b988cf2c	collector	MOCK_JIRA
1fe98d20-896d-434c-ad8d-480d71b0752d	mock	true
1fe98d20-896d-434c-ad8d-480d71b0752d	digest.short	b2317796fb07
1fe98d20-896d-434c-ad8d-480d71b0752d	collector	MOCK_JIRA
63e2f8da-ad19-4dfe-a3b5-269d524615e4	mock	true
63e2f8da-ad19-4dfe-a3b5-269d524615e4	digest.short	cda45cac5684
63e2f8da-ad19-4dfe-a3b5-269d524615e4	collector	MOCK_JIRA
1d48d771-d471-412a-ae68-41e15bc4be0e	mock	true
1d48d771-d471-412a-ae68-41e15bc4be0e	digest.short	90f4a5443a13
1d48d771-d471-412a-ae68-41e15bc4be0e	collector	MOCK_JIRA
831c1efb-ad04-4470-833a-540dcd2103e2	mock	true
831c1efb-ad04-4470-833a-540dcd2103e2	digest.short	e5bd892dbbce
831c1efb-ad04-4470-833a-540dcd2103e2	collector	MOCK_JIRA
7463f8fa-0d15-4c84-9da7-b341d2c7e004	mock	true
7463f8fa-0d15-4c84-9da7-b341d2c7e004	digest.short	54c4e03b4883
7463f8fa-0d15-4c84-9da7-b341d2c7e004	collector	MOCK_SERVICENOW
75022dc6-7f68-463b-b311-4dc3db75b929	mock	true
75022dc6-7f68-463b-b311-4dc3db75b929	digest.short	83a4560f72ee
75022dc6-7f68-463b-b311-4dc3db75b929	collector	MOCK_SERVICENOW
2df50382-7d87-4a1f-8d0d-2a09b419536f	mock	true
2df50382-7d87-4a1f-8d0d-2a09b419536f	digest.short	c6810c7cc2a8
2df50382-7d87-4a1f-8d0d-2a09b419536f	collector	MOCK_SERVICENOW
d5b4ef61-7f7a-40a6-b3f8-1a91078bb9dd	mock	true
d5b4ef61-7f7a-40a6-b3f8-1a91078bb9dd	digest.short	a579056be760
d5b4ef61-7f7a-40a6-b3f8-1a91078bb9dd	collector	MOCK_SERVICENOW
6ea84455-c871-43a1-9a30-4fe2bc7b491b	mock	true
6ea84455-c871-43a1-9a30-4fe2bc7b491b	digest.short	d578f160235a
6ea84455-c871-43a1-9a30-4fe2bc7b491b	collector	MOCK_SERVICENOW
631f064a-6c35-4867-b300-069b241c09a9	mock	true
631f064a-6c35-4867-b300-069b241c09a9	digest.short	e71280f49f4d
631f064a-6c35-4867-b300-069b241c09a9	collector	MOCK_SERVICENOW
07253b4b-34bc-4384-8da5-43a35ada05cb	mock	true
07253b4b-34bc-4384-8da5-43a35ada05cb	digest.short	2ef8adc1bb29
07253b4b-34bc-4384-8da5-43a35ada05cb	collector	MOCK_GITHUB
c138132b-9ab0-4ec7-84c7-b0a30c9964b9	mock	true
c138132b-9ab0-4ec7-84c7-b0a30c9964b9	digest.short	49cc556941ea
c138132b-9ab0-4ec7-84c7-b0a30c9964b9	collector	MOCK_GITHUB
afbba79b-e661-4fe7-bbd6-118ee8816a09	mock	true
afbba79b-e661-4fe7-bbd6-118ee8816a09	digest.short	cb76b392669f
afbba79b-e661-4fe7-bbd6-118ee8816a09	collector	MOCK_GITHUB
64c5905e-2f6d-483a-ac1d-2b5b33093a97	mock	true
64c5905e-2f6d-483a-ac1d-2b5b33093a97	digest.short	a4f5f7675007
64c5905e-2f6d-483a-ac1d-2b5b33093a97	collector	MOCK_GITHUB
6a8a71a8-7882-4158-a050-a59e1fd41343	mock	true
6a8a71a8-7882-4158-a050-a59e1fd41343	digest.short	7a1fcfe9eabd
6a8a71a8-7882-4158-a050-a59e1fd41343	collector	MOCK_GITHUB
c95767f9-cc99-4b78-aeba-0298ce4f05cc	mock	true
c95767f9-cc99-4b78-aeba-0298ce4f05cc	digest.short	9c2b58d5a0b3
c95767f9-cc99-4b78-aeba-0298ce4f05cc	collector	MOCK_GITHUB
4d503d2a-9e1f-41cb-b9a5-b9446548157d	mock	true
4d503d2a-9e1f-41cb-b9a5-b9446548157d	digest.short	cb76aa5c6d41
4d503d2a-9e1f-41cb-b9a5-b9446548157d	collector	MOCK_CONFLUENCE
925fbbfd-2442-4a7b-87f7-1e7cb2963bb7	mock	true
925fbbfd-2442-4a7b-87f7-1e7cb2963bb7	digest.short	5c868252c15d
925fbbfd-2442-4a7b-87f7-1e7cb2963bb7	collector	MOCK_CONFLUENCE
95892f53-fb69-4ed5-9e8d-9302a77b744e	mock	true
95892f53-fb69-4ed5-9e8d-9302a77b744e	digest.short	32e46f164025
95892f53-fb69-4ed5-9e8d-9302a77b744e	collector	MOCK_CONFLUENCE
58564bad-5f77-485d-8c31-fa54ccedd45a	mock	true
58564bad-5f77-485d-8c31-fa54ccedd45a	digest.short	190a3693a422
58564bad-5f77-485d-8c31-fa54ccedd45a	collector	MOCK_CONFLUENCE
e3dce241-04d8-45d6-aa41-b005bc76060b	mock	true
e3dce241-04d8-45d6-aa41-b005bc76060b	digest.short	cc56f053e9a4
e3dce241-04d8-45d6-aa41-b005bc76060b	collector	MOCK_CONFLUENCE
574bd101-ef2d-4cca-9f96-ad1e4be92868	mock	true
574bd101-ef2d-4cca-9f96-ad1e4be92868	digest.short	67ff45ef7176
574bd101-ef2d-4cca-9f96-ad1e4be92868	collector	MOCK_CONFLUENCE
b15acf59-d3ab-4881-81e1-8093709a9477	digest.short	475580fadf95
b15acf59-d3ab-4881-81e1-8093709a9477	technology	postgresql
b15acf59-d3ab-4881-81e1-8093709a9477	collector	database
b15acf59-d3ab-4881-81e1-8093709a9477	simulated	true
6e44556f-b5ba-4bab-bfc4-cd43a77e0282	digest.short	13895ef0fd3e
6e44556f-b5ba-4bab-bfc4-cd43a77e0282	technology	postgresql
6e44556f-b5ba-4bab-bfc4-cd43a77e0282	collector	database
6e44556f-b5ba-4bab-bfc4-cd43a77e0282	simulated	true
1253de3a-738f-471b-b022-b7059a871b0b	digest.short	e9145c7212c2
1253de3a-738f-471b-b022-b7059a871b0b	technology	postgresql
1253de3a-738f-471b-b022-b7059a871b0b	collector	database
1253de3a-738f-471b-b022-b7059a871b0b	simulated	true
e223babe-2fa1-4e27-942b-80802c13a564	digest.short	e789114133c7
e223babe-2fa1-4e27-942b-80802c13a564	technology	postgresql
e223babe-2fa1-4e27-942b-80802c13a564	collector	database
e223babe-2fa1-4e27-942b-80802c13a564	simulated	true
885ca079-e48f-4da9-9359-d6a3409b33bb	digest.short	20bfd386394c
885ca079-e48f-4da9-9359-d6a3409b33bb	technology	postgresql
885ca079-e48f-4da9-9359-d6a3409b33bb	collector	database
885ca079-e48f-4da9-9359-d6a3409b33bb	simulated	true
8be8fa24-d6a9-47b8-baf6-735afb9af30a	digest.short	5eac50333691
8be8fa24-d6a9-47b8-baf6-735afb9af30a	technology	postgresql
8be8fa24-d6a9-47b8-baf6-735afb9af30a	collector	database
8be8fa24-d6a9-47b8-baf6-735afb9af30a	simulated	true
a3d93258-91b6-4b9c-bac8-ab8e4b88d567	digest.short	e403a32d91e8
a3d93258-91b6-4b9c-bac8-ab8e4b88d567	technology	postgresql
a3d93258-91b6-4b9c-bac8-ab8e4b88d567	collector	database
a3d93258-91b6-4b9c-bac8-ab8e4b88d567	simulated	true
bab14424-a412-420b-8a37-83d7fa654b42	digest.short	12d01d9a516c
bab14424-a412-420b-8a37-83d7fa654b42	technology	postgresql
bab14424-a412-420b-8a37-83d7fa654b42	collector	database
bab14424-a412-420b-8a37-83d7fa654b42	simulated	true
79bd95a6-f0bd-4188-88b2-0b0dd6f38bdc	digest.short	65ec17c4c6ab
79bd95a6-f0bd-4188-88b2-0b0dd6f38bdc	technology	postgresql
79bd95a6-f0bd-4188-88b2-0b0dd6f38bdc	collector	database
79bd95a6-f0bd-4188-88b2-0b0dd6f38bdc	simulated	true
f94a1bef-b24a-40be-9057-ede21f9008f4	digest.short	1dd8ea5f547f
f94a1bef-b24a-40be-9057-ede21f9008f4	technology	postgresql
f94a1bef-b24a-40be-9057-ede21f9008f4	collector	database
f94a1bef-b24a-40be-9057-ede21f9008f4	simulated	true
8ef854b5-b3af-4ab3-af01-814b5d8dd4be	digest.short	e37820414fa5
8ef854b5-b3af-4ab3-af01-814b5d8dd4be	technology	postgresql
8ef854b5-b3af-4ab3-af01-814b5d8dd4be	collector	database
8ef854b5-b3af-4ab3-af01-814b5d8dd4be	simulated	true
ec4f8316-8e88-4b56-b957-ff2c32bba606	digest.short	0d41509fa14e
ec4f8316-8e88-4b56-b957-ff2c32bba606	technology	postgresql
ec4f8316-8e88-4b56-b957-ff2c32bba606	collector	database
ec4f8316-8e88-4b56-b957-ff2c32bba606	simulated	true
ae0bfa1f-0870-432b-b8d3-52eb1109f3e6	digest.short	bf9f883c2967
ae0bfa1f-0870-432b-b8d3-52eb1109f3e6	technology	mysql
ae0bfa1f-0870-432b-b8d3-52eb1109f3e6	collector	database
ae0bfa1f-0870-432b-b8d3-52eb1109f3e6	simulated	true
a0db63e7-02d1-4c58-8808-9ff89f73591c	digest.short	8e70e6a8752d
a0db63e7-02d1-4c58-8808-9ff89f73591c	technology	mysql
a0db63e7-02d1-4c58-8808-9ff89f73591c	collector	database
a0db63e7-02d1-4c58-8808-9ff89f73591c	simulated	true
bea7e95d-bb1e-425e-9559-0f4ecd18b3d4	digest.short	0cdd38a0c529
bea7e95d-bb1e-425e-9559-0f4ecd18b3d4	technology	mysql
bea7e95d-bb1e-425e-9559-0f4ecd18b3d4	collector	database
bea7e95d-bb1e-425e-9559-0f4ecd18b3d4	simulated	true
89fd89cf-733f-45e0-98f9-7b5f9e1bf18b	digest.short	328dcecb23b0
89fd89cf-733f-45e0-98f9-7b5f9e1bf18b	technology	mysql
89fd89cf-733f-45e0-98f9-7b5f9e1bf18b	collector	database
89fd89cf-733f-45e0-98f9-7b5f9e1bf18b	simulated	true
864463d9-38da-465c-88ff-ee973858a746	digest.short	fe98f6312f00
864463d9-38da-465c-88ff-ee973858a746	technology	mysql
864463d9-38da-465c-88ff-ee973858a746	collector	database
864463d9-38da-465c-88ff-ee973858a746	simulated	true
395d98db-d29f-452c-abef-c7ac4a70a5f8	digest.short	6d292256726e
395d98db-d29f-452c-abef-c7ac4a70a5f8	technology	mysql
395d98db-d29f-452c-abef-c7ac4a70a5f8	collector	database
395d98db-d29f-452c-abef-c7ac4a70a5f8	simulated	true
c9d18c4a-02c9-405d-aaf0-42159374d696	digest.short	ae8aa79a4602
c9d18c4a-02c9-405d-aaf0-42159374d696	technology	mysql
c9d18c4a-02c9-405d-aaf0-42159374d696	collector	database
c9d18c4a-02c9-405d-aaf0-42159374d696	simulated	true
e9734d01-7b81-49fd-93c6-784d8daffbea	digest.short	d0df489a1ae1
e9734d01-7b81-49fd-93c6-784d8daffbea	technology	mysql
e9734d01-7b81-49fd-93c6-784d8daffbea	collector	database
e9734d01-7b81-49fd-93c6-784d8daffbea	simulated	true
bc7e70d5-1afe-43f8-9f52-4d1356d87a3c	digest.short	a3bbf16c4db4
bc7e70d5-1afe-43f8-9f52-4d1356d87a3c	technology	mysql
bc7e70d5-1afe-43f8-9f52-4d1356d87a3c	collector	database
bc7e70d5-1afe-43f8-9f52-4d1356d87a3c	simulated	true
0367f406-3de0-4393-bfdb-dca91f3d8efc	digest.short	d4975be8cf3b
0367f406-3de0-4393-bfdb-dca91f3d8efc	technology	nginx
0367f406-3de0-4393-bfdb-dca91f3d8efc	collector	middleware
0367f406-3de0-4393-bfdb-dca91f3d8efc	simulated	true
309ae49e-bec3-4a7f-a848-29d018957a39	digest.short	1edf71853842
309ae49e-bec3-4a7f-a848-29d018957a39	technology	nginx
309ae49e-bec3-4a7f-a848-29d018957a39	collector	middleware
309ae49e-bec3-4a7f-a848-29d018957a39	simulated	true
4c93075c-312b-4120-bff4-53dde5a9721f	digest.short	f3843299319b
4c93075c-312b-4120-bff4-53dde5a9721f	technology	nginx
4c93075c-312b-4120-bff4-53dde5a9721f	collector	middleware
4c93075c-312b-4120-bff4-53dde5a9721f	simulated	true
ab713e9b-6bff-49d5-a930-7ae59aa50535	digest.short	3ed65603c5ab
ab713e9b-6bff-49d5-a930-7ae59aa50535	technology	nginx
ab713e9b-6bff-49d5-a930-7ae59aa50535	collector	middleware
ab713e9b-6bff-49d5-a930-7ae59aa50535	simulated	true
160b9064-cea6-459c-a135-f108ebcbbc9e	digest.short	98ef97151bd5
160b9064-cea6-459c-a135-f108ebcbbc9e	technology	nginx
160b9064-cea6-459c-a135-f108ebcbbc9e	collector	middleware
160b9064-cea6-459c-a135-f108ebcbbc9e	simulated	true
eaf35f27-869f-432c-b67b-9ddacea9c3d4	digest.short	e2695ff85867
eaf35f27-869f-432c-b67b-9ddacea9c3d4	technology	nginx
eaf35f27-869f-432c-b67b-9ddacea9c3d4	collector	middleware
eaf35f27-869f-432c-b67b-9ddacea9c3d4	simulated	true
80724f41-8cf1-4cd8-b19c-6f2ae1b56dce	digest.short	46138b664abb
80724f41-8cf1-4cd8-b19c-6f2ae1b56dce	technology	nginx
80724f41-8cf1-4cd8-b19c-6f2ae1b56dce	collector	middleware
80724f41-8cf1-4cd8-b19c-6f2ae1b56dce	simulated	true
55ede7de-f386-44c5-886b-79053a474390	digest.short	69eaaa610064
55ede7de-f386-44c5-886b-79053a474390	technology	nginx
55ede7de-f386-44c5-886b-79053a474390	collector	middleware
55ede7de-f386-44c5-886b-79053a474390	simulated	true
cce57c3a-3418-40d7-9baf-8f70580c3131	digest.short	030a1693b889
cce57c3a-3418-40d7-9baf-8f70580c3131	technology	nginx
cce57c3a-3418-40d7-9baf-8f70580c3131	collector	middleware
cce57c3a-3418-40d7-9baf-8f70580c3131	simulated	true
0a930117-e3eb-496f-a81f-06726bea54d7	digest.short	fa08d84232f4
0a930117-e3eb-496f-a81f-06726bea54d7	technology	tomcat
0a930117-e3eb-496f-a81f-06726bea54d7	collector	middleware
0a930117-e3eb-496f-a81f-06726bea54d7	simulated	true
445e0093-8033-4c6b-9a91-169637233c7c	digest.short	08291f351579
445e0093-8033-4c6b-9a91-169637233c7c	technology	tomcat
445e0093-8033-4c6b-9a91-169637233c7c	collector	middleware
445e0093-8033-4c6b-9a91-169637233c7c	simulated	true
84d53274-8635-43a4-ab9b-7944d33daf09	digest.short	1a3076e2061b
84d53274-8635-43a4-ab9b-7944d33daf09	technology	tomcat
84d53274-8635-43a4-ab9b-7944d33daf09	collector	middleware
84d53274-8635-43a4-ab9b-7944d33daf09	simulated	true
01995ecc-4171-46cc-9e70-02456a989c3e	digest.short	582873f51d97
01995ecc-4171-46cc-9e70-02456a989c3e	technology	tomcat
01995ecc-4171-46cc-9e70-02456a989c3e	collector	middleware
01995ecc-4171-46cc-9e70-02456a989c3e	simulated	true
45ebc468-9f74-4ecb-935b-13580e99d4f7	digest.short	e9fb75867426
45ebc468-9f74-4ecb-935b-13580e99d4f7	technology	tomcat
45ebc468-9f74-4ecb-935b-13580e99d4f7	collector	middleware
45ebc468-9f74-4ecb-935b-13580e99d4f7	simulated	true
28cf658e-3cb4-4de2-b849-cac5b16f58e8	digest.short	f8395725c7a0
28cf658e-3cb4-4de2-b849-cac5b16f58e8	technology	tomcat
28cf658e-3cb4-4de2-b849-cac5b16f58e8	collector	middleware
28cf658e-3cb4-4de2-b849-cac5b16f58e8	simulated	true
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	mock	true
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	digest.short	de22cbdd31e1
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	collector	SHAREPOINT
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	system.mode	mock
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
4a8111b1-8e91-47c9-b5b2-7f1e05f33ed2	system	sharepoint
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	mock	true
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	digest.short	bc4e083f8053
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	collector	SHAREPOINT
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	system.mode	mock
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
2293ea65-8b0d-46d0-8bfb-f7289cf76d28	system	sharepoint
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	mock	true
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	digest.short	7cfd86fa8706
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	collector	SHAREPOINT
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	system.mode	mock
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
ccc8d67f-d1bd-4b41-a8d4-68756bd5d48a	system	sharepoint
690e4e3e-b1bd-4171-9edb-d7b64f853184	mock	true
690e4e3e-b1bd-4171-9edb-d7b64f853184	digest.short	1a454add4879
690e4e3e-b1bd-4171-9edb-d7b64f853184	collector	SHAREPOINT
690e4e3e-b1bd-4171-9edb-d7b64f853184	system.mode	mock
690e4e3e-b1bd-4171-9edb-d7b64f853184	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
690e4e3e-b1bd-4171-9edb-d7b64f853184	system	sharepoint
d41d7a44-bfe2-476b-b3f2-e0752faabbac	mock	true
d41d7a44-bfe2-476b-b3f2-e0752faabbac	digest.short	2711b9c6b793
d41d7a44-bfe2-476b-b3f2-e0752faabbac	collector	SHAREPOINT
d41d7a44-bfe2-476b-b3f2-e0752faabbac	system.mode	mock
d41d7a44-bfe2-476b-b3f2-e0752faabbac	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
d41d7a44-bfe2-476b-b3f2-e0752faabbac	system	sharepoint
51dafa36-0f44-4937-a61c-45c12f04189e	mock	true
51dafa36-0f44-4937-a61c-45c12f04189e	digest.short	728624ae0d33
51dafa36-0f44-4937-a61c-45c12f04189e	collector	SHAREPOINT
51dafa36-0f44-4937-a61c-45c12f04189e	system.mode	mock
51dafa36-0f44-4937-a61c-45c12f04189e	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
51dafa36-0f44-4937-a61c-45c12f04189e	system	sharepoint
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	mock	true
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	digest.short	d6c3f9b77000
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	collector	SHAREPOINT
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	system.mode	mock
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
2dfb9a91-91ad-469d-a8e9-fc3d899f9a32	system	sharepoint
c6dd588f-a2c2-489a-bb73-83e937339ecf	mock	true
c6dd588f-a2c2-489a-bb73-83e937339ecf	digest.short	c36d58f806c4
c6dd588f-a2c2-489a-bb73-83e937339ecf	collector	SHAREPOINT
c6dd588f-a2c2-489a-bb73-83e937339ecf	system.mode	mock
c6dd588f-a2c2-489a-bb73-83e937339ecf	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
c6dd588f-a2c2-489a-bb73-83e937339ecf	system	sharepoint
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	mock	true
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	digest.short	6c8adaba94e5
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	collector	SHAREPOINT
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	system.mode	mock
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	system.baseUrl	https://contoso.sharepoint.com/sites/compliance
0f7d6cf6-74a5-4fc1-acb4-347025b1c17a	system	sharepoint
db0d26ec-bd9d-4224-a01f-b7f255d65828	mock	true
db0d26ec-bd9d-4224-a01f-b7f255d65828	digest.short	b175bdec976a
db0d26ec-bd9d-4224-a01f-b7f255d65828	collector	SERVICENOW
db0d26ec-bd9d-4224-a01f-b7f255d65828	system.mode	mock
db0d26ec-bd9d-4224-a01f-b7f255d65828	system.baseUrl	https://dev00000.service-now.com
db0d26ec-bd9d-4224-a01f-b7f255d65828	system	servicenow
03677143-fbde-4bf5-a1a3-33f1535eac0f	mock	true
03677143-fbde-4bf5-a1a3-33f1535eac0f	digest.short	757ebf21d27a
03677143-fbde-4bf5-a1a3-33f1535eac0f	collector	SERVICENOW
03677143-fbde-4bf5-a1a3-33f1535eac0f	system.mode	mock
03677143-fbde-4bf5-a1a3-33f1535eac0f	system.baseUrl	https://dev00000.service-now.com
03677143-fbde-4bf5-a1a3-33f1535eac0f	system	servicenow
6daadb8a-86eb-439f-848e-cdcaf87020f9	mock	true
6daadb8a-86eb-439f-848e-cdcaf87020f9	digest.short	2a2311cc3652
6daadb8a-86eb-439f-848e-cdcaf87020f9	collector	SERVICENOW
6daadb8a-86eb-439f-848e-cdcaf87020f9	system.mode	mock
6daadb8a-86eb-439f-848e-cdcaf87020f9	system.baseUrl	https://dev00000.service-now.com
6daadb8a-86eb-439f-848e-cdcaf87020f9	system	servicenow
27c683ed-20c7-4cfb-aa36-d9314a46f82a	mock	true
27c683ed-20c7-4cfb-aa36-d9314a46f82a	digest.short	d592c46467e2
27c683ed-20c7-4cfb-aa36-d9314a46f82a	collector	SERVICENOW
27c683ed-20c7-4cfb-aa36-d9314a46f82a	system.mode	mock
27c683ed-20c7-4cfb-aa36-d9314a46f82a	system.baseUrl	https://dev00000.service-now.com
27c683ed-20c7-4cfb-aa36-d9314a46f82a	system	servicenow
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	mock	true
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	digest.short	2660a79ef98a
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	collector	SERVICENOW
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	system.mode	mock
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	system.baseUrl	https://dev00000.service-now.com
c6e10d92-0246-4af6-b1a6-7063b40e8ab6	system	servicenow
1870e402-051a-433c-ba5b-f7a0b07e225a	mock	true
1870e402-051a-433c-ba5b-f7a0b07e225a	digest.short	ceec4ccb8f6b
1870e402-051a-433c-ba5b-f7a0b07e225a	collector	SERVICENOW
1870e402-051a-433c-ba5b-f7a0b07e225a	system.mode	mock
1870e402-051a-433c-ba5b-f7a0b07e225a	system.baseUrl	https://dev00000.service-now.com
1870e402-051a-433c-ba5b-f7a0b07e225a	system	servicenow
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	mock	true
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	digest.short	6cdc8a6f4dce
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	collector	SERVICENOW
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	system.mode	mock
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	system.baseUrl	https://dev00000.service-now.com
cf30b99c-cd0c-4767-9c22-1685d8c4e7b6	system	servicenow
d820f730-a2eb-45c2-8aee-759153e7763d	mock	true
d820f730-a2eb-45c2-8aee-759153e7763d	digest.short	ffddf055285e
d820f730-a2eb-45c2-8aee-759153e7763d	collector	SERVICENOW
d820f730-a2eb-45c2-8aee-759153e7763d	system.mode	mock
d820f730-a2eb-45c2-8aee-759153e7763d	system.baseUrl	https://dev00000.service-now.com
d820f730-a2eb-45c2-8aee-759153e7763d	system	servicenow
4efe5e01-9731-4b75-960e-28e15a43022c	mock	true
4efe5e01-9731-4b75-960e-28e15a43022c	digest.short	f92ed430cd5c
4efe5e01-9731-4b75-960e-28e15a43022c	collector	SERVICENOW
4efe5e01-9731-4b75-960e-28e15a43022c	system.mode	mock
4efe5e01-9731-4b75-960e-28e15a43022c	system.baseUrl	https://dev00000.service-now.com
4efe5e01-9731-4b75-960e-28e15a43022c	system	servicenow
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	init	SQL	V1__init.sql	1230699918	pramaan	2026-09-15 11:30:01.767247	144	t
2	2	seed reference	SQL	V2__seed_reference.sql	703474383	pramaan	2026-09-15 11:30:01.93722	7	t
3	3	check results	SQL	V3__check_results.sql	-1076988999	pramaan	2026-09-15 11:30:01.958441	40	t
4	4	evidence lifecycle	SQL	V4__evidence_lifecycle.sql	-1484087561	pramaan	2026-09-15 11:30:02.015391	25	t
5	5	compliance snapshot	SQL	V5__compliance_snapshot.sql	-435736199	pramaan	2026-09-15 11:30:02.054528	14	t
6	6	application active	SQL	V6__application_active.sql	2000324923	pramaan	2026-09-15 11:30:02.080971	8	t
7	7	snapshot evidence metrics	SQL	V7__snapshot_evidence_metrics.sql	267695465	pramaan	2026-09-15 11:30:02.100904	3	t
8	8	onboarding scan	SQL	V8__onboarding_scan.sql	1297779540	pramaan	2026-09-15 11:30:02.116953	39	t
9	9	evidence approval rbac	SQL	V9__evidence_approval_rbac.sql	2038309373	pramaan	2026-09-15 11:30:02.168931	13	t
10	10	evidence record sha256 varchar	SQL	V10__evidence_record_sha256_varchar.sql	85461758	pramaan	2026-09-15 11:51:15.484638	62	t
11	11	evidence version sha256 varchar	SQL	V11__evidence_version_sha256_varchar.sql	-1595638935	pramaan	2026-09-15 11:51:15.574313	26	t
\.


--
-- Data for Name: onboarding_scan; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.onboarding_scan (id, application_slug, status, requested_by, frameworks, sources, current_phase, scheduler_run_id, completeness_pct, compliance_pct, message, created_at, started_at, finished_at) FROM stdin;
\.


--
-- Data for Name: onboarding_scan_phase; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.onboarding_scan_phase (onboarding_scan_id, phase_key, phase_status) FROM stdin;
\.


--
-- Data for Name: scheduler_run; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.scheduler_run (id, trigger_type, status, requested_by, applications, frameworks, sources, received, ingested, duplicates, failed, message, created_at, started_at, finished_at) FROM stdin;
a927fcf0-8177-4266-8e00-775b3a45ab77	MANUAL	COMPLETED	ui	\N	\N	MOCK_JIRA,MOCK_SERVICENOW,MOCK_GITHUB,MOCK_CONFLUENCE,SIM_POSTGRES,SIM_MYSQL,SIM_NGINX,SIM_TOMCAT,SHAREPOINT,SERVICENOW	78	78	0	0	78 received, 78 ingested, 0 duplicates, 0 failed	2026-09-15 06:37:28.191438+00	2026-09-15 06:37:28.238614+00	2026-09-15 06:37:34.287021+00
f86662f8-7046-47ff-9582-ae829ef999c6	MANUAL	FAILED	ui	\N	\N	MOCK_JIRA,MOCK_SERVICENOW,MOCK_GITHUB,MOCK_CONFLUENCE,SIM_POSTGRES,SIM_MYSQL,SIM_NGINX,SIM_TOMCAT,SHAREPOINT,SERVICENOW	78	0	0	78	78 received, 0 ingested, 0 duplicates, 78 failed	2026-09-15 06:48:42.778099+00	2026-09-15 06:48:42.818183+00	2026-09-15 06:48:44.771907+00
\.


--
-- Data for Name: scheduler_run_source; Type: TABLE DATA; Schema: pramaan; Owner: -
--

COPY pramaan.scheduler_run_source (scheduler_run_id, source_key, source_summary) FROM stdin;
a927fcf0-8177-4266-8e00-775b3a45ab77	MOCK_GITHUB	received=6 ingested=6 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	SIM_MYSQL	received=9 ingested=9 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	SHAREPOINT	received=9 ingested=9 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	MOCK_SERVICENOW	received=6 ingested=6 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	MOCK_CONFLUENCE	received=6 ingested=6 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	SIM_POSTGRES	received=12 ingested=12 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	SIM_NGINX	received=9 ingested=9 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	SERVICENOW	received=9 ingested=9 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	SIM_TOMCAT	received=6 ingested=6 duplicates=0 failed=0
a927fcf0-8177-4266-8e00-775b3a45ab77	MOCK_JIRA	received=6 ingested=6 duplicates=0 failed=0
f86662f8-7046-47ff-9582-ae829ef999c6	MOCK_GITHUB	received=6 ingested=0 duplicates=0 failed=6
f86662f8-7046-47ff-9582-ae829ef999c6	SIM_MYSQL	received=9 ingested=0 duplicates=0 failed=9
f86662f8-7046-47ff-9582-ae829ef999c6	SHAREPOINT	received=9 ingested=0 duplicates=0 failed=9
f86662f8-7046-47ff-9582-ae829ef999c6	MOCK_SERVICENOW	received=6 ingested=0 duplicates=0 failed=6
f86662f8-7046-47ff-9582-ae829ef999c6	MOCK_CONFLUENCE	received=6 ingested=0 duplicates=0 failed=6
f86662f8-7046-47ff-9582-ae829ef999c6	SIM_POSTGRES	received=12 ingested=0 duplicates=0 failed=12
f86662f8-7046-47ff-9582-ae829ef999c6	SIM_NGINX	received=9 ingested=0 duplicates=0 failed=9
f86662f8-7046-47ff-9582-ae829ef999c6	SERVICENOW	received=9 ingested=0 duplicates=0 failed=9
f86662f8-7046-47ff-9582-ae829ef999c6	SIM_TOMCAT	received=6 ingested=0 duplicates=0 failed=6
f86662f8-7046-47ff-9582-ae829ef999c6	MOCK_JIRA	received=6 ingested=0 duplicates=0 failed=6
\.


--
-- Name: applications applications_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.applications
    ADD CONSTRAINT applications_pkey PRIMARY KEY (id);


--
-- Name: applications applications_slug_key; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.applications
    ADD CONSTRAINT applications_slug_key UNIQUE (slug);


--
-- Name: check_result check_result_evidence_record_id_check_id_key; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.check_result
    ADD CONSTRAINT check_result_evidence_record_id_check_id_key UNIQUE (evidence_record_id, check_id);


--
-- Name: check_result check_result_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.check_result
    ADD CONSTRAINT check_result_pkey PRIMARY KEY (id);


--
-- Name: compliance_snapshot compliance_snapshot_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.compliance_snapshot
    ADD CONSTRAINT compliance_snapshot_pkey PRIMARY KEY (id);


--
-- Name: evidence_approval_role evidence_approval_role_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_approval_role
    ADD CONSTRAINT evidence_approval_role_pkey PRIMARY KEY (role);


--
-- Name: evidence_approval_user evidence_approval_user_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_approval_user
    ADD CONSTRAINT evidence_approval_user_pkey PRIMARY KEY (username);


--
-- Name: evidence_lifecycle_event evidence_lifecycle_event_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_lifecycle_event
    ADD CONSTRAINT evidence_lifecycle_event_pkey PRIMARY KEY (id);


--
-- Name: evidence_record evidence_record_evidence_key_key; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_record
    ADD CONSTRAINT evidence_record_evidence_key_key UNIQUE (evidence_key);


--
-- Name: evidence_record evidence_record_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_record
    ADD CONSTRAINT evidence_record_pkey PRIMARY KEY (id);


--
-- Name: evidence_tag evidence_tag_evidence_record_id_tag_key_key; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_tag
    ADD CONSTRAINT evidence_tag_evidence_record_id_tag_key_key UNIQUE (evidence_record_id, tag_key);


--
-- Name: evidence_tag evidence_tag_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_tag
    ADD CONSTRAINT evidence_tag_pkey PRIMARY KEY (id);


--
-- Name: evidence_version evidence_version_evidence_record_id_version_number_key; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_version
    ADD CONSTRAINT evidence_version_evidence_record_id_version_number_key UNIQUE (evidence_record_id, version_number);


--
-- Name: evidence_version_metadata evidence_version_metadata_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_version_metadata
    ADD CONSTRAINT evidence_version_metadata_pkey PRIMARY KEY (evidence_version_id, meta_key);


--
-- Name: evidence_version evidence_version_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_version
    ADD CONSTRAINT evidence_version_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: onboarding_scan_phase onboarding_scan_phase_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.onboarding_scan_phase
    ADD CONSTRAINT onboarding_scan_phase_pkey PRIMARY KEY (onboarding_scan_id, phase_key);


--
-- Name: onboarding_scan onboarding_scan_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.onboarding_scan
    ADD CONSTRAINT onboarding_scan_pkey PRIMARY KEY (id);


--
-- Name: scheduler_run scheduler_run_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.scheduler_run
    ADD CONSTRAINT scheduler_run_pkey PRIMARY KEY (id);


--
-- Name: scheduler_run_source scheduler_run_source_pkey; Type: CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.scheduler_run_source
    ADD CONSTRAINT scheduler_run_source_pkey PRIMARY KEY (scheduler_run_id, source_key);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON pramaan.flyway_schema_history USING btree (success);


--
-- Name: ix_applications_active; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_applications_active ON pramaan.applications USING btree (active);


--
-- Name: ix_check_result_app; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_check_result_app ON pramaan.check_result USING btree (application_slug);


--
-- Name: ix_check_result_control; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_check_result_control ON pramaan.check_result USING btree (control_id);


--
-- Name: ix_check_result_framework; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_check_result_framework ON pramaan.check_result USING btree (framework);


--
-- Name: ix_check_result_status; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_check_result_status ON pramaan.check_result USING btree (status);


--
-- Name: ix_compliance_snapshot_taken_at; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_compliance_snapshot_taken_at ON pramaan.compliance_snapshot USING btree (taken_at);


--
-- Name: ix_evidence_lifecycle_event_record; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_lifecycle_event_record ON pramaan.evidence_lifecycle_event USING btree (evidence_record_id);


--
-- Name: ix_evidence_record_app; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_record_app ON pramaan.evidence_record USING btree (application_slug);


--
-- Name: ix_evidence_record_control; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_record_control ON pramaan.evidence_record USING btree (control_id);


--
-- Name: ix_evidence_record_framework; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_record_framework ON pramaan.evidence_record USING btree (framework);


--
-- Name: ix_evidence_record_lifecycle; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_record_lifecycle ON pramaan.evidence_record USING btree (lifecycle_state);


--
-- Name: ix_evidence_record_source; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_record_source ON pramaan.evidence_record USING btree (source_system);


--
-- Name: ix_evidence_version_sha; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_evidence_version_sha ON pramaan.evidence_version USING btree (sha256);


--
-- Name: ix_onboarding_scan_app; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_onboarding_scan_app ON pramaan.onboarding_scan USING btree (application_slug);


--
-- Name: ix_onboarding_scan_status; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_onboarding_scan_status ON pramaan.onboarding_scan USING btree (status);


--
-- Name: ix_scheduler_run_status; Type: INDEX; Schema: pramaan; Owner: -
--

CREATE INDEX ix_scheduler_run_status ON pramaan.scheduler_run USING btree (status);


--
-- Name: check_result check_result_evidence_record_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.check_result
    ADD CONSTRAINT check_result_evidence_record_id_fkey FOREIGN KEY (evidence_record_id) REFERENCES pramaan.evidence_record(id) ON DELETE CASCADE;


--
-- Name: evidence_approval_user evidence_approval_user_role_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_approval_user
    ADD CONSTRAINT evidence_approval_user_role_fkey FOREIGN KEY (role) REFERENCES pramaan.evidence_approval_role(role);


--
-- Name: evidence_lifecycle_event evidence_lifecycle_event_evidence_record_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_lifecycle_event
    ADD CONSTRAINT evidence_lifecycle_event_evidence_record_id_fkey FOREIGN KEY (evidence_record_id) REFERENCES pramaan.evidence_record(id) ON DELETE CASCADE;


--
-- Name: evidence_record evidence_record_application_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_record
    ADD CONSTRAINT evidence_record_application_id_fkey FOREIGN KEY (application_id) REFERENCES pramaan.applications(id);


--
-- Name: evidence_tag evidence_tag_evidence_record_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_tag
    ADD CONSTRAINT evidence_tag_evidence_record_id_fkey FOREIGN KEY (evidence_record_id) REFERENCES pramaan.evidence_record(id) ON DELETE CASCADE;


--
-- Name: evidence_version evidence_version_evidence_record_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_version
    ADD CONSTRAINT evidence_version_evidence_record_id_fkey FOREIGN KEY (evidence_record_id) REFERENCES pramaan.evidence_record(id);


--
-- Name: evidence_version_metadata evidence_version_metadata_evidence_version_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.evidence_version_metadata
    ADD CONSTRAINT evidence_version_metadata_evidence_version_id_fkey FOREIGN KEY (evidence_version_id) REFERENCES pramaan.evidence_version(id) ON DELETE CASCADE;


--
-- Name: onboarding_scan_phase onboarding_scan_phase_onboarding_scan_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.onboarding_scan_phase
    ADD CONSTRAINT onboarding_scan_phase_onboarding_scan_id_fkey FOREIGN KEY (onboarding_scan_id) REFERENCES pramaan.onboarding_scan(id) ON DELETE CASCADE;


--
-- Name: scheduler_run_source scheduler_run_source_scheduler_run_id_fkey; Type: FK CONSTRAINT; Schema: pramaan; Owner: -
--

ALTER TABLE ONLY pramaan.scheduler_run_source
    ADD CONSTRAINT scheduler_run_source_scheduler_run_id_fkey FOREIGN KEY (scheduler_run_id) REFERENCES pramaan.scheduler_run(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict Niet7IBrbSINHnOgZGwivvYN8K7pgK6M2SCVvwUOruqpndRDofhb9uLfM2E4k8T

