# Model Service

This directory contains the Python model-evaluation foundation and the internal
FastAPI service responsible for loading a pinned model and exposing evidence to
Spring Boot.

Modules 4 and 5 currently provide:

- A versioned 240-message realistic synthetic JSONL corpus, dataset card, and
  annotation guide.
- Pinned candidate manifests with domain-label mappings and supply-chain status.
- Safe Hugging Face evaluation that requires safetensors weights.
- Calibration-only threshold selection and untouched-test metrics.
- Dependency-free evaluation validation plus API contract tests that run in CI.
- A smoke report that explicitly does not select a production model.
- A batch-first FastAPI contract with separate liveness and readiness probes.
- One-time lifespan loading of an eligible, commit-pinned safetensors model.
- Provider-label translation into the five stable Java risk categories.
- Structured errors, correlation IDs, resource limits, and OpenAPI docs.
- Lightweight API tests that do not download model weights.

See `evaluation/README.md`, `evaluation/DATASET_CARD.md`,
`evaluation/DOMAIN_SMOKE_REPORT.md`, and `evaluation/SMOKE_REPORT.md`.

Current and planned structure:

```text
model-service/
|-- app/                   FastAPI application and Hugging Face runtime
|-- evaluation/          Dataset, annotation, candidates, and reports
|-- scripts/             Validation and reproducible model experiments
|-- tests/               Evaluation and API contract tests
|-- requirements-api.txt   Lightweight web-service dependencies
|-- requirements-model.txt PyTorch and Transformers dependencies
|-- requirements-test.txt CI/API-test dependencies without model weights
`-- requirements.txt       Complete local runtime dependencies
```

## Module 5 development status

Module 5 uses `minuva/MiniLMv2-toxic-jigsaw` at the exact revision recorded in
`evaluation/candidate-models.json`. This is a **provisional development
candidate**, not a production-approved model. Every readiness and prediction
response therefore includes:

```json
{
  "evaluationStatus": "PROVISIONAL",
  "approvedForProduction": false
}
```

Independent human review and adjudication of authorised domain data remain a
release gate. The service cannot be configured to report production approval
until that gate is implemented deliberately in a later change.

## Run locally

From the repository root:

```powershell
python -m venv .venv
& .\.venv\Scripts\python.exe -m pip install -r model-service/requirements.txt
& .\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir model-service --host 127.0.0.1 --port 8000
```

The full requirements include PyTorch and Transformers for actual model
inference. To run the lightweight API and evaluation tests without downloading
model weights, install and execute only the test requirements:

```powershell
& .\.venv\Scripts\python.exe -m pip install -r model-service/requirements-test.txt
& .\.venv\Scripts\python.exe -m pytest model-service/tests -q
```

The first startup may download the pinned model into the ignored
`model-service/model-cache` directory. After startup, use:

```text
GET  http://127.0.0.1:8000/live
GET  http://127.0.0.1:8000/ready
POST http://127.0.0.1:8000/api/v1/classify/batch
GET  http://127.0.0.1:8000/docs
```

The service accepts at most 32 English sequences and 4,000 characters per
sequence. The `POST` response contains model evidence only. Spring Boot owns
thresholds, primary-category selection, severity, and manual-review policy.

The main environment settings use the `CONTENT_FILTER_MODEL_` prefix. Examples
include `CANDIDATE_KEY`, `CANDIDATE_MANIFEST`, `MODEL_CACHE_DIRECTORY`,
`MAX_BATCH_SIZE`, `INFERENCE_BATCH_SIZE`, `MAX_TEXT_LENGTH`, and
`MAX_TOTAL_TEXT_LENGTH`.

## Connect Spring Boot during Module 4 review

After FastAPI reports `READY`, start Spring from the repository root with its
development bridge profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=fastapi"
```

This selects `FastApiRiskModel`, includes FastAPI in Spring readiness, and
allows the explicitly provisional response for development only. The default
profile still selects `DeterministicRiskModel`. Production configuration keeps
the provisional waiver disabled.

Downloaded model weights, generated predictions, virtual environments, secrets,
and Python caches must not be committed to Git. They are excluded by the
repository and model-service `.gitignore` files.
