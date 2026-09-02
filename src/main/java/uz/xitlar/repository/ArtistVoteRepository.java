package uz.xitlar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.xitlar.entity.ArtistVote;

import java.util.Optional;

public interface ArtistVoteRepository extends JpaRepository<ArtistVote, Integer> {

    Optional<ArtistVote> findByUserIdAndArtistId(Integer userId, Integer artistId);

    @Query("SELECT COUNT(v) FROM ArtistVote v WHERE v.artist.id = :artistId")
    int countByArtistId(@Param("artistId") Integer artistId);

    @Query("SELECT COALESCE(AVG(v.rating), 0.0) FROM ArtistVote v WHERE v.artist.id = :artistId")
    double averageRatingByArtistId(@Param("artistId") Integer artistId);
}
