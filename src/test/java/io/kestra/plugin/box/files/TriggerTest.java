package io.kestra.plugin.box.files;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.FoldersManager;
import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;
import com.box.sdkgen.schemas.filefull.FileFull;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.items.Items;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.box.models.BoxItem;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
public class TriggerTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeTrigger extends Trigger {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    private static OffsetDateTime at(int second) {
        return OffsetDateTime.of(2026, 1, 1, 10, 0, second, 0, ZoneOffset.UTC);
    }

    private static BoxItem boxFile(String id, int second) {
        return BoxItem.builder().id(id).name(id + ".txt").type("file").createdAt(at(second)).build();
    }

    @Test
    void next_returnsOldestFileAfterPosition() {
        var files = List.of(boxFile("c", 30), boxFile("a", 10), boxFile("b", 20));

        Optional<BoxItem> next = Trigger.next(new Trigger.Position(at(10).toInstant(), "a"), files);

        assertThat(next.orElseThrow().getId(), is("b"));
    }

    @Test
    void next_emptyWhenNothingNewer() {
        var files = List.of(boxFile("a", 10), boxFile("b", 20));

        assertThat(Trigger.next(new Trigger.Position(at(20).toInstant(), "b"), files).isPresent(), is(false));
    }

    @Test
    void next_sameSecondFilesAreNotSkippedNorRepeated() {
        var files = List.of(boxFile("a", 10), boxFile("b", 10), boxFile("c", 10));

        var first = Trigger.next(new Trigger.Position(Instant.parse("2026-01-01T09:00:00Z"), ""), files).orElseThrow();
        var second = Trigger.next(Trigger.Position.of(first), files).orElseThrow();
        var third = Trigger.next(Trigger.Position.of(second), files).orElseThrow();

        assertThat(first.getId() + second.getId() + third.getId(), is("abc"));
        assertThat(Trigger.next(Trigger.Position.of(third), files).isPresent(), is(false));
    }

    @Test
    void position_survivesFormatAndParse() {
        var position = new Trigger.Position(at(10).toInstant(), "id|with|pipes");

        assertThat(Trigger.Position.parse(position.format()), is(position));
    }

    @Test
    void watermarkKey_isDistinctPerFlowAndSegmentSplit() {
        var base = Trigger.watermarkKey("flow_a", "on_new_file", "123");

        assertThat(Trigger.watermarkKey("flow_b", "on_new_file", "123"), not(base));
        assertThat(Trigger.watermarkKey("flow_a", "on_new_file", "456"), not(base));
        // same joined text, different split between flow and trigger
        assertThat(Trigger.watermarkKey("ab", "c", "1"), not(Trigger.watermarkKey("a", "bc", "1")));
    }

    @Test
    void tryParse_corruptedValueIsEmpty() {
        assertThat(Trigger.Position.tryParse("garbage").isPresent(), is(false));
        assertThat(Trigger.Position.tryParse("").isPresent(), is(false));
        assertThat(Trigger.Position.tryParse("not-a-date|id").isPresent(), is(false));
        assertThat(Trigger.Position.tryParse(at(10).toInstant() + "|id").isPresent(), is(true));
    }

    @Test
    void evaluate_rejectsIntervalBelowMinimum() {
        var trigger = FakeTrigger.builder()
            .id("box-" + UUID.randomUUID())
            .type(Trigger.class.getName())
            .interval(Duration.ofSeconds(1))
            .build();

        assertThrows(IllegalArgumentException.class, () -> trigger.evaluate(null, null));
    }

    private Item fileItem(String id, int second) {
        FileFull file = mock(FileFull.class);
        when(file.getId()).thenReturn(id);
        when(file.getName()).thenReturn(id + ".txt");
        when(file.getCreatedAt()).thenReturn(at(second));
        Item item = mock(Item.class);
        when(item.isFileFull()).thenReturn(true);
        when(item.getFileFull()).thenReturn(file);
        return item;
    }

    private void folderContains(FoldersManager folders, Item... items) {
        Items page = mock(Items.class);
        when(page.getEntries()).thenReturn(List.of(items));
        when(folders.getFolderItems(eq("123"), any(GetFolderItemsQueryParams.class))).thenReturn(page);
    }

    @Test
    void evaluate_ignoresFileWithoutCreatedAt() throws Exception {
        FoldersManager folders = mock(FoldersManager.class);
        BoxClient client = mock(BoxClient.class);
        when(client.getFolders()).thenReturn(folders);
        var trigger = FakeTrigger.builder()
            .id("box-" + UUID.randomUUID())
            .type(Trigger.class.getName())
            .fakeClient(client)
            .folderId(Property.ofValue("123"))
            .build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        // a file with no created_at has no position: the first poll must not throw
        Item undated = mock(Item.class);
        when(undated.isFileFull()).thenReturn(true);
        when(undated.getFileFull()).thenReturn(mock(FileFull.class));
        folderContains(folders, undated, fileItem("old", 10));
        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(false));

        folderContains(folders, undated, fileItem("old", 10), fileItem("new", 20));
        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(true));
    }

    @Test
    void evaluate() throws Exception {
        FoldersManager folders = mock(FoldersManager.class);
        BoxClient client = mock(BoxClient.class);
        when(client.getFolders()).thenReturn(folders);

        var trigger = FakeTrigger.builder()
            .id("box-" + UUID.randomUUID())
            .type(Trigger.class.getName())
            .fakeClient(client)
            .folderId(Property.ofValue("123"))
            .build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        // 1st poll: "old" is already in the folder, so it only records the position
        folderContains(folders, fileItem("old", 10));
        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(false));

        // 2nd poll: nothing new
        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(false));

        // 3rd poll: two new files, the oldest fires first
        folderContains(folders, fileItem("old", 10), fileItem("new2", 30), fileItem("new1", 20));
        Optional<Execution> first = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(first.isPresent(), is(true));
        assertThat(first.get().getTrigger().getVariables().get("name"), is("new1.txt"));

        // 4th poll: the other one, then nothing
        Optional<Execution> second = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(second.orElseThrow().getTrigger().getVariables().get("name"), is("new2.txt"));
        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(false));
        assertThat(first.get().getTrigger().getVariables().get("id"), notNullValue());
    }
}
