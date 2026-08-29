package uz.xitlar.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.dto.playlist.*;
import uz.xitlar.service.PlaylistService;

import java.util.Set;

@Tag(name = "Playlist Controller", description = "Playlistlar bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "title", "createdAt");

    @Operation(summary = "Yangi playlist yaratish", description = "Tizimga yangi playlist qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<PlaylistResponse> create(
            @Valid @RequestPart("data") PlaylistCreateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return playlistService.createPlaylist(dto, file);
    }

    @Operation(summary = "Playlistni yangilash", description = "Playlist ma'lumotlarini yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<PlaylistResponse> update(
            @PathVariable Integer id,
            @Valid @RequestPart("data") PlaylistUpdateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return playlistService.updatePlaylist(id, dto, file);
    }

    @Operation(summary = "Barcha playlistlarni olish", description = "Paginatsiya yordamida barcha playlistlar ro'yxatini olish")
    @GetMapping
    public ResponseApi<Page<PlaylistResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));
        return playlistService.getAllPlaylists(pageable);
    }

    @Operation(summary = "Playlistni ID orqali olish", description = "Playlistning to'liq ma'lumotlarini ID orqali olish")
    @GetMapping("/{id}")
    public ResponseApi<PlaylistResponse> getById(@PathVariable Integer id) {
        return playlistService.getPlaylistById(id);
    }

    @Operation(summary = "Playlistni o'chirish", description = "Playlistni tizimdan o'chirish (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id) {
        return playlistService.deletePlaylist(id);
    }

    @Operation(summary = "Playlistga musiqa qo'shish", description = "Playlistga yangi musiqani oxiriga qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping("/{playlistId}/musics/{musicId}")
    public ResponseApi<PlaylistResponse> addMusic(
            @PathVariable Integer playlistId,
            @PathVariable Integer musicId) {
        return playlistService.addMusicToPlaylist(playlistId, musicId);
    }

    @Operation(summary = "Playlistga bir nechta musiqa qo'shish (bulk)", description = "Playlistga bir vaqtda 1-50 ta mavjud musiqani oxiriga qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping("/{playlistId}/musics/bulk")
    public ResponseApi<PlaylistBulkAddResponse> addMusicsBulk(
            @PathVariable Integer playlistId,
            @Valid @RequestBody PlaylistBulkMusicAddDto dto) {
        return playlistService.addMusicsToPlaylist(playlistId, dto);
    }

    @Operation(summary = "Playlistdan musiqani o'chirish", description = "Playlistdan musiqani olib tashlash (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{playlistId}/musics/{musicId}")
    public ResponseApi<PlaylistResponse> removeMusic(
            @PathVariable Integer playlistId,
            @PathVariable Integer musicId) {
        return playlistService.removeMusicFromPlaylist(playlistId, musicId);
    }

    @Operation(summary = "Playlistdagi musiqalar tartibini o'zgartirish", description = "Playlistdagi musiqalarning position tartibini yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping("/{playlistId}/musics/reorder")
    public ResponseApi<PlaylistResponse> reorderMusics(
            @PathVariable Integer playlistId,
            @Valid @RequestBody PlaylistReorderDto dto) {
        return playlistService.reorderPlaylistMusics(playlistId, dto);
    }
}
