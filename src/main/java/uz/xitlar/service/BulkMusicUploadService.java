package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.music.BulkMusicItemDto;
import uz.xitlar.dto.music.BulkMusicUploadItemResponse;
import uz.xitlar.dto.music.BulkMusicUploadResponse;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.enums.UploadStatus;
import uz.xitlar.repository.AlbumRepository;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.dto.music.UploadTask;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkMusicUploadService {

    private final BulkMusicUploadProcessor uploadProcessor;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final AudioProcessingService audioProcessingService;

    @Value("${app.music.upload.max-files:50}")
    private int maxFiles = 50;

    @Value("${app.music.upload.cpu-concurrency:4}")
    private int cpuConcurrency = 4;

    public ResponseApi<BulkMusicUploadResponse> uploadBulk(
            List<MultipartFile> files,
            List<BulkMusicItemDto> metadataList
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one audio file must be provided for bulk upload");
        }

        if (files.size() > maxFiles) {
            throw new IllegalArgumentException(String.format(
                    "Bulk upload exceeds maximum limit of %d files. Received: %d files.",
                    maxFiles, files.size()
            ));
        }

        int totalFiles = files.size();
        log.info("Starting bulk music upload for {} files (max allowed: {}, CPU concurrency: {})",
                totalFiles, maxFiles, cpuConcurrency);

        // 1. Build metadata lookup (by fileName or index)
        Map<String, BulkMusicItemDto> metadataByName = new HashMap<>();
        if (metadataList != null) {
            for (BulkMusicItemDto meta : metadataList) {
                if (meta.getFileName() != null && !meta.getFileName().isBlank()) {
                    metadataByName.put(meta.getFileName().toLowerCase(), meta);
                }
            }
        }

        // 2. Pre-fetch artists and albums to avoid N+1 queries across the batch
        Set<Integer> artistIds = new HashSet<>();
        Set<Integer> albumIds = new HashSet<>();
        if (metadataList != null) {
            for (BulkMusicItemDto meta : metadataList) {
                if (meta.getArtistId() != null) artistIds.add(meta.getArtistId());
                if (meta.getAlbumId() != null) albumIds.add(meta.getAlbumId());
            }
        }

        Map<Integer, Artist> artistCache = new ConcurrentHashMap<>();
        if (!artistIds.isEmpty()) {
            artistRepository.findAllById(artistIds).forEach(artist -> artistCache.put(artist.getId(), artist));
        }

        Map<Integer, Album> albumCache = new ConcurrentHashMap<>();
        if (!albumIds.isEmpty()) {
            albumRepository.findAllById(albumIds).forEach(album -> albumCache.put(album.getId(), album));
        }

        // 3. CPU Semaphore to bound heavy audio decoding/tagging operations
        int boundedCpuPermits = Math.max(1, cpuConcurrency);
        Semaphore cpuSemaphore = new Semaphore(boundedCpuPermits);

        // 4. Copy multipart files to temp files synchronously in the request thread
        List<UploadTask> uploadTasks = new ArrayList<>(totalFiles);
        for (int i = 0; i < totalFiles; i++) {
            MultipartFile file = files.get(i);
            int fileIndex = i;

            BulkMusicItemDto itemDto = null;
            if (file.getOriginalFilename() != null) {
                itemDto = metadataByName.get(file.getOriginalFilename().toLowerCase());
            }
            if (itemDto == null && metadataList != null && fileIndex < metadataList.size()) {
                itemDto = metadataList.get(fileIndex);
            }

            UploadTask task;
            try {
                Path tempPath = audioProcessingService.copyToTemp(file);
                task = new UploadTask(
                        tempPath,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize(),
                        itemDto
                );
            } catch (Exception e) {
                log.warn("Failed early validation/copy for bulk file: {}", file.getOriginalFilename(), e);
                task = new UploadTask(
                        null,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize(),
                        itemDto,
                        e.getMessage()
                );
            }
            uploadTasks.add(task);
        }

        // 5. Concurrently process files using Java Virtual Threads
        List<BulkMusicUploadItemResponse> results = new ArrayList<>(totalFiles);

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<BulkMusicUploadItemResponse>> tasks = new ArrayList<>(totalFiles);

            for (UploadTask uploadTask : uploadTasks) {
                tasks.add(() -> uploadProcessor.processOne(
                        uploadTask,
                        artistCache,
                        albumCache,
                        cpuSemaphore
                ));
            }

            List<Future<BulkMusicUploadItemResponse>> futures = virtualExecutor.invokeAll(tasks);
            for (Future<BulkMusicUploadItemResponse> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof org.springframework.dao.DataIntegrityViolationException
                            || (cause != null && cause.getCause() instanceof org.hibernate.exception.ConstraintViolationException)) {
                        results.add(BulkMusicUploadItemResponse.builder()
                                .fileName("unknown")
                                .status(UploadStatus.DUPLICATE)
                                .error("Duplicate entity detected via database constraint")
                                .build());
                    } else {
                        log.error("Virtual thread execution failed for upload item", e);
                        results.add(BulkMusicUploadItemResponse.builder()
                                .fileName("unknown")
                                .status(UploadStatus.FAILED)
                                .error("Internal processing error")
                                .build());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Bulk upload processing was interrupted", e);
            throw new RuntimeException("Bulk upload processing was interrupted", e);
        }

        // 5. Aggregate result statistics
        int successCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (BulkMusicUploadItemResponse item : results) {
            if (item.getStatus() == UploadStatus.SUCCESS) {
                successCount++;
            } else if (item.getStatus() == UploadStatus.DUPLICATE) {
                duplicateCount++;
            } else {
                failedCount++;
            }
        }

        log.info("Bulk music upload finished. Total: {}, Success: {}, Duplicate: {}, Failed: {}",
                totalFiles, successCount, duplicateCount, failedCount);

        BulkMusicUploadResponse response = BulkMusicUploadResponse.builder()
                .total(totalFiles)
                .successCount(successCount)
                .duplicateCount(duplicateCount)
                .failedCount(failedCount)
                .results(results)
                .build();

        return ResponseApi.<BulkMusicUploadResponse>builder()
                .success(true)
                .message("Bulk music upload completed")
                .data(response)
                .build();
    }
}
