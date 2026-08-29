package uz.xitlar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.xitlar.entity.Comment;

import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    @EntityGraph(attributePaths = {"user", "music"})
    Page<Comment> findAllByMusicId(Integer musicId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "music"})
    Page<Comment> findAllByUserId(Integer userId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "music"})
    Optional<Comment> findById(Integer id);
}
