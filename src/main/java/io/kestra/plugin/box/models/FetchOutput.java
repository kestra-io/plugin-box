package io.kestra.plugin.box.models;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** Output shared by every task that returns a list of Box items, shaped by `fetchType`. */
@SuperBuilder
@Getter
public class FetchOutput implements io.kestra.core.models.tasks.Output {
    @Schema(title = "Items", description = "Populated when `fetchType` is `FETCH`.")
    private final List<BoxItem> rows;

    @Schema(title = "First item", description = "Populated when `fetchType` is `FETCH_ONE`.")
    private final BoxItem row;

    @Schema(title = "Stored items URI", description = "Populated when `fetchType` is `STORE`.")
    private final URI uri;

    @Schema(title = "Item count")
    private final long size;

    /** Collects items as pages arrive: `STORE` streams to a temp file, so only `FETCH` keeps rows in memory. */
    public static Collector collector(RunContext runContext, FetchType fetchType) throws IOException {
        return new Collector(runContext, fetchType);
    }

    public static class Collector implements Closeable {
        private final RunContext runContext;
        private final FetchType fetchType;
        private final List<BoxItem> rows = new ArrayList<>();
        private final Path tempFile;
        private final OutputStream out;
        private long size;

        private Collector(RunContext runContext, FetchType fetchType) throws IOException {
            this.runContext = runContext;
            this.fetchType = fetchType;
            this.tempFile = fetchType == FetchType.STORE ? runContext.workingDir().createTempFile(".ion") : null;
            this.out = tempFile == null ? null : new BufferedOutputStream(Files.newOutputStream(tempFile), FileSerde.BUFFER_SIZE);
        }

        public void add(BoxItem item) throws IOException {
            size++;
            switch (fetchType) {
                case FETCH_ONE -> { if (rows.isEmpty()) rows.add(item); }
                case FETCH -> rows.add(item);
                case STORE -> FileSerde.write(out, item);
                case NONE -> { }
            }
        }

        public long size() {
            return size;
        }

        public FetchOutput build() throws IOException {
            FetchOutputBuilder<?, ?> output = FetchOutput.builder().size(size);
            switch (fetchType) {
                case FETCH_ONE -> output.row(rows.isEmpty() ? null : rows.getFirst());
                case FETCH -> output.rows(rows);
                case STORE -> {
                    close();
                    output.uri(runContext.storage().putFile(tempFile.toFile()));
                }
                case NONE -> { }
            }
            return output.build();
        }

        @Override
        public void close() throws IOException {
            if (out != null) {
                out.close();
            }
        }
    }
}
