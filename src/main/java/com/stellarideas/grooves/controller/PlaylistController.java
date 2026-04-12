package com.stellarideas.grooves.controller;

import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.Playlist;
import com.stellarideas.grooves.model.User;
import com.stellarideas.grooves.repository.MusicFileRepository;
import com.stellarideas.grooves.repository.PlaylistRepository;
import com.stellarideas.grooves.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistController.class);

    private final PlaylistRepository playlistRepository;
    private final MusicFileRepository musicFileRepository;

    public PlaylistController(UserRepository userRepository, PlaylistRepository playlistRepository,
                              MusicFileRepository musicFileRepository) {
        super(userRepository);
        this.playlistRepository = playlistRepository;
        this.musicFileRepository = musicFileRepository;
    }

    @GetMapping
    public List<Map<String, Object>> getPlaylists() {
        User user = getCurrentUser();
        return playlistRepository.findByUser(user).stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("trackCount", p.getTrackIds().size());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Playlist name is required"));
        }
        if (name.trim().length() > 80) {
            return ResponseEntity.badRequest().body(Map.of("error", "Playlist name must be 80 characters or less"));
        }
        User user = getCurrentUser();
        Playlist playlist = new Playlist();
        playlist.setName(name.trim());
        playlist.setUser(user);
        Playlist saved = playlistRepository.save(playlist);
        logger.info("User '{}' created playlist '{}'", user.getUsername(), saved.getName());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("name", saved.getName());
        result.put("trackCount", 0);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@PathVariable String id) {
        User user = getCurrentUser();
        Optional<Playlist> opt = playlistRepository.findByIdAndUser(id, user);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        playlistRepository.delete(opt.get());
        logger.info("User '{}' deleted playlist '{}'", user.getUsername(), opt.get().getName());
        return ResponseEntity.ok(Map.of("message", "Playlist deleted"));
    }

    @PostMapping("/{id}/tracks")
    public ResponseEntity<?> addTrack(@PathVariable String id, @RequestBody Map<String, String> body) {
        User user = getCurrentUser();
        String fileId = body.get("fileId");
        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId is required"));
        }
        Optional<Playlist> opt = playlistRepository.findByIdAndUser(id, user);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (musicFileRepository.findByIdAndUser(fileId, user).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Playlist playlist = opt.get();
        if (!playlist.getTrackIds().contains(fileId)) {
            playlist.getTrackIds().add(fileId);
            playlistRepository.save(playlist);
        }
        return ResponseEntity.ok(Map.of("message", "Track added", "trackCount", playlist.getTrackIds().size()));
    }

    @DeleteMapping("/{id}/tracks/{fileId}")
    public ResponseEntity<?> removeTrack(@PathVariable String id, @PathVariable String fileId) {
        User user = getCurrentUser();
        Optional<Playlist> opt = playlistRepository.findByIdAndUser(id, user);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist playlist = opt.get();
        playlist.getTrackIds().remove(fileId);
        playlistRepository.save(playlist);
        return ResponseEntity.ok(Map.of("message", "Track removed", "trackCount", playlist.getTrackIds().size()));
    }

    @GetMapping("/{id}/tracks")
    public ResponseEntity<?> getPlaylistTracks(@PathVariable String id) {
        User user = getCurrentUser();
        Optional<Playlist> opt = playlistRepository.findByIdAndUser(id, user);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist playlist = opt.get();
        List<String> trackIds = playlist.getTrackIds();
        if (trackIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        // Single query instead of N+1
        Map<String, MusicFile> byId = musicFileRepository.findByIdInAndUser(trackIds, user).stream()
                .collect(Collectors.toMap(MusicFile::getId, f -> f));
        // Preserve playlist ordering, skip deleted tracks
        List<MusicFile> tracks = trackIds.stream()
                .filter(byId::containsKey)
                .map(byId::get)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tracks);
    }

}
