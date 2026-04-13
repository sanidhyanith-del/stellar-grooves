package com.stellarideas.grooves.repository;

import com.stellarideas.grooves.model.Genre;
import com.stellarideas.grooves.model.MusicFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MusicFileRepository extends MongoRepository<MusicFile, String> {
    List<MusicFile> findByUserId(String userId);
    Page<MusicFile> findByUserId(String userId, Pageable pageable);
    Page<MusicFile> findByUserIdAndGenre(String userId, Genre genre, Pageable pageable);
    boolean existsByFilePathAndUserId(String filePath, String userId);
    boolean existsByTitleAndArtistAndUserId(String title, String artist, String userId);
    Optional<MusicFile> findByIdAndUserId(String id, String userId);
    List<MusicFile> findByIdInAndUserId(List<String> ids, String userId);
    long deleteByUserId(String userId);
    long countByUserId(String userId);
    List<MusicFile> findByUserIdAndFilePathIn(String userId, Set<String> filePaths);
}
