

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

}
