package io.kestra.plugin.box.folders;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.CreateFolderRequestBody;
import com.box.sdkgen.managers.folders.FoldersManager;
import com.box.sdkgen.schemas.folderfull.FolderFull;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
public class CreateTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeCreate extends Create {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    @Test
    void run() throws Exception {
        FolderFull created = mock(FolderFull.class);
        when(created.getId()).thenReturn("99");
        when(created.getName()).thenReturn("reports");

        FoldersManager folders = mock(FoldersManager.class);
        when(folders.createFolder(any(CreateFolderRequestBody.class))).thenReturn(created);
        BoxClient client = mock(BoxClient.class);
        when(client.getFolders()).thenReturn(folders);

        Create.Output output = FakeCreate.builder()
            .fakeClient(client)
            .parentFolderId(Property.ofValue("123"))
            .name(Property.ofValue("reports"))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getFolder().getId(), is("99"));
        assertThat(output.getFolder().getType(), is("folder"));

        ArgumentCaptor<CreateFolderRequestBody> sent = ArgumentCaptor.forClass(CreateFolderRequestBody.class);
        verify(folders).createFolder(sent.capture());
        assertThat(sent.getValue().getName(), is("reports"));
        assertThat(sent.getValue().getParent().getId(), is("123"));
    }
}
