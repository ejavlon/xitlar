package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.album.AlbumResponse;
import uz.xitlar.dto.artist.ArtistResponse;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.image.ImageResponse;
import uz.xitlar.dto.playlist.*;
import uz.xitlar.dto.user.UserResponse;
import uz.xitlar.entity.*;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.PlaylistMusicRepository;
import uz.xitlar.repository.PlaylistRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import uz.xitlar.repository.PlaylistVoteRepository;
import uz.xitlar.repository.UserRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistMusicRepository playlistMusicRepository;
    private final MusicRepository musicRepository;
    private final ImageStorageService imageStorageService;
    private final PlaylistVoteRepository playlistVoteRepository;
    private final UserRepository userRepository;

    @Transactional
    public ResponseApi<PlaylistResponse> createPlaylist(PlaylistCreateDto dto, MultipartFile file) {
        String trimmedTitle = dto.getTitle() != null ? dto.getTitle().trim() : "";
        if (trimmedTitle.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }

        String tagName = dto.getTagName() != null && !dto.getTagName().trim().isEmpty()
                ? dto.getTagName().trim().replaceAll("^#", "")
                : "playlists";

        Playlist playlist = Playlist.builder()
                .title(trimmedTitle)
                .tagName(tagName)
                .description(dto.getDescription())
                .build();

        if (file != null && !file.isEmpty()) {
            Integer imageId = imageStorageService.uploadImage(file).getData().getId();
            Image image = imageStorageService.getImageEntityOrThrow(imageId);
            playlist.setImage(image);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            log.info("Transaction rolled back. Cleaning up newly uploaded playlist image file: {}", image.getStoredName());
                            imageStorageService.deletePhysicalFile(image);
                        }
                    }
                });
            }
        }

        Playlist saved = playlistRepository.save(playlist);

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Playlist successfully created")
                .data(toDetailResponse(saved))
                .build();
    }

    @Transactional
    public ResponseApi<PlaylistResponse> updatePlaylist(Integer id, PlaylistUpdateDto dto, MultipartFile file) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }

        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + id));

        if (dto.getTitle() != null) {
            String trimmedTitle = dto.getTitle().trim();
            if (trimmedTitle.isEmpty()) {
                throw new IllegalArgumentException("Title must not be blank");
            }
            playlist.setTitle(trimmedTitle);
        }

        if (dto.getTagName() != null) {
            String cleanTag = dto.getTagName().trim().replaceAll("^#", "");
            playlist.setTagName(cleanTag.isEmpty() ? "playlists" : cleanTag);
        }

        if (dto.getDescription() != null) {
            playlist.setDescription(dto.getDescription());
        }

        if (file != null && !file.isEmpty()) {
            final Image oldImage = playlist.getImage();

            Integer imageId = imageStorageService.uploadImage(file).getData().getId();
            Image newImage = imageStorageService.getImageEntityOrThrow(imageId);
            playlist.setImage(newImage);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (oldImage != null) {
                            log.info("Transaction committed. Deleting old playlist image file: {}", oldImage.getStoredName());
                            imageStorageService.deletePhysicalFile(oldImage);
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            log.info("Transaction rolled back. Cleaning up newly uploaded playlist image file: {}", newImage.getStoredName());
                            imageStorageService.deletePhysicalFile(newImage);
                        }
                    }
                });
            }
        }

        Playlist saved = playlistRepository.save(playlist);

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Playlist successfully updated")
                .data(toDetailResponse(saved))
                .build();
    }

    public ResponseApi<PlaylistResponse> getPlaylistById(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }

        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + id));

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Playlist found")
                .data(toDetailResponse(playlist))
                .build();
    }

    public ResponseApi<Page<PlaylistResponse>> getAllPlaylists(Pageable pageable) {
        Page<PlaylistResponse> playlists = playlistRepository.findAll(pageable).map(this::toListResponse);
        return ResponseApi.<Page<PlaylistResponse>>builder()
                .success(true)
                .message("Playlists fetched")
                .data(playlists)
                .build();
    }

    @Transactional
    public ResponseApi<Void> deletePlaylist(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }

        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + id));

        final Image image = playlist.getImage();
        playlistRepository.delete(playlist);

        if (image != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Transaction committed. Deleting playlist image file: {}", image.getStoredName());
                    imageStorageService.deletePhysicalFile(image);
                }
            });
        }

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Playlist successfully deleted")
                .build();
    }

    @Transactional
    public ResponseApi<PlaylistResponse> addMusicToPlaylist(Integer playlistId, Integer musicId) {
        if (playlistId == null || playlistId <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }
        if (musicId == null || musicId <= 0) {
            throw new IllegalArgumentException("Music ID must be a positive number");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + playlistId));

        Music music = musicRepository.findById(musicId)
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + musicId));

        if (playlistMusicRepository.existsByPlaylistIdAndMusicId(playlistId, musicId)) {
            throw new DuplicateEntityException("Music is already in this playlist");
        }

        int nextPosition = playlistMusicRepository.countByPlaylistId(playlistId);

        PlaylistMusic playlistMusic = PlaylistMusic.builder()
                .playlist(playlist)
                .music(music)
                .position(nextPosition)
                .build();

        playlist.addPlaylistMusic(playlistMusic);

        try {
            playlistMusicRepository.saveAndFlush(playlistMusic);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate insertion attempt for playlist {} and music {}", playlistId, musicId);
            throw new DuplicateEntityException("Music is already in this playlist");
        }

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Music successfully added to playlist")
                .data(toDetailResponse(playlist))
                .build();
    }

    @Transactional
    public ResponseApi<PlaylistBulkAddResponse> addMusicsToPlaylist(Integer playlistId, PlaylistBulkMusicAddDto dto) {
        if (playlistId == null || playlistId <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }
        if (dto == null || dto.getMusicIds() == null) {
            throw new IllegalArgumentException("Music IDs list must not be null");
        }

        List<Integer> requestedIds = dto.getMusicIds();
        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("Music IDs list must not be empty");
        }
        if (requestedIds.size() > 50) {
            throw new IllegalArgumentException("Bulk add request exceeds maximum limit of 50 musics. Received: " + requestedIds.size());
        }

        for (Integer id : requestedIds) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Music ID must be a positive number");
            }
        }

        Set<Integer> uniqueIds = new HashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Bulk add request contains duplicate music IDs");
        }

        // 1. Find Playlist
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + playlistId));

        // 2. Batch fetch requested Musics
        List<Music> foundMusics = musicRepository.findAllById(requestedIds);
        Map<Integer, Music> musicMap = new HashMap<>();
        for (Music m : foundMusics) {
            musicMap.put(m.getId(), m);
        }

        List<Integer> missingIds = requestedIds.stream()
                .filter(id -> !musicMap.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new DataNotFoundException("Music not found with IDs: " + missingIds);
        }

        // 3. Batch check existing tracks in playlist
        List<PlaylistMusic> alreadyInPlaylist = playlistMusicRepository.findByPlaylistIdAndMusicIdIn(playlistId, requestedIds);
        if (!alreadyInPlaylist.isEmpty()) {
            List<Integer> duplicateIds = alreadyInPlaylist.stream()
                    .map(pm -> pm.getMusic().getId())
                    .toList();
            throw new DuplicateEntityException("Music tracks already in playlist with IDs: " + duplicateIds);
        }

        // 4. Calculate position and build PlaylistMusic records
        int currentTrackCount = playlistMusicRepository.countByPlaylistId(playlistId);
        List<PlaylistMusic> newTracks = new ArrayList<>(requestedIds.size());

        for (int i = 0; i < requestedIds.size(); i++) {
            Integer musicId = requestedIds.get(i);
            Music music = musicMap.get(musicId);
            PlaylistMusic pm = PlaylistMusic.builder()
                    .playlist(playlist)
                    .music(music)
                    .position(currentTrackCount + i)
                    .build();
            playlist.addPlaylistMusic(pm);
            newTracks.add(pm);
        }

        // 5. Batch insert
        try {
            playlistMusicRepository.saveAllAndFlush(newTracks);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate insertion attempt for playlist {} and musicIds {}", playlistId, requestedIds);
            throw new DuplicateEntityException("Music track is already in this playlist");
        }

        int finalTrackCount = currentTrackCount + newTracks.size();

        return ResponseApi.<PlaylistBulkAddResponse>builder()
                .success(true)
                .message("Music tracks added to playlist successfully")
                .data(PlaylistBulkAddResponse.builder()
                        .playlistId(playlistId)
                        .addedCount(newTracks.size())
                        .trackCount(finalTrackCount)
                        .build())
                .build();
    }

    @Transactional
    public ResponseApi<PlaylistResponse> removeMusicFromPlaylist(Integer playlistId, Integer musicId) {
        if (playlistId == null || playlistId <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }
        if (musicId == null || musicId <= 0) {
            throw new IllegalArgumentException("Music ID must be a positive number");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + playlistId));

        PlaylistMusic playlistMusic = playlistMusicRepository.findByPlaylistIdAndMusicId(playlistId, musicId)
                .orElseThrow(() -> new DataNotFoundException("Music is not in this playlist"));

        playlist.removePlaylistMusic(playlistMusic);
        playlistMusicRepository.delete(playlistMusic);
        playlistMusicRepository.flush();

        // Resequence remaining tracks to keep positions contiguous (0, 1, 2, ...)
        List<PlaylistMusic> remainingTracks = playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        for (int i = 0; i < remainingTracks.size(); i++) {
            remainingTracks.get(i).setPosition(i);
        }
        playlistMusicRepository.saveAllAndFlush(remainingTracks);

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Music successfully removed from playlist")
                .data(toDetailResponse(playlist))
                .build();
    }

    @Transactional
    public ResponseApi<PlaylistResponse> reorderPlaylistMusics(Integer playlistId, PlaylistReorderDto dto) {
        if (playlistId == null || playlistId <= 0) {
            throw new IllegalArgumentException("Playlist ID must be a positive number");
        }
        if (dto == null || dto.getMusicIds() == null) {
            throw new IllegalArgumentException("Music IDs list must not be null");
        }

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + playlistId));

        List<Integer> requestedMusicIds = dto.getMusicIds();

        // 1. Duplicate ID validation
        Set<Integer> uniqueIds = new HashSet<>(requestedMusicIds);
        if (uniqueIds.size() != requestedMusicIds.size()) {
            throw new IllegalArgumentException("Reorder request contains duplicate music IDs");
        }

        // 2. Fetch current tracks
        List<PlaylistMusic> currentTracks = playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(playlistId);

        // 3. Count matching
        if (currentTracks.size() != requestedMusicIds.size()) {
            throw new IllegalArgumentException("Reorder music IDs count does not match the playlist tracks count");
        }

        // 4. Map existing music IDs
        Map<Integer, PlaylistMusic> trackMap = new HashMap<>();
        for (PlaylistMusic pm : currentTracks) {
            trackMap.put(pm.getMusic().getId(), pm);
        }

        // 5. Verify all requested IDs exist in current playlist and reorder
        for (int i = 0; i < requestedMusicIds.size(); i++) {
            Integer musicId = requestedMusicIds.get(i);
            PlaylistMusic track = trackMap.get(musicId);
            if (track == null) {
                throw new IllegalArgumentException("Music ID " + musicId + " does not belong to this playlist");
            }
            track.setPosition(i);
        }

        playlistMusicRepository.saveAllAndFlush(currentTracks);

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Playlist musics successfully reordered")
                .data(toDetailResponse(playlist))
                .build();
    }

    @Transactional
    public ResponseApi<PlaylistResponse> votePlaylist(Integer playlistId, PlaylistVoteDto dto, UserDetails principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated");
        }

        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new DataNotFoundException("User not found: " + principal.getUsername()));

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new DataNotFoundException("Playlist not found with ID: " + playlistId));

        Optional<PlaylistVote> existingVote = playlistVoteRepository.findByUserIdAndPlaylistId(user.getId(), playlistId);

        if (existingVote.isPresent()) {
            PlaylistVote vote = existingVote.get();
            vote.setRating(dto.getRating());
            playlistVoteRepository.save(vote);
        } else {
            PlaylistVote vote = PlaylistVote.builder()
                    .user(user)
                    .playlist(playlist)
                    .rating(dto.getRating())
                    .build();
            playlistVoteRepository.save(vote);
        }

        int voteCount = playlistVoteRepository.countByPlaylistId(playlistId);
        double averageRating = playlistVoteRepository.averageRatingByPlaylistId(playlistId);

        playlist.setVoteCount(voteCount);
        playlist.setAverageRating(Math.round(averageRating * 10.0) / 10.0);
        playlistRepository.save(playlist);

        return ResponseApi.<PlaylistResponse>builder()
                .success(true)
                .message("Vote successfully recorded")
                .data(toDetailResponse(playlist))
                .build();
    }

    public ResponseApi<Page<PlaylistResponse>> getPlaylistsByTag(String tagName, Pageable pageable) {
        String cleanTag = tagName != null ? tagName.trim().replaceAll("^#", "") : "";
        Page<PlaylistResponse> playlists = playlistRepository.findByTagNameIgnoreCase(cleanTag, pageable).map(this::toListResponse);
        return ResponseApi.<Page<PlaylistResponse>>builder()
                .success(true)
                .message("Playlists for tag #" + cleanTag + " fetched")
                .data(playlists)
                .build();
    }

    public PlaylistResponse toDetailResponse(Playlist playlist) {
        List<PlaylistMusic> tracks = playlistMusicRepository.findByPlaylistIdOrderByPositionAsc(playlist.getId());
        List<PlaylistMusicResponse> trackResponses = tracks.stream()
                .map(this::toPlaylistMusicResponse)
                .toList();

        Integer userRating = getCurrentUserPlaylistRating(playlist.getId());

        return PlaylistResponse.builder()
                .id(playlist.getId())
                .title(playlist.getTitle())
                .tagName(playlist.getTagName() != null ? playlist.getTagName() : "playlists")
                .description(playlist.getDescription())
                .image(playlist.getImage() != null ? imageStorageService.toResponse(playlist.getImage()) : null)
                .musics(trackResponses)
                .trackCount(trackResponses.size())
                .voteCount(playlist.getVoteCount() != null ? playlist.getVoteCount() : 0)
                .averageRating(playlist.getAverageRating() != null ? playlist.getAverageRating() : 0.0)
                .userRating(userRating)
                .createdAt(playlist.getCreatedAt())
                .createdBy(toUserResponse(playlist.getCreatedBy()))
                .build();
    }

    public PlaylistResponse toListResponse(Playlist playlist) {
        int count = playlist.getPlaylistMusics() != null ? playlist.getPlaylistMusics().size() : 0;
        Integer userRating = getCurrentUserPlaylistRating(playlist.getId());

        return PlaylistResponse.builder()
                .id(playlist.getId())
                .title(playlist.getTitle())
                .tagName(playlist.getTagName() != null ? playlist.getTagName() : "playlists")
                .description(playlist.getDescription())
                .image(playlist.getImage() != null ? imageStorageService.toResponse(playlist.getImage()) : null)
                .musics(List.of())
                .trackCount(count)
                .voteCount(playlist.getVoteCount() != null ? playlist.getVoteCount() : 0)
                .averageRating(playlist.getAverageRating() != null ? playlist.getAverageRating() : 0.0)
                .userRating(userRating)
                .createdAt(playlist.getCreatedAt())
                .createdBy(toUserResponse(playlist.getCreatedBy()))
                .build();
    }

    private Integer getCurrentUserPlaylistRating(Integer playlistId) {
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
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    Optional<PlaylistVote> vote = playlistVoteRepository.findByUserIdAndPlaylistId(userOpt.get().getId(), playlistId);
                    if (vote.isPresent()) {
                        return vote.get().getRating();
                    }
                }
            }
        }
        return null;
    }

    private PlaylistMusicResponse toPlaylistMusicResponse(PlaylistMusic pm) {
        Music music = pm.getMusic();
        ArtistResponse artistResponse = null;
        if (music.getArtist() != null) {
            Artist artist = music.getArtist();
            artistResponse = ArtistResponse.builder()
                    .id(artist.getId())
                    .name(artist.getName())
                    .countOfTrack(artist.getCountOfTrack())
                    .genre(artist.getGenre())
                    .voteCount(artist.getVoteCount())
                    .averageRating(artist.getAverageRating())
                    .image(artist.getImage() != null ? imageStorageService.toResponse(artist.getImage()) : null)
                    .build();
        }

        AlbumResponse albumResponse = null;
        if (music.getAlbum() != null) {
            Album album = music.getAlbum();
            albumResponse = AlbumResponse.builder()
                    .id(album.getId())
                    .title(album.getTitle())
                    .artistId(album.getArtist() != null ? album.getArtist().getId() : null)
                    .artistName(album.getArtist() != null ? album.getArtist().getName() : null)
                    .image(album.getImage() != null ? imageStorageService.toResponse(album.getImage()) : null)
                    .build();
        }

        return PlaylistMusicResponse.builder()
                .id(music.getId())
                .title(music.getTitle())
                .audioUrl("/api/v1/musics/" + music.getId() + "/audio")
                .duration(music.getDuration())
                .artist(artistResponse)
                .album(albumResponse)
                .genre(music.getGenre())
                .position(pm.getPosition())
                .build();
    }

    private UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
