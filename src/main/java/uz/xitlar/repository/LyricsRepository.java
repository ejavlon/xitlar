package uz.xitlar.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.xitlar.entity.Lyrics;

import java.util.Optional;

@Repository
public interface LyricsRepository extends JpaRepository<Lyrics, Integer> {

    @EntityGraph(attributePaths = {"music"})
    Optional<Lyrics> findById(Integer id);

    @EntityGraph(attributePaths = {"music"})
    Optional<Lyrics> findByMusicId(Integer musicId);

    boolean existsByMusicId(Integer musicId);
}
