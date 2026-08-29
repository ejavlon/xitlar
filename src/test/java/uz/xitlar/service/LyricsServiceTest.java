package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uz.xitlar.dto.LyricsCreateDto;
import uz.xitlar.dto.LyricsResponse;
import uz.xitlar.dto.LyricsUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.LyricsRepository;
import uz.xitlar.repository.MusicRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LyricsServiceTest {

    @Mock
    private LyricsRepository lyricsRepository;

    @Mock
    private MusicRepository musicRepository;

    @InjectMocks
    private LyricsService lyricsService;

    private Music sampleMusic;
    private Lyrics sampleLyrics;

    @BeforeEach
    void setUp() {
        sampleMusic = Music.builder().title("Sample Music").build();
        ReflectionTestUtils.setField(sampleMusic, "id", 1);

        sampleLyrics = Lyrics.builder()
                .text("Sample Text")
                .language("en")
                .isSynced(false)
                .music(sampleMusic)
                .build();
        ReflectionTestUtils.setField(sampleLyrics, "id", 1);
    }

    @Test
    void createLyrics_success() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", false, null);
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);
        when(lyricsRepository.save(any(Lyrics.class))).thenReturn(sampleLyrics);

        ResponseApi<LyricsResponse> response = lyricsService.createLyrics(dto);

        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals("Sample Text", response.getData().getText());
    }

    @Test
    void createLyrics_success_synchronized() {
        String validLrc = "[00:12.50]Valid lyrics\n[01:05.25]Another line";
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, validLrc);
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        Lyrics syncedLyrics = Lyrics.builder()
                .text("Text")
                .language("en")
                .isSynced(true)
                .lrcContent(validLrc)
                .music(sampleMusic)
                .build();
        ReflectionTestUtils.setField(syncedLyrics, "id", 2);
        when(lyricsRepository.save(any(Lyrics.class))).thenReturn(syncedLyrics);

        ResponseApi<LyricsResponse> response = lyricsService.createLyrics(dto);

        assertTrue(response.getSuccess());
        assertTrue(response.getData().getIsSynced());
        assertEquals(validLrc, response.getData().getLrcContent());
    }

    @Test
    void createLyrics_musicNotFound() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", false, null);
        when(musicRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_duplicateMusic() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", false, null);
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(true);

        assertThrows(DuplicateEntityException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_syncedWithoutLrc() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_invalidLrc_notTimestamp() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "not-a-timestamp");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_invalidLrc_bracketInvalid() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "[invalid]lyrics");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_success_synchronized_3digits() {
        String validLrc = "[01:05.250]Line with 3 digits\n[00:00.000]Zero timestamp";
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, validLrc);
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        Lyrics syncedLyrics = Lyrics.builder()
                .text("Text")
                .language("en")
                .isSynced(true)
                .lrcContent(validLrc)
                .music(sampleMusic)
                .build();
        ReflectionTestUtils.setField(syncedLyrics, "id", 3);
        when(lyricsRepository.save(any(Lyrics.class))).thenReturn(syncedLyrics);

        ResponseApi<LyricsResponse> response = lyricsService.createLyrics(dto);

        assertTrue(response.getSuccess());
        assertTrue(response.getData().getIsSynced());
        assertEquals(validLrc, response.getData().getLrcContent());
    }

    @Test
    void createLyrics_invalidLrc_noFractionalDigits() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "[01:05]lyrics");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_invalidLrc_oneFractionalDigit() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "[01:05.2]lyrics");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_invalidLrc_fourFractionalDigits() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "[01:05.1234]lyrics");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_invalidLrc_abcFormat() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", true, "[abc]lyrics");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void createLyrics_inconsistentSyncedState() {
        LyricsCreateDto dto = new LyricsCreateDto(1, "Text", "en", false, "[00:12.50]lyrics");
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(lyricsRepository.existsByMusicId(1)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> lyricsService.createLyrics(dto));
    }

    @Test
    void updateLyrics_success() {
        LyricsUpdateDto dto = new LyricsUpdateDto("New Text", "uz", false, null);
        when(lyricsRepository.findById(1)).thenReturn(Optional.of(sampleLyrics));
        when(lyricsRepository.save(any(Lyrics.class))).thenReturn(sampleLyrics);

        ResponseApi<LyricsResponse> response = lyricsService.updateLyrics(1, dto);

        assertTrue(response.getSuccess());
        assertEquals("New Text", sampleLyrics.getText());
        assertEquals("uz", sampleLyrics.getLanguage());
    }

    @Test
    void updateLyrics_notFound() {
        LyricsUpdateDto dto = new LyricsUpdateDto("New Text", "uz", false, null);
        when(lyricsRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> lyricsService.updateLyrics(1, dto));
    }

    @Test
    void updateLyrics_invalidLrc() {
        LyricsUpdateDto dto = new LyricsUpdateDto("New Text", "uz", true, "invalid lrc");
        when(lyricsRepository.findById(1)).thenReturn(Optional.of(sampleLyrics));

        assertThrows(IllegalArgumentException.class, () -> lyricsService.updateLyrics(1, dto));
    }

    @Test
    void updateLyrics_inconsistentSyncedState() {
        LyricsUpdateDto dto = new LyricsUpdateDto("New Text", "uz", false, "[00:12.50]some lrc");
        when(lyricsRepository.findById(1)).thenReturn(Optional.of(sampleLyrics));

        assertThrows(IllegalArgumentException.class, () -> lyricsService.updateLyrics(1, dto));
    }

    @Test
    void getLyricsById_success() {
        when(lyricsRepository.findById(1)).thenReturn(Optional.of(sampleLyrics));

        ResponseApi<LyricsResponse> response = lyricsService.getLyricsById(1);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getData().getId());
    }

    @Test
    void getLyricsById_notFound() {
        when(lyricsRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> lyricsService.getLyricsById(1));
    }

    @Test
    void getLyricsByMusicId_success() {
        when(lyricsRepository.findByMusicId(1)).thenReturn(Optional.of(sampleLyrics));

        ResponseApi<LyricsResponse> response = lyricsService.getLyricsByMusicId(1);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getData().getId());
    }

    @Test
    void getLyricsByMusicId_notFound() {
        when(lyricsRepository.findByMusicId(1)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> lyricsService.getLyricsByMusicId(1));
    }

    @Test
    void deleteLyrics_success() {
        when(lyricsRepository.findById(1)).thenReturn(Optional.of(sampleLyrics));

        ResponseApi<Void> response = lyricsService.deleteLyrics(1);

        assertTrue(response.getSuccess());
        verify(lyricsRepository, times(1)).delete(sampleLyrics);
    }

    @Test
    void deleteLyrics_notFound() {
        when(lyricsRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> lyricsService.deleteLyrics(1));
    }
}
