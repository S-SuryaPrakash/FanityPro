# `service` package

[← back to index](README.md)

## Interfaces (ports)

### `ClassificationService`
`classify(List<ExtractedSequence>) -> List<ClassificationResult>`.
Implemented by `RiskClassificationService`. Used by
`ConversationRiskWorkflowService`.

### `ClassificationPolicy`
`decide(ModelPrediction) -> ClassificationResult`. Implemented by
`VersionedRiskPolicy`. Used by `RiskClassificationService`.

### `RiskModel`
`predict(List<ExtractedSequence>) -> List<ModelPrediction>`. Implemented by
`DeterministicRiskModel` and `FastApiRiskModel` — mutually exclusive via
`@ConditionalOnProperty(content-filter.classification.provider =
deterministic|fastapi)`. Used by `RiskClassificationService`.

### `LegacyClassificationService` *(`@Deprecated(forRemoval=true)`)*
`classify(String) -> ClassificationResponse`. Implemented by
`KeywordClassifier`. Used by `UploadClassificationService` (V0 chain).

## Implementations

### `VersionedRiskPolicy`
`@Component`, implements `ClassificationPolicy`. Injects
`RiskPolicyProperties`. Applies threshold/precedence logic; flags
`MANUAL_REVIEW` on truncated input, conflicting close scores, or
near-threshold scores; otherwise picks the highest-precedence passing
category or falls back to `NO_AUTOMATED_FLAG`. Maps category to a default
`RiskSeverity`.

### `RiskClassificationService`
`@Service`, implements `ClassificationService`. Injects `RiskModel` +
`ClassificationPolicy`. Validates unique/non-null sequence IDs, calls
`riskModel.predict(...)`, strictly correlates returned predictions back to
requested IDs by `sequenceId`, then maps each through `policy.decide(...)`.
Used by `ConversationRiskWorkflowService`.

### `DeterministicRiskModel`
`@Component`, implements `RiskModel`, active by default
(`matchIfMissing=true` when provider = `deterministic`). Keyword-phrase-based
fake scorer used for testing the orchestration without a real model.

### `FastApiRiskModel`
`@Component`, implements `RiskModel`, active when provider = `fastapi`.
Injects `@Qualifier("fastApiRestClient") RestClient` (from
`FastApiClientConfiguration`) and `ModelServiceProperties`. Batches requests
(`batchSize`), calls `POST /api/v1/classify/batch` with retry
(`maxAttempts`, retrying on 502/503/504 and `ResourceAccessException`),
validates model identity/revision/evaluation-gate metadata against config,
strictly maps responses back to sequence IDs, and throws
`ModelServiceException` (`UNAVAILABLE, TIMEOUT, REQUEST_REJECTED,
INVALID_RESPONSE, MODEL_NOT_APPROVED`) on failure. Also exposes
`readiness()` (`GET /ready`), used by `FastApiModelHealthIndicator`.
Propagates the correlation ID from `CorrelationIdFilter`'s MDC key into
outbound headers. Contains private nested request/response records
(`BatchRequest, SequenceRequest, BatchResponse, PredictionResponse,
ReadyResponse`).

### `ModelServiceException`
`RuntimeException` with a nested `enum Reason`. Thrown by `FastApiRiskModel`,
caught by `GlobalExceptionHandler` and by `FastApiModelHealthIndicator`.

### `FastApiModelHealthIndicator`
`@Component`, implements Spring Boot Actuator `HealthIndicator`, active
when provider = `fastapi`. Injects `FastApiRiskModel`, calls
`readiness()`; reports `up()` with model metadata, `outOfService()` on
`ModelServiceException`, `down()` on other runtime errors. Ties app
readiness to the pinned model.

### `ConversationRiskWorkflowService`
`@Service`. Injects `ExcelService` + `ClassificationService`. Orchestrates:
`excelService.extractConversationSequences(file, mapping)` →
`classificationService.classify(sequences)` → wraps into
`ConversationRiskAssessment`. Called by `WorkbookClassificationService`.

### `ExcelService`
`@Service`. Injects `UploadLimitsProperties`; configures POI
`ZipSecureFile` anti-zip-bomb safeguards in its constructor. Two entry
points: `extractSequences(file)` (legacy V0, no mapping — one sequence per
non-empty row, tab-joined) and `extractConversationSequences(file,
mapping)` (V1 — column-mapped text + context). Validates filename, size,
content type, file signature (OOXML magic bytes), macro-disabled state,
sheet/row limits, text length, and processing time, throwing
`WorkbookProcessingException` with specific `Reason`s on failure. Used by
`ConversationRiskWorkflowService` (V1) and `UploadClassificationService`
(legacy V0).

### `ExcelReportService`
`@Service`. Pure Apache POI logic, no injected collaborators.
`generate(file, mapping, assessment)` reopens the original workbook,
verifies no reserved sheet names (`Review Queue, Summary, Legend`) or
existing `Content Filter -` headers already exist, appends 8 base + 5
per-category-score result columns to the source sheet header row,
colors/annotates each source row's mapped cells by `primaryCategory`, and
builds three new sheets: Review Queue (sorted by severity/confidence, only
`manualReviewRequired` rows, with freeze pane + autofilter), Summary
(counts per category), and Legend (category meanings/typical severity).
Throws `WorkbookProcessingException` (already-annotated/reserved sheet) or
`ReportGenerationException` (bad coordinates/POI failure). Called by
`WorkbookClassificationService`.

### `WorkbookClassificationService`
`@Service`, the top-level V1 orchestrator. Injects
`ConversationRiskWorkflowService` + `ExcelReportService`.
`classify(file)`: builds `ConversationColumnMapping.standard()`, calls
`riskWorkflow.assess(...)`, rejects empty-message workbooks
(`WorkbookProcessingException INVALID_WORKBOOK`), calls
`reportService.generate(...)`, wraps the result into a `ClassifiedWorkbook`
with a sanitized `classified-<name>.xlsx` filename. Called directly by
`FileClassificationController`.

## Legacy V0 chain (all `@Deprecated(forRemoval=true)`)

### `KeywordClassifier`
`@Service`, implements `LegacyClassificationService`. Trivial keyword-match
classifier (`stupid/idiot` → abusive, `regards` → professional, else
neutral) with a random fake confidence score. Used by
`UploadClassificationService`.

### `UploadClassificationService`
`@Service`. Injects `ExcelService` + `LegacyClassificationService`.
`classifyRows(file)`: `excelService.extractSequences(file)`, then
classifies each sequence's text via `classificationService.classify(text)`,
zipping the results into `RowClassificationResponse`. Used by
`UploadController`.
