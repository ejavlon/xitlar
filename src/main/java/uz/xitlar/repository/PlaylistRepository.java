package uz.xitlar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.xitlar.entity.Playlist;

import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Integer> {

    @EntityGraph(attributePaths = {"image", "createdBy"})
    Page<Playlist> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "image",
            "createdBy",
            "playlistMusics",
            "playlistMusics.music",
            "playlistMusics.music.artist",
            "playlistMusics.music.artist.image",
            "playlistMusics.music.album",
            "playlistMusics.music.album.image"
    })
    Optional<Playlist> findById(Integer id);
}
