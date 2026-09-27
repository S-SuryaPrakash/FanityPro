# Root — `com.example.contentfilter`

[← back to index](README.md)

### `ContentfilterApplication`
`src/main/java/com/example/contentfilter/ContentfilterApplication.java`

Class, `@SpringBootApplication` + `@ConfigurationPropertiesScan`.

Application entry point (`main`). Bootstraps the Spring context and
component-scans the whole package tree. `@ConfigurationPropertiesScan` is
required because the [config](config.md) classes are plain records rather
than `@Component` beans, so they need to be discovered explicitly.
