package com.stellarideas.grooves.service;

import com.stellarideas.grooves.model.Genre;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MusicCatalogService {

    private final Map<String, Set<Genre>> bandGenreMap = new HashMap<>();

    public MusicCatalogService() {
        initializeData();
    }

    private void initializeData() {
        // ── 1960s ──────────────────────────────────────────────
        addBand("The Beatles",          Genre.CLASSIC_ROCK);
        addBand("The Rolling Stones",   Genre.CLASSIC_ROCK);
        addBand("The Who",              Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("The Doors",            Genre.CLASSIC_ROCK);
        addBand("Cream",                Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Jimi Hendrix",         Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Jefferson Airplane",   Genre.CLASSIC_ROCK);
        addBand("The Kinks",            Genre.CLASSIC_ROCK);
        addBand("Creedence Clearwater Revival", Genre.CLASSIC_ROCK);
        addBand("CCR",                  Genre.CLASSIC_ROCK);
        addBand("Steppenwolf",          Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Blue Cheer",           Genre.HARD_ROCK, Genre.HEAVY_METAL);

        // ── 1970s ──────────────────────────────────────────────
        addBand("Led Zeppelin",         Genre.CLASSIC_ROCK, Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Pink Floyd",           Genre.CLASSIC_ROCK);
        addBand("Fleetwood Mac",        Genre.CLASSIC_ROCK);
        addBand("Deep Purple",          Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("AC/DC",                Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Black Sabbath",        Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Judas Priest",         Genre.HEAVY_METAL);
        addBand("Aerosmith",            Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Boston",               Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Journey",              Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Kansas",               Genre.CLASSIC_ROCK);
        addBand("Rush",                 Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Thin Lizzy",           Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Rainbow",              Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("UFO",                  Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Scorpions",            Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Queen",                Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Heart",                Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Bad Company",          Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Foreigner",            Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("REO Speedwagon",       Genre.CLASSIC_ROCK);
        addBand("Cheap Trick",          Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Van Halen",            Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Ted Nugent",           Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Blue Öyster Cult",     Genre.HARD_ROCK, Genre.CLASSIC_ROCK);

        // ── 1980s ──────────────────────────────────────────────
        addBand("Iron Maiden",          Genre.HEAVY_METAL);
        addBand("Ozzy Osbourne",        Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Dio",                  Genre.HEAVY_METAL);
        addBand("Saxon",                Genre.HEAVY_METAL);
        addBand("Motörhead",            Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Whitesnake",           Genre.HARD_ROCK, Genre.HAIR_METAL);
        addBand("Def Leppard",          Genre.HARD_ROCK, Genre.HAIR_METAL);
        addBand("Guns N' Roses",        Genre.HARD_ROCK, Genre.HAIR_METAL);
        addBand("Bon Jovi",             Genre.HAIR_METAL, Genre.HARD_ROCK);
        addBand("Mötley Crüe",          Genre.HAIR_METAL, Genre.HARD_ROCK);
        addBand("Poison",               Genre.HAIR_METAL);
        addBand("Warrant",              Genre.HAIR_METAL);
        addBand("Ratt",                 Genre.HAIR_METAL, Genre.HARD_ROCK);
        addBand("Cinderella",           Genre.HAIR_METAL, Genre.HARD_ROCK);
        addBand("Winger",               Genre.HAIR_METAL);
        addBand("Dokken",               Genre.HAIR_METAL, Genre.HARD_ROCK);
        addBand("Skid Row",             Genre.HAIR_METAL, Genre.HARD_ROCK);
        addBand("Tesla",                Genre.HARD_ROCK, Genre.HAIR_METAL);
        addBand("Metallica",            Genre.THRASH_METAL, Genre.HEAVY_METAL);
        addBand("Slayer",               Genre.THRASH_METAL);
        addBand("Megadeth",             Genre.THRASH_METAL, Genre.HEAVY_METAL);
        addBand("Anthrax",              Genre.THRASH_METAL, Genre.HEAVY_METAL);
        addBand("Testament",            Genre.THRASH_METAL, Genre.HEAVY_METAL);
        addBand("Exodus",               Genre.THRASH_METAL);
        addBand("Overkill",             Genre.THRASH_METAL);
        addBand("Kreator",              Genre.THRASH_METAL);
        addBand("Sodom",                Genre.THRASH_METAL);
        addBand("Destruction",          Genre.THRASH_METAL);
        addBand("Pantera",              Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Alice In Chains",      Genre.HARD_ROCK, Genre.HEAVY_METAL);

        // ── 1990s ──────────────────────────────────────────────
        addBand("Soundgarden",          Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Stone Temple Pilots",  Genre.HARD_ROCK, Genre.CLASSIC_ROCK);
        addBand("Rage Against the Machine", Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Tool",                 Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Marilyn Manson",       Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Rob Zombie",           Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Nine Inch Nails",      Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Type O Negative",      Genre.HEAVY_METAL);
        addBand("Sepultura",            Genre.THRASH_METAL, Genre.HEAVY_METAL);
        addBand("Machine Head",         Genre.THRASH_METAL, Genre.HEAVY_METAL);
        addBand("White Zombie",         Genre.HEAVY_METAL, Genre.HARD_ROCK);

        // ── 2000s–2020s ────────────────────────────────────────
        addBand("System of a Down",     Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Slipknot",             Genre.HEAVY_METAL);
        addBand("Disturbed",            Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Avenged Sevenfold",    Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Trivium",              Genre.HEAVY_METAL, Genre.THRASH_METAL);
        addBand("Lamb of God",          Genre.HEAVY_METAL);
        addBand("Mastodon",             Genre.HEAVY_METAL);
        addBand("Ghost",                Genre.HEAVY_METAL, Genre.HARD_ROCK);
        addBand("Greta Van Fleet",      Genre.CLASSIC_ROCK, Genre.HARD_ROCK);
        addBand("Volbeat",              Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Halestorm",            Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Shinedown",            Genre.HARD_ROCK);
        addBand("Alter Bridge",         Genre.HARD_ROCK, Genre.HEAVY_METAL);
        addBand("Five Finger Death Punch", Genre.HEAVY_METAL, Genre.HARD_ROCK);
    }

    private void addBand(String name, Genre... genres) {
        bandGenreMap.put(name.toLowerCase(), new LinkedHashSet<>(Arrays.asList(genres)));
    }

    public Set<Genre> identifyGenres(String artistName) {
        if (artistName == null || artistName.isBlank()) return Collections.emptySet();
        return bandGenreMap.getOrDefault(artistName.toLowerCase(), Collections.emptySet());
    }

    public boolean isKnownArtist(String artistName) {
        if (artistName == null || artistName.isBlank()) return false;
        return bandGenreMap.containsKey(artistName.toLowerCase());
    }
}
