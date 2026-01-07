package com.nicosarr.jazzLibraryAPI.Artist;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;

@Repository
public class ArtistRep {

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional  // Initialize the lazy hibernate collection
    public List<Artist> retrieveAll() {
        String jpql = "SELECT a FROM Artist a";  // a is representation o an Artist Object
        Query query = entityManager.createQuery(jpql, Artist.class);
        List<Artist> artistList = query.getResultList();
        // Access videoContainsArtists here to initialize them, hibernation Lazy alongside many to many relationship , has bound artists with videocontainsartist
        for (Artist artist : artistList) {
            artist.getVideoContainsArtists().size();  // an mpei to sxoliasmeno ,,, auto den mpenei 

            
        //hibernation coralation code    
            List<Video> videoList = new ArrayList<>();

            // Initialize each VideoContainsArtist and its Video within the same session
            for (VideoContainsArtist vca : artist.getVideoContainsArtists()) {
                Video video = vca.getVideo();
                // Force initialization of videoContainsArtists for each Video
                video.getVideoContainsArtists().size();  // This ensures the collection is loaded
                videoList.add(video);
            }

            artist.setVideoList(videoList);  // Set the videos for the artist
        
        }
        
        
        
        
        
        
        return artistList;
    }
    
    
    
    public int create(Artist artist) {
    	String sql = "INSERT INTO Artist (artist_name, artist_surname, instrument_id, artist_video_count, artist_rank) VALUES (?, ?, ?, ?, ?)";
    	return jdbcTemplate.update(sql, artist.getArtist_name(), artist.getArtist_surname(), artist.getInstrument_id(),artist.getArtist_video_count(),artist.getArtist_rank());
    } 
    
    public int updateInstrumentId(int artistId,int instrumentId) {
    	String sql = "UPDATE artist SET instrument_id = ? WHERE artist_id = ?";
    	return jdbcTemplate.update(sql, instrumentId, artistId);
    	
    }  
    
    public int updateVideoCount(int artistId,int artistVideocount) {
    	String sql = "UPDATE artist SET artist_video_count = ? WHERE artist_id = ?";
    	return jdbcTemplate.update(sql, artistVideocount, artistId);
    	
    }    
    public int update(Artist artist) {
    	String sql = "UPDATE artist SET artist_name = ?, artist_surname = ?, instrument_id = ?, artist_video_count = ?, artist_rank = ? WHERE artist_id = ?";
    	return jdbcTemplate.update(sql, artist.getArtist_name(), artist.getArtist_surname(), artist.getInstrument_id(), artist.getArtist_video_count(),artist.getArtist_rank(),  artist.getArtist_id());
    	
    }       
    @Transactional                     // Initialize the lazy hibernate collection
    public List<Artist> retrieveArtistByName(String artistName, String artistSurname) {
            String jpql = "SELECT a FROM Artist a WHERE a.artist_name= :artistName and artist_surname= :artistSurname";  // a is representation o an Artist Object
            Query query = entityManager.createQuery(jpql, Artist.class);
            query.setParameter("artistName", artistName);
            query.setParameter("artistSurname", artistSurname);
            List<Artist> artistList = query.getResultList();
            // Access videoContainsArtists here to initialize them, hibernation Lazy alongside many to many relationship , has bound artists with videocontainsartist
            for (Artist artist : artistList) {
                artist.getVideoContainsArtists().size();  // Initialize the lazy hibernate collection  
            }
            return artistList;
    }  
    
    @Transactional                 // Initialize the lazy hibernate collection
    public Artist retrieveArtistByNameAndInstrumentId(String artistName, String artistSurname, int instrumentId) {
        String jpql = "SELECT a FROM Artist a WHERE a.artist_name= :artistName and artist_surname= :artistSurname and instrument_id= :instrumentId";  // a is representation o an Artist Object
        Query query = entityManager.createQuery(jpql, Artist.class);
        query.setParameter("artistName", artistName);
        query.setParameter("artistSurname", artistSurname); 
        query.setParameter("instrumentId", instrumentId);
        
        Artist artist = (Artist)  query.getSingleResult();  
        artist.getVideoContainsArtists().size();  // Initialize the lazy hibernate collection  
        return artist;
   }     
   @Transactional                              // Initialize the lazy hibernate collection
   public Artist retrieveArtistById(int artistId) {
        String jpql = "SELECT a FROM Artist a WHERE a.artist_id= :artistId"; 
        Query query = entityManager.createQuery(jpql, Artist.class);
        query.setParameter("artistId", artistId);
        
        Artist artist = (Artist)  query.getSingleResult();  
        artist.getVideoContainsArtists().size();  // Initialize the lazy hibernate collection  
        return artist;
    } 
   
   @Transactional                           // Initialize the lazy hibernate collection
   public List<Artist> retrieveArtistByInstrumentId(int instrumentId) {
        String jpql = "SELECT a FROM Artist a WHERE a.instrument_id= :instrumentId"; 
        Query query = entityManager.createQuery(jpql, Artist.class);
        query.setParameter("instrumentId", instrumentId);
        
        List<Artist> artistList = query.getResultList();
        // Access videoContainsArtists here to initialize them, hibernation Lazy alongside many to many relationship , has bound artists with videocontainsartist
        for (Artist artist : artistList) {
            artist.getVideoContainsArtists().size();  // Initialize the lazy hibernate collection  
        }
        return artistList;
   }   
   
