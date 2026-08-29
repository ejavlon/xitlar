package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.LyricsCreateDto;
import uz.xitlar.dto.LyricsCreateNestedDto;
import uz.xitlar.dto.LyricsResponse;
import uz.xitlar.dto.LyricsUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.LyricsRepository;
import uz.xitlar.repository.MusicRepository;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LyricsService {

    private final LyricsRepository lyricsRepository;
    private final MusicRepository musicRepository;

    private static final Pattern LRC_PATTERN = Pattern.compile("^\\[\\d{2}:\\d{2}\\.\\d{2,3}\\].*");

    @Transactional
    public ResponseApi<LyricsResponse> createLyrics(LyricsCreateDto dto) {
        Music music = musicRepository.findById(dto.getMusicId())
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + dto.getMusicId()));

        if (lyricsRepository.existsByMusicId(dto.getMusicId())) {
            throw new DuplicateEntityException("Lyrics already exists for this music");
        }

        validateLrc(dto.getIsSynced(), dto.getLrcContent());

        Lyrics lyrics = Lyrics.builder()
                .text(dto.getText())
                .language(dto.getLanguage())
                .isSynced(dto.getIsSynced())
                .lrcContent(dto.getIsSynced() ? dto.getLrcContent() : null)
                .music(music)
                .build();

        Lyrics saved = lyricsRepository.save(lyrics);
        return ResponseApi.<LyricsResponse>builder()
                .success(true)
                .message("Lyrics successfully created")
                .data(toResponse(saved))
                .build();
    }

    @Transactional
    public ResponseApi<LyricsResponse> updateLyrics(Integer id, LyricsUpdateDto dto) {
        Lyrics lyrics = lyricsRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Lyrics not found with ID: " + id));

        Boolean targetSynced = dto.getIsSynced() != null ? dto.getIsSynced() : lyrics.getIsSynced();
        String targetLrc = dto.getLrcContent() != null ? dto.getLrcContent() : (Boolean.TRUE.equals(targetSynced) ? lyrics.getLrcContent() : null);

        validateLrc(targetSynced, targetLrc);

        if (dto.getText() != null) {
            if (dto.getText().isBlank()) {
                throw new IllegalArgumentException("Text must not be blank");
            }
            lyrics.setText(dto.getText());
        }
        if (dto.getLanguage() != null) {
            lyrics.setLanguage(dto.getLanguage());
        }
        lyrics.setIsSynced(targetSynced);
        lyrics.setLrcContent(Boolean.TRUE.equals(targetSynced) ? targetLrc : null);

        Lyrics saved = lyricsRepository.save(lyrics);
        return ResponseApi.<LyricsResponse>builder()
                .success(true)
                .message("Lyrics successfully updated")
                .data(toResponse(saved))
                .build();
    }

    public ResponseApi<LyricsResponse> getLyricsById(Integer id) {
        Lyrics lyrics = lyricsRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Lyrics not found with ID: " + id));

        return ResponseApi.<LyricsResponse>builder()
                .success(true)
                .message("Lyrics found")
                .data(toResponse(lyrics))
                .build();
    }

    public ResponseApi<LyricsResponse> getLyricsByMusicId(Integer musicId) {
        Lyrics lyrics = lyricsRepository.findByMusicId(musicId)
                .orElseThrow(() -> new DataNotFoundException("Lyrics not found for Music ID: " + musicId));

        return ResponseApi.<LyricsResponse>builder()
                .success(true)
                .message("Lyrics found")
                .data(toResponse(lyrics))
                .build();
    }

    @Transactional
    public ResponseApi<Void> deleteLyrics(Integer id) {
        Lyrics lyrics = lyricsRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Lyrics not found with ID: " + id));

        lyrics.getMusic().removeLyrics();
        lyricsRepository.delete(lyrics);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Lyrics successfully deleted")
                .build();
    }

    @Transactional
    public Lyrics createNestedLyrics(Music music, LyricsCreateNestedDto dto) {
        validateLrc(dto.getIsSynced(), dto.getLrcContent());

        Lyrics lyrics = Lyrics.builder()
                .text(dto.getText())
                .language(dto.getLanguage())
                .isSynced(dto.getIsSynced())
                .lrcContent(dto.getIsSynced() ? dto.getLrcContent() : null)
                .music(music)
                .build();

        return lyricsRepository.save(lyrics);
    }

    public void validateLrc(Boolean isSynced, String lrcContent) {
        if (Boolean.TRUE.equals(isSynced)) {
            if (lrcContent == null || lrcContent.isBlank()) {
                throw new IllegalArgumentException("LRC content must be provided for synchronized lyrics");
            }
            String[] lines = lrcContent.split("\\r?\\n");
            boolean hasValidLine = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!LRC_PATTERN.matcher(trimmed).matches()) {
                    throw new IllegalArgumentException("Invalid LRC content format: " + trimmed);
                }
                hasValidLine = true;
            }
            if (!hasValidLine) {
                throw new IllegalArgumentException("LRC content must contain at least one valid timestamp line");
            }
        } else {
            if (lrcContent != null && !lrcContent.isBlank()) {
                throw new IllegalArgumentException("LRC content must be empty when isSynced is false");
            }
        }
    }

    public LyricsResponse toResponse(Lyrics lyrics) {
        if (lyrics == null) {
            return null;
        }
        return LyricsResponse.builder()
                .id(lyrics.getId())
                .text(lyrics.getText())
                .language(lyrics.getLanguage())
                .isSynced(lyrics.getIsSynced())
                .lrcContent(lyrics.getLrcContent())
                .musicId(lyrics.getMusic() != null ? lyrics.getMusic().getId() : null)
                .musicTitle(lyrics.getMusic() != null ? lyrics.getMusic().getTitle() : null)
                .build();
    }
}
