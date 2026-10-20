package io.kestra.plugin.box.files;

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
    title = "Get Box file metadata",
    description = "Fetches the metadata of a Box file (name, size, SHA-1, parent folder, timestamps)."
)
@Plugin(
    examples = {
        @Example(
            title = "Get the metadata of a Box file.",
            full = true,
            code = """
                id: box_get_file
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.box.files.Get
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    fileId: "123456789"

                  - id: log_name
                    type: io.kestra.plugin.core.log.Log
                    message: "File {{ outputs.get.file.name }} is {{ outputs.get.file.size }} bytes"
                """
        )
    }
)
public class Get extends AbstractBoxTask implements RunnableTask<Get.Output> {

    @Schema(title = "File ID", description = "ID of the Box file.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> fileId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFileId = runContext.render(fileId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'fileId' is required"));

        var client = client(runContext);
        runContext.logger().info("Getting the file with id: '{}'", rFileId);
        var file = client.getFiles().getFileById(rFileId);
        return Output.builder().file(BoxItem.of(file)).build();
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "File metadata")
        private final BoxItem file;
    }
}
