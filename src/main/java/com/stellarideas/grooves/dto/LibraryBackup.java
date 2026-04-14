package com.stellarideas.grooves.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full library backup structure containing tracks, playlists, and metadata.
 * Does NOT include audio files — only metadata.
 */
public class LibraryBackup {

    private String version = "1.0";
    private Instant exportedAt;
    private String username;
    private List<TrackBackup> tracks;
    private List<PlaylistBackup> playlists;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Instant getExportedAt() { return exportedAt; }
    public void setExportedAt(Instant exportedAt) { this.exportedAt = exportedAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public List<TrackBackup> getTracks() { return tracks; }
    public void setTracks(List<TrackBackup> tracks) { this.tracks = tracks; }

    public List<PlaylistBackup> getPlaylists() { return playlists; }
    public void setPlaylists(List<PlaylistBackup> playlists) { this.playlists = playlists; }

    public static class TrackBackup {
        private String fileName;
        private String filePath;
        private String artist;
        private String album;
        private String title;
        private String year;
        private String genre;
        private List<String> additionalGenres;
        private int rating;
        private String fileHash;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getArtist() { return artist; }
        public void setArtist(String artist) { this.artist = artist; }
        public String getAlbum() { return album; }
        public void setAlbum(String album) { this.album = album; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getYear() { return year; }
        public void setYear(String year) { this.year = year; }
        public String getGenre() { return genre; }
        public void setGenre(String genre) { this.genre = genre; }
        public List<String> getAdditionalGenres() { return additionalGenres; }
        public void setAdditionalGenres(List<String> additionalGenres) { this.additionalGenres = additionalGenres; }
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    }

    public static class PlaylistBackup {
        private String name;
        private List<String> trackFileNames;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getTrackFileNames() { return trackFileNames; }
        public void setTrackFileNames(List<String> trackFileNames) { this.trackFileNames = trackFileNames; }
    }
}
