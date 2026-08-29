package uz.xitlar.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.music.AudioMetadata;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.enums.AudioFormat;
import uz.xitlar.enums.Genre;
import uz.xitlar.exception.FileStorageException;
import uz.xitlar.exception.InvalidAudioFileException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class AudioProcessingService {

    private final Path audioStorageDir;
    private final Path tempStorageDir;
    private final Path imageStorageDir;
    private final List<String> allowedContentTypes = Arrays.asList("audio/mpeg", "audio/mp3", "audio/x-mpeg");

    public AudioProcessingService(@Value("${app.storage.path}") String storagePath) {
        this.audioStorageDir = Paths.get(storagePath).resolve("audio").normalize().toAbsolutePath();
        this.tempStorageDir = Paths.get(storagePath).resolve("temp").normalize().toAbsolutePath();
        this.imageStorageDir = Paths.get(storagePath).resolve("images").normalize().toAbsolutePath();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(audioStorageDir);
            Files.createDirectories(tempStorageDir);
        } catch (IOException e) {
            throw new FileStorageException("Could not create storage directories for audio", e);
        }
    }

    public AudioMetadata processAndSaveAudio(
            MultipartFile file,
            String expectedTitle,
            Artist artist,
            Album album,
            Genre genre,
            Integer trackNumber
    ) {
        validateAudioFile(file);

        String originalFilename = file.getOriginalFilename();
        String sanitizedOriginalName = originalFilename != null ? Paths.get(originalFilename).getFileName().toString() : "audio.mp3";
        String extension = "mp3";

        String uuid = UUID.randomUUID().toString();
        String tempName = "temp_" + uuid + "." + extension;
        String finalName = uuid + "." + extension;

        Path tempLocation = tempStorageDir.resolve(tempName).normalize();
        Path targetLocation = audioStorageDir.resolve(finalName).normalize();

        try {
            Files.copy(file.getInputStream(), tempLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store temporary audio file", e);
        }

        AudioMetadata metadata;
        try {
            File audioIoFile = tempLocation.toFile();
            AudioFile audioFile;
            try {
                audioFile = AudioFileIO.read(audioIoFile);
            } catch (Exception e) {
                throw new InvalidAudioFileException("Corrupted or unreadable audio file", e);
            }

            AudioHeader header = audioFile.getAudioHeader();

            // 1. Strict whitelist: Replace existing tag with a brand-new clean ID3v2.4 tag and wipe ID3v1
            org.jaudiotagger.tag.id3.ID3v24Tag cleanTag = new org.jaudiotagger.tag.id3.ID3v24Tag();
            audioFile.setTag(cleanTag);
            if (audioFile instanceof org.jaudiotagger.audio.mp3.MP3File mp3File) {
                mp3File.setID3v1Tag(null);
            }
            Tag tag = cleanTag;

            // 2. Set ONLY trusted domain metadata
            if (expectedTitle != null && !expectedTitle.isBlank()) {
                tag.setField(FieldKey.TITLE, expectedTitle);
            }

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

            // 3. Album Cover Artwork embedding: Music -> Album -> Image (NEVER Artist image)
            if (album != null && album.getImage() != null && album.getImage().getStoredName() != null) {
                Path albumImagePath = imageStorageDir.resolve(album.getImage().getStoredName()).normalize();
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
                    .originalFileName(sanitizedOriginalName)
                    .size(Files.size(targetLocation))
                    .contentType("audio/mpeg")
                    .duration(duration)
                    .bitrate(bitrate)
                    .sampleRate(sampleRate)
                    .format(AudioFormat.MP3)
                    .build();

        } catch (InvalidAudioFileException e) {
            deleteIfExistsSilently(tempLocation);
            throw e;
        } catch (Exception e) {
            deleteIfExistsSilently(tempLocation);
            throw new FileStorageException("Failed to process and save audio metadata: " + e.getMessage(), e);
        }

        return metadata;
    }

    private static final long MAX_AUDIO_FILE_SIZE = 50 * 1024 * 1024L; // 50MB

    private void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAudioFileException("Failed to store empty file");
        }

        if (file.getSize() > MAX_AUDIO_FILE_SIZE) {
            throw new InvalidAudioFileException("Audio file exceeds maximum allowed size of 50MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
            throw new InvalidAudioFileException("Invalid audio file extension. Only .mp3 files are supported.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !allowedContentTypes.contains(contentType.toLowerCase())) {
            throw new InvalidAudioFileException("Incompatible audio content type: " + contentType + ". Expected audio/mpeg.");
        }

        // Magic bytes inspection
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[10];
            int read = is.read(header);
            if (read < 4) {
                throw new InvalidAudioFileException("File is too small to be a valid audio file");
            }

            // Check ID3v2 ("ID3") or MPEG sync frame (0xFF 0xEx)
            boolean isId3 = header[0] == 'I' && header[1] == 'D' && header[2] == '3';
            boolean isMpegFrame = (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0;

            if (!isId3 && !isMpegFrame) {
                throw new InvalidAudioFileException("Invalid file format. File does not contain valid MP3 audio frames or ID3 header.");
            }
        } catch (IOException e) {
            throw new InvalidAudioFileException("Could not read uploaded audio content for validation", e);
        }
    }

    private void deleteIfExistsSilently(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public void deletePhysicalFile(String storedName) {
        if (storedName == null) return;
        Path filePath = audioStorageDir.resolve(storedName).normalize();
        if (!filePath.startsWith(audioStorageDir)) {
            throw new SecurityException("Unauthorized path access");
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete physical file: {}", filePath, e);
        }
    }

    public Path getAudioPath(String storedName) {
        Path filePath = audioStorageDir.resolve(storedName).normalize();
        if (!filePath.startsWith(audioStorageDir)) {
            throw new SecurityException("Unauthorized path access");
        }
        return filePath;
    }
}
