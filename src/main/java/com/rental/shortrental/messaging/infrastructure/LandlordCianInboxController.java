package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.messaging.application.AvitoChatListItem;
import com.rental.shortrental.messaging.application.AvitoThreadMessageDto;
import com.rental.shortrental.messaging.application.CianLandlordInboxService;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/landlord/messages/cian")
public class LandlordCianInboxController {

    private final CianLandlordInboxService inboxService;
    private final LandlordAccess landlordAccess;

    public LandlordCianInboxController(CianLandlordInboxService inboxService, LandlordAccess landlordAccess) {
        this.inboxService = inboxService;
        this.landlordAccess = landlordAccess;
    }

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