   @Transactional                           // Initialize the lazy hibernate collection   
   public List<Artist> retrieveByDurationId(int durationId) {
        String jpql = "SELECT a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank, COUNT(v) as artist_video_count " +
                "FROM Artist a " +
                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +  //vca.artist.artist_id  its many to many relationship style of writing query
                "INNER JOIN Video v ON vca.video.video_id = v.video_id " +                    //  vca.video.video_id  too
                "WHERE v.duration_id = :durationId " +
                "GROUP BY a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank";

		Query query = entityManager.createQuery(jpql);
		query.setParameter("durationId", durationId);
		
	    List<Artist> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Artist artist = new Artist();
		    artist.setArtist_id((Integer) row[0]);
		    artist.setArtist_name((String) row[1]);
		    artist.setArtist_surname((String) row[2]);
		    artist.setInstrument_id((int) row[3]);
		    artist.setArtist_rank((int) row[4]);			    
		    artist.setArtist_video_count(((Number) row[5]).intValue());
		    
		    artist.getVideoContainsArtists().size();    // Initialize the lazy hibernate collection  		    
		    
		    resultList.add(artist);

		}

	    return resultList;
   }        
   
   @Transactional                           // Initialize the lazy hibernate collection   
   public List<Artist> retrieveByTypeId(int typeId) {
        String jpql = "SELECT a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank, COUNT(v) as artist_video_count " +
                "FROM Artist a " +
                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +  //vca.artist.artist_id  its many to many relationship style of writing query
                "INNER JOIN Video v ON vca.video.video_id = v.video_id " +                    //  vca.video.video_id  too
                "WHERE v.type_id = :typeId " +
                "GROUP BY a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank";

		Query query = entityManager.createQuery(jpql);
		query.setParameter("typeId", typeId);
		
	    List<Artist> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Artist artist = new Artist();
		    artist.setArtist_id((Integer) row[0]);
		    artist.setArtist_name((String) row[1]);
		    artist.setArtist_surname((String) row[2]);
		    artist.setInstrument_id((int) row[3]);
		    artist.setArtist_rank((int) row[4]);			    
		    artist.setArtist_video_count(((Number) row[5]).intValue());
		    
		    artist.getVideoContainsArtists().size();    // Initialize the lazy hibernate collection  		    
		    
		    resultList.add(artist);

		}

	    return resultList;
   }        
   
   @Transactional                           // Initialize the lazy hibernate collection   
   public List<Artist> retrieveArtistByVideoId(int videoId) {
        String jpql = "SELECT a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank, COUNT(vca) as artist_video_count " +
                "FROM Artist a " +
                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " + 
                "WHERE vca.video.video_id = :videoId " +
                "GROUP BY a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank";

		Query query = entityManager.createQuery(jpql);
		query.setParameter("videoId", videoId);
		
	    List<Artist> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Artist artist = new Artist();
		    artist.setArtist_id((Integer) row[0]);
		    artist.setArtist_name((String) row[1]);
		    artist.setArtist_surname((String) row[2]);
		    artist.setInstrument_id((int) row[3]);
		    artist.setArtist_rank((int) row[4]);			    
		    artist.setArtist_video_count(((Number) row[5]).intValue());
		    
		    artist.getVideoContainsArtists().size();    // Initialize the lazy hibernate collection  		    
		    
		    resultList.add(artist);

		}

	    return resultList;
   }        
   
