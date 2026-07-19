"""Versioned HTTP request and response contracts for model evidence."""

from enum import Enum
from math import isfinite
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class RiskCategory(str, Enum):
    """Detected-risk categories shared with the Spring Boot domain model."""

    THREAT = "THREAT"
    HATE_OR_IDENTITY_ATTACK = "HATE_OR_IDENTITY_ATTACK"
    HARASSMENT_OR_INSULT = "HARASSMENT_OR_INSULT"
    OBSCENE_OR_PROFANE = "OBSCENE_OR_PROFANE"
    GENERAL_TOXICITY = "GENERAL_TOXICITY"


Score = Annotated[float, Field(ge=0.0, le=1.0, allow_inf_nan=False)]


class SequenceInput(BaseModel):
    """One complete message or text sequence submitted for classification."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    sequence_id: Annotated[str, Field(alias="sequenceId", min_length=1, max_length=128)]
    text: Annotated[str, Field(min_length=1, max_length=4_000)]
    conversation_id: Annotated[
        str | None, Field(default=None, alias="conversationId", min_length=1, max_length=128)
    ]
    speaker_role: Annotated[
        str | None, Field(default=None, alias="speakerRole", min_length=1, max_length=64)
    ]
    language: Annotated[str, Field(default="en", min_length=2, max_length=16)]

    @field_validator("sequence_id", "text", "conversation_id", "speaker_role")
    @classmethod
    def reject_blank_text(cls, value: str | None) -> str | None:
        if value is not None and not value.strip():
            raise ValueError("must not be blank")
        return value

    @field_validator("language")
    @classmethod
    def require_english_for_v1(cls, value: str) -> str:
        normalized = value.strip().lower().replace("_", "-")
        if normalized != "en" and not normalized.startswith("en-"):
            raise ValueError("V1 supports English sequences only")
        return normalized


class BatchClassificationRequest(BaseModel):
    """A bounded batch of sequences; sequence IDs must be unique."""

    model_config = ConfigDict(extra="forbid")

    sequences: Annotated[list[SequenceInput], Field(min_length=1, max_length=32)]

    @model_validator(mode="after")
    def require_unique_sequence_ids(self) -> "BatchClassificationRequest":
        identifiers = [sequence.sequence_id for sequence in self.sequences]
        if len(identifiers) != len(set(identifiers)):
            raise ValueError("sequenceId values must be unique within a batch")
        return self


class RiskScores(BaseModel):
    """Exactly the five provider-neutral probabilities required by Java."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    threat: Score = Field(alias=RiskCategory.THREAT.value)
    hate_or_identity_attack: Score = Field(alias=RiskCategory.HATE_OR_IDENTITY_ATTACK.value)
    harassment_or_insult: Score = Field(alias=RiskCategory.HARASSMENT_OR_INSULT.value)
    obscene_or_profane: Score = Field(alias=RiskCategory.OBSCENE_OR_PROFANE.value)
    general_toxicity: Score = Field(alias=RiskCategory.GENERAL_TOXICITY.value)

    @field_validator("*")
    @classmethod
    def require_finite_scores(cls, value: float) -> float:
        if not isfinite(value):
            raise ValueError("scores must be finite")
        return value


class PredictionResponse(BaseModel):
    """Model evidence correlated to one submitted sequence."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    sequence_id: str = Field(alias="sequenceId")
    scores: RiskScores
    input_truncated: bool = Field(alias="inputTruncated")


class BatchClassificationResponse(BaseModel):
    """Evidence returned for a complete batch without applying business policy."""

    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    model: str
    revision: str
    evaluation_status: Literal["PROVISIONAL"] = Field(alias="evaluationStatus")
    approved_for_production: Literal[False] = Field(alias="approvedForProduction")
    predictions: list[PredictionResponse]


class LiveResponse(BaseModel):
    """Process liveness, deliberately independent from model readiness."""

    status: Literal["LIVE"] = "LIVE"
    service: str


class ReadyResponse(BaseModel):
    """Readiness metadata for a successfully loaded pinned model."""

    model_config = ConfigDict(populate_by_name=True)

    status: Literal["READY"] = "READY"
    model: str
    revision: str
    evaluation_status: Literal["PROVISIONAL"] = Field(alias="evaluationStatus")
    approved_for_production: Literal[False] = Field(alias="approvedForProduction")


class ValidationIssue(BaseModel):
    """One safe validation error without the submitted conversation text."""

    location: list[str]
    message: str
    type: str


class ProblemDetails(BaseModel):
    """RFC 9457-style error contract shared by every endpoint."""

    model_config = ConfigDict(populate_by_name=True)

    type: str
    title: str
    status: int
    detail: str
    instance: str
    error_code: str = Field(alias="errorCode")
    correlation_id: str = Field(alias="correlationId")
    errors: list[ValidationIssue] | None = None
