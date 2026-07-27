package com.nicosarr.jazzLibraryAPI.User;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AppUserService {

    @Autowired
    private AppUserRep userRepository;
    
    @Autowired
    private FirebaseAuth firebaseAuth;

    /**
     * Get or create user from Firebase token (JIT provisioning)
     */
    @Transactional
    public AppUser getOrCreateUserFromToken(String idToken) throws Exception {
        // 1. Verify the token with Firebase
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
        
        // 2. Extract user info
        String uid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String name = decodedToken.getName();
        
        // 3. Check if user exists in local database
        Optional<AppUser> existingUser = userRepository.findByFirebaseUid(uid);
        
        if (existingUser.isPresent()) {
            // Update existing user
            AppUser user = existingUser.get();
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
            }
            if (name != null && !name.equals(user.getDisplayName())) {
                user.setDisplayName(name);
            }
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        } else {
            // Create new user with default 'user' role
            AppUser newUser = new AppUser();
            newUser.setFirebaseUid(uid);
            newUser.setEmail(email != null ? email : "no-email@example.com");
            newUser.setDisplayName(name != null ? name : "Unknown User");
            newUser.setRole("user");
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setUpdatedAt(LocalDateTime.now());
            
            System.out.println("🆕 New user created in local DB: " + uid);
            return userRepository.save(newUser);
        }
    }

    public Optional<AppUser> getUserByUid(String uid) {
        return userRepository.findByFirebaseUid(uid);
    }

    @Transactional
    public AppUser updateUserRole(String uid, String newRole) {
        AppUser user = userRepository.findByFirebaseUid(uid)
            .orElseThrow(() -> new RuntimeException("User not found with UID: " + uid));
        
        user.setRole(newRole);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public boolean isAdmin(String uid) {
        return userRepository.findByFirebaseUid(uid)
            .map(AppUser::isAdmin)
            .orElse(false);
    }

    public Iterable<AppUser> getAllUsers() {
        return userRepository.findAll();
    }
}