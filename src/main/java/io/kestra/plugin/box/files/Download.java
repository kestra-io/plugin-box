package io.kestra.plugin.box.files;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.box.AbstractBoxTask;
import io.kestra.plugin.box.models.BoxItem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Download a file from Box",
    description = "Downloads a file from Box to Kestra internal storage and returns its `uri`."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a file from Box to Kestra internal storage.",
            full = true,
            code = """
                id: box_download_file
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.box.files.Download
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    fileId: "123456789"

                  - id: log_uri
                    type: io.kestra.plugin.core.log.Log
                    message: "Downloaded {{ outputs.download.file.name }} to {{ outputs.download.uri }}"
                """
        )
    }
)
public class Download extends AbstractBoxTask implements RunnableTask<Download.Output> {

    @Schema(title = "File ID", description = "ID of the Box file to download.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> fileId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFileId = runContext.render(fileId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'fileId' is required"));

        var client = client(runContext);
        runContext.logger().info("Downloading Box file {}", rFileId);

        // stream to a temp file so the whole file is never held in memory
        var tempFile = runContext.workingDir().createTempFile();
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            client.getDownloads().downloadFileToOutputStream(rFileId, out);
        }

        // the download call returns no metadata, so fetch it for the stored file's name
        var metadata = client.getFiles().getFileById(rFileId);
        var uri = runContext.storage().putFile(tempFile.toFile(), metadata.getName());

        return Output.builder()
            .uri(uri)
            .file(BoxItem.of(metadata))
            .build();
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Downloaded file URI", description = "Location of the file in Kestra internal storage.")
        private final URI uri;

        @Schema(title = "File metadata", description = "Box metadata of the downloaded file.")
        private final BoxItem file;
    }
}
