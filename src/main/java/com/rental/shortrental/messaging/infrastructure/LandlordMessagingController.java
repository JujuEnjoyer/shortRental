package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.MessageSyncService;
import com.rental.shortrental.messaging.application.ReplyMessageRequest;
import com.rental.shortrental.messaging.entity.GuestMessage;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landlord/messages")
public class LandlordMessagingController {
    private final MessageSyncService messageSyncService;
    private final LandlordAccess landlordAccess;

    public LandlordMessagingController(MessageSyncService messageSyncService, LandlordAccess landlordAccess) {
        this.messageSyncService = messageSyncService;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping
    public List<GuestMessage> list(Authentication authentication, @RequestParam(required = false) String platform) {
        User landlord = landlordAccess.requireLandlord(authentication);
        ExternalPlatform p = parsePlatform(platform);
        return messageSyncService.getOwnerMessages(landlord.getId(), p);
    }

    private static ExternalPlatform parsePlatform(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ExternalPlatform.valueOf(raw.trim().toUpperCase());
    }

    @PostMapping("/sync")
    public Map<String, Object> sync(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        int imported = messageSyncService.syncOwnerMessages(landlord.getId());
        return Map.of("ownerId", landlord.getId(), "imported", imported);
    }

    @PostMapping("/reply")
    public GuestMessage reply(Authentication authentication, @RequestBody ReplyMessageRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return messageSyncService.reply(landlord.getId(), request);
    }
}
