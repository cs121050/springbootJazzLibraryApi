

package com.nicosarr.jazzLibraryAPI.BootStrapModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Duration.Duration;
import com.nicosarr.jazzLibraryAPI.Instrument.Instrument;
import com.nicosarr.jazzLibraryAPI.Quote.Quote;
import com.nicosarr.jazzLibraryAPI.Type.Type;
import com.nicosarr.jazzLibraryAPI.Video.Video;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;

import jakarta.persistence.Query;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;



@Repository
public class BootStrapRep {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<Integer, List<Artist>> groupArtistsByVideo() {
        // Use JPA entity relationships instead of SQL columns
        String jpql = "SELECT v.id.videoId, a " +
                      "FROM VideoContainsArtist v " +
                      "JOIN v.artist a " +
                      "ORDER BY v.id.videoId";

        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                                             .getResultList();

        Map<Integer, List<Artist>> videoArtistMap = new LinkedHashMap<>();

        for (Object[] row : results) {
            Integer videoId = (Integer) row[0];
            Artist artist = (Artist) row[1];
            
            videoArtistMap.computeIfAbsent(videoId, k -> new ArrayList<>()).add(artist);
        }

        return videoArtistMap;
        
        //HIBERNATION     QUERY EXPLAINED
//        1. SELECT v.id.videoId, a
//
//        v.id.videoId: Accesses the videoId from the composite primary key
//        (From your VideoContainsArtistId embedded ID class)
//
//        a: Returns the full Artist entity
//        (Through the v.artist relationship)
//
//    2. FROM VideoContainsArtist v
//
//        VideoContainsArtist: The entity representing your join table
//
//        v: Alias for the VideoContainsArtist entity
//
//    3. JOIN v.artist a
//
//        v.artist: Uses the @ManyToOne Artist artist relationship in VideoContainsArtist
//        (Defined by your @ManyToOne @MapsId("artistId") mapping)
//
//        a: Alias for the joined Artist entity
//
//    4. ORDER BY v.id.videoId
//
//        Orders results by the video ID from the composite key
//        (Maintains grouping order for your Map structure)
    }
        
    
    
    
    @Transactional
    public Map<Integer, List<Artist>> groupArtistsByInstrument() {
        // Use JPA entity relationships instead of SQL columns
        String jpql = "	SELECT a.instrument_id, a "
        		+ "    FROM Artist a"
        		+ "	   order by a.instrument_id, a.artist_id";

        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                                             .getResultList();

        Map<Integer, List<Artist>> instrumentArtistMap = new LinkedHashMap<>();

        for (Object[] row : results) {
            Integer instrumentId = (Integer) row[0];
            Artist artist = (Artist) row[1];
            
            instrumentArtistMap.computeIfAbsent(instrumentId, k -> new ArrayList<>()).add(artist);
        }

        return instrumentArtistMap;   
    }
    
    public Map<Integer, List<Artist>> groupArtistsByType() {
        // Use JPA entity relationships instead of SQL columns
        String jpql = "	SELECT DISTINCT v.type_id, a"
        		+ "	    FROM Artist a"
                +"      JOIN a.videoContainsArtists vca "  // Use mapped relationship looks like that :  a.videoContainsArtists.v
                +"      JOIN vca.video v " 
        		+ "	    order by v.type_id, a.artist_id";
        		

        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                                             .getResultList();

        Map<Integer, List<Artist>> typeArtistMap = new LinkedHashMap<>();

        for (Object[] row : results) {
            Integer typeId = (Integer) row[0];
            Artist artist = (Artist) row[1];
            
            typeArtistMap.computeIfAbsent(typeId, k -> new ArrayList<>()).add(artist);
        }

        return typeArtistMap;   
    }        

    public Map<Integer, List<Artist>> groupArtistsByDuration() {
        // Use JPA entity relationships instead of SQL columns
        String jpql = "SELECT DISTINCT  v.duration_id, a"
        		+ "    	FROM Artist a "
                +"      JOIN a.videoContainsArtists vca "  // Use mapped relationship looks like that :  a.videoContainsArtists.v
                +"      JOIN vca.video v " 
        		+ "    	order by v.duration_id, a.artist_id";
        		

        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                                             .getResultList();

        Map<Integer, List<Artist>> durationArtistMap = new LinkedHashMap<>();

        for (Object[] row : results) {
            Integer durationtId = (Integer) row[0];
            Artist artist = (Artist) row[1];
            
            durationArtistMap.computeIfAbsent(durationtId, k -> new ArrayList<>()).add(artist);
        }

        return durationArtistMap;   
    }      
    
    
    
    
    
