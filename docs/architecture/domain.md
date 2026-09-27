# `domain` package

[← back to index](README.md)

Spring-free records and enums — the shared vocabulary passed between
`service`, `controller`, and `dto`-adjacent boundaries. No annotations, only
data + validation invariants in compact constructors.

### `RiskCategory`
Enum: `THREAT, HATE_OR_IDENTITY_ATTACK, HARASSMENT_OR_INSULT,
OBSCENE_OR_PROFANE, GENERAL_TOXICITY, NO_AUTOMATED_FLAG, MANUAL_REVIEW`, plus
`isDetectedRisk()`. The central taxonomy — referenced by nearly every
`service`, `config`, and report class.

### `RiskSeverity`
Enum: `NONE, LOW, MEDIUM, HIGH, CRITICAL`. Assigned by policy
(`VersionedRiskPolicy`), never directly by the model.

### `ConversationContext`
Record of optional per-message metadata (`conversationId, messageId,
speakerRole, timestamp, language, channel`); normalizes blanks to `null`,
has a singleton `empty()`. Embedded in `ExtractedSequence`; consumed by
`FastApiRiskModel` (per-sequence request) and `ExcelReportService` (Review
Queue columns).

### `ExtractedSequence`
Record: `sequenceId, sheetIndex, rowIndex, sourceColumnIndexes, text,
context`. Validates non-blank id/text, non-negative coordinates, at least
one source column. Produced by `ExcelService`, consumed by
`ClassificationService` / `RiskModel` implementations and
`ExcelReportService`.

### `ConversationColumnMapping`
Record mapping workbook headers to columns (`textHeader` required;
conversation/message/role/timestamp/language/channel optional),
case-insensitive and whitespace-trimmed. Static factory `standard()`
returns the documented V1 header set (`text, conversation_id, message_id,
speaker_role, timestamp, language, channel`). Used by
`ExcelService.extractConversationSequences`,
`ConversationRiskWorkflowService`, `ExcelReportService`,
`WorkbookClassificationService`.

### `ModelPrediction`
Record: `sequenceId, scores (Map<RiskCategory,Double>), inputTruncated,
modelId, modelRevision`. Validates that scores only cover
detected-risk categories and lie in `[0,1]`. Produced by `RiskModel`
implementations, consumed by `ClassificationPolicy`.

### `ClassificationResult`
Record: `sequenceId, primaryCategory, severity, confidence, scores,
manualReviewRequired, reviewReason, modelId, modelRevision, policyVersion`.
The final auditable decision — produced by `VersionedRiskPolicy`, consumed
by `RiskClassificationService`, `ConversationRiskAssessment`, and
`ExcelReportService`.

### `ConversationRiskAssessment`
Record pairing parallel `List<ExtractedSequence>` and
`List<ClassificationResult>`, validated to be correlated by ID/order.
Produced by `ConversationRiskWorkflowService`, consumed by
`WorkbookClassificationService` and `ExcelReportService`.

### `ClassifiedWorkbook`
Record: `filename, content (byte[])`. Defensively clones the byte array on
both construction and access; requires an `.xlsx` filename. The final
output of `WorkbookClassificationService`, returned to
`FileClassificationController`.
