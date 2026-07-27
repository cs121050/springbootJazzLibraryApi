package com.nicosarr.jazzLibraryAPI.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/users")
public class AppUserCntr {

    @Autowired
    private AppUserService userService;

    /**
     * GET /api/users/me - Get current user's profile
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            String idToken = getTokenFromRequest(request);
            AppUser user = userService.getOrCreateUserFromToken(idToken);
            return ResponseEntity.ok(AppUserDTO.fromEntity(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing token: " + e.getMessage()));
        }
    }

    /**
     * GET /api/users/isAdmin - Check if current user is admin
     */
    @GetMapping("/isAdmin")
    public ResponseEntity<Map<String, Boolean>> isAdmin(HttpServletRequest request) {
        try {
            String idToken = getTokenFromRequest(request);
            AppUser user = userService.getOrCreateUserFromToken(idToken);
            
            Map<String, Boolean> response = new HashMap<>();
            response.put("isAdmin", user.isAdmin());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * GET /api/users/all - List all users (ADMIN ONLY)
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(HttpServletRequest request) {
        try {
            String idToken = getTokenFromRequest(request);
            AppUser currentUser = userService.getOrCreateUserFromToken(idToken);
            
            if (!currentUser.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin privileges required"));
            }
            
            Iterable<AppUser> users = userService.getAllUsers();
            List<AppUserDTO> userDTOs = StreamSupport.stream(users.spliterator(), false)
                .map(AppUserDTO::fromEntity)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(userDTOs);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * PUT /api/users/{uid}/role?role=admin - Update user role (ADMIN ONLY)
     */
    @PutMapping("/{uid}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String uid,
            @RequestParam String role,
            HttpServletRequest request) {
        try {
            String idToken = getTokenFromRequest(request);
            AppUser currentUser = userService.getOrCreateUserFromToken(idToken);
            
            if (!currentUser.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin privileges required"));
            }
            
            // Validate the role
            if (!role.equals("user") && !role.equals("admin") && !role.equals("developer")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Role must be 'user', 'admin', or 'developer'"));
            }
            
            AppUser updatedUser = userService.updateUserRole(uid, role);
            return ResponseEntity.ok(AppUserDTO.fromEntity(updatedUser));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Helper method to extract token from Authorization header
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new RuntimeException("No token provided");
    }
}