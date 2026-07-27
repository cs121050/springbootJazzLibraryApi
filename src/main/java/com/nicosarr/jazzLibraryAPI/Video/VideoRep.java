package com.nicosarr.jazzLibraryAPI.Video;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;


import com.nicosarr.jazzLibraryAPI.Artist.Artist;
import com.nicosarr.jazzLibraryAPI.Artist.ArtistWithVideoDTO;
import com.nicosarr.jazzLibraryAPI.VideoContainsArtist.VideoContainsArtist;
import com.nicosarr.jazzLibraryAPI.util.JobContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.springframework.web.client.RestTemplate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


@Repository
public class VideoRep {
	
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RestTemplate restTemplate;
    
	private static final Logger logger = LoggerFactory.getLogger(VideoRep.class);

    
    // Method to get all artists with videos as DTOs
    public List<VideoWithArtistDTO> retrieveAllWithArtists() {
        String jpql = "SELECT DISTINCT v FROM Video v " +
                "LEFT JOIN FETCH v.videoContainsArtists vca " +
                "LEFT JOIN FETCH vca.artist " +
                "ORDER BY v.video_id";
        
        TypedQuery<Video> query = entityManager.createQuery(jpql, Video.class);
        List<Video> videos = query.getResultList();
        
        // Convert entities to DTOs
        return videos.stream()
            .map(VideoWithArtistDTO::fromEntity)
            .collect(Collectors.toList());
    }
    // Method to get all artists with videos as DTOs
    public List<VideoDTO> retrieveAll() {
        String jpql = "SELECT DISTINCT v FROM Video v "+
                "ORDER BY v.video_id";
        
        TypedQuery<Video> query = entityManager.createQuery(jpql, Video.class);
        List<Video> videos = query.getResultList();
        
        // Convert entities to DTOs
        return videos.stream()
            .map(VideoDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    // ============================
    // NEW: YouTube availability check
    // ============================

    private static final String STATUS_AVAILABLE = "1";
    private static final String STATUS_NOT_EMBEDDABLE = "-1";
    private static final String STATUS_NOT_FOUND = "-2";
    private static final String STATUS_PRIVATE = "-3";
    private static final String STATUS_MEMBERS_ONLY = "-4";
    private static final String STATUS_OTHER_ERROR = "-5";

    @Transactional(timeout = 3600)
    public String processAllVideosAvailability(JobContext jobContext) {
        // Get jobId for logging (if available)
        String jobId = (jobContext != null) ? jobContext.getJobId() : "unknown";

        // ----- 1. Initial status counts -----
        Map<String, Long> initialCounts = getStatusCounts();
        logger.info("=== [Job {}] INITIAL STATUS DISTRIBUTION ===", jobId);
        logger.info("[Job {}] {}", jobId, formatStatusCounts(initialCounts));

        // ----- 2. Collect videos -----
        List<Video> videos = entityManager.createQuery("SELECT v FROM Video v", Video.class).getResultList();
        int total = videos.size();
        int available = 0, notEmbeddable = 0, notFound = 0, privateCount = 0, membersOnly = 0, otherError = 0;

        Map<String, List<Integer>> idsByNewStatus = new HashMap<>();
        Map<Integer, String> oldStatusMap = new HashMap<>();
        List<String> changes = new ArrayList<>();

        logger.info("[Job {}] Starting video availability check for {} videos.", jobId, total);

        for (int i = 0; i < videos.size(); i++) {
            // ---------- CANCELLATION CHECK (1) ----------
            if (jobContext != null && jobContext.isCancelled()) {
                logger.warn("[Job {}] Cancellation requested by user. Stopping at video index {}/{}.",
                            jobId, i, total);
                return "Processing cancelled by user. " + i + " videos processed.";
            }

            Video video = videos.get(i);
            int videoId = video.getVideo_id();
            String oldStatus = video.getVideo_availability();
            oldStatusMap.put(videoId, oldStatus);

            String videoPath = video.getVideo_path();
            String newStatus;
            if (videoPath == null || videoPath.trim().isEmpty()) {
                newStatus = STATUS_OTHER_ERROR;
            } else {
                String videoIdStr = extractYouTubeId(videoPath);
                if (videoIdStr == null) {
                    newStatus = STATUS_OTHER_ERROR;
                } else {
                    newStatus = checkVideoStatus(videoIdStr);
                }
            }

            // Count per status
            switch (newStatus) {
                case STATUS_AVAILABLE: available++; break;
                case STATUS_NOT_EMBEDDABLE: notEmbeddable++; break;
                case STATUS_NOT_FOUND: notFound++; break;
                case STATUS_PRIVATE: privateCount++; break;
                case STATUS_MEMBERS_ONLY: membersOnly++; break;
                default: otherError++; break;
            }

            if (oldStatus == null || !oldStatus.equals(newStatus)) {
                idsByNewStatus.computeIfAbsent(newStatus, k -> new ArrayList<>()).add(videoId);
                String changeMsg = String.format("video_id %d (\"%s\"): %s -> %s",
                        videoId, video.getVideo_name(), oldStatus != null ? oldStatus : "NULL", newStatus);
                changes.add(changeMsg);
            }

            // Progress log (includes jobId)
            int processed = i + 1;
            if (processed % 10 == 0 || processed == total) {
                int percent = (int) ((double) processed / total * 100);
                logger.info("[Job {}] Progress: {}/{} ({}%) – Available: {}, Not embeddable: {}, Not found: {}, Private: {}, Members only: {}, Other errors: {}",
                        jobId, processed, total, percent,
                        available, notEmbeddable, notFound, privateCount, membersOnly, otherError);
            }

            // Delay (respect YouTube TOS)
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Restore interrupted status
                Thread.currentThread().interrupt();
                // If cancellation was requested, return early
                if (jobContext != null && jobContext.isCancelled()) {
                    logger.warn("[Job {}] Interrupted due to cancellation. Stopping at video index {}.", jobId, processed);
                    return "Interrupted and cancelled. " + processed + " videos processed.";
                }
                // Otherwise, treat as an unexpected interruption
                logger.warn("[Job {}] Interrupted during sleep, but cancellation not requested. Continuing?", jobId);
                // You may choose to break or continue – here we break to be safe.
                break;
            }
        }

        // ----- 3. Execute batch updates -----
        int totalUpdated = 0;
        for (Map.Entry<String, List<Integer>> entry : idsByNewStatus.entrySet()) {
            String status = entry.getKey();
            List<Integer> ids = entry.getValue();
            if (!ids.isEmpty()) {
                for (int i = 0; i < ids.size(); i += 1000) {
                    List<Integer> chunk = ids.subList(i, Math.min(i + 1000, ids.size()));
                    String jpql = "UPDATE Video v SET v.video_availability = :status WHERE v.video_id IN (:ids)";
                    int updated = entityManager.createQuery(jpql)
                            .setParameter("status", status)
                            .setParameter("ids", chunk)
                            .executeUpdate();
                    totalUpdated += updated;
                    logger.info("[Job {}] Bulk update chunk: {} videos set to status '{}'", jobId, updated, status);
                }
            }
        }

        entityManager.clear();   // Detach all managed entities

        // ----- 4. Final status counts -----
        Map<String, Long> finalCounts = getStatusCounts();
        logger.info("=== [Job {}] FINAL STATUS DISTRIBUTION ===", jobId);
        logger.info("[Job {}] {}", jobId, formatStatusCounts(finalCounts));

        // Build summary
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append(String.format(
                "Processed %d videos. Available: %d, Not embeddable: %d, Not found: %d, Private: %d, Members only: %d, Other errors: %d",
                total, available, notEmbeddable, notFound, privateCount, membersOnly, otherError
        ));

        summaryBuilder.append("\n\n=== INITIAL STATUS ===\n");
        summaryBuilder.append(formatStatusCounts(initialCounts));
        summaryBuilder.append("\n=== FINAL STATUS ===\n");
        summaryBuilder.append(formatStatusCounts(finalCounts));

        if (!changes.isEmpty()) {
            summaryBuilder.append("\n=== CHANGES ===\n");
            for (String change : changes) {
                summaryBuilder.append(change).append("\n");
            }
            logger.info("[Job {}] Changes detected ({}):", jobId, changes.size());
            for (String change : changes) {
                logger.info("[Job {}]   {}", jobId, change);
            }
        } else {
            summaryBuilder.append("\n\nNo status changes.");
            logger.info("[Job {}] No status changes detected.", jobId);
        }

        String summary = summaryBuilder.toString();
        logger.info("[Job {}] ✅ {}", jobId, summary);
        return summary;
    }
//    
//    public String[] retrieveAllVideoNames() {
//        String jpql = "SELECT v.video_name FROM Video v";  
//        Query query = entityManager.createQuery(jpql);     
//        List<String> videoList = query.getResultList();  
//        
//        return videoList.toArray(new String[0]);
//    }    
//    
//    public int create(Video video) { 
//    	String sql = "INSERT INTO Video (duration_id, video_name, video_duration, video_path, type_id, location_id, video_availability) VALUES (?, ?, ?, ?, ?, ?, ?)";
//    	return jdbcTemplate.update(sql, video.getDuration_id(), video.getVideo_name(), video.getVideo_duration(),
//    			            video.getVideo_path(), video.getType_id(), video.getLocation_id(), video.getVideo_availability());
//    } 
//     
//    public int update(Video video) { 
//    	String sql = "UPDATE Video SET duration_id = ?, video_name = ?, video_duration = ?, video_path = ?,"
//    		 + "  type_id = ?, location_id = ?, video_availability = ? WHERE video_id = ?";
//    	return jdbcTemplate.update(sql, video.getDuration_id(), video.getVideo_name(), video.getVideo_duration(),
//	                        video.getVideo_path(), video.getType_id(), video.getLocation_id(), video.getVideo_availability(), video.getVideo_id ());	
//    }
//      
//    public Video retrieveVideoById(int videoId) {
//        String jpql = "SELECT v FROM Video v WHERE v.video_id= :videoId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoId", videoId);
//        Video video = (Video) query.getSingleResult();
//        
//        return video;
//    }    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByName(String videoName) {
//        String jpql = "SELECT v FROM Video v WHERE v.video_name LIKE :videoName"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");        
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }  
// 
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndTypeId(String videoName, int typeId) {
//        String jpql = "SELECT v FROM Video v WHERE v.video_name LIKE :videoName and v.type_id = :typeId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("typeId", typeId);  
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//     } 
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndDurationId(String videoName, int durationId) {
//        String jpql = "SELECT v FROM Video v WHERE v.video_name LIKE :videoName and v.duration_id = :durationId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("durationId", durationId);  
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//     } 
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndInstrumentId(String videoName, int instrumentId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "        		
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "
//        		+ " WHERE v.video_name LIKE :videoName and a.instrument_id = :instrumentId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("instrumentId", instrumentId);  
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//     } 
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndArtistId(String videoName, int artistId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "           		
//        		+ " WHERE v.video_name LIKE :videoName AND vca.artist.artist_id = :artistId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("artistId", artistId);          
//        
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }
//    
//    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndArtistIdAndTypeId(String videoName, int artistId, int typeId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "           		
//        		+ " WHERE v.video_name LIKE :videoName AND v.type_id= :typeId AND vca.artist.artist_id = :artistId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("artistId", artistId);  
//        query.setParameter("typeId", typeId);           
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }   
//    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndArtistIdAndDurationId(String videoName, int artistId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "           		
//        		+ " WHERE v.video_name LIKE :videoName AND vca.artist.artist_id = :artistId AND v.duration_id= :durationId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("artistId", artistId); 
//        query.setParameter("durationId", durationId);          
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }   
//    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndArtistIdAndTypeIdAndDurationId(String videoName, int artistId, int typeId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "           		
//        		+ " WHERE v.video_name LIKE :videoName AND vca.artist.artist_id = :artistId  AND v.duration_id= :durationId  AND v.type_id= :typeId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("artistId", artistId);          
//        query.setParameter("typeId", typeId);   
//        query.setParameter("durationId", durationId);           
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }   
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndInstrumentIdAndDurationId(String videoName, int instrumentId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "    
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "        		
//        		+ " WHERE v.video_name LIKE :videoName AND a.instrument_id = :instrumentId AND v.duration_id= :durationId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("instrumentId", instrumentId); 
//        query.setParameter("durationId", durationId);          
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }      
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndInstrumentIdAndTypeId(String videoName, int instrumentId, int typeId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "    
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "        		
//        		+ " WHERE v.video_name LIKE :videoName AND a.instrument_id = :instrumentId AND v.type_id= :typeId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("instrumentId", instrumentId); 
//        query.setParameter("typeId", typeId);          
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }  
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByNameAndInstrumentIdAndTypeIdAndDurationId(String videoName, int instrumentId, int typeId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "    
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "        		
//        		+ " WHERE v.video_name LIKE :videoName AND a.instrument_id = :instrumentId AND v.type_id= :typeId AND v.duration_id= :durationId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoName", "%"+videoName+"%");  
//        query.setParameter("instrumentId", instrumentId); 
//        query.setParameter("typeId", typeId);   
//        query.setParameter("durationId", durationId);          
//        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }     
//    
//    
//
//
//    
//    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByArtistId(int artistId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " WHERE vca.artist.artist_id= :artistId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("artistId", artistId);
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }      
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByInstrumentId(int instrumentId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "
//        		+ " WHERE a.instrument_id= :instrumentId "
//        		+ " order by v.video_id "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("instrumentId", instrumentId);
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }    
//    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByTypeId(int typeId) {
//        String jpql = "SELECT v FROM Video v  WHERE v.type_id = :typeId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("typeId", typeId);        
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//        
//        return videoList; 
//    }
//    
//
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByDurationId(int durationId) {
//        String jpql = "SELECT v FROM Video v  WHERE v.duration_id = :durationId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("durationId", durationId);        
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//        
//        return videoList; 
//    }
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByInstrumentIdAndTypeId(int instrumentId, int typeId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "
//        		+ " WHERE a.instrument_id= :instrumentId And v.type_id= :typeId"
//        		+ " order by v.video_id "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("instrumentId", instrumentId);
//        query.setParameter("typeId", typeId);        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }    
//    
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByInstrumentIdAndDurationId(int instrumentId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "
//        		+ " WHERE a.instrument_id= :instrumentId And v.duration_id= :durationId"
//        		+ " order by v.video_id "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("instrumentId", instrumentId);
//        query.setParameter("durationId", durationId);        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }    
//
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByTypeIdAndDurationId(int typeId, int durationId) {
//        String jpql = "SELECT v FROM Video v  WHERE v.type_id = :typeId AND v.duration_id = :durationId"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("typeId", typeId);      
//        query.setParameter("durationId", durationId);            
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//        
//        return videoList; 
//    }  
//    
//
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByInstrumentIdAndTypeIdAndDurationId(int instrumentId, int typeId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "
//        		+ " WHERE a.instrument_id= :instrumentId AND v.type_id= :typeId And v.duration_id= :durationId"
//        		+ " order by v.video_id "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("instrumentId", instrumentId);
//        query.setParameter("typeId", typeId);             
//        query.setParameter("durationId", durationId);        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }  
//    
//
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByArtistIdAndDurationId(int artistId, int durationId) {
//        String jpql = "SELECT v FROM Video v  "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " WHERE vca.artist.artist_id = :artistId AND v.duration_id = :durationId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("artistId", artistId);      
//        query.setParameter("durationId", durationId);            
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//        
//        return videoList; 
//    }  
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByArtistIdAndTypeId(int artistId, int typeId) {
//        String jpql = "SELECT v FROM Video v  "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " WHERE vca.artist.artist_id = :artistId AND v.type_id = :typeId "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("artistId", artistId);      
//        query.setParameter("typeId", typeId);            
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//        
//        return videoList; 
//    }   
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByArtistIdAndTypeIdAndDurationId(int artistId, int typeId, int durationId) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " WHERE vca.artist_id= :artistId AND v.type_id= :typeId And v.duration_id= :durationId"
//        		+ " order by v.video_id "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("artistId", artistId);
//        query.setParameter("typeId", typeId);             
//        query.setParameter("durationId", durationId);        
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }  
//
//    
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByArtistNameAndArtistSurname(String artistName, String artistSurname) {
//        String jpql = "SELECT v FROM Video v "
//        		+ " INNER JOIN VideoContainsArtist vca ON vca.video.video_id = v.video_id "
//        		+ " INNER JOIN Artist a ON vca.artist.artist_id = a.artist_id "
//        		+ " WHERE a.artist_name= :artistName AND a.artist_surname = :artistSurname"
//        		+ " order by v.video_id "; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("artistName", artistName);
//        query.setParameter("artistSurname", artistSurname);             
//  
//        List<Video> videoList = query.getResultList();
//
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//               
//        return videoList;
//    }  
//    
//
//    @Transactional  // Initialize the lazy hibernate collection
//    public List<Video> retrieveVideoByVideoPath(String videoPath) {
//        String jpql = "SELECT v FROM Video v  WHERE v.video_path = :videoPath"; 
//        Query query = entityManager.createQuery(jpql, Video.class);
//        query.setParameter("videoPath", videoPath);        
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//	      // Initialize each VideoContainsArtist and its artist within the same session
//	      for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//	      	artistList.add( vca.getArtist() );
//	 
//	      video.setArtistList(artistList);  // Set the videos for the artist
//	    }
//        
//        return videoList; 
//    }
//    
//    
//	@Transactional 
//    public List<Video> retriveRandomVideos(int howManyIds) {
//    	
//		String sExtraWhere = produseRandVideoIdExtraWhere(howManyIds);
//
//        String jpql = "SELECT v FROM Video v  WHERE 1=0 "+ sExtraWhere; 
//        
//        Query query = entityManager.createQuery(jpql, Video.class);  
//        List<Video> videoList = query.getResultList();  
//        
//        for (Video video : videoList) {
//	      List<Artist> artistList = new ArrayList<>();
//	
//        // Initialize each VideoContainsArtist and its artist within the same session
//        for (VideoContainsArtist vca : video.getVideoContainsArtists()) 
//        	artistList.add( vca.getArtist() );
// 
//        video.setArtistList(artistList);  // Set the videos for the artist
//
//        }
//        
//    return videoList;	
//    }
//	
//
//	
//   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
//   @Transactional
//   public List<List<Video>> retrieveAllGroupedByType() {
//       List<List<Video>> result = new ArrayList<>();
//       for (int typeId = 1; typeId <= 4; typeId++) {
//           result.add(retrieveVideoByTypeId(typeId));
//       }
//       return result;
//   }
//   
//   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
//   @Transactional
//   public List<List<Video>> retrieveAllGroupedByDuration() {
//       List<List<Video>> result = new ArrayList<>();
//       for (int durationId = 1; durationId <= 5; durationId++) {
//           result.add(retrieveVideoByDurationId(durationId));
//       }
//       return result;
//   }
//   
//   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
//   @Transactional
//   public List<List<Video>> retrieveAllGroupedByInstrument() {
//       List<List<Video>> result = new ArrayList<>();
//       for (int instrumentId = 1; instrumentId <= 14; instrumentId++) {
//           result.add(retrieveVideoByInstrumentId(instrumentId));
//       }
//       return result;
//   }
//   
//   //TODO// at the start of the epi, retrieve THE FIRST 8 ROWS OF tablerowcount TABLE, STORE the counts of instruments,artists,types,durations and store them in global variables ,,,, so to use them at the "grouped by functions"
//   @Transactional
//   public List<List<Video>> retrieveAllGroupedByArtist() {
//       List<List<Video>> result = new ArrayList<>();
//       for (int artistId = 1; artistId <= 343; artistId++) {
//           result.add(retrieveVideoByInstrumentId(artistId));
//       }
//       return result;
//   }
//	    	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	public String produseRandVideoIdExtraWhere(int howManyIds) {
//		
//    	int countOfVideos =  retriveVideoCount();
//    	int randomVideoId[] = randomIdGenerator(countOfVideos, howManyIds);    	
//    	
//    	StringBuilder sExtraWhere = new StringBuilder("");
//    	
//    	for(int i = 0 ; i<randomVideoId.length; i ++)
//    		sExtraWhere.append("  OR v.video_id = "+ randomVideoId[i]);
//    	
//    	
//    	return sExtraWhere.toString();
//		
//	}
//
//    
//
//    
//    public int retriveVideoCount() { 
//    	
//        String jpql = "SELECT t.video_count FROM TableRowCount t WHERE t.table_id = 1"; // table_id=1 : videos
//        Query query = entityManager.createQuery(jpql);
//        Integer result = (Integer) query.getSingleResult();
//        
//        return result != null ? result : 0; 
//    }
//
//    public static int[] randomIdGenerator(int maxID, int countID){
//
//    	int[] randomIDArray = new int[countID];
//    	
//    	for (int i=0 ; i<countID ; i++) {	
//	        Random r = new Random();
//	        int low = 1;
//	        int high = maxID;
//	        int randomID = r.nextInt(high-low) + low;
//	        
//	        randomIDArray[i] = randomID;
//    	}
//
//        return randomIDArray;
//    }
    
    
    /**
     * Extract YouTube video ID from a URL or raw ID string.
     */
    private String extractYouTubeId(String path) {
        String[] patterns = {
            "v=([a-zA-Z0-9_-]{11})",
            "youtu\\.be/([a-zA-Z0-9_-]{11})",
            "/([a-zA-Z0-9_-]{11})(?:\\?|$|/)",
            "^([a-zA-Z0-9_-]{11})$"
        };
        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(path);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /**
     * Check video availability using oEmbed first, then fallback to page scraping.
     */
    private String checkVideoStatus(String videoId) {
        // 1. Try oEmbed
        String oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json";
        try {
            var response = restTemplate.getForEntity(oembedUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return STATUS_AVAILABLE;
            } else if (response.getStatusCode().value() == 404) {
                return STATUS_NOT_FOUND;
            }
        } catch (Exception ignored) {
            // Fall through to scraping
        }

        // 2. Scrape video page
        String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            var entity = new org.springframework.http.HttpEntity<>(headers);
            var response = restTemplate.exchange(watchUrl, org.springframework.http.HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String content = response.getBody().toLowerCase();
                // Embed disabled
                if (content.contains("embeddable\":false") || content.contains("playback on other websites has been disabled")) {
                    return STATUS_NOT_EMBEDDABLE;
                }
                if (content.contains("private video") || content.contains("this video is private")) {
                    return STATUS_PRIVATE;
                }
                if (content.contains("sign in to confirm your age")) {
                    return STATUS_OTHER_ERROR; // Age restricted
                }
                if (content.contains("members only") || content.contains("members-only")) {
                    return STATUS_MEMBERS_ONLY;
                }
                if (content.contains("video unavailable") || content.contains("this video is not available") ||
                    content.contains("has been removed for violating youtube's terms of service")) {
                    return STATUS_NOT_FOUND;
                }
                return STATUS_AVAILABLE;
            } else if (response.getStatusCode().value() == 404) {
                return STATUS_NOT_FOUND;
            } else {
                return STATUS_OTHER_ERROR;
            }
        } catch (Exception e) {
            return STATUS_OTHER_ERROR;
        }
    }
    
    /**
     * Get current counts of video_availability values from the database.
     * Returns a map: statusCode -> count.
     */
    private Map<String, Long> getStatusCounts() {
        String jpql = "SELECT v.video_availability, COUNT(v) FROM Video v GROUP BY v.video_availability";
        Query query = entityManager.createQuery(jpql);
        List<Object[]> results = query.getResultList();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : results) {
            String status = (String) row[0];
            Long count = (Long) row[1];
            counts.put(status != null ? status : "NULL", count);
        }
        return counts;
    }

    /**
     * Format a status code with its definition.
     */
    private String getStatusDefinition(String status) {
        if (status == null || status.equals("NULL")) {
            return "Not set";
        }
        switch (status) {
            case "1":  return "Available";
            case "-1": return "Not embeddable";
            case "-2": return "Not found";
            case "-3": return "Private";
            case "-4": return "Members only";
            case "-5": return "Other error";
            default:   return "Unknown (" + status + ")";
        }
    }

    /**
     * Build a human-readable string of status counts.
     */
    private String formatStatusCounts(Map<String, Long> counts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            String status = entry.getKey();
            Long count = entry.getValue();
            String definition = getStatusDefinition(status);
            if (sb.length() > 0) sb.append(", ");
            sb.append(status).append(" (").append(definition).append("): ").append(count);
        }
        return sb.toString();
    }

}

