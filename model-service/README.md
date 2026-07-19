# Model Service

This directory contains the Python model-evaluation foundation and will later
contain the FastAPI service responsible for loading the selected model and
exposing prediction endpoints to Spring Boot.

Module 4 currently provides:

- A versioned synthetic JSONL seed dataset and annotation guide.
- Pinned candidate manifests with domain-label mappings and supply-chain status.
- Safe Hugging Face evaluation that requires safetensors weights.
- Calibration-only threshold selection and untouched-test metrics.
- Dependency-free validation and tests that run in CI.
- A smoke report that explicitly does not select a production model.

See `evaluation/README.md` and `evaluation/SMOKE_REPORT.md`.

Current and planned structure:

```text
model-service/
|-- evaluation/          Dataset, annotation, candidates, and reports
|-- scripts/             Validation and reproducible model experiments
|-- tests/               Dependency-free Python tests
|-- requirements-evaluation.txt
`-- app/                 FastAPI application (after model selection)
```

Downloaded model weights, generated predictions, virtual environments, secrets,
and Python caches must not be committed to Git. They are excluded by this
directory's `.gitignore`.
