package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class YandexTravelPartnerApiClient implements PartnerApiClient {
    private static final String PROVIDER = "Yandex Travel Partner API";
    private static final String DEFAULT_VERIFY_URL =
            "https://whitelabel.travel.yandex-net.ru/hotels/hotel/?hotel_id=1019057204";
    private static final List<String> CAPABILITIES = List.of(
            "credentialCheck",
            "partnerHotelData",
            "bookingDataWhenGranted",
            "icalCalendar",
            "emailRelayOrExtensionForMessages"
    );
    private static final List<String> LIMITATIONS = List.of(
            "В публичных партнёрских доках нет универсального chat/send-message endpoint для арендодателя.",
            "Для реальных сообщений используйте EMAIL_RELAY или отдельный партнёрский endpoint, если он доступен."
    );

    private final RestClient restClient = RestClient.create();

    @Override
    public boolean supports(ExternalPlatform platform) {
        return platform == ExternalPlatform.YANDEX_TRAVEL;
    }

    @Override
    public PartnerApiCheckResult verify(PartnerApiCredentials credentials) {
        if (credentials.mockRequested()) {
            return PartnerApiCheckResult.mock(ExternalPlatform.YANDEX_TRAVEL, PROVIDER, CAPABILITIES, LIMITATIONS);
        }
        if (!credentials.hasToken()) {
            return failure("Для Яндекс Путешествий нужен партнёрский token/OAuth token.");
        }
        String verifyUrl = firstNonBlank(credentials.outboundEndpoint(), DEFAULT_VERIFY_URL);
        try {
            requestWithAuth(verifyUrl, "Bearer " + credentials.authToken().trim());
            return success("Токен принят через Bearer Authorization.", verifyUrl);
        } catch (RestClientResponseException bearerError) {
            if (!isAuthError(bearerError.getStatusCode())) {
                return success("Endpoint ответил не 401/403; токен дошёл до партнёрского API.", verifyUrl);
            }
            try {
                requestWithAuth(verifyUrl, "OAuth " + credentials.authToken().trim());
                return success("Токен принят через OAuth Authorization.", verifyUrl);
            } catch (RestClientResponseException oauthError) {
                return failure("Яндекс Путешествия отклонили токен: HTTP " + oauthError.getStatusCode().value());
            }
        } catch (Exception e) {
            return failure("Не удалось проверить Яндекс API: " + e.getMessage());
        }
    }

    private void requestWithAuth(String verifyUrl, String authorization) {
        restClient.get()
                .uri(URI.create(verifyUrl))
                .header("Authorization", authorization)
                .retrieve()
                .toBodilessEntity();
    }

    private PartnerApiCheckResult success(String message, String verifyUrl) {
        return new PartnerApiCheckResult(
                true,
                ExternalPlatform.YANDEX_TRAVEL,
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
                ExternalPlatform.YANDEX_TRAVEL,
                PROVIDER,
                "LIVE",
                message,
                CAPABILITIES,
                LIMITATIONS,
                Map.of()
        );
    }

    private static boolean isAuthError(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
