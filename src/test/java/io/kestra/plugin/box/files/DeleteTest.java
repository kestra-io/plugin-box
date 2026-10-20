package io.kestra.plugin.box.files;

import org.junit.jupiter.api.Test;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.files.FilesManager;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
public class DeleteTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeDelete extends Delete {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    @Test
    void run() throws Exception {
        FilesManager files = mock(FilesManager.class);
        BoxClient client = mock(BoxClient.class);
        when(client.getFiles()).thenReturn(files);

        Delete.Output output = FakeDelete.builder()
            .fakeClient(client)
            .fileId(Property.ofValue("42"))
            .build()
            .run(runContextFactory.of());

        verify(files).deleteFileById("42");
        assertThat(output.getFileId(), is("42"));
    }
}
