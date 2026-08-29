package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import uz.xitlar.dto.*;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import uz.xitlar.enums.AudioFormat;
import uz.xitlar.enums.Genre;
import uz.xitlar.repository.AlbumRepository;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MusicServiceTest {

    @Mock
    private MusicRepository musicRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private AudioProcessingService audioProcessingService;

    @Mock
    private LyricsService lyricsService;

    @InjectMocks
    private MusicService musicService;

    private Artist artist;
    private Album album;
    private AudioMetadata metadata;

    @BeforeEach
    void setUp() {
        artist = new Artist();
        ReflectionTestUtils.setField(artist, "id", 1);
        artist.setName("Test Artist");

        album = new Album();
        ReflectionTestUtils.setField(album, "id", 1);
        album.setTitle("Test Album");
        album.setArtist(artist);

        metadata = AudioMetadata.builder()
                .storedName("test-uuid.mp3")
                .originalFileName("original.mp3")
                .size(1024L)
                .contentType("audio/mpeg")
                .duration(120)
                .bitrate(320)
                .sampleRate(44100)
                .format(AudioFormat.MP3)
                .build();
    }

    @Test
    void createMusic_Success() {
        MusicCreateDto dto = MusicCreateDto.builder()
                .title("Test Title")
                .artistId(1)
                .albumId(1)
                .genre(Genre.POP)
                .trackNumber(1)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test".getBytes());

        when(artistRepository.findById(1)).thenReturn(Optional.of(artist));
        when(albumRepository.findById(1)).thenReturn(Optional.of(album));
        when(musicRepository.existsByTitleIgnoreCaseAndArtistId("Test Title", 1)).thenReturn(false);
        when(audioProcessingService.processAndSaveAudio(file, "Test Title", artist, album, Genre.POP, 1)).thenReturn(metadata);

        Music savedMusic = new Music();
        ReflectionTestUtils.setField(savedMusic, "id", 10);
        savedMusic.setTitle("Test Title");
        savedMusic.setArtist(artist);
        savedMusic.setAlbum(album);
        when(musicRepository.save(any(Music.class))).thenReturn(savedMusic);

        ResponseApi<MusicResponse> response = musicService.createMusic(dto, file);

        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals("Test Title", response.getData().getTitle());
        verify(audioProcessingService, times(1)).processAndSaveAudio(file, "Test Title", artist, album, Genre.POP, 1);
        verify(musicRepository, times(1)).save(any(Music.class));
    }

    @Test
    void createMusic_withLyrics_Success() {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Test Lyrics Text")
                .language("uz")
                .isSynced(false)
                .build();

        MusicCreateDto dto = MusicCreateDto.builder()
                .title("Test Title")
                .artistId(1)
                .albumId(1)
                .genre(Genre.POP)
                .trackNumber(1)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test".getBytes());

        when(artistRepository.findById(1)).thenReturn(Optional.of(artist));
        when(albumRepository.findById(1)).thenReturn(Optional.of(album));
        when(musicRepository.existsByTitleIgnoreCaseAndArtistId("Test Title", 1)).thenReturn(false);
        when(audioProcessingService.processAndSaveAudio(file, "Test Title", artist, album, Genre.POP, 1)).thenReturn(metadata);

        Music savedMusic = new Music();
        ReflectionTestUtils.setField(savedMusic, "id", 10);
        savedMusic.setTitle("Test Title");
        savedMusic.setArtist(artist);
        savedMusic.setAlbum(album);
        when(musicRepository.save(any(Music.class))).thenReturn(savedMusic);

        Lyrics savedLyrics = Lyrics.builder()
                .text("Test Lyrics Text")
                .language("uz")
                .isSynced(false)
                .music(savedMusic)
                .build();
        ReflectionTestUtils.setField(savedLyrics, "id", 100);

        when(lyricsService.createNestedLyrics(eq(savedMusic), eq(nestedDto))).thenReturn(savedLyrics);
        when(lyricsService.toResponse(any(Lyrics.class))).thenReturn(LyricsResponse.builder()
                .id(100)
                .text("Test Lyrics Text")
                .language("uz")
                .isSynced(false)
                .musicId(10)
                .build());

        ResponseApi<MusicResponse> response = musicService.createMusic(dto, file);

        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertNotNull(response.getData().getLyrics());
        assertEquals(100, response.getData().getLyrics().getId());
        verify(lyricsService, times(1)).validateLrc(false, null);
        verify(lyricsService, times(1)).createNestedLyrics(savedMusic, nestedDto);
    }

    @Test
    void createMusic_invalidLyricsLrc_FailsPreValidationBeforeSave() {
        LyricsCreateNestedDto nestedDto = LyricsCreateNestedDto.builder()
                .text("Test Lyrics Text")
                .language("uz")
                .isSynced(true)
                .lrcContent("invalid lrc")
                .build();

        MusicCreateDto dto = MusicCreateDto.builder()
                .title("Test Title")
                .artistId(1)
                .lyrics(nestedDto)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test".getBytes());

        doThrow(new IllegalArgumentException("Invalid LRC content format: invalid lrc"))
                .when(lyricsService).validateLrc(true, "invalid lrc");

        assertThrows(IllegalArgumentException.class, () -> musicService.createMusic(dto, file));

        verify(audioProcessingService, never()).processAndSaveAudio(any(), any(), any(), any(), any(), any());
        verify(musicRepository, never()).save(any());
    }

    @Test
    void createMusic_PersistenceFailure_DeletesPhysicalFile() {
        MusicCreateDto dto = MusicCreateDto.builder()
                .title("Test Title")
                .artistId(1)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "test".getBytes());

        when(artistRepository.findById(1)).thenReturn(Optional.of(artist));
        when(musicRepository.existsByTitleIgnoreCaseAndArtistId("Test Title", 1)).thenReturn(false);
        when(audioProcessingService.processAndSaveAudio(file, "Test Title", artist, null, null, null)).thenReturn(metadata);
        when(musicRepository.save(any(Music.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> musicService.createMusic(dto, file));

        // Verifies the physical file was immediately deleted on persistence failure
        verify(audioProcessingService, times(1)).deletePhysicalFile("test-uuid.mp3");
    }

    @Test
    void updateMusic_WithAudioReplacement_Success() {
        Music music = new Music();
        ReflectionTestUtils.setField(music, "id", 10);
        music.setTitle("Old Title");
        music.setStoredName("old-uuid.mp3");
        music.setArtist(artist);

        when(musicRepository.findById(10)).thenReturn(Optional.of(music));

        AudioMetadata newMetadata = AudioMetadata.builder()
                .storedName("new-uuid.mp3")
                .originalFileName("new.mp3")
                .size(2048L)
                .contentType("audio/mpeg")
                .duration(150)
                .bitrate(320)
                .sampleRate(44100)
                .format(AudioFormat.MP3)
                .build();

        MockMultipartFile newFile = new MockMultipartFile("file", "new.mp3", "audio/mpeg", "new-content".getBytes());
        when(audioProcessingService.processAndSaveAudio(newFile, "Old Title", artist, null, null, null)).thenReturn(newMetadata);
        when(musicRepository.save(any(Music.class))).thenReturn(music);

        MusicUpdateDto updateDto = new MusicUpdateDto();

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ResponseApi<MusicResponse> response = musicService.updateMusic(10, updateDto, newFile);

            assertTrue(response.getSuccess());
            assertEquals("new-uuid.mp3", music.getStoredName());
            verify(musicRepository, times(1)).save(music);

            // Prior to commit: old file is NOT deleted
            verify(audioProcessingService, never()).deletePhysicalFile("old-uuid.mp3");

            // Execute afterCommit
            for (org.springframework.transaction.support.TransactionSynchronization sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            // After commit: old file IS deleted
            verify(audioProcessingService, times(1)).deletePhysicalFile("old-uuid.mp3");
            // New file is preserved
            verify(audioProcessingService, never()).deletePhysicalFile("new-uuid.mp3");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteMusic_Success() {
        Music music = new Music();
        ReflectionTestUtils.setField(music, "id", 10);
        music.setTitle("To Delete");
        music.setStoredName("delete-uuid.mp3");

        when(musicRepository.findById(10)).thenReturn(Optional.of(music));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ResponseApi<Void> response = musicService.deleteMusic(10);

            assertTrue(response.getSuccess());
            verify(musicRepository, times(1)).delete(music);

            // Prior to commit: file is NOT deleted
            verify(audioProcessingService, never()).deletePhysicalFile("delete-uuid.mp3");

            // Execute afterCommit
            for (org.springframework.transaction.support.TransactionSynchronization sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            // After commit: file IS deleted
            verify(audioProcessingService, times(1)).deletePhysicalFile("delete-uuid.mp3");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteMusic_Rollback_PreservesPhysicalFile() {
        Music music = new Music();
        ReflectionTestUtils.setField(music, "id", 10);
        music.setTitle("To Delete");
        music.setStoredName("delete-uuid.mp3");

        when(musicRepository.findById(10)).thenReturn(Optional.of(music));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ResponseApi<Void> response = musicService.deleteMusic(10);

            assertTrue(response.getSuccess());
            verify(musicRepository, times(1)).delete(music);

            // Execute afterCompletion with rollback status
            for (org.springframework.transaction.support.TransactionSynchronization sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            // On rollback: physical file MUST remain untouched
            verify(audioProcessingService, never()).deletePhysicalFile("delete-uuid.mp3");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateMusic_PersistenceFailure_CleansUpNewFileAndKeepsOldFile() {
        Music music = new Music();
        ReflectionTestUtils.setField(music, "id", 10);
        music.setTitle("Old Title");
        music.setStoredName("old-uuid.mp3");
        music.setArtist(artist);

        when(musicRepository.findById(10)).thenReturn(Optional.of(music));

        AudioMetadata newMetadata = AudioMetadata.builder()
                .storedName("failed-new-uuid.mp3")
                .originalFileName("new.mp3")
                .size(2048L)
                .contentType("audio/mpeg")
                .duration(150)
                .bitrate(320)
                .sampleRate(44100)
                .format(AudioFormat.MP3)
                .build();

        MockMultipartFile newFile = new MockMultipartFile("file", "new.mp3", "audio/mpeg", "new-content".getBytes());
        when(audioProcessingService.processAndSaveAudio(newFile, "Old Title", artist, null, null, null)).thenReturn(newMetadata);
        when(musicRepository.save(any(Music.class))).thenThrow(new RuntimeException("Database error during update"));

        MusicUpdateDto updateDto = new MusicUpdateDto();
        assertThrows(RuntimeException.class, () -> musicService.updateMusic(10, updateDto, newFile));

        // New file is deleted immediately on failure
        verify(audioProcessingService, times(1)).deletePhysicalFile("failed-new-uuid.mp3");
        // Old file is never deleted
        verify(audioProcessingService, never()).deletePhysicalFile("old-uuid.mp3");
    }
}
