package uz.xitlar.repository;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.xitlar.entity.Artist;

import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, Integer> {
    
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    @EntityGraph(attributePaths = {"image"})
    @NonNull
    Page<Artist> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"image"})
    @NonNull
    Optional<Artist> findById(@NonNull Integer id);
}
