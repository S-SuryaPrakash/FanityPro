# `controller` package

[← back to index](README.md)

### `FileClassificationController`
`@RestController`, no `@RequestMapping` prefix. Single endpoint
`POST /api/v1/files/classify` (multipart `.xlsx` in, annotated `.xlsx` out).
Injects `WorkbookClassificationService`. Calls `.classify(file)`, streams
the resulting `ClassifiedWorkbook` bytes back with
`Content-Disposition: attachment`, `no-store` cache headers, and
`X-Content-Type-Options: nosniff`. **This is the production V1 entry
point.**

### `UploadController` *(`@Deprecated(forRemoval=true)`)*
`@RestController`. Endpoint `POST /upload`. Injects
`UploadClassificationService`. Calls `.classifyRows(file)`, wraps the
result in `UploadResponse`. Legacy V0 prototype endpoint, superseded by
`FileClassificationController`.
