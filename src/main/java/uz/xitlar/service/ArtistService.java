package uz.xitlar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.xitlar.dto.artist.ArtistCreateDto;
import uz.xitlar.dto.artist.ArtistResponse;
import uz.xitlar.dto.artist.ArtistUpdateDto;
import uz.xitlar.dto.artist.ArtistVoteDto;
import uz.xitlar.dto.common.ResponseApi;
import uz.xitlar.entity.Artist;
import uz.xitlar.entity.ArtistVote;
import uz.xitlar.entity.Image;
import uz.xitlar.entity.User;
import uz.xitlar.exception.DataNotFoundException;
import uz.xitlar.exception.DuplicateEntityException;
import uz.xitlar.repository.ArtistRepository;
import uz.xitlar.repository.ArtistVoteRepository;
import uz.xitlar.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final ImageStorageService imageStorageService;
    private final ArtistVoteRepository artistVoteRepository;
    private final UserRepository userRepository;

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

    @Transactional
    public ResponseApi<ArtistResponse> voteArtist(Integer artistId, ArtistVoteDto dto, UserDetails principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated");
        }

        User user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new DataNotFoundException("User not found: " + principal.getUsername()));

        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new DataNotFoundException("Artist not found with ID: " + artistId));

        Optional<ArtistVote> existingVote = artistVoteRepository.findByUserIdAndArtistId(user.getId(), artistId);

        if (existingVote.isPresent()) {
            // Update existing vote
            ArtistVote vote = existingVote.get();
            vote.setRating(dto.getRating());
            artistVoteRepository.save(vote);
        } else {
            // Create new vote
            ArtistVote vote = ArtistVote.builder()
                    .user(user)
                    .artist(artist)
                    .rating(dto.getRating())
                    .build();
            artistVoteRepository.save(vote);
        }

        // Recalculate artist stats
        int voteCount = artistVoteRepository.countByArtistId(artistId);
        double averageRating = artistVoteRepository.averageRatingByArtistId(artistId);

        artist.setVoteCount(voteCount);
        artist.setAverageRating(Math.round(averageRating * 10.0) / 10.0); // round to 1 decimal
        artistRepository.save(artist);

        return ResponseApi.<ArtistResponse>builder()
                .success(true)
                .message("Vote successfully recorded")
                .data(toResponse(artist))
                .build();
    }

    private ArtistResponse toResponse(Artist artist) {
        Integer userRating = null;

        // Get current user's rating if authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            Object principalObj = authentication.getPrincipal();
            String username = null;
            if (principalObj instanceof UserDetails userDetails) {
                username = userDetails.getUsername();
            } else if (principalObj instanceof User u) {
                username = u.getUsername();
            }
            if (username != null) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    Optional<ArtistVote> vote = artistVoteRepository.findByUserIdAndArtistId(userOpt.get().getId(), artist.getId());
                    if (vote.isPresent()) {
                        userRating = vote.get().getRating();
                    }
                }
            }
        }

        return ArtistResponse.builder()
                .id(artist.getId())
                .name(artist.getName())
                .countOfTrack(artist.getCountOfTrack())
                .genre(artist.getGenre())
                .voteCount(artist.getVoteCount())
                .averageRating(artist.getAverageRating())
                .image(artist.getImage() != null ? imageStorageService.toResponse(artist.getImage()) : null)
                .userRating(userRating)
                .build();
    }
}
