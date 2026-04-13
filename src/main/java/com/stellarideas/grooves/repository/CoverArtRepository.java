package com.stellarideas.grooves.repository;

import com.stellarideas.grooves.model.CoverArt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CoverArtRepository extends MongoRepository<CoverArt, String> {
    Optional<CoverArt> findByUserIdAndArtistAndAlbum(String userId, String artist, String album);
    long deleteByUserId(String userId);
}
