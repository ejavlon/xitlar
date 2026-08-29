package uz.xitlar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.xitlar.entity.PlaylistMusic;

import java.util.List;
import java.util.Optional;

public interface PlaylistMusicRepository extends JpaRepository<PlaylistMusic, Integer> {

    boolean existsByPlaylistIdAndMusicId(Integer playlistId, Integer musicId);

    Optional<PlaylistMusic> findByPlaylistIdAndMusicId(Integer playlistId, Integer musicId);

    List<PlaylistMusic> findByPlaylistIdOrderByPositionAsc(Integer playlistId);

    List<PlaylistMusic> findByPlaylistIdAndMusicIdIn(Integer playlistId, java.util.Collection<Integer> musicIds);

    int countByPlaylistId(Integer playlistId);

    void deleteByPlaylistIdAndMusicId(Integer playlistId, Integer musicId);
}
