package nl.nielsvanbruggen.videostreamingplatform.media.service;

import com.sun.jdi.InternalException;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import nl.nielsvanbruggen.videostreamingplatform.media.dto.MediaDTO;
import nl.nielsvanbruggen.videostreamingplatform.media.dto.MediaDTOSimplifiedMapper;
import nl.nielsvanbruggen.videostreamingplatform.watched.WatchedRepository;
import nl.nielsvanbruggen.videostreamingplatform.actor.model.Actor;
import nl.nielsvanbruggen.videostreamingplatform.actor.model.MediaActor;
import nl.nielsvanbruggen.videostreamingplatform.actor.repository.ActorRepository;
import nl.nielsvanbruggen.videostreamingplatform.actor.repository.MediaActorRepository;
import nl.nielsvanbruggen.videostreamingplatform.genre.Genre;
import nl.nielsvanbruggen.videostreamingplatform.genre.GenreRepository;
import nl.nielsvanbruggen.videostreamingplatform.genre.MediaGenre;
import nl.nielsvanbruggen.videostreamingplatform.genre.MediaGenreRepository;
import nl.nielsvanbruggen.videostreamingplatform.media.controller.*;
import nl.nielsvanbruggen.videostreamingplatform.media.dto.MediaDTOMapper;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Media;
import nl.nielsvanbruggen.videostreamingplatform.global.service.VideoService;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Rating;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Review;
import nl.nielsvanbruggen.videostreamingplatform.media.model.Video;
import nl.nielsvanbruggen.videostreamingplatform.global.service.ImageService;
import nl.nielsvanbruggen.videostreamingplatform.media.repository.*;
import nl.nielsvanbruggen.videostreamingplatform.user.model.Role;
import nl.nielsvanbruggen.videostreamingplatform.user.model.User;
import nl.nielsvanbruggen.videostreamingplatform.user.repository.UserRepository;
import org.apache.commons.io.FilenameUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableCaching
public class MediaService {
    private final VideoRepository videoRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final GenreRepository genreRepository;
    private final ActorRepository actorRepository;
    private final ReviewRepository reviewRepository;
    private final WatchedRepository watchedRepository;
    private final SubtitleRepository subtitleRepository;
    private final MediaGenreRepository mediaGenreRepository;
    private final MediaActorRepository mediaActorRepository;
    private final MediaDTOMapper mediaDTOMapper;
    private final MediaDTOSimplifiedMapper mediaDTOSimplifiedMapper;
    private final VideoService videoService;
    private final ImageService imageService;

    //TODO: Probably does not work correctly.
    @PostConstruct
    public void initAllMedia() {
        boolean doLoop = true;
        int pageNumber = 0;
        long end;

        while (doLoop) {
            end = getAllMedia(pageNumber, 30, "", false).getTotalPages();
            pageNumber++;
            if (pageNumber > end) doLoop = false;
        }
    }

