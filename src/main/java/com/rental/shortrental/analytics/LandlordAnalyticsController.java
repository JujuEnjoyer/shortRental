package com.rental.shortrental.analytics;

import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/landlord/analytics")
public class LandlordAnalyticsController {

    private final AnalyticsService analyticsService;
    private final LandlordAccess landlordAccess;

    public LandlordAnalyticsController(AnalyticsService analyticsService, LandlordAccess landlordAccess) {
        this.analyticsService = analyticsService;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping("/summary")
    public AnalyticsService.AnalyticsSummary summary(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return analyticsService.buildSummary(landlord);
    }
}
