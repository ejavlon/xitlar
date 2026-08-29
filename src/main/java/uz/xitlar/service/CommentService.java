package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.xitlar.dto.CommentCreateDto;
import uz.xitlar.dto.CommentResponse;
import uz.xitlar.dto.CommentUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.entity.Comment;
import uz.xitlar.entity.Music;
import uz.xitlar.entity.User;
import uz.xitlar.enums.Role;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.repository.CommentRepository;
import uz.xitlar.repository.MusicRepository;
import uz.xitlar.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final MusicRepository musicRepository;
    private final UserRepository userRepository;

    @Transactional
    public ResponseApi<CommentResponse> createComment(CommentCreateDto dto, UserDetails currentUser) {
        User actor = getActor(currentUser);

        if (dto.getMusicId() == null || dto.getMusicId() <= 0) {
            throw new IllegalArgumentException("musicId must be positive");
        }

        Music music = musicRepository.findById(dto.getMusicId())
                .orElseThrow(() -> new DataNotFoundException("Music not found with ID: " + dto.getMusicId()));

        Comment comment = Comment.builder()
                .text(dto.getText())
                .music(music)
                .user(actor)
                .createdAt(LocalDateTime.now())
                .build();

        Comment savedComment = commentRepository.save(comment);

        return ResponseApi.<CommentResponse>builder()
                .success(true)
                .message("Comment created successfully")
                .data(toResponse(savedComment))
                .build();
    }

    @Transactional
    public ResponseApi<CommentResponse> updateComment(Integer id, CommentUpdateDto dto, UserDetails currentUser) {
        User actor = getActor(currentUser);

        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Comment not found with ID: " + id));

        checkOwnership(comment, actor);

        comment.setText(dto.getText());
        Comment updatedComment = commentRepository.save(comment);

        return ResponseApi.<CommentResponse>builder()
                .success(true)
                .message("Comment updated successfully")
                .data(toResponse(updatedComment))
                .build();
    }

    @Transactional
    public ResponseApi<Void> deleteComment(Integer id, UserDetails currentUser) {
        User actor = getActor(currentUser);

        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Comment not found with ID: " + id));

        checkOwnership(comment, actor);

        commentRepository.delete(comment);

        return ResponseApi.<Void>builder()
                .success(true)
                .message("Comment deleted successfully")
                .build();
    }

    public ResponseApi<Page<CommentResponse>> getCommentsByMusic(Integer musicId, Pageable pageable) {
        if (musicId == null || musicId <= 0) {
            throw new IllegalArgumentException("musicId must be positive");
        }

        if (!musicRepository.existsById(musicId)) {
            throw new DataNotFoundException("Music not found with ID: " + musicId);
        }

        Page<Comment> commentPage = commentRepository.findAllByMusicId(musicId, pageable);
        Page<CommentResponse> responsePage = commentPage.map(this::toResponse);

        return ResponseApi.<Page<CommentResponse>>builder()
                .success(true)
                .message("Comments fetched successfully")
                .data(responsePage)
                .build();
    }

    private User getActor(UserDetails principal) {
        if (principal == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        Optional<User> userOptional = userRepository.findByUsername(principal.getUsername());
        if (userOptional.isPresent()) {
            return userOptional.get();
        }
        if (principal instanceof User user && user.getId() != null) {
            return user;
        }
        throw new DataNotFoundException("User not found with username: " + principal.getUsername());
    }

    private void checkOwnership(Comment comment, User actor) {
        boolean isPrivileged = actor.getRole() == Role.ADMIN || actor.getRole() == Role.MODERATOR;
        if (!isPrivileged) {
            if (comment.getUser() == null || comment.getUser().getId() == null || !comment.getUser().getId().equals(actor.getId())) {
                throw new AccessDeniedException("Access denied");
            }
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .musicId(comment.getMusic() != null ? comment.getMusic().getId() : null)
                .userId(comment.getUser() != null ? comment.getUser().getId() : null)
                .userName(comment.getUser() != null ? comment.getUser().getUsername() : null)
                .build();
    }
}
