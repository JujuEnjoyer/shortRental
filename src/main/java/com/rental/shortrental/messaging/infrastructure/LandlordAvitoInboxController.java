package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.messaging.application.AvitoChatListItem;
import com.rental.shortrental.messaging.application.AvitoLandlordInboxService;
import com.rental.shortrental.messaging.application.AvitoThreadMessageDto;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/landlord/messages/avito")
public class LandlordAvitoInboxController {

    private final AvitoLandlordInboxService inboxService;
    private final LandlordAccess landlordAccess;

    public LandlordAvitoInboxController(AvitoLandlordInboxService inboxService, LandlordAccess landlordAccess) {
        this.inboxService = inboxService;
        this.landlordAccess = landlordAccess;
    }

    /**
     * All Avito chats for the landlord (every enabled Avito OPEN_API integration), with pagination on the Avito side.
     */
    @GetMapping("/chats")
    public List<AvitoChatListItem> chats(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return inboxService.listAllChats(landlord.getId());
    }

    @GetMapping("/messages")
    public List<AvitoThreadMessageDto> messages(
            Authentication authentication,
            @RequestParam("propertyId") long propertyId,
            @RequestParam("chatId") String chatId
    ) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return inboxService.listThreadMessages(landlord.getId(), propertyId, chatId);
    }
}