    @Cacheable(value = "media", key = "#id")
    public MediaDTO getMedia(int id) {
        return mediaRepository.findById((long) id)
                .map(mediaDTOMapper)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));
    }

    @Cacheable(value = "allMedia")
    public Page<MediaDTO> getAllMedia(int pageNumber, int pageSize, String search, boolean complete) {
        if(complete) {
            return mediaRepository.findAllByPartialName(search, PageRequest.of(pageNumber, pageSize))
                    .map(mediaDTOMapper);
        }
        return mediaRepository.findAllByPartialName(search, PageRequest.of(pageNumber, pageSize))
                .map(mediaDTOSimplifiedMapper);
    }

    @Cacheable(value = "bestRatedMedia")
    public Page<MediaDTO> getBestRated(int pageNumber, int pageSize) {
        return mediaRepository.findAllBestRated(PageRequest.of(pageNumber, pageSize))
                .map(mediaDTOSimplifiedMapper);
    }

    @Cacheable(value = "MostWatchedMedia")
    public Page<MediaDTO> getMostWatched(int pageNumber, int pageSize) {
        return mediaRepository.findAllMostWatched(PageRequest.of(pageNumber, pageSize))
                .map(mediaDTOSimplifiedMapper);
    }

    @Cacheable(value = "LastWatchedMedia")
    public Page<MediaDTO> getLastWatched(int pageNumber, int pageSize) {
         return mediaRepository.findAllLastWatched(PageRequest.of(pageNumber, pageSize))
                 .map(mediaDTOSimplifiedMapper);
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
                .title(request.getTitle())
                .comment(request.getComment())
                .user(user)
                .updatedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        reviewRepository.save(review);
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
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());

        reviewRepository.save(review);
    }

    public void deleteReview(Long id, ReviewDeleteRequest request, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));

        Review review = reviewRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Review does not exist"));

        if(!user.getAuthorities().contains(new SimpleGrantedAuthority(Role.ADMIN.name())) &&
                !review.getUser().equals(user)) {
            throw new IllegalArgumentException("Insufficient permission");
        }

        reviewRepository.delete(review);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allMedia"),
            @CacheEvict(value = "media", key = "#id")
    })
    public void patchMedia(Long id, MediaPatchRequest request) {
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
            List<MediaGenre> mediaGenres = genres.stream()
                    .map(genre -> new MediaGenre(media, genre))
                    .toList();

            mediaGenreRepository.deleteByMedia(media);
            mediaGenreRepository.saveAll(mediaGenres);
        }
        if(request.getActors() != null) {
            List<Actor> actors = actorRepository.findAllById(request.getActors());
            if (actors.size() < request.getActors().size()) {
                throw new IllegalArgumentException("Not all actors exist in the database.");
            }
            List<MediaActor> mediaActors = actorRepository.findAllById(request.getActors()).stream()
                    .map(actor -> new MediaActor(media, actor))
                    .toList();

            mediaActorRepository.deleteByMedia(media);
            mediaActorRepository.saveAll(mediaActors);
        }
        if(request.getThumbnail() != null) {
            String imageName = media.getName() + "_" + media.getYear() + ".jpg";
            try {
                imageService.saveImage(request.getThumbnail().getInputStream(), imageName);
            } catch (IOException ex) {
                throw new InternalException("Saving thumbnail went wrong.");
            }
        }
        if(request.getOrder() != null) {
            request.getOrder()
                    .forEach(entry -> {
                        Video video = videoRepository.findById(entry.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Video does not exist."));
                        video.setIndex(entry.getIndex());
                        videoRepository.save(video);
            });
        }

        if(request.isUpdateFiles()) {
            try {
                videoService.updateVideos(media);
            } catch (IOException ex) {
                throw new InternalException(ex.getMessage());
            }
        }

        media.setUpdatedAt(Instant.now());
        mediaRepository.save(media);
    }


    @CacheEvict(value = "allMedia")
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
        try {
            videoService.updateVideos(media);
        } catch (IOException ex) {
            mediaRepository.delete(media);
          throw new InternalException(ex.getMessage());
        }
        genres.forEach(genre -> mediaGenreRepository.save(new MediaGenre(media, genre)));
        actors.forEach(actor -> mediaActorRepository.save(new MediaActor(media, actor)));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allMedia"),
            @CacheEvict(value = "media", key = "#id")
    })
    public void deleteMedia(Long id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Id does not exist."));

        List<Video> videos = videoRepository.findAllByMedia(media);

        watchedRepository.deleteByVideoIn(videos);
        subtitleRepository.deleteByVideoIn(videos);
        ratingRepository.deleteByMedia(media);
        reviewRepository.deleteByMedia(media);
        mediaGenreRepository.deleteByMedia(media);
        mediaActorRepository.deleteByMedia(media);

        videoRepository.deleteAll(videos);
        mediaRepository.delete(media);
    }
}
