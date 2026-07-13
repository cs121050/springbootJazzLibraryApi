package com.nicosarr.jazzLibraryAPI.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AppUserRep extends JpaRepository<AppUser, String> {

    Optional<AppUser> findByFirebaseUid(String firebaseUid);
    
    Optional<AppUser> findByEmail(String email);
    
    boolean existsByFirebaseUid(String firebaseUid);
}