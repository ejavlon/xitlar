package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.music.MusicCreateDto;
import uz.xitlar.dto.music.MusicResponse;
import uz.xitlar.dto.music.MusicUpdateDto;
import uz.xitlar.service.MusicService;

import java.util.Set;

@Slf4j
@Tag(name = "Music Controller", description = "Musiqalar bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/musics")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "title", "addedDate", "likeCount", "dislikeCount", "duration", "trackNumber", "genre"
    );

    @Operation(summary = "Yangi musiqa qo'shish", description = "Tizimga yangi musiqa qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<MusicResponse> create(
            @Valid @RequestPart("data") MusicCreateDto dto,
            @RequestPart("file") MultipartFile file) {
        return musicService.createMusic(dto, file);
    }

    @Operation(summary = "Musiqani yangilash", description = "Musiqa ma'lumotlarini yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<MusicResponse> update(
            @PathVariable Integer id,
            @Valid @RequestPart("data") MusicUpdateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return musicService.updateMusic(id, dto, file);
    }

    @Operation(summary = "Barcha musiqalarni olish", description = "Paginatsiya yordamida barcha musiqalar ro'yxatini olish")
    @GetMapping
    public ResponseApi<Page<MusicResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));
        return musicService.getAllMusics(pageable);
    }

    @Operation(summary = "Musiqani ID orqali olish", description = "Musiqaning to'liq ma'lumotlarini ID orqali olish")
    @GetMapping("/{id}")
    public ResponseApi<MusicResponse> getById(@PathVariable Integer id) {
        return musicService.getMusicById(id);
    }

    @Operation(summary = "Musiqani o'chirish", description = "Musiqani tizimdan o'chirish (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id) {
        return musicService.deleteMusic(id);
    }

    @Operation(summary = "Audio faylni o'qish/stream", description = "Musiqa faylini HTTP Range orqali uzatish")
    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> streamAudio(
            @PathVariable Integer id,
            @RequestHeader HttpHeaders headers) {
        return musicService.streamAudio(id, headers);
    }
}
