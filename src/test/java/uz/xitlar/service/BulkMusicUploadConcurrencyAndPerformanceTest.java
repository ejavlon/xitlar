package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.music.BulkMusicUploadResponse;
import uz.xitlar.enums.UploadStatus;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.util.AudioTestHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class BulkMusicUploadConcurrencyAndPerformanceTest {

    @Autowired
    private BulkMusicUploadService bulkMusicUploadService;

    @Autowired
    private AudioProcessingService audioProcessingService;

    @Autowired
    private MusicRepository musicRepository;

    @BeforeEach
    void cleanDb() {
        musicRepository.deleteAll();
    }

    @Test
    void concurrentUpload_SameAudioContent_GuaranteesSingleEntityAndDuplicateResponses() throws Exception {
        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3WithPayload("concurrency-test-marker".getBytes());
        String sha256 = audioProcessingService.calculateSha256(new java.io.ByteArrayInputStream(mp3Bytes));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<ResponseApi<BulkMusicUploadResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                latch.await();
                MockMultipartFile file = new MockMultipartFile("files", "concurrent_" + index + ".mp3", "audio/mpeg", mp3Bytes);
                return bulkMusicUploadService.uploadBulk(List.of(file), null);
            }));
        }

        // Release all threads simultaneously
        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (Future<ResponseApi<BulkMusicUploadResponse>> future : futures) {
            ResponseApi<BulkMusicUploadResponse> response = future.get();
            assertTrue(response.getSuccess());
            BulkMusicUploadResponse data = response.getData();
            if (data.getSuccessCount() == 1) {
                successCount.incrementAndGet();
            }
            if (data.getDuplicateCount() == 1) {
                duplicateCount.incrementAndGet();
            }
        }

        // Exactly 1 upload succeeded, 9 identified as duplicates
        assertEquals(1, successCount.get(), "Exactly 1 thread should succeed in creating the music");
        assertEquals(9, duplicateCount.get(), "Remaining 9 threads should receive DUPLICATE status");

        // DB table musics contains exactly 1 entity with this hash
        long countInDb = musicRepository.findAll().stream()
                .filter(m -> sha256.equals(m.getAudioHash()))
                .count();
        assertEquals(1, countInDb, "Database must have exactly 1 record for this audio hash");
    }

    @Test
    void bulkUpload_50Files_PerformanceAndMemorySafety() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3WithPayload(("perf-track-" + i).getBytes());
            files.add(new MockMultipartFile("files", "perf_" + i + ".mp3", "audio/mpeg", mp3Bytes));
        }

        long startTime = System.currentTimeMillis();
        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(files, null);
        long elapsed = System.currentTimeMillis() - startTime;

        assertTrue(response.getSuccess());
        assertEquals(50, response.getData().getTotal());
        assertEquals(50, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getFailedCount());
        assertEquals(0, response.getData().getDuplicateCount());

        // Performance check: 50 small MP3 files should finish quickly via Virtual Threads
        System.out.println("Bulk upload of 50 audio files took " + elapsed + " ms");
        assertTrue(elapsed < 20000, "50 audio files processing should complete within 20 seconds");
    }
}
