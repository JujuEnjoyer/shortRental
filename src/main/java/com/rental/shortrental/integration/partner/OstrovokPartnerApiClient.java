package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class OstrovokPartnerApiClient implements PartnerApiClient {
    private static final String PROVIDER = "Emerging Travel Group / Ostrovok B2B API";
    private static final String DEFAULT_VERIFY_URL =
            "https://api.worldota.net/api/b2b/v3/general/contract/data/info/";
    private static final List<String> CAPABILITIES = List.of(
            "credentialCheck",
            "hotelSearchAndBookingWhenGranted",
            "orderDataWhenGranted",
            "icalCalendar",
            "emailRelayOrExtensionForMessages"
    );
    private static final List<String> LIMITATIONS = List.of(
            "B2B API Островка не является открытым чатом арендодателя.",
            "Для сообщений с гостем нужен EMAIL_RELAY или отдельный партнёрский endpoint, если его выдадут."
    );

    private final RestClient restClient = RestClient.create();

    @Override
    public boolean supports(ExternalPlatform platform) {
        return platform == ExternalPlatform.OSTROVOK;
    }

    @Override
    public PartnerApiCheckResult verify(PartnerApiCredentials credentials) {
        if (credentials.mockRequested()) {
            return PartnerApiCheckResult.mock(ExternalPlatform.OSTROVOK, PROVIDER, CAPABILITIES, LIMITATIONS);
        }
        if (!credentials.hasClientCredentials()) {
            return failure("Для Островка нужны key id в clientId и api key в clientSecret.");
        }
        String verifyUrl = firstNonBlank(credentials.outboundEndpoint(), DEFAULT_VERIFY_URL);
        try {
            restClient.get()
                    .uri(URI.create(verifyUrl))
                    .headers(h -> h.setBasicAuth(credentials.clientId().trim(), credentials.clientSecret().trim()))
                    .retrieve()
                    .toBodilessEntity();
            return success("Basic Auth ключи приняты API Островка.", verifyUrl);
        } catch (RestClientResponseException e) {
            return failure("Островок отклонил ключи: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            return failure("Не удалось проверить API Островка: " + e.getMessage());
        }
    }

    private PartnerApiCheckResult success(String message, String verifyUrl) {
        return new PartnerApiCheckResult(
                true,
                ExternalPlatform.OSTROVOK,
                PROVIDER,
                "LIVE",
                message,
                CAPABILITIES,
                LIMITATIONS,
                Map.of("verifyUrl", verifyUrl)
        );
    }

    private PartnerApiCheckResult failure(String message) {
        return new PartnerApiCheckResult(
                false,
                ExternalPlatform.OSTROVOK,
                PROVIDER,
                "LIVE",
                message,
                CAPABILITIES,
                LIMITATIONS,
                Map.of()
        );
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
