package com.stellarideas.grooves.service;

import com.stellarideas.grooves.dto.MusicFileDTO;
import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.PlayEvent;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlayEventRepository;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class PlayHistoryService {

    private static final int DEFAULT_TOP_LIMIT = 25;
    private static final int MAX_TOP_LIMIT = 200;

    private final PlayEventRepository playEventRepository;
    private final MusicFileRepository musicFileRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public PlayHistoryService(PlayEventRepository playEventRepository,
                              MusicFileRepository musicFileRepository,
                              MongoTemplate mongoTemplate) {
        this(playEventRepository, musicFileRepository, mongoTemplate, Clock.systemUTC());
    }

    PlayHistoryService(PlayEventRepository playEventRepository,
                       MusicFileRepository musicFileRepository,
                       MongoTemplate mongoTemplate,
                       Clock clock) {
        this.playEventRepository = playEventRepository;
        this.musicFileRepository = musicFileRepository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    // ── Recording ───────────────────────────────────────────

    /**
     * Record a play event for a track the user owns. Inserts a PlayEvent row and
     * atomically increments playCount + sets lastPlayedAt on the MusicFile.
     *
     * @return true if recorded, false if the file doesn't exist or isn't owned by the user
     */
    public boolean recordPlay(String userId, String musicFileId, int listenedMs, boolean completed) {
        if (musicFileRepository.findByIdAndUserIdAndDeletedFalse(musicFileId, userId).isEmpty()) {
            return false;
        }
        Instant now = Instant.now(clock);
        playEventRepository.save(new PlayEvent(userId, musicFileId, now, Math.max(0, listenedMs), completed));

        Query query = new Query(Criteria.where("_id").is(musicFileId).and("userId").is(userId));
        Update update = new Update().inc("playCount", 1).set("lastPlayedAt", now);
        mongoTemplate.updateFirst(query, update, MusicFile.class);
        return true;
    }

    // ── Retrieval ───────────────────────────────────────────

    /**
     * Return recent play events (reverse-chronological) with the track hydrated inline.
     * Events whose track has been purged from the library are dropped from the response.
     */
    public Page<RecentPlay> getRecentPlays(String userId, Window window, int page, int size) {
        int clampedSize = Math.max(1, Math.min(size, 200));
        int clampedPage = Math.max(0, page);
        PageRequest pageable = PageRequest.of(clampedPage, clampedSize);

        Query query = new Query(userCriteria(userId, window))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "playedAt"))
                .with(pageable);
        List<PlayEvent> events = mongoTemplate.find(query, PlayEvent.class);
        long total = mongoTemplate.count(new Query(userCriteria(userId, window)), PlayEvent.class);

        List<String> fileIds = events.stream().map(PlayEvent::getMusicFileId).distinct().toList();
        Map<String, MusicFile> byId = fileIds.isEmpty()
                ? Map.of()
                : musicFileRepository.findByIdInAndUserId(fileIds, userId).stream()
                        .collect(java.util.stream.Collectors.toMap(MusicFile::getId, f -> f));

        List<RecentPlay> items = new ArrayList<>(events.size());
        for (PlayEvent ev : events) {
            MusicFile file = byId.get(ev.getMusicFileId());
            if (file == null) continue; // purged
            items.add(new RecentPlay(ev.getPlayedAt(), ev.getListenedMs(), ev.isCompleted(), MusicFileDTO.from(file)));
        }
        return new PageImpl<>(items, pageable, total);
    }

    /** Top N tracks by play-count within the window, hydrated with track metadata. */
    public List<TopTrack> getTopTracks(String userId, Window window, int limit) {
        int cap = clampLimit(limit);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(userCriteria(userId, window)),
                Aggregation.group("musicFileId").count().as("plays"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "plays"),
                Aggregation.limit(cap));
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, PlayEvent.class, Document.class);

        LinkedHashMap<String, Integer> order = new LinkedHashMap<>();
        for (Document d : results.getMappedResults()) {
            String id = d.getString("_id");
            if (id == null) continue;
            order.put(id, d.getInteger("plays", 0));
        }
        if (order.isEmpty()) return List.of();

        Map<String, MusicFile> byId = musicFileRepository.findByIdInAndUserId(new ArrayList<>(order.keySet()), userId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(MusicFile::getId, f -> f));

        List<TopTrack> out = new ArrayList<>(order.size());
        for (Map.Entry<String, Integer> e : order.entrySet()) {
            MusicFile file = byId.get(e.getKey());
            if (file == null) continue; // purged
            out.add(new TopTrack(MusicFileDTO.from(file), e.getValue()));
        }
        return out;
    }

    /**
     * Top N artists by play-count within the window. Requires joining {@link PlayEvent}
     * against {@link MusicFile} (the event stores a fileId; the artist is on the file).
     */
    public List<TopArtist> getTopArtists(String userId, Window window, int limit) {
        int cap = clampLimit(limit);
        // 1. Aggregate event counts by fileId for the window.
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(userCriteria(userId, window)),
                Aggregation.group("musicFileId").count().as("plays"));
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, PlayEvent.class, Document.class);

        Map<String, Integer> playsByFile = new LinkedHashMap<>();
        for (Document d : results.getMappedResults()) {
            String id = d.getString("_id");
            if (id == null) continue;
            playsByFile.put(id, d.getInteger("plays", 0));
        }
        if (playsByFile.isEmpty()) return List.of();

        // 2. Fold into a per-artist total by looking up each file.
        List<MusicFile> files = musicFileRepository.findByIdInAndUserId(new ArrayList<>(playsByFile.keySet()), userId);
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (MusicFile f : files) {
            String artist = f.getArtist();
            if (artist == null || artist.isBlank()) continue;
            totals.merge(artist, playsByFile.getOrDefault(f.getId(), 0), Integer::sum);
        }

        List<TopArtist> ranked = new ArrayList<>();
        totals.forEach((artist, plays) -> ranked.add(new TopArtist(artist, plays)));
        ranked.sort((a, b) -> Integer.compare(b.plays(), a.plays()));
        if (ranked.size() > cap) return ranked.subList(0, cap);
        return ranked;
    }

    // ── Internals ───────────────────────────────────────────

    private Criteria userCriteria(String userId, Window window) {
        Criteria c = Criteria.where("userId").is(userId);
        Optional<Instant> threshold = window.threshold(clock);
        threshold.ifPresent(t -> c.and("playedAt").gte(t));
        return c;
    }

    private static int clampLimit(int requested) {
        if (requested <= 0) return DEFAULT_TOP_LIMIT;
        return Math.min(requested, MAX_TOP_LIMIT);
    }

    // ── DTO-ish records ─────────────────────────────────────

    public record RecentPlay(Instant playedAt, int listenedMs, boolean completed, MusicFileDTO track) {}

    public record TopTrack(MusicFileDTO track, int plays) {}

    public record TopArtist(String artist, int plays) {}

    /** Supported time windows for history queries. */
    public enum Window {
        ALL, WEEK, MONTH, YEAR;

        Optional<Instant> threshold(Clock clock) {
            Instant now = Instant.now(clock);
            return switch (this) {
                case ALL   -> Optional.empty();
                case WEEK  -> Optional.of(now.minus(Duration.ofDays(7)));
                case MONTH -> Optional.of(now.minus(Duration.ofDays(30)));
                case YEAR  -> Optional.of(now.minus(Duration.ofDays(365)));
            };
        }

        public static Window parse(String raw) {
            if (raw == null || raw.isBlank()) return ALL;
            try {
                return Window.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ALL;
            }
        }
    }

}
