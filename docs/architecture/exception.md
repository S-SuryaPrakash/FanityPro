# `exception` package

[← back to index](README.md)

### `InvalidClassificationRequestException`
`RuntimeException` subclass. Thrown by the legacy `KeywordClassifier` on
blank/oversized input. Caught by `GlobalExceptionHandler` → HTTP 400.

### `WorkbookProcessingException`
`RuntimeException` subclass with a nested `enum Reason {INVALID_REQUEST,
UNSUPPORTED_TYPE, LIMIT_EXCEEDED, INVALID_WORKBOOK}`. Thrown throughout
`ExcelService` (and once by `WorkbookClassificationService`, and by
`ExcelReportService` for pre-existing report sheets) to signal
validation/parsing/limit failures. Caught by `GlobalExceptionHandler`,
which maps each `Reason` to a distinct HTTP status/error code.

### `ReportGenerationException`
`RuntimeException` subclass. Thrown by `ExcelReportService` when POI
workbook annotation fails unexpectedly. Caught by `GlobalExceptionHandler`
→ HTTP 500, logged with the correlation ID.

### `GlobalExceptionHandler`
`@RestControllerAdvice`. Central exception translator producing RFC-7807
`ProblemDetail` responses. Handles `WorkbookProcessingException`,
`InvalidClassificationRequestException`, `IllegalArgumentException`,
`ModelServiceException` (from `service`), `ReportGenerationException`,
`MaxUploadSizeExceededException`, `MultipartException`, and generic
`Exception`. Pulls the correlation ID from the request attribute set by
`CorrelationIdFilter` and stamps every problem response with `errorCode`,
`correlationId`, `timestamp`.
