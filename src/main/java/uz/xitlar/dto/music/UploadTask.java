package uz.xitlar.dto.music;

import java.nio.file.Path;

public record UploadTask(
        Path tempPath,
        String originalFilename,
        String contentType,
        long size,
        BulkMusicItemDto metadata,
        String error
) {
    public UploadTask(Path tempPath, String originalFilename, String contentType, long size, BulkMusicItemDto metadata) {
        this(tempPath, originalFilename, contentType, size, metadata, null);
    }
}
