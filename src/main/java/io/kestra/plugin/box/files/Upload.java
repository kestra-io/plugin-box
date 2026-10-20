package io.kestra.plugin.box.files;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.box.sdkgen.managers.uploads.UploadFileRequestBody;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesField;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesParentField;
import com.box.sdkgen.schemas.file.File;

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
import lombok.Builder;
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
    title = "Upload a file to Box",
    description = """
        Uploads a file from Kestra internal storage to a Box folder. Files above 50 MB use Box chunked upload automatically."""
)
@Plugin(
    examples = {
        @Example(
            title = "Upload a generated report to a Box folder.",
            full = true,
            code = """
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
                """
        )
    }
)
public class Upload extends AbstractBoxTask implements RunnableTask<Upload.Output> {

    // Box recommends chunked upload for big files, the simple endpoint is capped at 50 MB
    static final long CHUNKED_THRESHOLD = 50L * 1024 * 1024;

    @Schema(title = "Source file", description = "Kestra internal storage URI, e.g. `{{ outputs.prev_task.uri }}`.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> from;

    @Schema(title = "Destination folder ID", description = "`0` is the root folder.")
    @Builder.Default
    @PluginProperty(group = "destination")
    private Property<String> folderId = Property.ofValue("0");

    @Schema(title = "File name in Box", description = "Defaults to the name of the source file in internal storage.")
    @PluginProperty(group = "destination")
    private Property<String> name;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFrom = URI.create(runContext.render(from).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'from' is required")));
        var rFolderId = runContext.render(folderId).as(String.class).orElse("0");
        var rName = runContext.render(name).as(String.class)
            .orElseGet(() -> Path.of(rFrom.getPath()).getFileName().toString());

        var tempFile = runContext.workingDir().createTempFile();
        try (InputStream in = runContext.storage().getFile(rFrom)) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        var size = Files.size(tempFile);

        var client = client(runContext);
        runContext.logger().info("Uploading '{}' ({} bytes) to Box folder {}", rName, size, rFolderId);

        File uploaded;
        try (InputStream in = Files.newInputStream(tempFile)) {
            if (size > CHUNKED_THRESHOLD) {
                uploaded = client.getChunkedUploads().uploadBigFile(in, rName, size, rFolderId);
            } else {
                var attributes = new UploadFileRequestBodyAttributesField(
                    rName,
                    new UploadFileRequestBodyAttributesParentField(rFolderId)
                );
                uploaded = client.getUploads().uploadFile(new UploadFileRequestBody(attributes, in)).getEntries().getFirst();
            }
        }

        return Output.builder().file(BoxItem.of(uploaded)).build();
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Uploaded file", description = "Metadata of the file created in Box.")
        private final BoxItem file;
    }
}
