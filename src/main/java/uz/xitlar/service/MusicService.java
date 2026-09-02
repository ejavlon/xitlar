package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.album.AlbumResponse;
import uz.xitlar.dto.artist.ArtistResponse;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.image.ImageResponse;
import uz.xitlar.dto.lyrics.LyricsCreateNestedDto;
import uz.xitlar.dto.lyrics.LyricsResponse;
import uz.xitlar.dto.music.AudioMetadata;
import uz.xitlar.dto.music.MusicCreateDto;
import uz.xitlar.dto.music.MusicResponse;
import uz.xitlar.dto.music.MusicUpdateDto;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Image;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.entity.User;
import uz.xitlar.repository.AlbumRepository;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.stream.Collectors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

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
    private final UserRepository userRepository;

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
                    .audioHash(metadata.getAudioHash())
                    .artist(artist)
                    .album(album)
                    .genre(dto.getGenre())
                    .trackNumber(dto.getTrackNumber())
                    .build();

            Music saved = musicRepository.save(music);

            // Update artist track count
            if (artist != null) {
                artist.setCountOfTrack(artist.getCountOfTrack() + 1);
                artistRepository.save(artist);
            }

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

        Artist oldArtist = music.getArtist();
        Artist artist = oldArtist;
        if (dto.getArtistId() != null && (artist == null || !artist.getId().equals(dto.getArtistId()))) {
            artist = artistRepository.findById(dto.getArtistId())
                    .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + dto.getArtistId()));
            music.setArtist(artist);

            // Update track counts when artist changes
            if (oldArtist != null) {
                oldArtist.setCountOfTrack(Math.max(0, oldArtist.getCountOfTrack() - 1));
                artistRepository.save(oldArtist);
            }
            artist.setCountOfTrack(artist.getCountOfTrack() + 1);
            artistRepository.save(artist);
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
            music.setAudioHash(metadata.getAudioHash());

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

        // Update artist track count
        if (music.getArtist() != null) {
            Artist artist = music.getArtist();
            artist.setCountOfTrack(Math.max(0, artist.getCountOfTrack() - 1));
            artistRepository.save(artist);
        }

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
        Boolean isLiked = false;
        Boolean isDisliked = false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            Object principalObj = authentication.getPrincipal();
            String username = null;
            if (principalObj instanceof UserDetails userDetails) {
                username = userDetails.getUsername();
            } else if (principalObj instanceof User u) {
                username = u.getUsername();
            }
            if (username != null) {
                java.util.Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    isLiked = user.getLikedMusics().contains(music);
                    isDisliked = user.getDislikedMusics().contains(music);
                }
            }
        }

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
                .isLiked(isLiked)
                .isDisliked(isDisliked)
                .audioFormat(music.getAudioFormat())
                .addedDate(music.getAddedDate())
                .lyrics(lyricsResponse)
                .build();
    }

    public ResponseEntity<Resource> streamAudio(Integer id, HttpHeaders headers) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + id));

        Path path = audioProcessingService.getAudioPath(music.getStoredName());
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new DataNotFoundException("Audio file not found or not readable");
            }
        } catch (DataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DataNotFoundException("Audio file not found");
        }

        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MediaType mediaType = MediaType.parseMediaType(
                music.getAudioContentType() != null ? music.getAudioContentType() : "audio/mpeg"
        );

        List<HttpRange> httpRanges;
        try {
            httpRanges = headers.getRange();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        // 1. No Range requested -> 200 OK with the COMPLETE audio file
        if (httpRanges == null || httpRanges.isEmpty()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(contentLength)
                    .contentType(mediaType)
                    .body(resource);
        }

        // 2. Range requested
        HttpRange range = httpRanges.get(0);
        long start;
        long end;
        try {
            start = range.getRangeStart(contentLength);
            end = range.getRangeEnd(contentLength);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        // Validate range bounds
        if (start >= contentLength || end >= contentLength || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        long rangeLength = end - start + 1;

        try (InputStream is = resource.getInputStream()) {
            long skipped = 0;
            while (skipped < start) {
                long s = is.skip(start - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            byte[] buffer = is.readNBytes((int) rangeLength);
            ByteArrayResource partialResource = new ByteArrayResource(buffer);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, contentLength))
                    .contentLength(rangeLength)
                    .contentType(mediaType)
                    .body(partialResource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<Resource> downloadAudio(Integer id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + id));

        Path path = audioProcessingService.getAudioPath(music.getStoredName());
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new DataNotFoundException("Audio file not found or not readable");
            }
        } catch (DataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DataNotFoundException("Audio file not found");
        }

        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        MediaType mediaType = MediaType.parseMediaType(
                music.getAudioContentType() != null ? music.getAudioContentType() : "audio/mpeg"
        );

        String filename = music.getOriginalFileName();
        if (filename == null || filename.isBlank()) {
            filename = music.getTitle() + "." + (music.getAudioFormat() != null ? music.getAudioFormat().name().toLowerCase() : "mp3");
        }

        String contentDispositionValue = "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + UriUtils.encode(filename, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionValue)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(contentLength)
                .contentType(mediaType)
                .body(resource);
    }

    public ResponseApi<List<MusicResponse>> getLikedMusics(UserDetails principal) {
        if (principal == null) {
            return ResponseApi.<List<MusicResponse>>builder()
                    .success(true)
                    .message("Success")
                    .data(List.of())
                    .build();
        }
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new DataNotFoundException("User not found: " + principal.getUsername()));

        List<MusicResponse> likedList = user.getLikedMusics().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseApi.<List<MusicResponse>>builder()
                .success(true)
                .message("Success")
                .data(likedList)
                .build();
    }

    @Transactional
    public ResponseApi<MusicResponse> toggleLike(Integer musicId, UserDetails principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated");
        }
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new DataNotFoundException("User not found: " + principal.getUsername()));
        
        Music music = musicRepository.findById(musicId)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + musicId));

        if (user.getLikedMusics().contains(music)) {
            user.getLikedMusics().remove(music);
            music.setLikeCount(Math.max(0, music.getLikeCount() - 1));
        } else {
            user.getLikedMusics().add(music);
            music.setLikeCount(music.getLikeCount() + 1);
            if (user.getDislikedMusics().contains(music)) {
                user.getDislikedMusics().remove(music);
                music.setDislikeCount(Math.max(0, music.getDislikeCount() - 1));
            }
        }

        userRepository.save(user);
        Music savedMusic = musicRepository.save(music);

        return ResponseApi.<MusicResponse>builder()
                .success(true)
                .message("Like status updated successfully")
                .data(toResponse(savedMusic))
                .build();
    }

    @Transactional
    public ResponseApi<MusicResponse> toggleDislike(Integer musicId, UserDetails principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated");
        }
        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new DataNotFoundException("User not found: " + principal.getUsername()));
        
        Music music = musicRepository.findById(musicId)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + musicId));

        if (user.getDislikedMusics().contains(music)) {
            user.getDislikedMusics().remove(music);
            music.setDislikeCount(Math.max(0, music.getDislikeCount() - 1));
        } else {
            user.getDislikedMusics().add(music);
            music.setDislikeCount(music.getDislikeCount() + 1);
            if (user.getLikedMusics().contains(music)) {
                user.getLikedMusics().remove(music);
                music.setLikeCount(Math.max(0, music.getLikeCount() - 1));
            }
        }

        userRepository.save(user);
        Music savedMusic = musicRepository.save(music);

        return ResponseApi.<MusicResponse>builder()
                .success(true)
                .message("Dislike status updated successfully")
                .data(toResponse(savedMusic))
                .build();
    }
}
