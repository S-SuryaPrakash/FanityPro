# Model Service

This directory will contain the Python/FastAPI service responsible for loading
the Hugging Face content-classification model and exposing prediction endpoints
to the Spring Boot application.

Planned structure:

```text
model-service/
├── app/                 FastAPI application and inference code
├── scripts/             Standalone model experiments
├── tests/               Python tests
├── requirements.txt     Python dependencies
└── Dockerfile           Model-service container definition
```

Downloaded model weights and local virtual environments must not be committed
to Git. They are excluded by this directory's `.gitignore`.