//   @Transactional                           // Initialize the lazy hibernate collection   
//   public Artist retrieveArtistByQuoteId(int quoteId) {
//        String jpql = "SELECT a FROM Artist a " +
//                "INNER JOIN Quote q ON a.artist_id = q.artist_id " +      
//                "WHERE q.quote_id = :quoteId ";
//
//		Query query = entityManager.createQuery(jpql);
//		query.setParameter("quoteId", quoteId);
//		
//		return  (Artist) query.getSingleResult();
//
//   }           
   
   @Transactional                           // Initialize the lazy hibernate collection
   public List<Artist> retrieveByInstrumentIdAndTypeId(int instrumentId, int typeId) {
        String jpql = "SELECT a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank,  COUNT(v) as artist_video_count " +
                "FROM Artist a " +
                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +  //vca.artist.artist_id  its many to many relationship style of writing query
                "INNER JOIN Video v ON vca.video.video_id = v.video_id " +                    //  vca.video.video_id  too
                "WHERE a.instrument_id = :instrumentId AND v.type_id = :typeId " +
                "GROUP BY a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank";

		Query query = entityManager.createQuery(jpql);
		query.setParameter("instrumentId", instrumentId);
		query.setParameter("typeId", typeId);		
		
	    List<Artist> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();
		for (Object[] row : results) {
		    Artist artist = new Artist();
		    artist.setArtist_id((Integer) row[0]);
		    artist.setArtist_name((String) row[1]);
		    artist.setArtist_surname((String) row[2]);
		    artist.setInstrument_id((int) row[3]);	
		    artist.setArtist_rank((int) row[4]);			    
		    artist.setArtist_video_count(((Number) row[5]).intValue()); 
		    
		    artist.getVideoContainsArtists().size();    // Initialize the lazy hibernate collection  		    
		    
		    resultList.add(artist);
		}

	    return resultList;
   }   

   @Transactional                           // Initialize the lazy hibernate collection   
   public List<Artist> retrieveByInstrumentIdAndDurationId(int instrumentId, int durationId) {
        String jpql = "SELECT a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank,  COUNT(v) as artist_video_count " +
                "FROM Artist a " +
                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +  //vca.artist.artist_id  its many to many relationship style of writing query
                "INNER JOIN Video v ON vca.video.video_id = v.video_id " +                    //  vca.video.video_id  too
                "WHERE a.instrument_id = :instrumentId AND v.duration_id = :durationId " +
                "GROUP BY a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank";

		Query query = entityManager.createQuery(jpql);
		query.setParameter("instrumentId", instrumentId);
		query.setParameter("durationId", durationId);		
        //return query.getResultList();  // i should do that instead and over with it... but LAZY hibarnate want me to initialise collection before closing it !! so in feeding the object manualy
	    List<Artist> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();	
		for (Object[] row : results) {
		    Artist artist = new Artist();
		    artist.setArtist_id((Integer) row[0]);
		    artist.setArtist_name((String) row[1]);
		    artist.setArtist_surname((String) row[2]);
		    artist.setInstrument_id((int) row[3]);	
		    artist.setArtist_rank((int) row[4]);			    
		    artist.setArtist_video_count(((Number) row[5]).intValue()); 
		    		    
		    artist.getVideoContainsArtists().size();    // Initialize the lazy hibernate collection  		    
		    		    
		    resultList.add(artist);
		}

	    return resultList;
   }     
    
   @Transactional                           // Initialize the lazy hibernate collection         
   public List<Artist> retriveByInstrumentIdTypeIdAndDurationId(int instrumentId, int typeId, int durationId) {
        String jpql = "SELECT a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank,  COUNT(v) as artist_video_count " +
                "FROM Artist a " +
                "INNER JOIN VideoContainsArtist vca ON a.artist_id = vca.artist.artist_id " +  //vca.artist.artist_id  its many to many relationship style of writing query
                "INNER JOIN Video v ON vca.video.video_id = v.video_id " +                    //  vca.video.video_id  too
                "WHERE a.instrument_id = :instrumentId AND v.type_id = :typeId AND v.duration_id = :durationId " +
                "GROUP BY a.artist_id, a.artist_name, a.artist_surname, a.instrument_id, a.artist_rank";

		Query query = entityManager.createQuery(jpql);
		query.setParameter("instrumentId", instrumentId);
		query.setParameter("typeId", typeId);		
		query.setParameter("durationId", durationId);		
		
	    List<Artist> resultList = new ArrayList<>();
	    List<Object[]> results = query.getResultList();	
		for (Object[] row : results) {
		    Artist artist = new Artist();
		    artist.setArtist_id((Integer) row[0]);
		    artist.setArtist_name((String) row[1]);
		    artist.setArtist_surname((String) row[2]);
		    artist.setInstrument_id((int) row[3]);
		    artist.setArtist_rank((int) row[4]);			    
		    artist.setArtist_video_count(((Number) row[5]).intValue());	 
		    		    
	    	artist.getVideoContainsArtists().size();    // Initialize the lazy hibernate collection  		   
		    
		    resultList.add(artist);
		}

	    return resultList;
   }  
    
    
   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
   @Transactional
   public List<List<Artist>> retrieveAllGroupedByType() {
       List<List<Artist>> result = new ArrayList<>();
       for (int typeId = 1; typeId <= 4; typeId++) {
           result.add(retrieveByTypeId(typeId));
       }
       return result;
   }
   
   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
   @Transactional
   public List<List<Artist>> retrieveAllGroupedByDuration() {
       List<List<Artist>> result = new ArrayList<>();
       for (int durationId = 1; durationId <= 5; durationId++) {
           result.add(retrieveByDurationId(durationId));
       }
       return result;
   }
   
   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
   @Transactional
   public List<List<Artist>> retrieveAllGroupedByInstrument() {
       List<List<Artist>> result = new ArrayList<>();
       for (int instrumentId = 1; instrumentId <= 14; instrumentId++) {
           result.add(retrieveArtistByInstrumentId(instrumentId));
       }
       return result;
   }
   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
   @Transactional
   public List<List<Artist>> retrieveAllGroupedByVideo() {
       List<List<Artist>> result = new ArrayList<>();
       for (int videoId = 1; videoId <= 3218; videoId++) {
           result.add(retrieveArtistByVideoId(videoId));
       }
       return result;
   }
    
        
	
}
