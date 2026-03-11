package com.stellarideas.grooves.repository;

import com.stellarideas.grooves.model.Genre;
import com.stellarideas.grooves.model.MusicFile;
import com.stellarideas.grooves.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface MusicFileRepository extends MongoRepository<MusicFile, String> {
    List<MusicFile> findByUser(User user);
    List<MusicFile> findByUserAndGenre(User user, Genre genre);
    boolean existsByFilePathAndUser(String filePath, User user);
    boolean existsByTitleAndArtistAndUser(String title, String artist, User user);
    Optional<MusicFile> findByIdAndUser(String id, User user);
}
