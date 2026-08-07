# Multi-stage build for the FastAPI model service.
# Stage 1: download the pinned Hugging Face model weights
# Stage 2: run the FastAPI app with a minimal Python image

# ---- Model download stage ----
FROM python:3.13-slim AS model-download

WORKDIR /download

COPY model-service/requirements-model.txt .

RUN pip install --no-cache-dir -r requirements-model.txt

# Copy the manifest and a small download script
COPY model-service/evaluation/candidate-models.json /download/manifest.json
COPY model-service/app/ /download/app/

# Download the pinned model weights during build
# The model-cache directory will be copied to the runtime stage
RUN python -c "\
import json, pathlib, sys; \
sys.path.insert(0, '/download'); \
from app.model_manifest import load_eligible_candidate; \
from app.settings import ModelServiceSettings; \
from transformers import AutoModelForSequenceClassification, AutoTokenizer; \
settings = ModelServiceSettings(_env_file=None); \
candidate = load_eligible_candidate(pathlib.Path('/download/manifest.json'), settings.candidate_key); \
model_id = candidate['modelId']; \
revision = candidate['revision']; \
print(f'Downloading {model_id} at revision {revision}'); \
AutoModelForSequenceClassification.from_pretrained(model_id, revision=revision, cache_dir='/model-cache'); \
AutoTokenizer.from_pretrained(model_id, revision=revision, cache_dir='/model-cache'); \
print('Download complete')"

# ---- Runtime stage ----
FROM python:3.13-slim

# Non-root user for security
RUN groupadd -r contentfilter-model && useradd -r -g contentfilter-model contentfilter-model

WORKDIR /app

# Install only API dependencies (no torch/transformers — those come with the model)
COPY model-service/requirements-api.txt .
RUN pip install --no-cache-dir -r requirements-api.txt

# Install model dependencies (torch + transformers needed at runtime)
COPY model-service/requirements-model.txt .
RUN pip install --no-cache-dir -r requirements-model.txt

# Copy the application code
COPY model-service/app/ /app/app/
COPY model-service/evaluation/candidate-models.json /app/evaluation/candidate-models.json

# Copy the downloaded model weights from the build stage
COPY --from=model-download /model-cache /app/model-cache

# Set environment variables for the model service
ENV CONTENT_FILTER_MODEL_CANDIDATE_KEY="minilm-toxic-jigsaw"
ENV CONTENT_FILTER_MODEL_MANIFEST_PATH="/app/evaluation/candidate-models.json"
ENV CONTENT_FILTER_MODEL_CACHE_DIR="/app/model-cache"

# Create writable temp directory
RUN mkdir -p /tmp/contentfilter-model && chown contentfilter-model:contentfilter-model /tmp/contentfilter-model

USER contentfilter-model

EXPOSE 8000

# Health check: liveness probe
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/live')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
