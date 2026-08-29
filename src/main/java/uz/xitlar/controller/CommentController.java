package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import uz.xitlar.dto.comment.CommentCreateDto;
import uz.xitlar.dto.comment.CommentResponse;
import uz.xitlar.dto.comment.CommentUpdateDto;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.service.CommentService;

@Slf4j
@Tag(name = "Comment Controller", description = "Musiqa izohlari bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Yangi izoh qo'shish", description = "Musiqaga yangi izoh qo'shish (faqat avtorizatsiyadan o'tgan foydalanuvchilar uchun)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseApi<CommentResponse> create(
            @Valid @RequestBody CommentCreateDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        return commentService.createComment(dto, principal);
    }

    @Operation(summary = "Izohni yangilash", description = "Izohni tahrirlash (izoh egasi, MODERATOR yoki ADMIN uchun)")
    @PutMapping("/{id}")
    public ResponseApi<CommentResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody CommentUpdateDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        return commentService.updateComment(id, dto, principal);
    }

    @Operation(summary = "Izohni o'chirish", description = "Izohni o'chirish (izoh egasi, MODERATOR yoki ADMIN uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserDetails principal) {
        return commentService.deleteComment(id, principal);
    }

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of("id", "createdAt");

    @Operation(summary = "Musiqaning barcha izohlarini olish", description = "Musiqa ID bo'yicha barcha izohlar ro'yxatini paginatsiya bilan olish")
    @GetMapping("/music/{musicId}")
    public ResponseApi<Page<CommentResponse>> getByMusicId(
            @PathVariable Integer musicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        if (musicId == null || musicId <= 0) {
            throw new IllegalArgumentException("musicId must be positive");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));
        return commentService.getCommentsByMusic(musicId, pageable);
    }
}