    @Transactional  // Initialize the lazy hibernate collection
    public Map<Integer, List<Video>> groupVideosByArtist() {
        String jpql =  "SELECT vca.id.artistId, v " +
                "FROM VideoContainsArtist vca " +
                "JOIN vca.video v " +
                "ORDER BY vca.id.artistId";
        
        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                .getResultList();

		Map<Integer, List<Video>> artistVideoMap = new LinkedHashMap<>();
		
		for (Object[] row : results) {
		Integer artistId = (Integer) row[0];
		Video video = (Video) row[1];
		
		artistVideoMap.computeIfAbsent(artistId, k -> new ArrayList<>()).add(video);
		}
		
		return artistVideoMap; 
    }
    
    @Transactional  // Initialize the lazy hibernate collection
    public Map<Integer, List<Video>> groupVideosByInstrument() {
        String jpql = "SELECT DISTINCT  a.instrument_id, v"
        		+ "		FROM Video v "
        		+ "		JOIN v.videoContainsArtists vca "  
        		+ "		JOIN vca.artist a "
        		+ "		order by a.instrument_id, v.video_id";
        
        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                .getResultList();

		Map<Integer, List<Video>> instrumentVideoMap = new LinkedHashMap<>();
		
		for (Object[] row : results) {
		Integer instrumentId = (Integer) row[0];
		Video video = (Video) row[1];
		
		instrumentVideoMap.computeIfAbsent(instrumentId, k -> new ArrayList<>()).add(video);
		}
		
		return instrumentVideoMap; 
    }  
    
    @Transactional  // Initialize the lazy hibernate collection
    public Map<Integer, List<Video>> groupVideosByType() {
        String jpql = "	SELECT v.type_id, v"
        		+ "	    FROM Video v"
        		+ "	    order by v.type_id, v.video_id";
        
        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                .getResultList();

		Map<Integer, List<Video>> typeVideoMap = new LinkedHashMap<>();
		
		for (Object[] row : results) {
		Integer typeId = (Integer) row[0];
		Video video = (Video) row[1];
		
		typeVideoMap.computeIfAbsent(typeId, k -> new ArrayList<>()).add(video);
		}
		
		return typeVideoMap; 
    }
    
    @Transactional  // Initialize the lazy hibernate collection
    public Map<Integer, List<Video>> groupVideosByDuration() {
        String jpql = "	SELECT v.duration_id, v"
        		+ "	    FROM Video v"
        		+ "	    order by v.duration_id, v.video_id";
        
        List<Object[]> results = entityManager.createQuery(jpql, Object[].class)
                .getResultList();

		Map<Integer, List<Video>> durationVideoMap = new LinkedHashMap<>();
		
		for (Object[] row : results) {
		Integer durationId = (Integer) row[0];
		Video video = (Video) row[1];
		
		durationVideoMap.computeIfAbsent(durationId, k -> new ArrayList<>()).add(video);
		}
		
		return durationVideoMap; 
    }
    
    
    @Transactional  // Initialize the lazy hibernate collection
    public List<Artist> retrieveAllArtists() {
        String jpql = "SELECT DISTINCT a FROM Artist a " +
                "LEFT JOIN FETCH a.videoContainsArtists vca " +
                "LEFT JOIN FETCH vca.video " +
                "ORDER BY a.artist_id";

        List<Artist> artistList = entityManager.createQuery(jpql, Artist.class)
                                        .getResultList();
  		return artistList;
        
    }


	   @Transactional                         
    public List<Duration> retrieveAllDurations() {
        return entityManager.createQuery("SELECT d FROM Duration d", Duration.class)
                .getResultList();
    }
	   @Transactional                          
    public List<Instrument> retrieveAllInstruments() {
		    return entityManager.createQuery("SELECT i FROM Instrument i", Instrument.class)
                    .getResultList();
    }
	   @Transactional                          
    public List<Type> retrieveAllTypes() {
 			return entityManager.createQuery("SELECT t FROM Type t", Type.class)
 					.getResultList();
    }	   
	   @Transactional                           // Initialize the lazy hibernate collection		   
    public List<Video> retrieveAllVideos() {
		    String jpql = "SELECT DISTINCT v FROM Video v " +
	                  "LEFT JOIN FETCH v.videoContainsArtists vca " +
	                  "LEFT JOIN FETCH vca.artist " +
	                  "ORDER BY v.video_id";

	    List<Video> videoList = entityManager.createQuery(jpql, Video.class)
	                                       .getResultList();

        return videoList; 
    }	   
    
