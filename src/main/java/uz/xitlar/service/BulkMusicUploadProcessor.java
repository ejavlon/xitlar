package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.music.AudioMetadata;
import uz.xitlar.dto.music.BulkMusicItemDto;
import uz.xitlar.dto.music.BulkMusicUploadItemResponse;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Lyrics;
import uz.xitlar.entity.Music;
import uz.xitlar.enums.AudioFormat;
import uz.xitlar.enums.Genre;
import uz.xitlar.enums.UploadStatus;
import uz.xitlar.repository.AlbumRepository;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.MusicRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkMusicUploadProcessor {

    private final MusicRepository musicRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final AudioProcessingService audioProcessingService;
    private final LyricsService lyricsService;
    private final PlatformTransactionManager transactionManager;

    private static final long MAX_AUDIO_FILE_SIZE = 50 * 1024 * 1024L; // 50MB

    public BulkMusicUploadItemResponse processOne(
            MultipartFile file,
            BulkMusicItemDto itemDto,
            Map<Integer, Artist> artistCache,
            Map<Integer, Album> albumCache,
            Semaphore cpuSemaphore
    ) {
        String originalFilename = file != null ? file.getOriginalFilename() : "unknown.mp3";
        String sanitizedFilename = originalFilename != null ? Paths.get(originalFilename).getFileName().toString() : "unknown.mp3";

        // 1. Initial basic validation
        if (file == null || file.isEmpty()) {
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Failed to store empty file")
                    .build();
        }

        if (file.getSize() > MAX_AUDIO_FILE_SIZE) {
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Audio file exceeds maximum allowed size of 50MB")
                    .build();
        }

        if (!sanitizedFilename.toLowerCase().endsWith(".mp3")) {
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Invalid audio file extension. Only .mp3 files are supported.")
                    .build();
        }

        String contentType = file.getContentType();
        if (contentType != null && !isAllowedContentType(contentType)) {
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Incompatible audio content type: " + contentType + ". Expected audio/mpeg.")
                    .build();
        }

        // 2. Magic bytes validation
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[10];
            int read = is.read(header);
            if (read < 4) {
                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.FAILED)
                        .error("File is too small to be a valid audio file")
                        .build();
            }

            boolean isId3 = header[0] == 'I' && header[1] == 'D' && header[2] == '3';
            boolean isMpegFrame = (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0;

            if (!isId3 && !isMpegFrame) {
                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.FAILED)
                        .error("Invalid file format. File does not contain valid MP3 audio frames or ID3 header.")
                        .build();
            }
        } catch (IOException e) {
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Could not read uploaded audio content for validation")
                    .build();
        }

        // 3. Resolve metadata and relationships
        String title = resolveTitle(itemDto, sanitizedFilename);
        if (title.isBlank()) {
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Title must not be blank")
                    .build();
        }

        Artist artist = null;
        if (itemDto != null && itemDto.getArtistId() != null) {
            artist = artistCache.computeIfAbsent(itemDto.getArtistId(), id ->
                    artistRepository.findById(id).orElse(null)
            );
            if (artist == null) {
                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.FAILED)
                        .error("Artist not found with ID: " + itemDto.getArtistId())
                        .build();
            }
        }

        Album album = null;
        if (itemDto != null && itemDto.getAlbumId() != null) {
            album = albumCache.computeIfAbsent(itemDto.getAlbumId(), id ->
                    albumRepository.findById(id).orElse(null)
            );
            if (album == null) {
                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.FAILED)
                        .error("Album not found with ID: " + itemDto.getAlbumId())
                        .build();
            }
            if (artist == null && album.getArtist() != null) {
                artist = album.getArtist();
            }
        }

        Genre genre = itemDto != null ? itemDto.getGenre() : null;
        Integer trackNumber = itemDto != null ? itemDto.getTrackNumber() : null;

        // Pre-validate lyrics if present
        if (itemDto != null && itemDto.getLyrics() != null) {
            try {
                lyricsService.validateLrc(itemDto.getLyrics().getIsSynced(), itemDto.getLyrics().getLrcContent());
            } catch (Exception e) {
                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.FAILED)
                        .error("Invalid lyrics format: " + e.getMessage())
                        .build();
            }
        }

        // 4. Pre-check Title + Artist duplicate
        if (artist != null && musicRepository.existsByTitleIgnoreCaseAndArtistId(title, artist.getId())) {
            Optional<Music> existing = musicRepository.findByTitleIgnoreCaseAndArtistId(title, artist.getId());
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.DUPLICATE)
                    .musicId(existing.map(Music::getId).orElse(null))
                    .title(title)
                    .error("Music with this title already exists for this artist")
                    .build();
        }

        // 5. Stream to temporary file
        String uuid = UUID.randomUUID().toString();
        String tempName = "bulk_temp_" + uuid + ".mp3";
        String finalName = uuid + ".mp3";

        Path tempLocation = audioProcessingService.getTempStorageDir().resolve(tempName).normalize();
        Path targetLocation = audioProcessingService.getAudioStorageDir().resolve(finalName).normalize();

        try {
            Files.copy(file.getInputStream(), tempLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            audioProcessingService.deleteIfExistsSilently(tempLocation);
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Failed to store temporary audio file")
                    .build();
        }

        // 6. Calculate SHA-256 hash & check duplicate
        String audioHash;
        try {
            audioHash = audioProcessingService.calculateSha256(tempLocation);
        } catch (Exception e) {
            audioProcessingService.deleteIfExistsSilently(tempLocation);
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Failed to calculate SHA-256 hash")
                    .build();
        }

        Optional<Music> existingByHash = musicRepository.findFirstByAudioHash(audioHash);
        if (existingByHash.isPresent()) {
            audioProcessingService.deleteIfExistsSilently(tempLocation);
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.DUPLICATE)
                    .musicId(existingByHash.get().getId())
                    .title(existingByHash.get().getTitle())
                    .error("Duplicate audio content detected with SHA-256 hash")
                    .build();
        }

        // 7. CPU-bounded audio processing, tag normalization and permanent storage
        AudioMetadata metadata;
        boolean acquiredPermit = false;
        try {
            if (cpuSemaphore != null) {
                cpuSemaphore.acquire();
                acquiredPermit = true;
            }

            File audioIoFile = tempLocation.toFile();
            AudioFile audioFile;
            try {
                audioFile = AudioFileIO.read(audioIoFile);
            } catch (Exception e) {
                audioProcessingService.deleteIfExistsSilently(tempLocation);
                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.FAILED)
                        .error("Corrupted or unreadable audio file")
                        .build();
            }

            AudioHeader header = audioFile.getAudioHeader();

            // Strict whitelist: Replace existing tag with clean ID3v2.4 tag
            org.jaudiotagger.tag.id3.ID3v24Tag cleanTag = new org.jaudiotagger.tag.id3.ID3v24Tag();
            audioFile.setTag(cleanTag);
            if (audioFile instanceof org.jaudiotagger.audio.mp3.MP3File mp3File) {
                mp3File.setID3v1Tag(null);
            }
            Tag tag = cleanTag;

            tag.setField(FieldKey.TITLE, title);

            String artistName = artist != null ? artist.getName() : (album != null && album.getArtist() != null ? album.getArtist().getName() : null);
            if (artistName != null && !artistName.isBlank()) {
                tag.setField(FieldKey.ARTIST, artistName);
            }

            if (album != null && album.getTitle() != null && !album.getTitle().isBlank()) {
                tag.setField(FieldKey.ALBUM, album.getTitle());
                String albumArtistName = album.getArtist() != null ? album.getArtist().getName() : artistName;
                if (albumArtistName != null && !albumArtistName.isBlank()) {
                    tag.setField(FieldKey.ALBUM_ARTIST, albumArtistName);
                }
            }

            if (genre != null) {
                tag.setField(FieldKey.GENRE, genre.getDisplayName());
            }

            if (trackNumber != null && trackNumber > 0) {
                tag.setField(FieldKey.TRACK, String.valueOf(trackNumber));
            }

            if (album != null && album.getImage() != null && album.getImage().getStoredName() != null) {
                Path albumImagePath = audioProcessingService.getImageStorageDir().resolve(album.getImage().getStoredName()).normalize();
                if (Files.exists(albumImagePath)) {
                    Artwork artwork = ArtworkFactory.createArtworkFromFile(albumImagePath.toFile());
                    tag.setField(artwork);
                }
            }

            audioFile.commit();

            int duration = header.getTrackLength();
            int bitrate = (int) header.getBitRateAsNumber();
            int sampleRate = header.getSampleRateAsNumber();

            Files.move(tempLocation, targetLocation, StandardCopyOption.REPLACE_EXISTING);

            metadata = AudioMetadata.builder()
                    .storedName(finalName)
                    .originalFileName(sanitizedFilename)
                    .size(Files.size(targetLocation))
                    .contentType("audio/mpeg")
                    .duration(duration)
                    .bitrate(bitrate)
                    .sampleRate(sampleRate)
                    .format(AudioFormat.MP3)
                    .audioHash(audioHash)
                    .build();

        } catch (Exception e) {
            audioProcessingService.deleteIfExistsSilently(tempLocation);
            audioProcessingService.deletePhysicalFile(finalName);
            log.error("Audio processing failed for file: {}", sanitizedFilename, e);
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Failed to process audio file metadata")
                    .build();
        } finally {
            if (acquiredPermit && cpuSemaphore != null) {
                cpuSemaphore.release();
            }
        }

        // 8. Save Music entity in DB inside an isolated REQUIRES_NEW programmatic transaction
        final String createdStoredName = metadata.getStoredName();
        final Artist finalArtist = artist;
        final Album finalAlbum = album;
        final Genre finalGenre = genre;
        final Integer finalTrackNumber = trackNumber;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return txTemplate.execute(txStatus -> {
                if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCompletion(int status) {
                                    if (status == org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK) {
                                        log.warn("Transaction rolled back for bulk upload file: {}, cleaning up physical file", createdStoredName);
                                        audioProcessingService.deletePhysicalFile(createdStoredName);
                                    }
                                }
                            }
                    );
                }

                Music music = Music.builder()
                        .title(title)
                        .storedName(metadata.getStoredName())
                        .originalFileName(metadata.getOriginalFileName())
                        .audioSize(metadata.getSize())
                        .audioContentType(metadata.getContentType())
                        .duration(metadata.getDuration())
                        .bitrate(metadata.getBitrate())
                        .sampleRate(metadata.getSampleRate())
                        .audioFormat(metadata.getFormat())
                        .audioHash(metadata.getAudioHash())
                        .artist(finalArtist)
                        .album(finalAlbum)
                        .genre(finalGenre)
                        .trackNumber(finalTrackNumber)
                        .build();

                Music saved = musicRepository.saveAndFlush(music);

                if (itemDto != null && itemDto.getLyrics() != null) {
                    Lyrics lyrics = lyricsService.createNestedLyrics(saved, itemDto.getLyrics());
                    saved.addLyrics(lyrics);
                    musicRepository.saveAndFlush(saved);
                }

                return BulkMusicUploadItemResponse.builder()
                        .fileName(sanitizedFilename)
                        .status(UploadStatus.SUCCESS)
                        .musicId(saved.getId())
                        .title(saved.getTitle())
                        .build();
            });

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate insertion race condition detected for audioHash: {}", audioHash);
            audioProcessingService.deletePhysicalFile(createdStoredName);

            Integer existingId = null;
            try {
                Optional<Music> existing = musicRepository.findFirstByAudioHash(audioHash);
                if (existing.isEmpty() && finalArtist != null) {
                    existing = musicRepository.findByTitleIgnoreCaseAndArtistId(title, finalArtist.getId());
                }
                existingId = existing.map(Music::getId).orElse(null);
            } catch (Exception ignored) {
            }

            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.DUPLICATE)
                    .musicId(existingId)
                    .title(title)
                    .error("Duplicate entity detected via database constraint")
                    .build();

        } catch (Exception e) {
            log.error("Database save failed for file: {}", sanitizedFilename, e);
            audioProcessingService.deletePhysicalFile(createdStoredName);
            return BulkMusicUploadItemResponse.builder()
                    .fileName(sanitizedFilename)
                    .status(UploadStatus.FAILED)
                    .error("Failed to persist music record in database")
                    .build();
        }
    }

    private boolean isAllowedContentType(String contentType) {
        String lower = contentType.toLowerCase();
        return lower.equals("audio/mpeg") || lower.equals("audio/mp3") || lower.equals("audio/x-mpeg");
    }

    private String resolveTitle(BulkMusicItemDto itemDto, String originalFilename) {
        if (itemDto != null && itemDto.getTitle() != null && !itemDto.getTitle().trim().isBlank()) {
            return itemDto.getTitle().trim();
        }
        if (originalFilename != null) {
            String nameWithoutExt = originalFilename;
            int lastDot = originalFilename.lastIndexOf('.');
            if (lastDot > 0) {
                nameWithoutExt = originalFilename.substring(0, lastDot);
            }
            return nameWithoutExt.trim();
        }
        return "Untitled";
    }
}
