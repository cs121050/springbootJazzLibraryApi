package com.nicosarr.jazzLibraryAPI.Song;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class SongRep {

    @PersistenceContext
    private EntityManager entityManager;

    // Retrieve all songs (basic DTO)
    public List<SongDTO> retrieveAll() {
        String jpql = "SELECT s FROM Song s " +
                      "JOIN FETCH s.mainArtist " +
                      "LEFT JOIN FETCH s.album " +
                      "ORDER BY s.songId";
        TypedQuery<Song> query = entityManager.createQuery(jpql, Song.class);
        List<Song> songs = query.getResultList();
        return songs.stream().map(SongDTO::fromEntity).collect(Collectors.toList());
    }

    // Retrieve all songs with full main artist details
    public List<SongWithArtistDTO> retrieveAllWithMainArtist() {
        String jpql = "SELECT s FROM Song s JOIN FETCH s.mainArtist ORDER BY s.songId";
        TypedQuery<Song> query = entityManager.createQuery(jpql, Song.class);
        List<Song> songs = query.getResultList();
        return songs.stream().map(SongWithArtistDTO::fromEntity).collect(Collectors.toList());
    }

    // Find songs by album ID
    public List<SongDTO> findByAlbumId(int albumId) {
        String jpql = "SELECT s FROM Song s WHERE s.album.album_id = :albumId ORDER BY s.songId";
        TypedQuery<Song> query = entityManager.createQuery(jpql, Song.class);
        query.setParameter("albumId", albumId);
        return query.getResultList().stream().map(SongDTO::fromEntity).collect(Collectors.toList());
    }

    // Find songs by main artist ID
    public List<SongDTO> findByMainArtistId(int artistId) {
        String jpql = "SELECT s FROM Song s WHERE s.mainArtist.artist_id = :artistId ORDER BY s.songId";
        TypedQuery<Song> query = entityManager.createQuery(jpql, Song.class);
        query.setParameter("artistId", artistId);
        return query.getResultList().stream().map(SongDTO::fromEntity).collect(Collectors.toList());
    }
}