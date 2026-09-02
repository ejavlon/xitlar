package uz.xitlar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.xitlar.entity.Music;

import java.util.Optional;

public interface MusicRepository extends JpaRepository<Music, Integer> {

    boolean existsByTitleIgnoreCaseAndArtistId(String title, Integer artistId);

    Optional<Music> findByTitleIgnoreCaseAndArtistId(String title, Integer artistId);

    Optional<Music> findByAudioHash(String audioHash);

    Optional<Music> findFirstByAudioHash(String audioHash);

    boolean existsByAudioHash(String audioHash);

    @EntityGraph(attributePaths = {"artist", "artist.image", "album", "album.image", "lyrics"})
    Page<Music> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"artist", "artist.image", "album", "album.image", "lyrics"})
    Optional<Music> findById(Integer id);
}
