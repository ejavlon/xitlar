package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.*;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Image;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.AlbumRepository;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicService {

    private final MusicRepository musicRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final AudioProcessingService audioProcessingService;
    private final LyricsService lyricsService;

    @Transactional
    public ResponseApi<MusicResponse> createMusic(MusicCreateDto dto, MultipartFile file) {
        String trimmedTitle = dto.getTitle() != null ? dto.getTitle().trim() : "";
        if (trimmedTitle.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }

        if (dto.getLyrics() != null) {
            lyricsService.validateLrc(dto.getLyrics().getIsSynced(), dto.getLyrics().getLrcContent());
        }

        Artist artist = null;
        if (dto.getArtistId() != null) {
            artist = artistRepository.findById(dto.getArtistId())
                    .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + dto.getArtistId()));

            if (musicRepository.existsByTitleIgnoreCaseAndArtistId(trimmedTitle, artist.getId())) {
                throw new DuplicateEntityException("Music with this title already exists for this artist");
            }
        }

        Album album = null;
        if (dto.getAlbumId() != null) {
            album = albumRepository.findById(dto.getAlbumId())
                    .orElseThrow(() -> new DataNotFoundException("Album not found with ID: " + dto.getAlbumId()));
            if (artist == null && album.getArtist() != null) {
                artist = album.getArtist();
            }
        }

        AudioMetadata metadata = audioProcessingService.processAndSaveAudio(
                file,
                trimmedTitle,
                artist,
                album,
                dto.getGenre(),
                dto.getTrackNumber()
        );

        final String newStoredName = metadata.getStoredName();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        log.info("Transaction rolled back. Cleaning up newly created audio file: {}", newStoredName);
                        audioProcessingService.deletePhysicalFile(newStoredName);
                    }
                }
            });
        }

        try {
            Music music = Music.builder()
                    .title(trimmedTitle)
                    .storedName(metadata.getStoredName())
                    .originalFileName(metadata.getOriginalFileName())
                    .audioSize(metadata.getSize())
                    .audioContentType(metadata.getContentType())
                    .duration(metadata.getDuration())
                    .bitrate(metadata.getBitrate())
                    .sampleRate(metadata.getSampleRate())
                    .audioFormat(metadata.getFormat())
                    .artist(artist)
                    .album(album)
                    .genre(dto.getGenre())
                    .trackNumber(dto.getTrackNumber())
                    .build();

            Music saved = musicRepository.save(music);

            if (dto.getLyrics() != null) {
                Lyrics lyrics = lyricsService.createNestedLyrics(saved, dto.getLyrics());
                saved.addLyrics(lyrics);
            }

            return ResponseApi.<MusicResponse>builder()
                    .success(true)
                    .message("Music successfully created")
                    .data(toResponse(saved))
                    .build();
        } catch (Exception e) {
            audioProcessingService.deletePhysicalFile(newStoredName);
            throw e;
        }
    }

    @Transactional
    public ResponseApi<MusicResponse> updateMusic(Integer id, MusicUpdateDto dto, MultipartFile file) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + id));

        Artist artist = music.getArtist();
        if (dto.getArtistId() != null && (artist == null || !artist.getId().equals(dto.getArtistId()))) {
            artist = artistRepository.findById(dto.getArtistId())
                    .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + dto.getArtistId()));
            music.setArtist(artist);
        }

        Album album = music.getAlbum();
        if (dto.getAlbumId() != null && (album == null || !album.getId().equals(dto.getAlbumId()))) {
            album = albumRepository.findById(dto.getAlbumId())
                    .orElseThrow(() -> new DataNotFoundException("Album not found with ID: " + dto.getAlbumId()));
            music.setAlbum(album);
        }

        if (dto.getTitle() != null) {
            String trimmedTitle = dto.getTitle().trim();
            if (trimmedTitle.isEmpty()) {
                throw new IllegalArgumentException("Title must not be blank");
            }
            if (!music.getTitle().equalsIgnoreCase(trimmedTitle)) {
                if (artist != null && musicRepository.existsByTitleIgnoreCaseAndArtistId(trimmedTitle, artist.getId())) {
                    throw new DuplicateEntityException("Another music with this title already exists for this artist");
                }
                music.setTitle(trimmedTitle);
            }
        }

        if (dto.getGenre() != null) {
            music.setGenre(dto.getGenre());
        }

        if (dto.getTrackNumber() != null) {
            music.setTrackNumber(dto.getTrackNumber());
        }

        String newStoredNameLocal = null;
        if (file != null && !file.isEmpty()) {
            final String oldStoredName = music.getStoredName();

            AudioMetadata metadata = audioProcessingService.processAndSaveAudio(
                    file,
                    music.getTitle(),
                    music.getArtist(),
                    music.getAlbum(),
                    music.getGenre(),
                    music.getTrackNumber()
            );

            final String newStoredName = metadata.getStoredName();
            newStoredNameLocal = newStoredName;

            music.setStoredName(newStoredName);
            music.setOriginalFileName(metadata.getOriginalFileName());
            music.setAudioSize(metadata.getSize());
            music.setAudioContentType(metadata.getContentType());
            music.setDuration(metadata.getDuration());
            music.setBitrate(metadata.getBitrate());
            music.setSampleRate(metadata.getSampleRate());
            music.setAudioFormat(metadata.getFormat());

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("Transaction committed. Deleting old physical audio file: {}", oldStoredName);
                        audioProcessingService.deletePhysicalFile(oldStoredName);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            log.info("Transaction rolled back. Cleaning up newly uploaded audio file: {}", newStoredName);
                            audioProcessingService.deletePhysicalFile(newStoredName);
                        }
                    }
                });
            }
        }

        final String finalNewStoredName = newStoredNameLocal;
        try {
            Music saved = musicRepository.save(music);

            return ResponseApi.<MusicResponse>builder()
                    .success(true)
                    .message("Music successfully updated")
                    .data(toResponse(saved))
                    .build();
        } catch (Exception e) {
            if (finalNewStoredName != null) {
                audioProcessingService.deletePhysicalFile(finalNewStoredName);
            }
            throw e;
        }
    }

    public ResponseApi<MusicResponse> getMusicById(Integer id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + id));

        return ResponseApi.<MusicResponse>builder()
                .success(true)
                .message("Music found")
                .data(toResponse(music))
                .build();
    }

    public ResponseApi<Page<MusicResponse>> getAllMusics(Pageable pageable) {
        Page<MusicResponse> musics = musicRepository.findAll(pageable).map(this::toResponse);
        return ResponseApi.<Page<MusicResponse>>builder()
                .success(true)
                .message("Musics fetched")
                .data(musics)
                .build();
    }

    @Transactional
    public ResponseApi<Void> deleteMusic(Integer id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + id));

        final String storedName = music.getStoredName();
        musicRepository.delete(music);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Transaction committed. Deleting physical audio file: {}", storedName);
                    audioProcessingService.deletePhysicalFile(storedName);
                }
            });
        }

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Music successfully deleted")
                .build();
    }

    public MusicResponse toResponse(Music music) {
        ArtistResponse artistResponse = null;
        if (music.getArtist() != null) {
            Artist artist = music.getArtist();
            ImageResponse imgResponse = null;
            if (artist.getImage() != null) {
                Image img = artist.getImage();
                imgResponse = ImageResponse.builder()
                        .id(img.getId())
                        .originalName(img.getOriginalName())
                        .contentType(img.getContentType())
                        .size(img.getSize())
                        .url("/api/v1/images/" + img.getId())
                        .build();
            }

            artistResponse = ArtistResponse.builder()
                    .id(artist.getId())
                    .name(artist.getName())
                    .countOfTrack(artist.getCountOfTrack())
                    .genre(artist.getGenre())
                    .voteCount(artist.getVoteCount())
                    .averageRating(artist.getAverageRating())
                    .image(imgResponse)
                    .build();
        }

        AlbumResponse albumResponse = null;
        if (music.getAlbum() != null) {
            Album album = music.getAlbum();
            ImageResponse albumImgResponse = null;
            if (album.getImage() != null) {
                Image img = album.getImage();
                albumImgResponse = ImageResponse.builder()
                        .id(img.getId())
                        .originalName(img.getOriginalName())
                        .contentType(img.getContentType())
                        .size(img.getSize())
                        .url("/api/v1/images/" + img.getId())
                        .build();
            }

            albumResponse = AlbumResponse.builder()
                    .id(album.getId())
                    .title(album.getTitle())
                    .artistId(album.getArtist() != null ? album.getArtist().getId() : null)
                    .artistName(album.getArtist() != null ? album.getArtist().getName() : null)
                    .image(albumImgResponse)
                    .build();
        }

        LyricsResponse lyricsResponse = music.getLyrics() != null ? lyricsService.toResponse(music.getLyrics()) : null;

        return MusicResponse.builder()
                .id(music.getId())
                .title(music.getTitle())
                .audioUrl("/api/v1/musics/" + music.getId() + "/audio")
                .duration(music.getDuration())
                .bitrate(music.getBitrate())
                .sampleRate(music.getSampleRate())
                .originalFileName(music.getOriginalFileName())
                .audioSize(music.getAudioSize())
                .audioContentType(music.getAudioContentType())
                .artist(artistResponse)
                .album(albumResponse)
                .genre(music.getGenre())
                .trackNumber(music.getTrackNumber())
                .likeCount(music.getLikeCount())
                .dislikeCount(music.getDislikeCount())
                .audioFormat(music.getAudioFormat())
                .addedDate(music.getAddedDate())
                .lyrics(lyricsResponse)
                .build();
    }
}
