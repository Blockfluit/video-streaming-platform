package nl.nielsvanbruggen.videostreamingplatform.media.service;

import com.sun.jdi.InternalException;
import lombok.RequiredArgsConstructor;
import nl.nielsvanbruggen.videostreamingplatform.actor.model.Actor;
import nl.nielsvanbruggen.videostreamingplatform.actor.model.MediaActor;
import nl.nielsvanbruggen.videostreamingplatform.actor.repository.ActorRepository;
import nl.nielsvanbruggen.videostreamingplatform.actor.repository.MediaActorRepository;
import nl.nielsvanbruggen.videostreamingplatform.genre.Genre;
import nl.nielsvanbruggen.videostreamingplatform.genre.GenreRepository;
import nl.nielsvanbruggen.videostreamingplatform.genre.MediaGenre;
import nl.nielsvanbruggen.videostreamingplatform.genre.MediaGenreRepository;
import nl.nielsvanbruggen.videostreamingplatform.global.service.VideoService;
import nl.nielsvanbruggen.videostreamingplatform.media.controller.*;
import nl.nielsvanbruggen.videostreamingplatform.media.dto.*;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Media;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Rating;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Review;
import nl.nielsvanbruggen.videostreamingplatform.media.repository.*;
import nl.nielsvanbruggen.videostreamingplatform.global.service.ImageService;
import nl.nielsvanbruggen.videostreamingplatform.user.model.Role;
import nl.nielsvanbruggen.videostreamingplatform.user.model.User;
import nl.nielsvanbruggen.videostreamingplatform.user.repository.UserRepository;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaService {
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final GenreRepository genreRepository;
    private final ActorRepository actorRepository;
    private final ReviewRepository reviewRepository;
    private final MediaGenreRepository mediaGenreRepository;
    private final MediaActorRepository mediaActorRepository;
    private final MediaDTOGeneralMapper mediaDTOGeneralMapper;
    private final MediaDTOSpecificMapper mediaDTOSpecificMapper;
    private final VideoService videoService;
    private final ImageService imageService;
    @Value("${env.videos.root}")
    private String videosRoot;
    @Value("${env.ffprobe.path}")
    private String ffprobePath;
    private static List<MediaDTOGeneral> allMedia = new ArrayList<>();

    public List<?> getMedia(Long id) {
        if(id != null) {
            return List.of(mediaRepository.findById(id)
                    .map(mediaDTOSpecificMapper)
                    .orElseThrow(() -> new IllegalArgumentException("Id does not exist.")));
        }

        if(allMedia.isEmpty()) {
            updateAllMedia();
        }
        return allMedia;
    }

    public void postRating(Long id, RatingPostRequest request, Authentication authentication) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));

        Rating rating = Rating.builder()
                .media(media)
                .user(user)
                .score(request.getRating())
                .build();

        ratingRepository.save(rating);
        updateAllMedia();
    }

    public void postReview(Long id, ReviewPostRequest request, Authentication authentication) {
        if(!(authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.CRITIC.name())) ||
                authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.ADMIN.name())))) {
            throw new IllegalArgumentException("Insufficient permission");
        }

        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));

        Review review = Review.builder()
                .media(media)
                .comment(request.getComment())
                .user(user)
                .updatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        reviewRepository.save(review);
        updateAllMedia();
    }

    public void patchReview(Long id, ReviewPatchRequest request, Authentication authentication) {
        if(!authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.CRITIC.name())) &&
                !authentication.getAuthorities().contains(new SimpleGrantedAuthority(Role.ADMIN.name()))) {
            throw new IllegalArgumentException("Insufficient permission");
        }

        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));

        Review review = reviewRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Review does not exist"));

        if(!user.equals(review.getUser())) {
            throw new IllegalArgumentException("Insufficient permission");
        }
        review.setUpdatedAt(Instant.now());
        review.setComment(request.getComment());

        reviewRepository.save(review);
        updateAllMedia();
    }

    public void patchMedia(Long id, MediaPatchRequest request) {
        // TODO: add logic for updating media
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));

        if(request.getTrailer() != null) media.setTrailer(request.getTrailer());
        if(request.getPlot() != null) media.setPlot(request.getPlot());
        if(request.getType() != null) media.setType(request.getType());
        if(request.getYear() != null) media.setYear(request.getYear());
        if(request.getGenres() != null) {
            List<Genre> genres = genreRepository.findAllById(request.getGenres());
            if (genres.size() < request.getGenres().size()) {
                throw new IllegalArgumentException("Not all genres exist in the database.");
            }
        }
        if(request.getActors() != null) {
            List<Actor> actors = actorRepository.findAllById(request.getActors());
            if (actors.size() < request.getActors().size()) {
                throw new IllegalArgumentException("Not all actors exist in the database.");
            }
        }
        if(request.getThumbnail() != null) {
            String imageName = media.getName() + "_" + media.getYear() + ".jpg";
            try {
                imageService.saveImage(request.getThumbnail().getInputStream(), imageName);
            } catch (IOException ex) {
                throw new InternalException("Saving thumbnail went wrong.");
            }
        }

        if(request.isUpdateFiles()) {
            videoService.updateVideos(media);
        }

        mediaRepository.save(media);
        updateAllMedia();
    }

    public void postMedia(MediaPostRequest request) {
        if(request.getThumbnail() == null) {
            throw new IllegalArgumentException("No thumbnail provided.");
        }
        if(!List.of("png", "jpg", "jpeg").contains(FilenameUtils.getExtension(request.getThumbnail().getOriginalFilename()))) {
            throw new IllegalArgumentException("Invalid file extension.");
        }

        if(mediaRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("Media already exists.");
        }

        List<Genre> genres = genreRepository.findAllById(request.getGenres());
        if(genres.size() < request.getGenres().size()) {
            throw new IllegalArgumentException("Not all genres exist in the database.");
        }

        List<Actor> actors = actorRepository.findAllById(request.getActors());
        if (actors.size() < request.getActors().size()) {
            throw new IllegalArgumentException("Not all actors exist in the database.");
        }

        String imageName = request.getName() + "_" + request.getYear() + ".jpg";
        try {
            imageService.saveImage(request.getThumbnail().getInputStream(), imageName);
        } catch (IOException ex) {
            throw new InternalException("Saving thumbnail went wrong.");
        }

        Media media = Media.builder()
                .name(request.getName())
                .trailer(request.getTrailer())
                .thumbnail(imageName)
                .plot(request.getPlot())
                .type(request.getType())
                .year(request.getYear())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        mediaRepository.save(media);
        videoService.updateVideos(media);
        genres.forEach(genre -> mediaGenreRepository.save(new MediaGenre(media, genre)));
        actors.forEach(actor -> mediaActorRepository.save(new MediaActor(media, actor)));
        updateAllMedia();
    }

    public void deleteMedia(Long id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));

        mediaRepository.delete(media);
    }

    public void updateAllMedia() {
        allMedia = mediaRepository.findAll().stream()
                .map(mediaDTOGeneralMapper)
                .collect(Collectors.toList());
    }
}
