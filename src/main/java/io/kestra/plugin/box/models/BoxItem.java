package io.kestra.plugin.box.models;

import java.time.OffsetDateTime;

import com.box.sdkgen.schemas.file.File;
import com.box.sdkgen.schemas.folder.Folder;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.searchresultitem.SearchResultItem;
import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class BoxItem {
    @Schema(title = "Item ID")
    @PluginProperty(group = "advanced")
    private final String id;

    @Schema(title = "Item name")
    @PluginProperty(group = "advanced")
    private final String name;

    @Schema(title = "Item type", description = "`file`, `folder` or `web_link`.")
    @PluginProperty(group = "advanced")
    private final String type;

    @Schema(title = "Parent folder ID")
    @PluginProperty(group = "advanced")
    private final String parentId;

    @Schema(title = "Size in bytes", description = "Null for folders.")
    @PluginProperty(group = "advanced")
    private final Long size;

    @Schema(title = "Creation time")
    @PluginProperty(group = "advanced")
    private final OffsetDateTime createdAt;

    @Schema(title = "Last modification time")
    @PluginProperty(group = "advanced")
    private final OffsetDateTime modifiedAt;

    @Schema(title = "SHA-1 hash", description = "Null for folders.")
    @PluginProperty(group = "advanced")
    private final String sha1;

    public static BoxItem of(Folder folder) {
        return BoxItem.builder()
            .id(folder.getId())
            .name(folder.getName())
            .type("folder")
            .parentId(folder.getParent() == null ? null : folder.getParent().getId())
            .size(folder.getSize())
            .createdAt(folder.getCreatedAt())
            .modifiedAt(folder.getModifiedAt())
            .build();
    }

    // folder listing entry: a file, a folder or a web link
    public static BoxItem of(Item item) {
        if (item.isFileFull()) {
            return of(item.getFileFull());
        } else if (item.isFolderFull()) {
            return of(item.getFolderFull());
        }
        return BoxItem.builder().id(item.getWebLink().getId()).name(item.getWebLink().getName()).type("web_link").build();
    }

    // search result entry: same three cases
    public static BoxItem of(SearchResultItem item) {
        if (item.isFileFull()) {
            return of(item.getFileFull());
        } else if (item.isFolderFull()) {
            return of(item.getFolderFull());
        }
        return BoxItem.builder().id(item.getWebLink().getId()).name(item.getWebLink().getName()).type("web_link").build();
    }

    public static BoxItem of(File file) {
        return BoxItem.builder()
            .id(file.getId())
            .name(file.getName())
            .type("file")
            .parentId(file.getParent() == null ? null : file.getParent().getId())
            .size(file.getSize())
            .createdAt(file.getCreatedAt())
            .modifiedAt(file.getModifiedAt())
            .sha1(file.getSha1())
            .build();
    }
}
