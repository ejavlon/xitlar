package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.artist.ArtistCreateDto;
import uz.xitlar.dto.artist.ArtistResponse;
import uz.xitlar.dto.artist.ArtistUpdateDto;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.Image;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.ArtistRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final ImageStorageService imageStorageService;

    @Transactional
    public ResponseApi<ArtistResponse> createArtist(ArtistCreateDto dto, MultipartFile file) {
        String trimmedName = dto.getName().trim();
        if (artistRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateEntityException("Artist with this name already exists");
        }

        Artist artist = Artist.builder()
                .name(trimmedName)
                .genre(dto.getGenre())
                .build();

        if (file != null && !file.isEmpty()) {
            Integer imageId = imageStorageService.uploadImage(file).getData().getId();
            Image image = imageStorageService.getImageEntityOrThrow(imageId);
            artist.setImage(image);
        }

        Artist saved = artistRepository.save(artist);
        return ResponseApi.<ArtistResponse>builder()
                .success(true)
                .message("Artist successfully created")
                .data(toResponse(saved))
                .build();
    }

    @Transactional
    public ResponseApi<ArtistResponse> updateArtist(Integer id, ArtistUpdateDto dto, MultipartFile file) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + id));

        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new IllegalArgumentException("Artist name must not be blank");
            }
            if (artistRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
                throw new DuplicateEntityException("Another artist with this name already exists");
            }
            artist.setName(trimmedName);
        }

        if (dto.getGenre() != null) {
            artist.setGenre(dto.getGenre());
        }

        if (file != null && !file.isEmpty()) {
            if (artist.getImage() != null) {
                imageStorageService.deletePhysicalFile(artist.getImage());
            }
            
            Integer imageId = imageStorageService.uploadImage(file).getData().getId();
            Image image = imageStorageService.getImageEntityOrThrow(imageId);
            artist.setImage(image);
        }

        Artist saved = artistRepository.save(artist);
        return ResponseApi.<ArtistResponse>builder()
                .success(true)
                .message("Artist successfully updated")
                .data(toResponse(saved))
                .build();
    }

    public ResponseApi<ArtistResponse> getArtistById(Integer id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + id));

        return ResponseApi.<ArtistResponse>builder()
                .success(true)
                .message("Artist found")
                .data(toResponse(artist))
                .build();
    }

    public ResponseApi<Page<ArtistResponse>> getAllArtists(Pageable pageable) {
        Page<ArtistResponse> artists = artistRepository.findAll(pageable).map(this::toResponse);
        return ResponseApi.<Page<ArtistResponse>>builder()
                .success(true)
                .message("Artists fetched")
                .data(artists)
                .build();
    }

    @Transactional
    public ResponseApi<Void> deleteArtist(Integer id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + id));

        if (artist.getImage() != null) {
            imageStorageService.deletePhysicalFile(artist.getImage());
        }

        artistRepository.delete(artist);
        return ResponseApi.<Void>builder()
                .success(true)
                .message("Artist successfully deleted")
                .build();
    }

    private ArtistResponse toResponse(Artist artist) {
        return ArtistResponse.builder()
                .id(artist.getId())
                .name(artist.getName())
                .countOfTrack(artist.getCountOfTrack())
                .genre(artist.getGenre())
                .voteCount(artist.getVoteCount())
                .averageRating(artist.getAverageRating())
                .image(artist.getImage() != null ? imageStorageService.toResponse(artist.getImage()) : null)
                .build();
    }
}
