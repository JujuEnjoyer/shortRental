package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class SutochnoPartnerApiClient implements PartnerApiClient {
    private static final String PROVIDER = "Travelpayouts / Sutochno.ru API";
    private static final String DEFAULT_VERIFY_URL =
            "https://api.travelpayouts.com/statistics/v1/get_fields_list";
    private static final List<String> CAPABILITIES = List.of(
            "credentialCheck",
            "affiliateStatistics",
            "sutochnoOffersWhenProgramGranted",
            "icalCalendar",
            "emailRelayOrExtensionForMessages"
    );
    private static final List<String> LIMITATIONS = List.of(
            "Публичный API Суточно.ру через Travelpayouts описывает партнёрские данные, а не кабинетный чат арендодателя.",
            "Для ответов гостю нужен EMAIL_RELAY или отдельный API-доступ от Суточно.ру."
    );

    private final RestClient restClient = RestClient.create();

    @Override
    public boolean supports(ExternalPlatform platform) {
        return platform == ExternalPlatform.SUTOCHNO;
    }

    @Override
    public PartnerApiCheckResult verify(PartnerApiCredentials credentials) {
        if (credentials.mockRequested()) {
            return PartnerApiCheckResult.mock(ExternalPlatform.SUTOCHNO, PROVIDER, CAPABILITIES, LIMITATIONS);
        }
        if (!credentials.hasToken()) {
            return failure("Для Суточно.ру/Travelpayouts нужен API token.");
        }
        String verifyUrl = firstNonBlank(credentials.outboundEndpoint(), DEFAULT_VERIFY_URL);
        try {
            restClient.get()
                    .uri(URI.create(verifyUrl))
                    .header("X-Access-Token", credentials.authToken().trim())
                    .retrieve()
                    .toBodilessEntity();
            return success("Travelpayouts token принят; доступ к конкретной программе Суточно зависит от аккаунта.", verifyUrl);
        } catch (RestClientResponseException e) {
            return failure("Travelpayouts/Sutochno API отклонил token: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            return failure("Не удалось проверить Суточно API: " + e.getMessage());
        }
    }

    private PartnerApiCheckResult success(String message, String verifyUrl) {
        return new PartnerApiCheckResult(
                true,
                ExternalPlatform.SUTOCHNO,
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
                ExternalPlatform.SUTOCHNO,
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
