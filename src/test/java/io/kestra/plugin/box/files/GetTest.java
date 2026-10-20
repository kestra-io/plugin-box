package io.kestra.plugin.box.files;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.files.FilesManager;
import com.box.sdkgen.schemas.filefull.FileFull;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
public class GetTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeGet extends Get {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    @Test
    void run() throws Exception {
        FileFull file = mock(FileFull.class);
        when(file.getId()).thenReturn("42");
        when(file.getName()).thenReturn("report.csv");
        when(file.getSize()).thenReturn(5L);
        when(file.getSha1()).thenReturn("abc");
        when(file.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-01-01T10:00:00Z"));

        FilesManager files = mock(FilesManager.class);
        when(files.getFileById("42")).thenReturn(file);
        BoxClient client = mock(BoxClient.class);
        when(client.getFiles()).thenReturn(files);

        Get.Output output = FakeGet.builder()
            .fakeClient(client)
            .fileId(Property.ofValue("42"))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getFile().getId(), is("42"));
        assertThat(output.getFile().getName(), is("report.csv"));
        assertThat(output.getFile().getSize(), is(5L));
        assertThat(output.getFile().getSha1(), is("abc"));
        assertThat(output.getFile().getType(), is("file"));
    }
}
