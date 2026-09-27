# `dto` package

[← back to index](README.md)

All classes here are `@Deprecated(forRemoval=true)` — legacy V0 response
shapes only, used by the `/upload` endpoint.

### `ClassificationResponse`
Record: `category, confidence, timestamp`; has `categoryLower()`. Returned
by `KeywordClassifier` / `LegacyClassificationService`.

### `RowClassificationResponse`
Record: `rowNumber, text, classification (ClassificationResponse)`.
Produced by `UploadClassificationService`.

### `UploadResponse`
Record: `fileName, size, contentType, results (List<RowClassificationResponse>)`.
Returned by `UploadController`.
