# `web` package

[← back to index](README.md)

### `CorrelationIdFilter`
`@Component`, `@Order(HIGHEST_PRECEDENCE)`, extends `OncePerRequestFilter`.
Runs first on every request. Accepts a safe caller-supplied
`X-Correlation-ID` (regex `[A-Za-z0-9._-]{1,64}`) or generates a UUID,
stores it as a request attribute (`REQUEST_ATTRIBUTE`) and in SLF4J `MDC`
(`MDC_KEY`), and echoes it back in the response header. Read by
`GlobalExceptionHandler` (for `ProblemDetail`) and `FastApiRiskModel`
(propagated to outbound FastAPI calls via `addCorrelationId`).
