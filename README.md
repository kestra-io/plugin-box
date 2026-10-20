<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
  <a href="https://twitter.com/kestra_io" style="margin: 0 10px;">
        <img src="https://kestra.io/twitter.svg" alt="twitter" width="35" height="25" /></a>
  <a href="https://www.linkedin.com/company/kestra/" style="margin: 0 10px;">
        <img src="https://kestra.io/linkedin.svg" alt="linkedin" width="35" height="25" /></a>
  <a href="https://www.youtube.com/@kestra-io" style="margin: 0 10px;">
        <img src="https://kestra.io/youtube.svg" alt="youtube" width="35" height="25" /></a>
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 3 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 3 minutes.</i></p>

# Kestra Box Plugin

## Why

- Teams that exchange documents through Box (reports out, documents in) usually call the Box REST API from shell or Python tasks and handle token refresh by hand. This plugin does it from a single flow, with the official Box SDK handling authentication and token refresh.
- It keeps Box steps in the same Kestra flow as upstream preparation, retries, notifications and downstream systems, next to the other file-storage plugins (Dropbox, S3, Microsoft 365).
- It removes custom API glue code, and credentials stay in Kestra secrets.

## What

Plugin components under `io.kestra.plugin.box`:

| Task or trigger | What it does |
|---|---|
| `files.Upload` | Upload a file from Kestra internal storage to a Box folder (chunked above 50 MB) |
| `files.Download` | Download a Box file to Kestra internal storage and output its `uri` |
| `files.Get` | Get a file's metadata |
| `files.Delete` | Delete a file |
| `folders.List` | List a folder's items, with `fetchType` `FETCH_ONE`, `FETCH` or `STORE` |
| `folders.Create` | Create a folder |
| `search.Search` | Search content by query, file extension and owner |
| `files.Trigger` | Poll a folder and start an execution for each new file |

Every task takes the same connection properties: `developerToken`, `jwtConfig`, or `clientId` + `clientSecret` with `enterpriseId` or `userId`. Keep them in secrets.

## Example

```yaml
id: box_upload_report
namespace: company.team

tasks:
  - id: generate
    type: io.kestra.plugin.core.storage.LocalFiles
    outputs:
      - report.csv

  - id: upload
    type: io.kestra.plugin.box.files.Upload
    clientId: "{{ secret('BOX_CLIENT_ID') }}"
    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
    from: "{{ outputs.generate.outputFiles['report.csv'] }}"
    folderId: "0"
    name: report.csv
```

## Setup

### Prerequisites

- JDK 21 to 23. The build uses Lombok, which does not support newer JDKs yet, so check `java -version`.
- Docker with Docker Compose, to run Kestra locally.
- A Box account. The free [developer tier](https://developer.box.com) is enough.

### Box credentials

1. In the Box developer console, create a **Custom App** with **Server Authentication (Client Credentials Grant)**.
2. Have a Box admin **authorize the app** in the Admin Console. Without this, Box answers `unauthorized_client` even with correct credentials.
3. Copy the client ID, client secret and enterprise ID. For a quick test you can use a developer token instead (it expires after 60 minutes).

### Build and test

```bash
./gradlew test               # unit tests, no Box account needed
./gradlew build              # tests + plugin documentation lint
./gradlew shadowJar          # plugin jar in build/libs/
```

The tests use a fake Box client, so no credentials are needed in CI.

## Running Kestra locally with this plugin

1. Build the shadow JAR: `./gradlew shadowJar`. The output lands in `build/libs/`.
2. Run `docker compose up`. `docker-compose.yml` builds the Kestra image pinned in `Dockerfile` and mounts `build/libs/` to `/app/plugins/`, so Kestra picks up the jar on startup. Kestra serves its UI from the same container.
3. Open the Kestra UI at [localhost:8080](http://localhost:8080). The Box plugin appears under Plugins, and its tasks are available in the flow editor.
4. After changing code, rebuild with `./gradlew shadowJar` and restart with `docker compose restart`.

### Secrets in a local Kestra

`{{ secret('BOX_CLIENT_ID') }}` reads an environment variable named `SECRET_BOX_CLIENT_ID` whose value is **base64-encoded**. Add one per secret to the `environment:` section of the `app` service in `docker-compose.yml`:

```yaml
    environment:
      SECRET_BOX_CLIENT_ID: <base64 of the client ID>
      SECRET_BOX_CLIENT_SECRET: <base64 of the client secret>
      SECRET_BOX_ENTERPRISE_ID: <base64 of the enterprise ID>
```

Do not commit real credentials.

### Plugins folder gotcha

Mounting a host folder onto `/app/plugins/` replaces the container's plugins directory rather than adding to it. Core plugins (the ones logged as `Registered N core plugins`) are compiled into Kestra itself and aren't affected, but any additional plugin normally bundled in the base image under `/app/plugins/` (e.g. the Python script plugin) gets hidden once the mount is in place. If a flow you're testing depends on another plugin, copy its jar into `build/libs/` too before starting the container.

### JFR startup error

On some hosts, `command: server local` fails with:
```
Unable to create JFR repository directory using base location (/tmp)
```
`docker-compose.yml` works around this by mounting `/tmp` as `tmpfs`. If you build your own compose file or run Kestra via `docker run`, add the same workaround, e.g. `-v /tmp:/tmp` or `--tmpfs /tmp`. Tracked upstream in [kestra-io/kestra#17405](https://github.com/kestra-io/kestra/issues/17405).

## Documentation
* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)


## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
