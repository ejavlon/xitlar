package uz.xitlar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uz.xitlar.dto.comment.CommentCreateDto;
import uz.xitlar.dto.comment.CommentResponse;
import uz.xitlar.dto.comment.CommentUpdateDto;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.GlobalExceptionHandler;
import uz.xitlar.service.CommentService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class CommentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_Success_Returns201() throws Exception {
        CommentCreateDto dto = new CommentCreateDto("Great track!", 1);
        CommentResponse response = CommentResponse.builder()
                .id(1)
                .text("Great track!")
                .createdAt(LocalDateTime.now())
                .musicId(1)
                .userId(10)
                .userName("john_doe")
                .build();

        when(commentService.createComment(any(CommentCreateDto.class), any()))
                .thenReturn(ResponseApi.<CommentResponse>builder().success(true).data(response).build());

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.text").value("Great track!"))
                .andExpect(jsonPath("$.data.musicId").value(1))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.userName").value("john_doe"));
    }

    @Test
    void create_BlankText_Returns400() throws Exception {
        CommentCreateDto dto = new CommentCreateDto("", 1);

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_MissingMusicId_Returns400() throws Exception {
        CommentCreateDto dto = new CommentCreateDto("Text", null);

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ZeroMusicId_Returns400() throws Exception {
        CommentCreateDto dto = new CommentCreateDto("Text", 0);

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_NegativeMusicId_Returns400() throws Exception {
        CommentCreateDto dto = new CommentCreateDto("Text", -5);

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_NonexistentMusic_Returns404() throws Exception {
        CommentCreateDto dto = new CommentCreateDto("Text", 999);

        when(commentService.createComment(any(CommentCreateDto.class), any()))
                .thenThrow(new DataNotFoundException("Music not found with ID: 999"));

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Music not found with ID: 999"));
    }

    @Test
    void getByMusicId_Success_Returns200WithPagination() throws Exception {
        CommentResponse response = CommentResponse.builder()
                .id(1)
                .text("Awesome song!")
                .musicId(5)
                .userId(12)
                .userName("listener_1")
                .build();

        Page<CommentResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);

        when(commentService.getCommentsByMusic(eq(5), any(Pageable.class)))
                .thenReturn(ResponseApi.<Page<CommentResponse>>builder().success(true).data(page).build());

        mockMvc.perform(get("/api/v1/comments/music/5?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].text").value("Awesome song!"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getByMusicId_NonexistentMusic_Returns404() throws Exception {
        when(commentService.getCommentsByMusic(eq(999), any(Pageable.class)))
                .thenThrow(new DataNotFoundException("Music not found with ID: 999"));

        mockMvc.perform(get("/api/v1/comments/music/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getByMusicId_InvalidMusicId_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/comments/music/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/comments/music/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByMusicId_TypeMismatch_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/comments/music/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Parameter 'musicId' must be of type 'Integer'"));
    }

    @Test
    void getByMusicId_UnknownSortBy_FallsBackToDefault() throws Exception {
        Page<CommentResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        when(commentService.getCommentsByMusic(eq(5), any(Pageable.class)))
                .thenReturn(ResponseApi.<Page<CommentResponse>>builder().success(true).data(page).build());

        mockMvc.perform(get("/api/v1/comments/music/5?sortBy=nonexistent_field"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void create_TextTooLong_Returns400() throws Exception {
        String longText = "a".repeat(2001);
        CommentCreateDto dto = new CommentCreateDto(longText, 1);

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_TextTooLong_Returns400() throws Exception {
        String longText = "a".repeat(2001);
        CommentUpdateDto dto = new CommentUpdateDto(longText);

        mockMvc.perform(put("/api/v1/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_Success_Returns200() throws Exception {
        CommentUpdateDto dto = new CommentUpdateDto("Updated comment text");
        CommentResponse response = CommentResponse.builder()
                .id(1)
                .text("Updated comment text")
                .musicId(1)
                .userId(10)
                .userName("john_doe")
                .build();

        when(commentService.updateComment(eq(1), any(CommentUpdateDto.class), any()))
                .thenReturn(ResponseApi.<CommentResponse>builder().success(true).data(response).build());

        mockMvc.perform(put("/api/v1/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.text").value("Updated comment text"));
    }

    @Test
    void update_NonexistentComment_Returns404() throws Exception {
        CommentUpdateDto dto = new CommentUpdateDto("Updated text");

        when(commentService.updateComment(eq(999), any(CommentUpdateDto.class), any()))
                .thenThrow(new DataNotFoundException("Comment not found with ID: 999"));

        mockMvc.perform(put("/api/v1/comments/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void update_BlankText_Returns400() throws Exception {
        CommentUpdateDto dto = new CommentUpdateDto("");

        mockMvc.perform(put("/api/v1/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_OtherUserComment_Returns403() throws Exception {
        CommentUpdateDto dto = new CommentUpdateDto("Hacked text");

        when(commentService.updateComment(eq(1), any(CommentUpdateDto.class), any()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(put("/api/v1/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void delete_Success_Returns200() throws Exception {
        when(commentService.deleteComment(eq(1), any()))
                .thenReturn(ResponseApi.<Void>builder().success(true).message("Comment deleted successfully").build());

        mockMvc.perform(delete("/api/v1/comments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_NonexistentComment_Returns404() throws Exception {
        when(commentService.deleteComment(eq(999), any()))
                .thenThrow(new DataNotFoundException("Comment not found with ID: 999"));

        mockMvc.perform(delete("/api/v1/comments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void delete_OtherUserComment_Returns403() throws Exception {
        when(commentService.deleteComment(eq(1), any()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(delete("/api/v1/comments/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
