package uz.xitlar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.xitlar.entity.PlaylistVote;

import java.util.Optional;

@Repository
public interface PlaylistVoteRepository extends JpaRepository<PlaylistVote, Integer> {

    Optional<PlaylistVote> findByUserIdAndPlaylistId(Integer userId, Integer playlistId);

    int countByPlaylistId(Integer playlistId);

    @Query("SELECT COALESCE(AVG(pv.rating), 0.0) FROM PlaylistVote pv WHERE pv.playlist.id = :playlistId")
    double averageRatingByPlaylistId(@Param("playlistId") Integer playlistId);
}
