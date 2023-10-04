package nl.nielsvanbruggen.videostreamingplatform.Watched;

import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class WatchedDTOMapper implements Function<Watched, WatchedDTO> {
    @Override
    public WatchedDTO apply(Watched watched) {
        return new WatchedDTO(
                watched.getVideo().getId(),
                watched.getVideo().getName(),
                watched.getVideo().getSeason(),
                watched.getTimestamp(),
                watched.getVideo().getMedia().getId(),
                watched.getUpdatedAt(),
                watched.getVideo().getDuration()
        );
    }
}
