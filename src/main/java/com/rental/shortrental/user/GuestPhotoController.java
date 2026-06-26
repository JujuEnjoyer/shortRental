package com.rental.shortrental.user;

import com.rental.shortrental.common.util.FileStorageService;
import com.rental.shortrental.security.LandlordAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GuestPhotoController {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final LandlordAccess landlordAccess;

    public GuestPhotoController(
            FileStorageService fileStorageService,
            UserRepository userRepository,
            LandlordAccess landlordAccess
    ) {
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping("/api/landlord/guests/photos/{name}")
    public ResponseEntity<byte[]> servePhoto(Authentication authentication, @PathVariable String name) {
        User landlord = landlordAccess.requireLandlord(authentication);
        if (!userRepository.existsPassportPhotoForLandlord(landlord.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        }
        byte[] data = fileStorageService.load(name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType(name))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(data);
    }

    private String contentType(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
