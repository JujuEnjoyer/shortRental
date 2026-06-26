package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.AvitoChatListItem;
import com.rental.shortrental.messaging.application.AvitoThreadMessageDto;
import com.rental.shortrental.messaging.application.PartnerPlatformInboxService;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/landlord/messages/partner")
public class LandlordPartnerPlatformInboxController {
    private static final Set<ExternalPlatform> SUPPORTED = Set.of(
            ExternalPlatform.SUTOCHNO,
            ExternalPlatform.YANDEX_TRAVEL,
            ExternalPlatform.OSTROVOK,
            ExternalPlatform.HOSTAWAY,
            ExternalPlatform.CHANNEX,
            ExternalPlatform.BOOKING,
            ExternalPlatform.GUESTY
    );

    private final PartnerPlatformInboxService inboxService;
    private final LandlordAccess landlordAccess;

    public LandlordPartnerPlatformInboxController(
            PartnerPlatformInboxService inboxService,
            LandlordAccess landlordAccess
    ) {
        this.inboxService = inboxService;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping("/chats")
    public List<AvitoChatListItem> chats(
            Authentication authentication,
            @RequestParam("platform") ExternalPlatform platform
    ) {
        requireSupported(platform);
        User landlord = landlordAccess.requireLandlord(authentication);
        return inboxService.listAllChats(landlord.getId(), platform);
    }

    @GetMapping("/messages")
    public List<AvitoThreadMessageDto> messages(
            Authentication authentication,
            @RequestParam("platform") ExternalPlatform platform,
            @RequestParam("propertyId") long propertyId,
            @RequestParam("chatId") String chatId
    ) {
        requireSupported(platform);
        User landlord = landlordAccess.requireLandlord(authentication);
        return inboxService.listThreadMessages(landlord.getId(), platform, propertyId, chatId);
    }

    private static void requireSupported(ExternalPlatform platform) {
        if (!SUPPORTED.contains(platform)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported partner platform " + platform);
        }
    }
}
