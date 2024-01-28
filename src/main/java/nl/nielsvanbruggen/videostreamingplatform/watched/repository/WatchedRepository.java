package nl.nielsvanbruggen.videostreamingplatform.watched.repository;

import nl.nielsvanbruggen.videostreamingplatform.media.model.Media;
import nl.nielsvanbruggen.videostreamingplatform.video.model.Video;
import nl.nielsvanbruggen.videostreamingplatform.user.model.User;
import nl.nielsvanbruggen.videostreamingplatform.watched.model.Watched;
import nl.nielsvanbruggen.videostreamingplatform.watched.id.WatchedId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchedRepository extends JpaRepository<Watched, WatchedId> {
    List<Watched> findAllByUser(User user);

    Optional<Watched> findByUserAndVideo(User user, Video video);

    @Query("SELECT COUNT(DISTINCT user.id) " +
            "FROM Watched w " +
            "INNER JOIN Video v ON w.video.id = v.id " +
            "INNER JOIN Media m ON v.media.id = m.id " +
            "WHERE m = :media")
    int totalUniqueViewsByMedia(@Param("media") Media media);

    @Query("SELECT w " +
            "FROM Watched w " +
            "WHERE w.user = :user " +
            "ORDER BY w.updatedAt DESC")
    List<Watched> findLastWatchedByUser(@Param("user") User user, Pageable pageable);

    List<Watched> deleteByVideoIn(List<Video> videos);

    @Query("SELECT v.media, MAX(w.updatedAt) " +
            "FROM Watched w " +
            "INNER JOIN Video v ON w.video = v " +
            "WHERE w.user = :user " +
            "GROUP BY v.media " +
            "ORDER BY MAX(w.updatedAt) DESC")
    List<Media> findAllMediaByUser(@Param("user") User user);

    @Query("SELECT COUNT(DISTINCT v.media) " +
            "FROM Watched w " +
            "INNER JOIN Video v ON w.video = v " +
            "WHERE w.user = :user")
    int countAllNotWatchedByUser(User user);
}
