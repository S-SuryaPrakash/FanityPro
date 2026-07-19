"""Validated environment configuration for the model service."""

from pathlib import Path
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


MODEL_SERVICE_ROOT = Path(__file__).resolve().parents[1]


class ModelServiceSettings(BaseSettings):
    """Runtime limits and the pinned model candidate selected for development."""

    model_config = SettingsConfigDict(
        env_prefix="CONTENT_FILTER_MODEL_",
        env_file=MODEL_SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    service_name: str = "content-filter-model-service"
    candidate_key: str = "minilm-toxic-jigsaw"
    candidate_manifest: Path = MODEL_SERVICE_ROOT / "evaluation" / "candidate-models.json"
    model_cache_directory: Path = MODEL_SERVICE_ROOT / "model-cache"
    max_batch_size: int = Field(default=32, ge=1, le=32)
    inference_batch_size: int = Field(default=8, ge=1, le=32)
    max_text_length: int = Field(default=4_000, ge=1, le=4_000)
    max_total_text_length: int = Field(default=128_000, ge=1, le=128_000)
    evaluation_status: Literal["PROVISIONAL"] = "PROVISIONAL"
    approved_for_production: Literal[False] = False
