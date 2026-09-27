# Content Filter — Project Status

_Last updated: 2026-08-14_

## What this project is

An automated risk-classification pipeline for conversation exports. A user
uploads an `.xlsx` workbook of messages; the system classifies every message
into a risk category (threat, hate/identity attack, harassment/insult,
obscene/profane, general toxicity, or no flag / manual review) and returns an
annotated workbook. The system is split into three parts:

- **`contentfilter` (Spring Boot, Java 21)** — the production API: ingestion,
  business/decision policy, reporting, and orchestration.
- **`model-service` (FastAPI, Python)** — an internal ML microservice that
  loads a pinned Hugging Face model and returns raw classification evidence
  only. Spring Boot owns thresholds, severity, and policy — the model never
  makes the final call.
- **`frontend` (React + TypeScript + Vite)** — a minimal drag-and-drop upload
  UI that posts a workbook and downloads the annotated result.

## What's been built so far

### Spring Boot API
- Domain model: `RiskCategory`, `RiskSeverity`, `ModelPrediction`,
  `ClassificationResult`, `ConversationRiskAssessment`, `ExtractedSequence`,
  `ConversationContext`, `ConversationColumnMapping`, `ClassifiedWorkbook`.
- Secure, coordinate-aware Excel extraction with configurable upload limits.
- Classification pipeline: `ClassificationService`, `ClassificationPolicy`,
  `VersionedRiskPolicy` (versioned decision policy on top of raw model
  output), `RiskClassificationService`, `ConversationRiskWorkflowService`.
- Two pluggable risk-model providers, switchable via
  `content-filter.classification.provider`:
  - `DeterministicRiskModel` — rule-based, no external dependency (default).
  - `FastApiRiskModel` — calls the Python model-service over REST, with a
    dedicated `FastApiClientConfiguration`, request/response mapping, and a
    `FastApiModelHealthIndicator` feeding into Spring health checks.
- Reporting: `ExcelReportService` / `WorkbookClassificationService` produce
  the annotated `.xlsx` output.
- Web layer: `FileClassificationController`, `UploadController`,
  `CorrelationIdFilter` for request tracing, `GlobalExceptionHandler` with
  structured error responses, and dedicated exception types.
- Solid unit/integration test coverage across the services above, plus
  operational contract tests.

### Python model-service (Modules 4 & 5)
- Versioned 240-message synthetic domain dataset (`v1-domain-synthetic`)
  with a dataset card and annotation guide.
- Candidate-model manifest with commit-pinned, safetensors-only supply-chain
  checks — 2 of 4 evaluated candidates were rejected outright for missing
  safe weights or label mismatches.
- Reproducible evaluation pipeline: calibration-only threshold selection
  (F2-maximizing with a minimum-precision constraint) and a strictly
  untouched test split for final metrics.
- FastAPI app exposing `/live`, `/ready`, `POST /api/v1/classify/batch`, and
  `/docs`; batch limits (32 sequences / 4,000 chars each), one-time lifespan
  model load, structured errors, correlation IDs, and OpenAPI docs.
- Every response is explicitly labeled `evaluationStatus: PROVISIONAL`,
  `approvedForProduction: false` — no model has cleared the release gate yet.

### Frontend
- Functional drag-and-drop `.xlsx` uploader with progress/error states and
  a one-click download of the classified workbook.
- Not yet containerized — runs standalone via `npm run dev` (Vite), proxying
  `/api/*` to the Spring Boot API. Not part of `compose.yaml`.

### Deployment (Docker Compose)
- Multi-stage `Dockerfile` (Spring Boot API) and `model-service/Dockerfile`
  (FastAPI + pinned model, downloaded during the image build) — both build
  and run cleanly.
- `compose.yaml` wires three services: `contentfilter-api`,
  `contentfilter-model`, `contentfilter-db` (Postgres, `v2` profile).
- Two Docker networks: `contentfilter-edge` (published, host-reachable) and
  `contentfilter-internal` (`internal: true`, no route out). Only
  `contentfilter-api` sits on both, so it's the sole bridge between the host
  and the model service / database — neither of which is reachable from
  outside the container boundary.
- Health checks gate startup order (`depends_on: condition: service_healthy`)
  and drive Spring's separate liveness/readiness probes; resource limits and
  writable `tmpfs` mounts are set per service.
- Not yet done: production config profiles, real secrets management (the
  Postgres password is currently a `compose.yaml` default fallback, fine for
  local dev only), and containerizing the frontend.

### CI/CD
- GitHub Actions runs on every push/PR to `master` across three jobs:
  Python dataset validation + contract tests, a full Maven `verify` for the
  Java side, and (after both of those pass) a `docker` job that builds both
  service images with `docker compose build`, brings up the full stack, and
  polls `/actuator/health/readiness` until the API, model service, and
  database are genuinely reachable end to end — not just "the process
  started". CI does not yet build or publish versioned/immutable images.

## Current model status (important caveat)

The domain dataset has now been through the full three-step review process
(Reviewer 1 → Reviewer 2 → independent adjudicator; see
`model-service/evaluation/ADJUDICATOR_REPORT.md`), and both eligible
candidates have been re-evaluated against the finalized ground truth (see
`model-service/evaluation/ADJUDICATED_DOMAIN_REPORT.md`). The best candidate,
`unitary/toxic-bert`, scores macro F1 0.631 — but **threat recall is only
0.31** (MiniLM: 0.25), which is unacceptable for a safety-critical release;
7/8 benign identity mentions are falsely flagged; and quoted-harm/self-directed
messages are flagged 100% of the time. Re-evaluation against the adjudicated
labels confirmed rather than changed this conclusion. **No model is approved
for production**; the deterministic rule-based model remains the default in
production configuration.

## Roadmap to a full-fledged product

1. **Clear the model-approval gate** — independent double-annotation and
   adjudication of the domain dataset, resolved policy calls on edge cases
   (quoted harm, self-directed language, indirect threats), an expanded and
   privacy-reviewed dataset, and explicit minimum acceptance criteria
   (especially threat recall) before any model can flip
   `approvedForProduction` to `true`.
2. **Improve model quality** — evaluate further candidates, ensembling, or
   fine-tuning on the domain dataset now that the evaluation pipeline is
   validated end-to-end.
3. **Manual-review workflow** — `MANUAL_REVIEW` currently exists only as a
   policy outcome; there's no reviewer queue or UI to act on it yet.
4. **Frontend maturity** — inline per-message results (not just a workbook
   download), better error handling for edge cases, and replacing the
   remaining default Vite scaffolding.
5. **Deployment hardening** — Dockerfiles and Docker Compose orchestration
   are done and CI-verified; remaining work is containerizing the frontend,
   production config profiles, real secrets management (the Postgres
   password is currently a `compose.yaml` default, not a managed secret),
   and observability beyond correlation IDs (metrics, logging, tracing).
6. **Auth & data handling** — authentication/authorization if this becomes
   multi-user, plus a data-retention/privacy review for conversation data.
7. **API documentation** — OpenAPI/docs for the Spring Boot API itself
   (currently only the model-service documents its API).
8. **Broaden input support** — formats beyond `.xlsx` if needed (e.g. CSV).
9. **CI coverage** — add a frontend build/lint job (still the one part of
   the stack CI never touches), and build/publish versioned, immutable
   images tied to a Git SHA rather than only verifying the build succeeds.
