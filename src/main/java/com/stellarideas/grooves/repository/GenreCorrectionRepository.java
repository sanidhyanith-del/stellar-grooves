package com.stellarideas.grooves.repository;

import com.stellarideas.grooves.model.GenreCorrection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface GenreCorrectionRepository extends MongoRepository<GenreCorrection, String> {
    Optional<GenreCorrection> findByArtistLower(String artistLower);
}
