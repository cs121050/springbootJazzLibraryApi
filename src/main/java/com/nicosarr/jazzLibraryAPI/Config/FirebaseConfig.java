package com.nicosarr.jazzLibraryAPI.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final String ENV_BASE64 = "FIREBASE_SERVICE_ACCOUNT_BASE64";
    private static final String DEV_FILE = "service-account.json";

    @PostConstruct
    public void initialize() throws Exception {
        InputStream serviceAccount = null;

        // 1. Try Base64 environment variable (PRODUCTION)
        String base64Encoded = System.getenv(ENV_BASE64);
        if (base64Encoded != null && !base64Encoded.isEmpty()) {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Encoded);
            serviceAccount = new ByteArrayInputStream(decodedBytes);
            System.out.println("✅ Firebase: Loaded from " + ENV_BASE64 + " environment variable");
        }

        // 2. Fallback: Load from classpath (DEVELOPMENT)
        if (serviceAccount == null) {
            try {
                serviceAccount = new ClassPathResource(DEV_FILE).getInputStream();
                System.out.println("✅ Firebase: Loaded from classpath: " + DEV_FILE + " (development mode)");
            } catch (Exception e) {
                // File not found, continue
            }
        }

        // 3. If still null, throw a clear error
        if (serviceAccount == null) {
            throw new IllegalStateException(
                "\n❌ Firebase credentials not found! Please set one of:\n" +
                "  1. " + ENV_BASE64 + " environment variable (Base64 encoded JSON) - for production\n" +
                "  2. Place service-account.json in src/main/resources/ - for development\n"
            );
        }

        // Initialize Firebase
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            System.out.println("✅ Firebase Admin SDK initialized successfully!");
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}