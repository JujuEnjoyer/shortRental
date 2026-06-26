package com.rental.shortrental.privacy;

import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/landlord/settings/retention")
public class LandlordRetentionController {

    private final DataRetentionService dataRetentionService;
    private final LandlordAccess landlordAccess;

    public LandlordRetentionController(DataRetentionService dataRetentionService, LandlordAccess landlordAccess) {
        this.dataRetentionService = dataRetentionService;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping
    public DataRetentionPolicy get(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return dataRetentionService.getOrCreatePolicy(landlord);
    }

    @PutMapping
    public DataRetentionPolicy update(
            Authentication authentication,
            @RequestBody DataRetentionService.UpdateRetentionPolicyRequest request
    ) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return dataRetentionService.updatePolicy(landlord, request);
    }

    @PostMapping("/purge")
    public DataRetentionService.RetentionPurgeResult purge(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return dataRetentionService.purgeExpired(landlord);
    }
}
