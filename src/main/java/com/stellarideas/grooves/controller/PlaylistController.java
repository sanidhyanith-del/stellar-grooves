package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.dto.*;
import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.Playlist;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.security.CurrentUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/playlists")
public class PlaylistController {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistController.class);

    private final PlaylistRepository playlistRepository;
    private final MusicFileRepository musicFileRepository;
    private final MessageSource messageSource;

    public PlaylistController(PlaylistRepository playlistRepository,
                              MusicFileRepository musicFileRepository,
                              MessageSource messageSource) {
        this.playlistRepository = playlistRepository;
        this.musicFileRepository = musicFileRepository;
        this.messageSource = messageSource;
    }

    private String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, Locale.getDefault());
    }

    @GetMapping
    public List<PlaylistDTO> getPlaylists(@CurrentUser User user) {
        return playlistRepository.findByUserId(user.getId()).stream()
                .map(PlaylistDTO::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@CurrentUser User user, @Valid @RequestBody CreatePlaylistRequest body) {
        Playlist playlist = new Playlist();
        playlist.setName(body.getName().trim());
        playlist.setUserId(user.getId());
        Playlist saved = playlistRepository.save(playlist);
        logger.info("User '{}' created playlist '{}'", user.getUsername(), saved.getName());
        return ResponseEntity.ok(PlaylistDTO.from(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@CurrentUser User user, @PathVariable String id) {
        Optional<Playlist> opt = playlistRepository.findByIdAndUserId(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        playlistRepository.delete(opt.get());
        logger.info("User '{}' deleted playlist '{}'", user.getUsername(), opt.get().getName());
        return ResponseEntity.ok(Map.of("message", msg("playlist.deleted")));
    }

    @PostMapping("/{id}/tracks")
    public ResponseEntity<?> addTrack(@CurrentUser User user, @PathVariable String id,
                                      @Valid @RequestBody AddTrackRequest body) {
        String fileId = body.getFileId();
        Optional<Playlist> opt = playlistRepository.findByIdAndUserId(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (musicFileRepository.findByIdAndUserId(fileId, user.getId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Playlist playlist = opt.get();
        if (!playlist.getTrackIds().contains(fileId)) {
            playlist.getTrackIds().add(fileId);
            playlistRepository.save(playlist);
        }
        return ResponseEntity.ok(Map.of("message", msg("playlist.track.added"), "trackCount", playlist.getTrackIds().size()));
    }

    @DeleteMapping("/{id}/tracks/{fileId}")
    public ResponseEntity<?> removeTrack(@CurrentUser User user, @PathVariable String id, @PathVariable String fileId) {
        Optional<Playlist> opt = playlistRepository.findByIdAndUserId(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist playlist = opt.get();
        playlist.getTrackIds().remove(fileId);
        playlistRepository.save(playlist);
        return ResponseEntity.ok(Map.of("message", msg("playlist.track.removed"), "trackCount", playlist.getTrackIds().size()));
    }

    @GetMapping("/{id}/tracks")
    public ResponseEntity<?> getPlaylistTracks(@CurrentUser User user, @PathVariable String id) {
        Optional<Playlist> opt = playlistRepository.findByIdAndUserId(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist playlist = opt.get();
        List<String> trackIds = playlist.getTrackIds();
        if (trackIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Map<String, MusicFile> byId = musicFileRepository.findByIdInAndUserId(trackIds, user.getId()).stream()
                .collect(Collectors.toMap(MusicFile::getId, f -> f));
        List<MusicFileDTO> tracks = trackIds.stream()
                .filter(byId::containsKey)
                .map(byId::get)
                .map(MusicFileDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tracks);
    }

    // ── Reorder tracks ──────────────────────────────────

    @PutMapping("/{id}/tracks/reorder")
    public ResponseEntity<?> reorderTracks(@CurrentUser User user, @PathVariable String id,
                                           @Valid @RequestBody ReorderTracksRequest request) {
        Optional<Playlist> opt = playlistRepository.findByIdAndUserId(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist playlist = opt.get();
        // Validate: new order must contain exactly the same track IDs
        Set<String> existing = new HashSet<>(playlist.getTrackIds());
        Set<String> incoming = new HashSet<>(request.getTrackIds());
        if (!existing.equals(incoming)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Track IDs must match existing playlist tracks"));
        }
        playlist.setTrackIds(new ArrayList<>(request.getTrackIds()));
        playlistRepository.save(playlist);
        return ResponseEntity.ok(Map.of("message", "Tracks reordered", "trackCount", playlist.getTrackIds().size()));
    }

    // ── Export ───────────────────────────────────────────

    @GetMapping("/{id}/export")
    public ResponseEntity<?> exportPlaylist(@CurrentUser User user, @PathVariable String id,
                                            @RequestParam(defaultValue = "json") String format) {
        Optional<Playlist> opt = playlistRepository.findByIdAndUserId(id, user.getId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist playlist = opt.get();

        List<String> trackIds = playlist.getTrackIds();
        Map<String, MusicFile> byId = trackIds.isEmpty() ? Map.of()
                : musicFileRepository.findByIdInAndUserId(trackIds, user.getId()).stream()
                        .collect(Collectors.toMap(MusicFile::getId, f -> f));
        List<MusicFile> orderedFiles = trackIds.stream()
                .filter(byId::containsKey)
                .map(byId::get)
                .collect(Collectors.toList());

        if ("m3u".equalsIgnoreCase(format)) {
            return exportAsM3u(playlist, orderedFiles);
        }
        return exportAsJson(playlist, orderedFiles);
    }

    private ResponseEntity<?> exportAsM3u(Playlist playlist, List<MusicFile> files) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        sb.append("#PLAYLIST:").append(playlist.getName()).append("\n");
        for (MusicFile f : files) {
            sb.append("#EXTINF:-1,");
            if (f.getArtist() != null && !f.getArtist().isBlank()) {
                sb.append(f.getArtist()).append(" - ");
            }
            sb.append(f.getTitle() != null ? f.getTitle() : f.getFileName());
            sb.append("\n");
            sb.append(f.getFilePath()).append("\n");
        }
        String filename = playlist.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".m3u";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("audio/x-mpegurl"))
                .body(sb.toString());
    }

    private ResponseEntity<?> exportAsJson(Playlist playlist, List<MusicFile> files) {
        List<Map<String, String>> tracks = files.stream().map(f -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("title", f.getTitle());
            m.put("artist", f.getArtist());
            m.put("album", f.getAlbum());
            m.put("year", f.getYear());
            m.put("fileName", f.getFileName());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", playlist.getName());
        body.put("trackCount", tracks.size());
        body.put("tracks", tracks);

        String filename = playlist.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
