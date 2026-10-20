package io.kestra.plugin.box.folders;


import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.box.AbstractBoxTask;
import io.kestra.plugin.box.models.BoxItem;
import io.kestra.plugin.box.models.FetchOutput;

import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "List Box folder items",
    description = "Lists the files, folders and web links directly inside a Box folder. `fetchType` controls memory vs storage output."
)
@Plugin(
    examples = {
        @Example(
            title = "List a folder and log the count.",
            full = true,
            code = """
                id: box_list_folder
                namespace: company.team

                tasks:
                  - id: list_items
                    type: io.kestra.plugin.box.folders.List
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    folderId: "123456789"
                    fetchType: FETCH

                  - id: log_results
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_items.size }} items"
                """
        )
    }
)
public class List extends AbstractBoxTask implements RunnableTask<FetchOutput> {

    @Schema(title = "Folder ID", description = "`0` is the root folder.")
    @Builder.Default
    @PluginProperty(group = "main")
    private Property<String> folderId = Property.ofValue("0");

    @Schema(
        title = "Fetch strategy",
        description = """
            `FETCH_ONE`: first item only.
            `FETCH` (default): all items in memory.
            `STORE`: write items to Kestra storage and return the URI."""
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        var rFolderId = runContext.render(folderId).as(String.class).orElse("0");
        var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        var client = client(runContext);

        try (var items = FetchOutput.collector(runContext, rFetchType)) {
            String marker = null;
            do {
                // Box returns only id/name/type by default, ask for what BoxItem exposes
                var params = new GetFolderItemsQueryParams.Builder()
                    .usemarker(true)
                    .marker(marker)
                    .limit(rFetchType == FetchType.FETCH_ONE ? 1L : 1000L)
                    .fields(java.util.List.of("id", "name", "type", "size", "parent", "created_at", "modified_at", "sha1"))
                    .build();
                var page = client.getFolders().getFolderItems(rFolderId, params);
                for (var item : page.getEntries()) {
                    items.add(BoxItem.of(item));
                }
                marker = page.getNextMarker();
            } while (marker != null && !marker.isEmpty() && rFetchType != FetchType.FETCH_ONE);

            runContext.logger().debug("Found {} items in Box folder {}", items.size(), rFolderId);
            return items.build();
        }
    }
}
