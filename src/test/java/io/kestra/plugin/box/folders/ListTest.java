package io.kestra.plugin.box.folders;

import java.io.BufferedInputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.FoldersManager;
import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;
import com.box.sdkgen.schemas.filefull.FileFull;
import com.box.sdkgen.schemas.folderfull.FolderFull;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.items.Items;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.box.models.FetchOutput;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
public class ListTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeList extends List {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    private static Item file(String id, String name) {
        FileFull file = mock(FileFull.class);
        when(file.getId()).thenReturn(id);
        when(file.getName()).thenReturn(name);
        Item item = mock(Item.class);
        when(item.isFileFull()).thenReturn(true);
        when(item.getFileFull()).thenReturn(file);
        return item;
    }

    private static Item folder(String id, String name) {
        FolderFull folder = mock(FolderFull.class);
        when(folder.getId()).thenReturn(id);
        when(folder.getName()).thenReturn(name);
        Item item = mock(Item.class);
        when(item.isFolderFull()).thenReturn(true);
        when(item.getFolderFull()).thenReturn(folder);
        return item;
    }

    private static Items page(String nextMarker, Item... items) {
        Items page = mock(Items.class);
        when(page.getEntries()).thenReturn(java.util.List.of(items));
        when(page.getNextMarker()).thenReturn(nextMarker);
        return page;
    }

    private FoldersManager folders;

    private FakeList.FakeListBuilder<?, ?> task(Items first, Items... others) {
        folders = mock(FoldersManager.class);
        when(folders.getFolderItems(eq("123"), any(GetFolderItemsQueryParams.class))).thenReturn(first, others);
        BoxClient client = mock(BoxClient.class);
        when(client.getFolders()).thenReturn(folders);
        return FakeList.builder().fakeClient(client).folderId(Property.ofValue("123"));
    }

    @Test
    void fetch_followsMarkerAcrossPages() throws Exception {
        var task = task(page("m2", file("1", "a.txt")), page(null, folder("2", "docs"))).build();

        FetchOutput output = task.run(runContextFactory.of());

        assertThat(output.getSize(), is(2L));
        assertThat(output.getRows().get(0).getName(), is("a.txt"));
        assertThat(output.getRows().get(0).getType(), is("file"));
        assertThat(output.getRows().get(1).getType(), is("folder"));

        ArgumentCaptor<GetFolderItemsQueryParams> params = ArgumentCaptor.forClass(GetFolderItemsQueryParams.class);
        verify(folders, times(2)).getFolderItems(eq("123"), params.capture());
        assertThat(params.getAllValues().get(0).getMarker(), nullValue());
        assertThat(params.getAllValues().get(1).getMarker(), is("m2"));
    }

    @Test
    void fetchOne_stopsAfterFirstPage() throws Exception {
        var task = task(page("m2", file("1", "a.txt"))).fetchType(Property.ofValue(FetchType.FETCH_ONE)).build();

        FetchOutput output = task.run(runContextFactory.of());

        assertThat(output.getRow().getId(), is("1"));
        verify(folders, times(1)).getFolderItems(eq("123"), any(GetFolderItemsQueryParams.class));
    }

    @Test
    void store_streamsEveryPageInOrder() throws Exception {
        RunContext runContext = runContextFactory.of();
        var task = task(page("m2", file("1", "a.txt"), file("2", "b.txt")), page(null, file("3", "c.txt")))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        FetchOutput output = task.run(runContext);

        assertThat(output.getSize(), is(3L));
        assertThat(output.getRows(), nullValue());
        try (var in = runContext.storage().getFile(output.getUri())) {
            var rows = FileSerde.readAll(new BufferedInputStream(in), Map.class).collectList().block();
            assertThat(rows.stream().map(r -> r.get("name")).toList(), is(java.util.List.of("a.txt", "b.txt", "c.txt")));
        }
    }

    @Test
    void store() throws Exception {
        RunContext runContext = runContextFactory.of();
        var task = task(page(null, file("1", "a.txt"))).fetchType(Property.ofValue(FetchType.STORE)).build();

        FetchOutput output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        try (var in = runContext.storage().getFile(output.getUri())) {
            var rows = FileSerde.readAll(new BufferedInputStream(in), Map.class).collectList().block();
            assertThat(rows.getFirst().get("name"), is("a.txt"));
        }
    }
}
