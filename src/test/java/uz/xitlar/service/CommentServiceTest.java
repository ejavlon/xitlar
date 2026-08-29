package uz.xitlar.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private MusicRepository musicRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    private User ownerUser;
    private User otherUser;
    private User moderatorUser;
    private User adminUser;
    private Music sampleMusic;
    private Comment sampleComment;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder()
                .firstName("Owner")
                .lastName("User")
                .username("owner_user")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(ownerUser, "id", 101);

        otherUser = User.builder()
                .firstName("Other")
                .lastName("User")
                .username("other_user")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(otherUser, "id", 102);

        moderatorUser = User.builder()
                .firstName("Moderator")
                .lastName("User")
                .username("mod_user")
                .role(Role.MODERATOR)
                .build();
        ReflectionTestUtils.setField(moderatorUser, "id", 201);

        adminUser = User.builder()
                .firstName("Admin")
                .lastName("User")
                .username("admin_user")
                .role(Role.ADMIN)
                .build();
        ReflectionTestUtils.setField(adminUser, "id", 301);

        sampleMusic = Music.builder()
                .title("Sample Track")
                .build();
        ReflectionTestUtils.setField(sampleMusic, "id", 1);

        sampleComment = Comment.builder()
                .text("Initial comment text")
                .createdAt(LocalDateTime.now())
                .music(sampleMusic)
                .user(ownerUser)
                .build();
        ReflectionTestUtils.setField(sampleComment, "id", 10);
    }

    @Test
    void createComment_Success() {
        CommentCreateDto dto = new CommentCreateDto("Great track!", 1);

        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));
        when(musicRepository.findById(1)).thenReturn(Optional.of(sampleMusic));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 11);
            return c;
        });

        ResponseApi<CommentResponse> response = commentService.createComment(dto, ownerUser);

        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals("Great track!", response.getData().getText());
        assertEquals(1, response.getData().getMusicId());
        assertEquals(101, response.getData().getUserId());
        assertEquals("owner_user", response.getData().getUserName());
        assertEquals(11, response.getData().getId());
        assertNotNull(response.getData().getCreatedAt());

        verify(commentRepository, times(1)).save(argThat(c ->
                c.getText().equals("Great track!") &&
                c.getUser().equals(ownerUser) &&
                c.getMusic().equals(sampleMusic)
        ));
    }

    @Test
    void createComment_MusicNotFound_ThrowsDataNotFoundException() {
        CommentCreateDto dto = new CommentCreateDto("Great track!", 999);

        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));
        when(musicRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> commentService.createComment(dto, ownerUser));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void createComment_InvalidMusicId_ThrowsIllegalArgumentException() {
        CommentCreateDto dto = new CommentCreateDto("Great track!", 0);

        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));

        assertThrows(IllegalArgumentException.class, () -> commentService.createComment(dto, ownerUser));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_OwnComment_Success() {
        CommentUpdateDto dto = new CommentUpdateDto("Updated text by owner");

        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(sampleComment);

        ResponseApi<CommentResponse> response = commentService.updateComment(10, dto, ownerUser);

        assertTrue(response.getSuccess());
        assertEquals("Updated text by owner", response.getData().getText());
        assertEquals("owner_user", response.getData().getUserName());
        verify(commentRepository, times(1)).save(sampleComment);
    }

    @Test
    void updateComment_OtherUserComment_ThrowsAccessDeniedException() {
        CommentUpdateDto dto = new CommentUpdateDto("Malicious update");

        when(userRepository.findByUsername(otherUser.getUsername())).thenReturn(Optional.of(otherUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));

        assertThrows(AccessDeniedException.class, () -> commentService.updateComment(10, dto, otherUser));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateComment_ByModerator_Success() {
        CommentUpdateDto dto = new CommentUpdateDto("Moderated text");

        when(userRepository.findByUsername(moderatorUser.getUsername())).thenReturn(Optional.of(moderatorUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(sampleComment);

        ResponseApi<CommentResponse> response = commentService.updateComment(10, dto, moderatorUser);

        assertTrue(response.getSuccess());
        assertEquals("Moderated text", response.getData().getText());
        verify(commentRepository, times(1)).save(sampleComment);
    }

    @Test
    void updateComment_ByAdmin_Success() {
        CommentUpdateDto dto = new CommentUpdateDto("Admin updated text");

        when(userRepository.findByUsername(adminUser.getUsername())).thenReturn(Optional.of(adminUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(sampleComment);

        ResponseApi<CommentResponse> response = commentService.updateComment(10, dto, adminUser);

        assertTrue(response.getSuccess());
        assertEquals("Admin updated text", response.getData().getText());
        verify(commentRepository, times(1)).save(sampleComment);
    }

    @Test
    void updateComment_CommentNotFound_ThrowsDataNotFoundException() {
        CommentUpdateDto dto = new CommentUpdateDto("Updated text");

        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));
        when(commentRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> commentService.updateComment(999, dto, ownerUser));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_OwnComment_Success() {
        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));

        ResponseApi<Void> response = commentService.deleteComment(10, ownerUser);

        assertTrue(response.getSuccess());
        verify(commentRepository, times(1)).delete(sampleComment);
    }

    @Test
    void deleteComment_OtherUserComment_ThrowsAccessDeniedException() {
        when(userRepository.findByUsername(otherUser.getUsername())).thenReturn(Optional.of(otherUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));

        assertThrows(AccessDeniedException.class, () -> commentService.deleteComment(10, otherUser));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_ByModerator_Success() {
        when(userRepository.findByUsername(moderatorUser.getUsername())).thenReturn(Optional.of(moderatorUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));

        ResponseApi<Void> response = commentService.deleteComment(10, moderatorUser);

        assertTrue(response.getSuccess());
        verify(commentRepository, times(1)).delete(sampleComment);
    }

    @Test
    void deleteComment_ByAdmin_Success() {
        when(userRepository.findByUsername(adminUser.getUsername())).thenReturn(Optional.of(adminUser));
        when(commentRepository.findById(10)).thenReturn(Optional.of(sampleComment));

        ResponseApi<Void> response = commentService.deleteComment(10, adminUser);

        assertTrue(response.getSuccess());
        verify(commentRepository, times(1)).delete(sampleComment);
    }

    @Test
    void deleteComment_CommentNotFound_ThrowsDataNotFoundException() {
        when(userRepository.findByUsername(ownerUser.getUsername())).thenReturn(Optional.of(ownerUser));
        when(commentRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(DataNotFoundException.class, () -> commentService.deleteComment(999, ownerUser));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void getCommentsByMusic_Success_PaginationAndMapping() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Comment> commentPage = new PageImpl<>(List.of(sampleComment), pageable, 1);

        when(musicRepository.existsById(1)).thenReturn(true);
        when(commentRepository.findAllByMusicId(1, pageable)).thenReturn(commentPage);

        ResponseApi<Page<CommentResponse>> response = commentService.getCommentsByMusic(1, pageable);

        assertTrue(response.getSuccess());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getTotalElements());
        assertEquals(1, response.getData().getContent().size());

        CommentResponse mapped = response.getData().getContent().get(0);
        assertEquals(10, mapped.getId());
        assertEquals("Initial comment text", mapped.getText());
        assertEquals(1, mapped.getMusicId());
        assertEquals(101, mapped.getUserId());
        assertEquals("owner_user", mapped.getUserName());
    }

    @Test
    void getCommentsByMusic_MusicNotFound_ThrowsDataNotFoundException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(musicRepository.existsById(999)).thenReturn(false);

        assertThrows(DataNotFoundException.class, () -> commentService.getCommentsByMusic(999, pageable));
        verify(commentRepository, never()).findAllByMusicId(any(), any());
    }

    @Test
    void getCommentsByMusic_InvalidMusicId_ThrowsIllegalArgumentException() {
        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(IllegalArgumentException.class, () -> commentService.getCommentsByMusic(0, pageable));
        assertThrows(IllegalArgumentException.class, () -> commentService.getCommentsByMusic(-1, pageable));
        verify(commentRepository, never()).findAllByMusicId(any(), any());
    }
}
