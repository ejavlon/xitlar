package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.lyrics.LyricsCreateNestedDto;
import uz.xitlar.dto.music.*;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import uz.xitlar.enums.Genre;
import uz.xitlar.enums.UploadStatus;
import uz.xitlar.repository.AlbumRepository;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.util.AudioTestHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BulkMusicUploadServiceTest {

    @Mock
    private MusicRepository musicRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private LyricsService lyricsService;

    private AudioProcessingService audioProcessingService;
    private BulkMusicUploadProcessor uploadProcessor;
    private BulkMusicUploadService bulkMusicUploadService;

    private Artist artist;
    private Album album;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        audioProcessingService = new AudioProcessingService(tempDir.toString());
        audioProcessingService.init();

        org.springframework.transaction.PlatformTransactionManager transactionManager =
                new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                    @Override
                    protected Object doGetTransaction() { return new Object(); }
                    @Override
                    protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
                    @Override
                    protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                    @Override
                    protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                };

        uploadProcessor = new BulkMusicUploadProcessor(
                musicRepository,
                artistRepository,
                albumRepository,
                audioProcessingService,
                lyricsService,
                transactionManager
        );

        bulkMusicUploadService = new BulkMusicUploadService(
                uploadProcessor,
                artistRepository,
                albumRepository
        );
        ReflectionTestUtils.setField(bulkMusicUploadService, "maxFiles", 50);
        ReflectionTestUtils.setField(bulkMusicUploadService, "cpuConcurrency", 4);

        artist = new Artist();
        ReflectionTestUtils.setField(artist, "id", 1);
        artist.setName("Test Artist");

        album = new Album();
        ReflectionTestUtils.setField(album, "id", 1);
        album.setTitle("Test Album");
        album.setArtist(artist);
    }

    private MockMultipartFile createMp3File(String filename, String contentMarker) {
        byte[] bytes = AudioTestHelper.createMinimalValidMp3WithPayload(contentMarker.getBytes());
        return new MockMultipartFile("files", filename, "audio/mpeg", bytes);
    }

    @Test
    void uploadBulk_SingleFile_Success() {
        MockMultipartFile file = createMp3File("track1.mp3", "track1-payload");
        List<MultipartFile> files = List.of(file);

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 101);
            return m;
        });

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(files, null);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getData().getTotal());
        assertEquals(1, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getDuplicateCount());
        assertEquals(0, response.getData().getFailedCount());
        assertEquals(UploadStatus.SUCCESS, response.getData().getResults().get(0).getStatus());
        assertEquals(101, response.getData().getResults().get(0).getMusicId());
    }

    @Test
    void uploadBulk_10Files_Success() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            files.add(createMp3File("song_" + i + ".mp3", "payload_" + i));
        }

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", new Random().nextInt(1000) + 1);
            return m;
        });

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(files, null);

        assertTrue(response.getSuccess());
        assertEquals(10, response.getData().getTotal());
        assertEquals(10, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getDuplicateCount());
        assertEquals(0, response.getData().getFailedCount());
    }

    @Test
    void uploadBulk_50Files_Success() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            files.add(createMp3File("track_" + i + ".mp3", "distinct_payload_" + i));
        }

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", new Random().nextInt(10000) + 1);
            return m;
        });

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(files, null);

        assertTrue(response.getSuccess());
        assertEquals(50, response.getData().getTotal());
        assertEquals(50, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getDuplicateCount());
        assertEquals(0, response.getData().getFailedCount());
    }

    @Test
    void uploadBulk_51Files_ThrowsIllegalArgumentException() {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            files.add(createMp3File("track_" + i + ".mp3", "payload_" + i));
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                bulkMusicUploadService.uploadBulk(files, null)
        );
        assertTrue(ex.getMessage().contains("50"));
    }

    @Test
    void uploadBulk_EmptyFileList_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                bulkMusicUploadService.uploadBulk(Collections.emptyList(), null)
        );
    }

    @Test
    void uploadBulk_EmptyFile_ReturnsFailed() {
        MockMultipartFile emptyFile = new MockMultipartFile("files", "empty.mp3", "audio/mpeg", new byte[0]);
        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(emptyFile), null);

        assertEquals(1, response.getData().getFailedCount());
        assertEquals(UploadStatus.FAILED, response.getData().getResults().get(0).getStatus());
        assertTrue(response.getData().getResults().get(0).getError().contains("empty"));
    }

    @Test
    void uploadBulk_InvalidExtension_ReturnsFailed() {
        MockMultipartFile file = new MockMultipartFile("files", "test.wav", "audio/mpeg", AudioTestHelper.createMinimalValidMp3());
        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), null);

        assertEquals(1, response.getData().getFailedCount());
        assertEquals(UploadStatus.FAILED, response.getData().getResults().get(0).getStatus());
        assertTrue(response.getData().getResults().get(0).getError().contains("extension"));
    }

    @Test
    void uploadBulk_InvalidMimeType_ReturnsFailed() {
        MockMultipartFile file = new MockMultipartFile("files", "test.mp3", "application/pdf", AudioTestHelper.createMinimalValidMp3());
        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), null);

        assertEquals(1, response.getData().getFailedCount());
        assertEquals(UploadStatus.FAILED, response.getData().getResults().get(0).getStatus());
        assertTrue(response.getData().getResults().get(0).getError().contains("content type"));
    }

    @Test
    void uploadBulk_InvalidMagicBytes_ReturnsFailed() {
        byte[] badBytes = "NOT_AN_AUDIO_FILE_JUST_TEXT".getBytes();
        MockMultipartFile file = new MockMultipartFile("files", "fake.mp3", "audio/mpeg", badBytes);
        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), null);

        assertEquals(1, response.getData().getFailedCount());
        assertEquals(UploadStatus.FAILED, response.getData().getResults().get(0).getStatus());
        assertTrue(response.getData().getResults().get(0).getError().contains("format"));
    }

    @Test
    void uploadBulk_DuplicateSha256_ReturnsDuplicate() throws Exception {
        MockMultipartFile file = createMp3File("duplicate.mp3", "identical_audio");
        String hash = audioProcessingService.calculateSha256(new java.io.ByteArrayInputStream(file.getBytes()));

        Music existingMusic = new Music();
        ReflectionTestUtils.setField(existingMusic, "id", 77);
        existingMusic.setTitle("Already Existing Track");
        existingMusic.setAudioHash(hash);

        when(musicRepository.findFirstByAudioHash(hash)).thenReturn(Optional.of(existingMusic));

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), null);

        assertEquals(1, response.getData().getDuplicateCount());
        assertEquals(0, response.getData().getSuccessCount());
        BulkMusicUploadItemResponse item = response.getData().getResults().get(0);
        assertEquals(UploadStatus.DUPLICATE, item.getStatus());
        assertEquals(77, item.getMusicId());
    }

    @Test
    void uploadBulk_SameFilenameDifferentContent_NotDuplicate() {
        MockMultipartFile file1 = createMp3File("song.mp3", "content-A");
        MockMultipartFile file2 = createMp3File("song.mp3", "content-B");

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", new Random().nextInt(1000) + 1);
            return m;
        });

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file1, file2), null);

        assertEquals(2, response.getData().getTotal());
        assertEquals(2, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getDuplicateCount());
    }

    @Test
    void uploadBulk_TitleAndArtistDuplicate_ReturnsDuplicate() {
        MockMultipartFile file = createMp3File("song.mp3", "unique-content");
        BulkMusicItemDto meta = BulkMusicItemDto.builder()
                .fileName("song.mp3")
                .title("Duplicate Title")
                .artistId(1)
                .build();

        when(artistRepository.findAllById(any())).thenReturn(List.of(artist));
        when(musicRepository.existsByTitleIgnoreCaseAndArtistId("Duplicate Title", 1)).thenReturn(true);

        Music existing = new Music();
        ReflectionTestUtils.setField(existing, "id", 88);
        when(musicRepository.findByTitleIgnoreCaseAndArtistId("Duplicate Title", 1)).thenReturn(Optional.of(existing));

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), List.of(meta));

        assertEquals(1, response.getData().getDuplicateCount());
        assertEquals(88, response.getData().getResults().get(0).getMusicId());
    }

    @Test
    void uploadBulk_PartialSuccess_MixedResults() throws Exception {
        MockMultipartFile validFile = createMp3File("valid.mp3", "valid-1");
        MockMultipartFile invalidFile = new MockMultipartFile("files", "corrupt.mp3", "audio/mpeg", new byte[0]);
        MockMultipartFile dupFile = createMp3File("dup.mp3", "dup-content");

        String dupHash = audioProcessingService.calculateSha256(new java.io.ByteArrayInputStream(dupFile.getBytes()));
        Music existingDup = new Music();
        ReflectionTestUtils.setField(existingDup, "id", 99);
        when(musicRepository.findFirstByAudioHash(dupHash)).thenReturn(Optional.of(existingDup));

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 10);
            return m;
        });

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(
                List.of(validFile, invalidFile, dupFile), null
        );

        BulkMusicUploadResponse data = response.getData();
        assertEquals(3, data.getTotal());
        assertEquals(1, data.getSuccessCount());
        assertEquals(1, data.getDuplicateCount());
        assertEquals(1, data.getFailedCount());
    }

    @Test
    void uploadBulk_DatabaseError_CleansUpPhysicalFile() {
        MockMultipartFile file = createMp3File("track.mp3", "payload");
        when(musicRepository.saveAndFlush(any(Music.class))).thenThrow(new RuntimeException("DB down"));

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), null);

        assertEquals(1, response.getData().getFailedCount());
        assertEquals(UploadStatus.FAILED, response.getData().getResults().get(0).getStatus());

        // Verify audio storage directory has no orphan permanent files
        try {
            long fileCount = Files.list(audioProcessingService.getAudioStorageDir()).count();
            assertEquals(0, fileCount, "No orphan files should remain in audio storage after DB error");
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void uploadBulk_ConcurrentRaceCondition_ReturnsDuplicate() {
        MockMultipartFile file = createMp3File("race.mp3", "race-payload");

        when(musicRepository.saveAndFlush(any(Music.class))).thenThrow(
                new DataIntegrityViolationException("Unique constraint violation: uk_musics_audio_hash")
        );

        Music raceWinner = new Music();
        ReflectionTestUtils.setField(raceWinner, "id", 200);
        raceWinner.setTitle("Race Winner");
        when(musicRepository.findFirstByAudioHash(any())).thenReturn(Optional.of(raceWinner));

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), null);

        assertEquals(1, response.getData().getDuplicateCount());
        assertEquals(200, response.getData().getResults().get(0).getMusicId());
    }

    @Test
    void uploadBulk_WithArtistAndAlbumAndLyrics_Success() {
        MockMultipartFile file = createMp3File("song.mp3", "song-lyrics-payload");
        BulkMusicItemDto meta = BulkMusicItemDto.builder()
                .fileName("song.mp3")
                .title("My Song")
                .artistId(1)
                .albumId(1)
                .genre(Genre.ROCK)
                .trackNumber(3)
                .lyrics(LyricsCreateNestedDto.builder().text("Lyrics text").isSynced(false).build())
                .build();

        when(artistRepository.findAllById(any())).thenReturn(List.of(artist));
        when(albumRepository.findAllById(any())).thenReturn(List.of(album));

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 333);
            return m;
        });

        Lyrics lyrics = Lyrics.builder().text("Lyrics text").build();
        when(lyricsService.createNestedLyrics(any(Music.class), any())).thenReturn(lyrics);

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), List.of(meta));

        assertEquals(1, response.getData().getSuccessCount());
        assertEquals(333, response.getData().getResults().get(0).getMusicId());
        verify(lyricsService, times(1)).validateLrc(false, null);
        verify(lyricsService, times(1)).createNestedLyrics(any(Music.class), any());
    }

    @Test
    void uploadBulk_ArtistNotFound_ReturnsFailed() {
        MockMultipartFile file = createMp3File("song.mp3", "song-payload");
        BulkMusicItemDto meta = BulkMusicItemDto.builder()
                .fileName("song.mp3")
                .artistId(999)
                .build();

        when(artistRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(artistRepository.findById(999)).thenReturn(Optional.empty());

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), List.of(meta));

        assertEquals(1, response.getData().getFailedCount());
        assertTrue(response.getData().getResults().get(0).getError().contains("Artist not found"));
    }

    @Test
    void uploadBulk_AlbumNotFound_ReturnsFailed() {
        MockMultipartFile file = createMp3File("song.mp3", "song-payload");
        BulkMusicItemDto meta = BulkMusicItemDto.builder()
                .fileName("song.mp3")
                .albumId(999)
                .build();

        when(albumRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(albumRepository.findById(999)).thenReturn(Optional.empty());

        ResponseApi<BulkMusicUploadResponse> response = bulkMusicUploadService.uploadBulk(List.of(file), List.of(meta));

        assertEquals(1, response.getData().getFailedCount());
        assertTrue(response.getData().getResults().get(0).getError().contains("Album not found"));
    }

    @Test
    void uploadBulk_TransactionRollback_CleansUpPhysicalFile() {
        MockMultipartFile file = createMp3File("track.mp3", "rollback-payload");

        when(musicRepository.saveAndFlush(any(Music.class))).thenAnswer(invocation -> {
            Music m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 100);

            // Trigger rollback synchronization
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                }
            }
            return m;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            uploadProcessor.processOne(file, null, new HashMap<>(), new HashMap<>(), new Semaphore(4));

            // Audio directory should be clean
            long fileCount = Files.list(audioProcessingService.getAudioStorageDir()).count();
            assertEquals(0, fileCount, "Physical file should be cleaned up upon rollback");
        } catch (Exception e) {
            fail(e);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
