package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.xitlar.dto.LyricsCreateDto;
import uz.xitlar.dto.LyricsResponse;
import uz.xitlar.dto.LyricsUpdateDto;
import uz.xitlar.dto.ResponseApi;
import uz.xitlar.service.LyricsService;

@Tag(name = "Lyrics Controller", description = "Musiqa matnlari bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/lyrics")
@RequiredArgsConstructor
public class LyricsController {

    private final LyricsService lyricsService;

    @Operation(summary = "Yangi matn qo'shish", description = "Musiqaga yangi matn qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseApi<LyricsResponse> create(@Valid @RequestBody LyricsCreateDto dto) {
        return lyricsService.createLyrics(dto);
    }

    @Operation(summary = "Matnni yangilash", description = "Mavjud matnni yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping("/{id}")
    public ResponseApi<LyricsResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody LyricsUpdateDto dto) {
        return lyricsService.updateLyrics(id, dto);
    }

    @Operation(summary = "Matnni ID orqali olish", description = "Matnni ID orqali olish")
    @GetMapping("/{id}")
    public ResponseApi<LyricsResponse> getById(@PathVariable Integer id) {
        return lyricsService.getLyricsById(id);
    }

    @Operation(summary = "Musiqa ID orqali matnni olish", description = "Musiqa ID orqali matnni olish")
    @GetMapping("/music/{musicId}")
    public ResponseApi<LyricsResponse> getByMusicId(@PathVariable Integer musicId) {
        return lyricsService.getLyricsByMusicId(musicId);
    }

    @Operation(summary = "Matnni o'chirish", description = "Matnni o'chirish (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id) {
        return lyricsService.deleteLyrics(id);
    }
}
