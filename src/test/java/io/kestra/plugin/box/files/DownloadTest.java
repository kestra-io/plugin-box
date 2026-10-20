package io.kestra.plugin.box.files;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.downloads.DownloadsManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
public class DownloadTest {
    @Inject
    private RunContextFactory runContextFactory;

    @SuperBuilder
    @NoArgsConstructor
    public static class FakeDownload extends Download {
        private BoxClient fakeClient;

        @Override
        protected BoxClient client(RunContext runContext) {
            return fakeClient;
        }
    }

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        // downloadFileToOutputStream returns void: the fake "downloads" by writing bytes into the stream it receives
        DownloadsManager downloads = mock(DownloadsManager.class);
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write("hello box".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(downloads).downloadFileToOutputStream(eq("42"), any(OutputStream.class));

        FileFull file = mock(FileFull.class);
        when(file.getId()).thenReturn("42");
        when(file.getName()).thenReturn("report.csv");
        FilesManager files = mock(FilesManager.class);
        when(files.getFileById("42")).thenReturn(file);

        BoxClient client = mock(BoxClient.class);
        when(client.getDownloads()).thenReturn(downloads);
        when(client.getFiles()).thenReturn(files);

        Download.Output output = FakeDownload.builder()
            .fakeClient(client)
            .fileId(Property.ofValue("42"))
            .build()
            .run(runContext);

        assertThat(output.getFile().getName(), is("report.csv"));
        try (var in = runContext.storage().getFile(output.getUri())) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8), is("hello box"));
        }
    }
}
