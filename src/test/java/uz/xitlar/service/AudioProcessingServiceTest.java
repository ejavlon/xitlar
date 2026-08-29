package uz.xitlar.service;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import uz.xitlar.dto.music.AudioMetadata;
import uz.xitlar.entity.Album;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Image;
import uz.xitlar.enums.AudioFormat;
import uz.xitlar.enums.Genre;
import uz.xitlar.exception.InvalidAudioFileException;
import uz.xitlar.util.AudioTestHelper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AudioProcessingServiceTest {

    @TempDir
    Path tempFolder;

    private AudioProcessingService audioProcessingService;
    private Path imagesDir;
    private Path audioDir;

    @BeforeEach
    void setUp() {
        audioProcessingService = new AudioProcessingService(tempFolder.toString());
        audioProcessingService.init();
        imagesDir = tempFolder.resolve("images");
        audioDir = tempFolder.resolve("audio");
    }

    @Test
    void processAndSaveAudio_ValidMp3_Success() throws Exception {
        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "My Song.mp3",
                "audio/mpeg",
                mp3Bytes
        );

        Artist artist = Artist.builder().name("Test Artist").build();
        Album album = Album.builder().title("Test Album").artist(artist).build();

        AudioMetadata metadata = audioProcessingService.processAndSaveAudio(
                file,
                "Clean Title",
                artist,
                album,
                Genre.POP,
                1
        );

        assertNotNull(metadata);
        assertEquals("My Song.mp3", metadata.getOriginalFileName());
        assertTrue(metadata.getStoredName().endsWith(".mp3"));
        assertEquals(AudioFormat.MP3, metadata.getFormat());
        assertTrue(metadata.getSize() > 0);

        // Verify file exists on disk with UUID-based name
        Path physicalFile = audioProcessingService.getAudioPath(metadata.getStoredName());
        assertTrue(Files.exists(physicalFile));

        // Verify tags written
        AudioFile audioFile = AudioFileIO.read(physicalFile.toFile());
        Tag tag = audioFile.getTag();
        assertEquals("Clean Title", tag.getFirst(FieldKey.TITLE));
        assertEquals("Test Artist", tag.getFirst(FieldKey.ARTIST));
        assertEquals("Test Album", tag.getFirst(FieldKey.ALBUM));
        assertEquals("Test Artist", tag.getFirst(FieldKey.ALBUM_ARTIST));
        assertEquals(Genre.POP.getDisplayName(), tag.getFirst(FieldKey.GENRE));
        assertEquals("1", tag.getFirst(FieldKey.TRACK));
    }

    @Test
    void processAndSaveAudio_AlbumArtworkEmbedded() throws Exception {
        // Create dummy album image file
        Files.createDirectories(imagesDir);
        Path imagePath = imagesDir.resolve("album-cover.jpg");
        // Simple 1x1 JPEG minimal bytes
        byte[] dummyJpeg = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
                0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48,
                0x00, 0x48, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
        };
        Files.write(imagePath, dummyJpeg);

        Image albumImage = Image.builder()
                .storedName("album-cover.jpg")
                .originalName("cover.jpg")
                .build();

        Artist artist = Artist.builder().name("Artist Name").build();
        Album album = Album.builder().title("Album Title").image(albumImage).artist(artist).build();

        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "song.mp3",
                "audio/mpeg",
                mp3Bytes
        );

        AudioMetadata metadata = audioProcessingService.processAndSaveAudio(
                file,
                "Song Title",
                artist,
                album,
                Genre.ROCK,
                2
        );

        Path physicalFile = audioProcessingService.getAudioPath(metadata.getStoredName());
        AudioFile audioFile = AudioFileIO.read(physicalFile.toFile());
        Tag tag = audioFile.getTag();
        assertNotNull(tag.getFirstArtwork());
    }

    @Test
    void processAndSaveAudio_EmptyFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", new byte[0]);
        assertThrows(InvalidAudioFileException.class, () ->
                audioProcessingService.processAndSaveAudio(file, "Title", null, null, null, null)
        );
    }

    @Test
    void processAndSaveAudio_InvalidExtension_ThrowsException() {
        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile file = new MockMultipartFile("file", "test.wav", "audio/mpeg", mp3Bytes);
        assertThrows(InvalidAudioFileException.class, () ->
                audioProcessingService.processAndSaveAudio(file, "Title", null, null, null, null)
        );
    }

    @Test
    void processAndSaveAudio_InvalidContentType_ThrowsException() {
        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "application/pdf", mp3Bytes);
        assertThrows(InvalidAudioFileException.class, () ->
                audioProcessingService.processAndSaveAudio(file, "Title", null, null, null, null)
        );
    }

    @Test
    void processAndSaveAudio_ArtistImageNeverUsedAsArtwork_WhenAlbumHasNoImage() throws Exception {
        Image artistImage = Image.builder()
                .storedName("artist-portrait.jpg")
                .originalName("portrait.jpg")
                .build();
        Artist artistWithPortrait = Artist.builder()
                .name("Artist With Portrait")
                .image(artistImage)
                .build();

        // Album without image
        Album albumWithoutImage = Album.builder()
                .title("Album No Cover")
                .artist(artistWithPortrait)
                .image(null)
                .build();

        byte[] mp3Bytes = AudioTestHelper.createMinimalValidMp3();
        MockMultipartFile file = new MockMultipartFile("file", "song.mp3", "audio/mpeg", mp3Bytes);

        AudioMetadata metadata = audioProcessingService.processAndSaveAudio(
                file,
                "Song Title",
                artistWithPortrait,
                albumWithoutImage,
                Genre.POP,
                1
        );

        Path physicalFile = audioProcessingService.getAudioPath(metadata.getStoredName());
        AudioFile audioFile = AudioFileIO.read(physicalFile.toFile());
        Tag tag = audioFile.getTag();

        // Artwork must be NULL because Album has no image; Artist image must NEVER be used
        assertNull(tag.getFirstArtwork());
    }

    @Test
    void processAndSaveAudio_FakeExeWithAudioMime_ThrowsException() {
        // Starts with MZ header (Windows executable)
        byte[] exeBytes = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "payload.mp3", "audio/mpeg", exeBytes);
        assertThrows(InvalidAudioFileException.class, () ->
                audioProcessingService.processAndSaveAudio(file, "Title", null, null, null, null)
        );
    }

    @Test
    void processAndSaveAudio_CorruptedAudio_ThrowsException() {
        // Starts with MP3 header but followed by garbage that Jaudiotagger fails to parse
        byte[] corrupted = new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00, 0x12, 0x34, 0x56};
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.mp3", "audio/mpeg", corrupted);
        assertThrows(InvalidAudioFileException.class, () ->
                audioProcessingService.processAndSaveAudio(file, "Title", null, null, null, null)
        );
    }

    @Test
    void processAndSaveAudio_SanitizesAndRemovesArbitraryUploadedTags() throws Exception {
        Path dirtyMp3Path = tempFolder.resolve("dirty_source.mp3");
        Files.write(dirtyMp3Path, AudioTestHelper.createMinimalValidMp3());

        AudioFile audioFile = AudioFileIO.read(dirtyMp3Path.toFile());
        Tag dirtyTag = audioFile.getTagOrCreateAndSetDefault();
        dirtyTag.setField(FieldKey.COMMENT, "Promotional spam comment http://adware.org");
        dirtyTag.setField(FieldKey.COPYRIGHT, "Spam Copyright");
        dirtyTag.setField(FieldKey.URL_OFFICIAL_RELEASE_SITE, "http://spam-release.com");
        dirtyTag.setField(FieldKey.ALBUM, "Old Pirate Album");
        dirtyTag.setField(FieldKey.ARTIST, "Old Pirate Artist");
        audioFile.commit();

        byte[] dirtyBytes = Files.readAllBytes(dirtyMp3Path);
        MockMultipartFile file = new MockMultipartFile("file", "dirty.mp3", "audio/mpeg", dirtyBytes);

        Artist cleanArtist = Artist.builder().name("Clean Artist").build();
        Album cleanAlbum = Album.builder().title("Clean Album").artist(cleanArtist).build();

        AudioMetadata metadata = audioProcessingService.processAndSaveAudio(
                file,
                "Clean Title",
                cleanArtist,
                cleanAlbum,
                Genre.POP,
                1
        );

        Path finalFile = audioProcessingService.getAudioPath(metadata.getStoredName());
        AudioFile processedAudioFile = AudioFileIO.read(finalFile.toFile());
        Tag cleanResultTag = processedAudioFile.getTag();

        // Verify arbitrary/spam tags are removed
        assertTrue(cleanResultTag.getFirst(FieldKey.COMMENT).isEmpty());
        assertTrue(cleanResultTag.getFirst(FieldKey.COPYRIGHT).isEmpty());
        assertTrue(cleanResultTag.getFirst(FieldKey.URL_OFFICIAL_RELEASE_SITE).isEmpty());

        // Verify only Xitlar domain values are present
        assertEquals("Clean Title", cleanResultTag.getFirst(FieldKey.TITLE));
        assertEquals("Clean Artist", cleanResultTag.getFirst(FieldKey.ARTIST));
        assertEquals("Clean Album", cleanResultTag.getFirst(FieldKey.ALBUM));
        assertEquals(Genre.POP.getDisplayName(), cleanResultTag.getFirst(FieldKey.GENRE));
        assertEquals("1", cleanResultTag.getFirst(FieldKey.TRACK));
    }
}
