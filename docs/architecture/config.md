# `config` package

[← back to index](README.md)

### `UploadLimitsProperties`
Record, `@ConfigurationProperties("content-filter.upload")`, `@Validated`.
Bounds on file size, worksheets, rows, sequences, cells/sequence, text
lengths, and processing time. Consumed by `ExcelService` to enforce limits
during extraction.

### `RiskPolicyProperties`
Record, `@ConfigurationProperties("content-filter.classification.policy")`.
Holds policy `version`, `uncertaintyMargin`, per-`RiskCategory` `thresholds`
map, and category `precedence` list; validates completeness against
`RiskCategory`'s detected-risk set. Consumed by `VersionedRiskPolicy`.

### `ModelServiceProperties`
Record,
`@ConfigurationProperties("content-filter.classification.model-service")`.
FastAPI connection/resilience settings: `baseUrl`, `expectedModelId`,
`expectedRevision` (40-hex sha), timeouts, `batchSize`, `maxAttempts`,
`allowProvisional`. Validates the URL shape and that
`timeout × attempts` stays under a 60s budget. Consumed by `FastApiRiskModel`
and `FastApiClientConfiguration`.

### `CorsConfiguration`
`@Configuration`,
`@ConditionalOnProperty("content-filter.cors.allowed-origins")`, implements
`WebMvcConfigurer`. Dev-only relaxed CORS for `/api/**` POSTs; disabled
unless the property is set.

### `FastApiClientConfiguration`
`@Configuration(proxyBeanMethods=false)`,
`@ConditionalOnProperty(name="content-filter.classification.provider",
havingValue="fastapi")`. Declares the `fastApiRestClient` `RestClient` bean
(qualifier `"fastApiRestClient"`) built on a JDK `HttpClient` with
connect/read timeouts from `ModelServiceProperties`. Injected into
`FastApiRiskModel`.