    public List<Quote> retrieveAllQuotes() {
 			return entityManager.createQuery("SELECT q FROM Quote q", Quote.class)
 					.getResultList();
    }	   
    

	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
 
	    private Map<String, Map<Integer, List<Artist>>> createArtistFilters() {
	        Map<String, Map<Integer, List<Artist>>> filters = new HashMap<>();
	        
	        filters.put("byVideo", groupArtistsByVideo());
	        filters.put("byInstrument", groupArtistsByInstrument());
	        filters.put("byType", groupArtistsByType());
	        filters.put("byDuration", groupArtistsByDuration());
	        
	        return filters;
	    }
	   
	    private Map<String, Map<Integer, List<Video>>> createVideoFilters() {
	        Map<String, Map<Integer, List<Video>>> filters = new HashMap<>();
	        
	        filters.put("byArtist", groupVideosByArtist());
	        filters.put("byInstrument", groupVideosByInstrument());
	        filters.put("byType", groupVideosByType());
	        filters.put("byDuration", groupVideosByDuration());

	        
	        return filters;
	    }

	   @Transactional
	   public BootStrapModel retrieveAll() {
		   		    // Retrieve core data
		    List<Quote> quotes = retrieveAllQuotes();
		    List<Artist> artists = retrieveAllArtists();
		    List<Instrument> instruments = retrieveAllInstruments();
		    List<Video> videos = retrieveAllVideos();
		    List<Type> types = retrieveAllTypes();
		    List<Duration> durations = retrieveAllDurations();
		    
		    // Create filters and store locally
//		    Map<String, Map<Integer, List<Artist>>> artistFilters = createArtistFilters();
//		    Map<String, Map<Integer, List<Video>>> videoFilters = createVideoFilters();		   
		   
		   
		   BootStrapModel bootStrapModel = new BootStrapModel();
		   
		   //bootStrapModel.setQuoteList(retrieveAllQuotes());
  		   bootStrapModel.setArtistList(artists);
		   bootStrapModel.setInstrumentList(instruments);
		   bootStrapModel.setVideoList(videos);
		   bootStrapModel.setTypeList(types);
		   bootStrapModel.setDurationList(durations);
		   
		    // Filter maps
		   bootStrapModel.setArtistFilters(createArtistFilters());
		   bootStrapModel.setVideoFilters(createVideoFilters());
		   
		   
		    // Pass filters to metadata creation
		   bootStrapModel.setCounts(createMetadata( artists, videos, instruments, types, durations, quotes
//				   									,artistFilters,  // Include artist filters
//											        videoFilters    // Include video filters
		    ));
		    
	       return bootStrapModel;
	   }   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	   
	//util   
	@SuppressWarnings("unused")
	private Map<String, Integer> createMetadata(
			    List<Artist> artists,
			    List<Video> videos,
			    List<Instrument> instruments,
			    List<Type> types,
			    List<Duration> durations,
			    List<Quote> quotes
//			    ,Map<String, Map<Integer, List<Artist>>> artistFilters,
//			    Map<String, Map<Integer, List<Video>>> videoFilters
			) {
			    Map<String, Integer> counts = new LinkedHashMap<>();

			    // Core totals
			    counts.put("total_artists", artists.size());
			    counts.put("total_videos", videos.size());
			    counts.put("total_instruments", instruments.size());
			    counts.put("total_types", types.size());
			    counts.put("total_durations", durations.size());
			    counts.put("total_quotes", quotes.size());

			    
/*  very time expensive
 * 
 * 
 * 
			    // Artist filters: Count items per grouping bucket
			    artistFilters.forEach((filterName, filterMap) -> {
			        String prefix = "artist_filter_" + filterName;

//			        // Total groups (e.g., number of videos/instruments)
//			        counts.put(prefix + "_groups", filterMap.size());

			        // Items per group (e.g., "artist_filter_byVideo_group_123": 5)
			        filterMap.forEach((groupId, artistsInGroup) -> {
			            String key = prefix + "_id_" + groupId;
			            counts.put(key, artistsInGroup.size());
			        });
			    });

			    // Video filters: Count items per grouping bucket
			    videoFilters.forEach((filterName, filterMap) -> {
			        String prefix = "video_filter_" + filterName;

//			        // Total groups (e.g., number of artists/durations)
//			        counts.put(prefix + "_groups", filterMap.size());

			        // Items per group (e.g., "video_filter_byArtist_group_456": 3)
			        filterMap.forEach((groupId, videosInGroup) -> {
			            String key = prefix + "_id_" + groupId;
			            counts.put(key, videosInGroup.size());
			        });
			    });
			    
			    
*/
			    return counts;
			}
}
