package io.kestra.plugin.box.files;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.uploads.UploadFileRequestBody;
import com.box.sdkgen.managers.uploads.UploadsManager;
import com.box.sdkgen.schemas.filefull.FileFull;
import com.box.sdkgen.schemas.files.Files;

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
public class UploadTest {
    @Inject
    private RunContextFactory runContextFactory;

    // Test double: the real task, but client() hands back a mock instead of calling Box
    @SuperBuilder
    @NoArgsConstructor
    public static class FakeUpload extends Upload {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI source = runContext.storage().putFile(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "report.csv");

        FileFull uploaded = mock(FileFull.class);
        when(uploaded.getId()).thenReturn("42");
        when(uploaded.getName()).thenReturn("report.csv");
        when(uploaded.getSize()).thenReturn(5L);
        Files answer = mock(Files.class);
        when(answer.getEntries()).thenReturn(List.of(uploaded));

        UploadsManager uploads = mock(UploadsManager.class);
        when(uploads.uploadFile(any(UploadFileRequestBody.class))).thenReturn(answer);
        BoxClient client = mock(BoxClient.class);
        when(client.getUploads()).thenReturn(uploads);

        Upload.Output output = FakeUpload.builder()
            .fakeClient(client)
            .from(Property.ofValue(source.toString()))
            .folderId(Property.ofValue("123"))
            .name(Property.ofValue("report.csv"))
            .build()
            .run(runContext);

        assertThat(output.getFile().getId(), is("42"));
        assertThat(output.getFile().getName(), is("report.csv"));

        // what was actually sent to Box
        ArgumentCaptor<UploadFileRequestBody> sent = ArgumentCaptor.forClass(UploadFileRequestBody.class);
        verify(uploads).uploadFile(sent.capture());
        assertThat(sent.getValue().getAttributes().getName(), is("report.csv"));
        assertThat(sent.getValue().getAttributes().getParent().getId(), is("123"));
    }
}
