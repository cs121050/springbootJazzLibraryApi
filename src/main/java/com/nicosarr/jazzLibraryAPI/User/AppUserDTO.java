package com.nicosarr.jazzLibraryAPI.User;

public class AppUserDTO {
    private String firebaseUid;
    private String email;
    private String displayName;
    private String role;

    public AppUserDTO() {}

    public AppUserDTO(String firebaseUid, String email, String displayName, String role) {
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }

    public static AppUserDTO fromEntity(AppUser user) {
        return new AppUserDTO(
            user.getFirebaseUid(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole()
        );
    }

    // Getters and Setters
    public String getFirebaseUid() { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid) { this.firebaseUid = firebaseUid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}