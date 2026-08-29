package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.image.ImageResponse;
import uz.xitlar.dto.playlist.*;
import uz.xitlar.entity.*;
import uz.xitlar.enums.Genre;
import uz.xitlar.enums.Role;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.PlaylistMusicRepository;
import uz.xitlar.repository.PlaylistRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private PlaylistMusicRepository playlistMusicRepository;

    @Mock
    private MusicRepository musicRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private PlaylistService playlistService;

    private Playlist testPlaylist;
    private Music testMusic1;
    private Music testMusic2;
    private User testUser;
    private Image testImage;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .firstName("Test")
                .lastName("Admin")
                .username("admin")
                .role(Role.ADMIN)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1);

        testImage = Image.builder()
                .originalName("cover.png")
                .storedName("stored_cover.png")
                .contentType("image/png")
                .size(1024L)
                .directoryPath("images")
                .url("images/stored_cover.png")
                .build();
        ReflectionTestUtils.setField(testImage, "id", 10);

        testPlaylist = Playlist.builder()
                .title("Hits 2026")
                .description("Top songs of the year")
                .image(testImage)
                .createdBy(testUser)
                .createdAt(LocalDateTime.now())
                .playlistMusics(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testPlaylist, "id", 100);

        testMusic1 = Music.builder()
                .title("Song 1")
                .storedName("song1.mp3")
                .originalFileName("song1.mp3")
                .audioSize(1000L)
                .audioContentType("audio/mpeg")
                .genre(Genre.POP)
                .duration(200)
                .build();
        ReflectionTestUtils.setField(testMusic1, "id", 501);

        testMusic2 = Music.builder()
                .title("Song 2")
                .storedName("song2.mp3")
                .originalFileName("song2.mp3")
                .audioSize(2000L)
                .audioContentType("audio/mpeg")
                .genre(Genre.ROCK)
                .duration(250)
                .build();
        ReflectionTestUtils.setField(testMusic2, "id", 502);
    }

    // ================= CREATE PLAYLIST =================

    @Test
    void createPlaylist_Success_WithoutImage() {
        PlaylistCreateDto dto = PlaylistCreateDto.builder()
                .title("My Chill Playlist")
                .description("Relaxing tracks")
                .build();

        when(playlistRepository.save(any(Playlist.class))).thenAnswer(invocation -> {
            Playlist p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 101);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        ResponseApi<PlaylistResponse> response = playlistService.createPlaylist(dto, null);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("Playlist successfully created", response.getMessage());
        assertEquals("My Chill Playlist", response.getData().getTitle());
        assertEquals("Relaxing tracks", response.getData().getDescription());
        verify(playlistRepository, times(1)).save(any(Playlist.class));
    }

    @Test
    void createPlaylist_Success_WithImage() {
        PlaylistCreateDto dto = PlaylistCreateDto.builder()
                .title("Party Mix")
                .description("Upbeat tracks")
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "party.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ImageResponse imageResponse = ImageResponse.builder().id(10).url("/api/v1/images/10").build();

        when(imageStorageService.uploadImage(file)).thenReturn(ResponseApi.<ImageResponse>builder().data(imageResponse).build());
        when(imageStorageService.getImageEntityOrThrow(10)).thenReturn(testImage);
        when(imageStorageService.toResponse(testImage)).thenReturn(imageResponse);
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(invocation -> {
            Playlist p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 102);
            return p;
        });

        ResponseApi<PlaylistResponse> response = playlistService.createPlaylist(dto, file);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertNotNull(response.getData().getImage());
        assertEquals("/api/v1/images/10", response.getData().getImage().getUrl());
        verify(imageStorageService, times(1)).uploadImage(file);
    }

    @Test
    void createPlaylist_BlankTitle_ThrowsIllegalArgumentException() {
        PlaylistCreateDto dto = PlaylistCreateDto.builder()
                .title("   ")
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.createPlaylist(dto, null));
        verify(playlistRepository, never()).save(any());
    }

    // ================= GET & PAGINATION =================

    @Test
    void getPlaylistById_Success() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));

        ResponseApi<PlaylistResponse> response = playlistService.getPlaylistById(100);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(100, response.getData().getId());
        assertEquals("Hits 2026", response.getData().getTitle());
    }

    @Test
    void getPlaylistById_NotFound_ThrowsDataNotFoundException() {
        when(playlistRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> playlistService.getPlaylistById(999));
    }

    @Test
    void getPlaylistById_InvalidId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> playlistService.getPlaylistById(-1));
    }

    @Test
    void getAllPlaylists_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Playlist> page = new PageImpl<>(List.of(testPlaylist), pageable, 1);
        when(playlistRepository.findAll(pageable)).thenReturn(page);

        ResponseApi<Page<PlaylistResponse>> response = playlistService.getAllPlaylists(pageable);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(1, response.getData().getTotalElements());
        assertEquals("Hits 2026", response.getData().getContent().get(0).getTitle());
    }

    // ================= UPDATE PLAYLIST =================

    @Test
    void updatePlaylist_Success_TitleAndDescription() {
        PlaylistUpdateDto dto = PlaylistUpdateDto.builder()
                .title("Updated Hits")
                .description("New Description")
                .build();

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseApi<PlaylistResponse> response = playlistService.updatePlaylist(100, dto, null);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("Updated Hits", response.getData().getTitle());
        assertEquals("New Description", response.getData().getDescription());
    }

    @Test
    void updatePlaylist_BlankTitle_ThrowsIllegalArgumentException() {
        PlaylistUpdateDto dto = PlaylistUpdateDto.builder()
                .title("   ")
                .build();

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));

        assertThrows(IllegalArgumentException.class, () -> playlistService.updatePlaylist(100, dto, null));
    }

    // ================= DELETE PLAYLIST =================

    @Test
    void deletePlaylist_Success() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));

        ResponseApi<Void> response = playlistService.deletePlaylist(100);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(playlistRepository, times(1)).delete(testPlaylist);
    }

    // ================= ADD MUSIC =================

    @Test
    void addMusicToPlaylist_Success() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findById(501)).thenReturn(Optional.of(testMusic1));
        when(playlistMusicRepository.existsByPlaylistIdAndMusicId(100, 501)).thenReturn(false);
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(0);

        ResponseApi<PlaylistResponse> response = playlistService.addMusicToPlaylist(100, 501);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(playlistMusicRepository, times(1)).saveAndFlush(any(PlaylistMusic.class));
    }

    @Test
    void addMusicToPlaylist_Duplicate_ThrowsDuplicateEntityException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findById(501)).thenReturn(Optional.of(testMusic1));
        when(playlistMusicRepository.existsByPlaylistIdAndMusicId(100, 501)).thenReturn(true);

        assertThrows(DuplicateEntityException.class, () -> playlistService.addMusicToPlaylist(100, 501));
        verify(playlistMusicRepository, never()).saveAndFlush(any());
    }

    @Test
    void addMusicToPlaylist_ConcurrentRaceCondition_ThrowsDuplicateEntityException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findById(501)).thenReturn(Optional.of(testMusic1));
        when(playlistMusicRepository.existsByPlaylistIdAndMusicId(100, 501)).thenReturn(false);
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(0);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key violates unique constraint uk_playlist_musics_playlist_music"))
                .when(playlistMusicRepository).saveAndFlush(any(PlaylistMusic.class));

        DuplicateEntityException ex = assertThrows(DuplicateEntityException.class,
                () -> playlistService.addMusicToPlaylist(100, 501));

        assertEquals("Music is already in this playlist", ex.getMessage());
    }

    @Test
    void addMusicToPlaylist_MusicNotFound_ThrowsDataNotFoundException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> playlistService.addMusicToPlaylist(100, 999));
    }

    @Test
    void addMusicToPlaylist_MultipleArtists_PreservesArtistAndAlbumIntegrity() {
        Artist artistA = Artist.builder().name("Artist A").build();
        ReflectionTestUtils.setField(artistA, "id", 201);
        Album albumA = Album.builder().title("Album A").artist(artistA).build();
        ReflectionTestUtils.setField(albumA, "id", 301);
        testMusic1.setArtist(artistA);
        testMusic1.setAlbum(albumA);

        Artist artistB = Artist.builder().name("Artist B").build();
        ReflectionTestUtils.setField(artistB, "id", 202);
        Album albumB = Album.builder().title("Album B").artist(artistB).build();
        ReflectionTestUtils.setField(albumB, "id", 302);
        testMusic2.setArtist(artistB);
        testMusic2.setAlbum(albumB);

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findById(501)).thenReturn(Optional.of(testMusic1));
        when(playlistMusicRepository.existsByPlaylistIdAndMusicId(100, 501)).thenReturn(false);
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(0);

        ResponseApi<PlaylistResponse> response1 = playlistService.addMusicToPlaylist(100, 501);
        assertNotNull(response1);
        assertTrue(response1.getSuccess());

        // Verify Music original artist and album were NEVER modified
        assertEquals("Artist A", testMusic1.getArtist().getName());
        assertEquals("Album A", testMusic1.getAlbum().getTitle());
        assertEquals("Artist B", testMusic2.getArtist().getName());
        assertEquals("Album B", testMusic2.getAlbum().getTitle());
    }

    // ================= REMOVE MUSIC =================

    @Test
    void removeMusicFromPlaylist_Success_ResequencesRemaining() {
        PlaylistMusic pm1 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic1).position(0).build();
        PlaylistMusic pm2 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic2).position(1).build();

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(playlistMusicRepository.findByPlaylistIdAndMusicId(100, 501)).thenReturn(Optional.of(pm1));
        when(playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(100)).thenReturn(List.of(pm2));

        ResponseApi<PlaylistResponse> response = playlistService.removeMusicFromPlaylist(100, 501);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        verify(playlistMusicRepository, times(1)).delete(pm1);
        assertEquals(0, pm2.getPosition());
        verify(playlistMusicRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void removeMusicFromPlaylist_NotInPlaylist_ThrowsDataNotFoundException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(playlistMusicRepository.findByPlaylistIdAndMusicId(100, 501)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> playlistService.removeMusicFromPlaylist(100, 501));
    }

    // ================= REORDER MUSICS =================

    @Test
    void reorderPlaylistMusics_Success() {
        PlaylistMusic pm1 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic1).position(0).build();
        PlaylistMusic pm2 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic2).position(1).build();

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(100)).thenReturn(List.of(pm1, pm2));

        PlaylistReorderDto dto = PlaylistReorderDto.builder()
                .musicIds(List.of(502, 501)) // reversed
                .build();

        ResponseApi<PlaylistResponse> response = playlistService.reorderPlaylistMusics(100, dto);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(1, pm1.getPosition());
        assertEquals(0, pm2.getPosition());
        verify(playlistMusicRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void reorderPlaylistMusics_DuplicateIds_ThrowsIllegalArgumentException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));

        PlaylistReorderDto dto = PlaylistReorderDto.builder()
                .musicIds(List.of(501, 501))
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.reorderPlaylistMusics(100, dto));
        verify(playlistMusicRepository, never()).saveAll(any());
    }

    @Test
    void reorderPlaylistMusics_CountMismatch_ThrowsIllegalArgumentException() {
        PlaylistMusic pm1 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic1).position(0).build();
        PlaylistMusic pm2 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic2).position(1).build();

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(100)).thenReturn(List.of(pm1, pm2));

        PlaylistReorderDto dto = PlaylistReorderDto.builder()
                .musicIds(List.of(501)) // only 1 of 2
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.reorderPlaylistMusics(100, dto));
    }

    @Test
    void reorderPlaylistMusics_UnknownMusicId_ThrowsIllegalArgumentException() {
        PlaylistMusic pm1 = PlaylistMusic.builder().playlist(testPlaylist).music(testMusic1).position(0).build();

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(100)).thenReturn(List.of(pm1));

        PlaylistReorderDto dto = PlaylistReorderDto.builder()
                .musicIds(List.of(999))
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.reorderPlaylistMusics(100, dto));
    }

    @Test
    void addMusicsToPlaylist_SingleMusic_Success() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findAllById(List.of(501))).thenReturn(List.of(testMusic1));
        when(playlistMusicRepository.findByPlaylistIdAndMusicIdIn(100, List.of(501))).thenReturn(List.of());
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(0);

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501))
                .build();

        ResponseApi<PlaylistBulkAddResponse> response = playlistService.addMusicsToPlaylist(100, dto);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(100, response.getData().getPlaylistId());
        assertEquals(1, response.getData().getAddedCount());
        assertEquals(1, response.getData().getTrackCount());
        verify(playlistMusicRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void addMusicsToPlaylist_50Musics_Success() {
        List<Integer> musicIds = new ArrayList<>();
        List<Music> musics = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            int id = 500 + i;
            musicIds.add(id);
            Music m = Music.builder()
                    .title("Track " + i)
                    .storedName("t" + i + ".mp3")
                    .originalFileName("t" + i + ".mp3")
                    .genre(Genre.POP)
                    .duration(180)
                    .build();
            ReflectionTestUtils.setField(m, "id", id);
            musics.add(m);
        }

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findAllById(musicIds)).thenReturn(musics);
        when(playlistMusicRepository.findByPlaylistIdAndMusicIdIn(100, musicIds)).thenReturn(List.of());
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(5); // existing 5 tracks

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(musicIds)
                .build();

        ResponseApi<PlaylistBulkAddResponse> response = playlistService.addMusicsToPlaylist(100, dto);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals(100, response.getData().getPlaylistId());
        assertEquals(50, response.getData().getAddedCount());
        assertEquals(55, response.getData().getTrackCount());
        verify(playlistMusicRepository, times(1)).saveAllAndFlush(anyList());
    }

    @Test
    void addMusicsToPlaylist_EmptyList_ThrowsIllegalArgumentException() {
        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of())
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.addMusicsToPlaylist(100, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_51Musics_ThrowsIllegalArgumentException() {
        List<Integer> musicIds = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            musicIds.add(500 + i);
        }

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(musicIds)
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.addMusicsToPlaylist(100, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_NullIdInList_ThrowsIllegalArgumentException() {
        List<Integer> musicIds = new ArrayList<>();
        musicIds.add(501);
        musicIds.add(null);

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(musicIds)
                .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.addMusicsToPlaylist(100, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_DuplicateIdsInList_ThrowsIllegalArgumentException() {
        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501, 502, 501))
        .build();

        assertThrows(IllegalArgumentException.class, () -> playlistService.addMusicsToPlaylist(100, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_NonexistentPlaylist_ThrowsDataNotFoundException() {
        when(playlistRepository.findById(999)).thenReturn(Optional.empty());

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501))
                .build();

        assertThrows(DataNotFoundException.class, () -> playlistService.addMusicsToPlaylist(999, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_NonexistentMusic_ThrowsDataNotFoundException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findAllById(List.of(501, 999))).thenReturn(List.of(testMusic1)); // 999 is missing

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501, 999))
                .build();

        assertThrows(DataNotFoundException.class, () -> playlistService.addMusicsToPlaylist(100, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_MusicAlreadyInPlaylist_ThrowsDuplicateEntityException() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findAllById(List.of(501, 502))).thenReturn(List.of(testMusic1, testMusic2));

        PlaylistMusic existingPm = PlaylistMusic.builder()
                .playlist(testPlaylist)
                .music(testMusic1)
                .position(0)
                .build();
        when(playlistMusicRepository.findByPlaylistIdAndMusicIdIn(100, List.of(501, 502)))
                .thenReturn(List.of(existingPm));

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501, 502))
                .build();

        assertThrows(DuplicateEntityException.class, () -> playlistService.addMusicsToPlaylist(100, dto));
        verify(playlistMusicRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void addMusicsToPlaylist_PositionsCalculatedContiguously() {
        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findAllById(List.of(501, 502))).thenReturn(List.of(testMusic1, testMusic2));
        when(playlistMusicRepository.findByPlaylistIdAndMusicIdIn(100, List.of(501, 502))).thenReturn(List.of());
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(3); // tracks 0, 1, 2 exist

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501, 502))
                .build();

        ResponseApi<PlaylistBulkAddResponse> response = playlistService.addMusicsToPlaylist(100, dto);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getData().getAddedCount());
        assertEquals(5, response.getData().getTrackCount());

        verify(playlistMusicRepository).saveAllAndFlush(argThat(list -> {
            List<PlaylistMusic> tracks = (List<PlaylistMusic>) list;
            return tracks.size() == 2 && tracks.get(0).getPosition() == 3 && tracks.get(1).getPosition() == 4;
        }));
    }

    @Test
    void addMusicsToPlaylist_ArtistAndAlbumAndMusicEntitiesUnchanged() {
        Artist testArtist = Artist.builder().name("Original Artist").build();
        ReflectionTestUtils.setField(testArtist, "id", 10);
        Album testAlbum = Album.builder().title("Original Album").build();
        ReflectionTestUtils.setField(testAlbum, "id", 20);

        testMusic1.setArtist(testArtist);
        testMusic1.setAlbum(testAlbum);

        when(playlistRepository.findById(100)).thenReturn(Optional.of(testPlaylist));
        when(musicRepository.findAllById(List.of(501))).thenReturn(List.of(testMusic1));
        when(playlistMusicRepository.findByPlaylistIdAndMusicIdIn(100, List.of(501))).thenReturn(List.of());
        when(playlistMusicRepository.countByPlaylistId(100)).thenReturn(0);

        PlaylistBulkMusicAddDto dto = PlaylistBulkMusicAddDto.builder()
                .musicIds(List.of(501))
                .build();

        playlistService.addMusicsToPlaylist(100, dto);

        assertSame(testArtist, testMusic1.getArtist());
        assertSame(testAlbum, testMusic1.getAlbum());
        assertEquals("Song 1", testMusic1.getTitle());
    }
}
