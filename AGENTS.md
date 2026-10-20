# Kestra Box Plugin

## What

- Provides plugin components under `io.kestra.plugin.box` to upload, download, search and manage files and folders in Box, and to trigger flows on new files.
- Includes classes such as `Upload`, `Download`, `Get`, `Delete`, `Trigger`, `List`, `Create`, `Search`.

## Why

- What user problem does this solve? Teams that exchange documents through Box otherwise call the Box REST API from shell or Python tasks and handle token refresh by hand.
- Why would a team adopt this plugin in a workflow? It keeps Box steps in the same Kestra flow as upstream preparation, retries, notifications and downstream systems, with the official Box SDK handling authentication.
- What operational/business outcome does it enable? Reports go out to Box and documents come in from a single flow, without custom API glue code.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `box` (shared base classes), `box.files`, `box.folders`, `box.search`, `box.models`

Tasks extend `AbstractBoxTask`; the trigger extends `AbstractBoxTrigger`. Both declare the same connection properties and build the SDK client through `BoxConnectionInterface.client(...)`: developer token, else JWT config, else client credentials (CCG) with `enterpriseId` or `userId`. `folders.List` and `search.Search` share `models.FetchOutput` for `fetchType` handling. `models.BoxItem` is the common output row.

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.box.files.Upload`
- `io.kestra.plugin.box.files.Download`
- `io.kestra.plugin.box.files.Get`
- `io.kestra.plugin.box.files.Delete`
- `io.kestra.plugin.box.files.Trigger`
- `io.kestra.plugin.box.folders.List`
- `io.kestra.plugin.box.folders.Create`
- `io.kestra.plugin.box.search.Search`

### Project Structure

```
plugin-box/
├── src/main/java/io/kestra/plugin/box/
│   ├── files/ folders/ search/ models/
│   ├── AbstractBoxTask.java, AbstractBoxTrigger.java, BoxConnectionInterface.java
├── src/test/java/io/kestra/plugin/box/
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.
- Use the official Box SDK (`com.box.sdkgen`) for every API call, no extra HTTP client.
- Every input is a `Property<T>` rendered with `runContext.render`; secrets use `@PluginProperty(secret = true)` and `@ToString.Exclude`.
- Tests fake the Box client by overriding `client(RunContext)` in a `public static` subclass; no live credentials in tests.
- Build with JDK 21 to 23 (Lombok does not support newer JDKs yet). Run `./gradlew build` before pushing: it also lints the plugin docs.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://developer.box.com/reference
