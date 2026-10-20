package io.kestra.plugin.box.search;

import java.util.List;

import com.box.sdkgen.managers.search.SearchForContentQueryParams;

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
    title = "Search Box content",
    description = "Searches files and folders by query, with optional file extension and owner filters."
)
@Plugin(
    examples = {
        @Example(
            title = "Find all PDF files matching a query.",
            full = true,
            code = """
                id: box_search
                namespace: company.team

                tasks:
                  - id: search
                    type: io.kestra.plugin.box.search.Search
                    clientId: "{{ secret('BOX_CLIENT_ID') }}"
                    clientSecret: "{{ secret('BOX_CLIENT_SECRET') }}"
                    enterpriseId: "{{ secret('BOX_ENTERPRISE_ID') }}"
                    query: invoice
                    fileExtensions:
                      - pdf
                    fetchType: FETCH
                """
        )
    }
)
public class Search extends AbstractBoxTask implements RunnableTask<FetchOutput> {

    @Schema(title = "Search query")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> query;

    @Schema(title = "File extensions", description = "Only return files with these extensions, e.g. `pdf`, `csv`.")
    @PluginProperty(group = "advanced")
    private Property<List<String>> fileExtensions;

    @Schema(title = "Owner user IDs", description = "Only return content owned by these Box users.")
    @PluginProperty(group = "advanced")
    private Property<List<String>> ownerUserIds;

    @Schema(
        title = "Fetch strategy",
        description = """
            `FETCH_ONE`: first result only.
            `FETCH` (default): all results in memory.
            `STORE`: write results to Kestra storage and return the URI."""
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    private static final long MAX_RESULTS = 10_000L;

    @Override
    public FetchOutput run(RunContext runContext) throws Exception {
        var rQuery = runContext.render(query).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'query' is required"));
        var rFetchType = runContext.render(fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        var client = client(runContext);

        // Box rejects an empty filter list, so an unset filter must be sent as null
        var rExtensions = nullIfEmpty(runContext.render(fileExtensions).asList(String.class));
        var rOwners = nullIfEmpty(runContext.render(ownerUserIds).asList(String.class));

        try (var items = FetchOutput.collector(runContext, rFetchType)) {
            long offset = 0;
            while (true) {
                // Box rejects offset + limit above 10000
                var limit = rFetchType == FetchType.FETCH_ONE ? 1L : Math.min(200L, MAX_RESULTS - offset);
                if (limit <= 0) {
                    runContext.logger().warn("Box search returns at most {} results, stopping there. Narrow the query with fileExtensions or ownerUserIds to get the rest.", MAX_RESULTS);
                    break;
                }
                var params = new SearchForContentQueryParams.Builder()
                    .query(rQuery)
                    .fileExtensions(rExtensions)
                    .ownerUserIds(rOwners)
                    .limit(limit)
                    .offset(offset)
                    .build();
                var results = client.getSearch().searchForContent(params).getSearchResults();

                for (var entry : results.getEntries()) {
                    items.add(BoxItem.of(entry));
                }
                offset += results.getEntries().size();
                var total = results.getTotalCount();
                if (results.getEntries().isEmpty() || (total != null && offset >= total) || rFetchType == FetchType.FETCH_ONE) {
                    break;
                }
            }

            runContext.logger().debug("Found {} Box results for '{}'", items.size(), rQuery);
            return items.build();
        }
    }

    private static List<String> nullIfEmpty(List<String> list) {
        return list == null || list.isEmpty() ? null : list;
    }
}
