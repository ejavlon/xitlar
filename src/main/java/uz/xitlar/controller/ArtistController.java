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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.artist.ArtistCreateDto;
import uz.xitlar.dto.artist.ArtistResponse;
import uz.xitlar.dto.artist.ArtistUpdateDto;
import uz.xitlar.dto.artist.ArtistVoteDto;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.service.ArtistService;

@Tag(name = "Artist Controller", description = "Artistlar bilan ishlash uchun API")
@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @Operation(summary = "Yangi artist qo'shish", description = "Tizimga yangi artist qo'shish (faqat ADMIN yoki MODERATOR uchun)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<ArtistResponse> create(
            @Valid @RequestPart("data") ArtistCreateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return artistService.createArtist(dto, file);
    }

    @Operation(summary = "Artistni yangilash", description = "Artist ma'lumotlarini yangilash (faqat ADMIN yoki MODERATOR uchun)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseApi<ArtistResponse> update(
            @PathVariable Integer id,
            @Valid @RequestPart("data") ArtistUpdateDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return artistService.updateArtist(id, dto, file);
    }

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS = java.util.Set.of(
            "id", "name", "countOfTrack", "genre", "voteCount", "averageRating"
    );

    @Operation(summary = "Barcha artistlarni olish", description = "Paginatsiya yordamida barcha artistlarni ro'yxatini olish")
    @GetMapping
    public ResponseApi<Page<ArtistResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));
        return artistService.getAllArtists(pageable);
    }

    @Operation(summary = "Artistni ID orqali olish", description = "Artistning to'liq ma'lumotlarini ID orqali olish")
    @GetMapping("/{id}")
    public ResponseApi<ArtistResponse> getById(@PathVariable Integer id) {
        return artistService.getArtistById(id);
    }

    @Operation(summary = "Artistga vote berish", description = "Artistga 1-5 gacha rating berish (faqat autentifikatsiya qilingan foydalanuvchilar uchun)")
    @PostMapping("/{id}/vote")
    public ResponseApi<ArtistResponse> vote(
            @PathVariable Integer id,
            @Valid @RequestBody ArtistVoteDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        return artistService.voteArtist(id, dto, principal);
    }

    @Operation(summary = "Artistni o'chirish", description = "Artistni tizimdan o'chirish (faqat ADMIN yoki MODERATOR uchun)")
    @DeleteMapping("/{id}")
    public ResponseApi<Void> delete(@PathVariable Integer id) {
        return artistService.deleteArtist(id);
    }
}
