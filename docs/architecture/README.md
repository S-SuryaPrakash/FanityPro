# Content Filter — Class & Architecture Reference

Generated overview of every class/interface/enum/record in
`src/main/java/com/example/contentfilter`, what it's for, and how it connects
to the rest of the codebase.

The service exposes **two parallel API generations**:

- **V1 (production)** — `POST /api/v1/files/classify`: upload an `.xlsx`,
  run each conversation row through a risk classification pipeline, get back
  an annotated workbook with a Review Queue / Summary / Legend.
- **V0 (legacy, `@Deprecated(forRemoval=true)`)** — `POST /upload`: upload an
  `.xlsx`, get back JSON from a trivial keyword classifier. Superseded by V1,
  kept only for backwards compatibility until removal.

## Package index

| Package | File | Contents |
|---|---|---|
| root | [root.md](root.md) | Application entry point |
| `domain` | [domain.md](domain.md) | Spring-free records/enums — the shared vocabulary |
| `service` | [service.md](service.md) | Interfaces, implementations, orchestrators |
| `config` | [config.md](config.md) | `@ConfigurationProperties` and Spring `@Configuration` |
| `web` | [web.md](web.md) | Servlet filter (correlation ID) |
| `controller` | [controller.md](controller.md) | REST endpoints |
| `dto` | [dto.md](dto.md) | Legacy V0 response records |
| `exception` | [exception.md](exception.md) | Exceptions + global handler |

## Request flow — V1 production pipeline

```
FileClassificationController                (controller)
 -> WorkbookClassificationService.classify(file)                 (service)
     -> ConversationRiskWorkflowService.assess(file, mapping)     (service)
         -> ExcelService.extractConversationSequences(file, mapping)  (service)
              parses/validates the .xlsx via Apache POI against
              UploadLimitsProperties (config); returns List<ExtractedSequence> (domain)

         -> RiskClassificationService.classify(sequences)          (service, implements ClassificationService)
              -> RiskModel.predict(sequences)                      (service interface)
                   implemented by DeterministicRiskModel (fake/test)
                   or FastApiRiskModel (calls real model over HTTP),
                   chosen by content-filter.classification.provider
                   -> List<ModelPrediction>                        (domain)

              -> ClassificationPolicy.decide(prediction) per sequence   (service interface)
                   implemented by VersionedRiskPolicy, using thresholds
                   and precedence from RiskPolicyProperties (config)
                   -> List<ClassificationResult>                   (domain)

         -> wraps into ConversationRiskAssessment                  (domain)

     -> rejects empty-message workbooks (WorkbookProcessingException)

     -> ExcelReportService.generate(file, mapping, assessment)     (service)
          reopens the original workbook, annotates source rows,
          adds Review Queue / Summary / Legend sheets

     -> ClassifiedWorkbook(filename, bytes)                        (domain)

 -> HTTP response: annotated .xlsx streamed back as an attachment
```

Cross-cutting:

- **`CorrelationIdFilter`** (`web`) runs first on every request; assigns/reads
  an `X-Correlation-ID`, stores it in a request attribute + SLF4J MDC. Read
  by `GlobalExceptionHandler` and propagated to outbound FastAPI calls by
  `FastApiRiskModel`.
- **`GlobalExceptionHandler`** (`exception`) is a `@RestControllerAdvice`
  that catches every exception thrown anywhere in the chain above
  (`WorkbookProcessingException`, `ModelServiceException`,
  `ReportGenerationException`, `InvalidClassificationRequestException`,
  Spring multipart errors, generic `Exception`) and turns it into an
  RFC-7807 `ProblemDetail` JSON response tagged with the correlation ID.
- **`FastApiModelHealthIndicator`** (`service`) exposes the FastAPI model's
  readiness/approval status to Spring Boot Actuator health checks when the
  `fastapi` provider is active.

## Request flow — V0 legacy pipeline (deprecated)

```
UploadController                              (controller)
 -> UploadClassificationService.classifyRows(file)        (service)
     -> ExcelService.extractSequences(file)                (service — shared with V1, no column mapping)
     -> LegacyClassificationService.classify(text) per row  (interface, implemented by KeywordClassifier)
     -> List<RowClassificationResponse>                     (dto)
 -> UploadResponse (JSON)                                    (dto)
```

This path shares only `ExcelService` with the V1 flow. It bypasses the
`RiskModel` / `ClassificationPolicy` pipeline entirely in favor of a trivial
keyword matcher, and returns JSON instead of an annotated workbook.
