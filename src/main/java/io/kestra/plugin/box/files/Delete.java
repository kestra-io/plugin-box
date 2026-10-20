package io.kestra.plugin.box.files;

import com.box.sdkgen.client.BoxClient;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.box.AbstractBoxTask;

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
    title = "Delete a file from Box",
    description = "Deletes a file from Box."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a file from Box.",
            full = true,
            code = """
                id: box_delete_file
                namespace: company.team

                tasks:
                  - id: delete
                    type: io.kestra.plugin.box.files.Delete
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    fileId: "123456789"

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message: "Deleted file {{ outputs.delete.fileId }}"
                """
        )
    }
)
public class Delete extends AbstractBoxTask implements RunnableTask<Delete.Output> {

    @Schema(title = "File ID", description = "ID of the Box file to delete.")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> fileId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFileId = runContext.render(fileId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'fileId' is required"));

        var client = client(runContext);
        runContext.logger().info("Deleting Box file {}", rFileId);
        client.getFiles().deleteFileById(rFileId);
        return Output.builder().fileId(rFileId).build();
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Deleted file ID")
        private final String fileId;
    }
}
