package com.nicosarr.jazzLibraryAPI.Config;

import com.nicosarr.jazzLibraryAPI.User.AppUser;
import com.nicosarr.jazzLibraryAPI.User.AppUserService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UserContextFilter implements Filter {

    @Autowired
    private AppUserService userService;
    
    private static final ThreadLocal<AppUser> currentUser = new ThreadLocal<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String idToken = authHeader.substring(7);
                AppUser user = userService.getOrCreateUserFromToken(idToken);
                currentUser.set(user);
            }
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            // Token invalid - still allow the request but user will be null
            currentUser.remove();
            chain.doFilter(request, response);
        } finally {
            currentUser.remove();
        }
    }
    
    public static AppUser getCurrentUser() {
        return currentUser.get();
    }
}