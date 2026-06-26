package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.integration.partner.messaging.LivePartnerMessagingClient;
import com.rental.shortrental.integration.partner.messaging.LivePartnerMessagingClientRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class LiveMessagingPartnerApiClient implements PartnerApiClient {
    private final LivePartnerMessagingClientRegistry messagingClients;
    private final RestClient restClient = RestClient.create();

    public LiveMessagingPartnerApiClient(LivePartnerMessagingClientRegistry messagingClients) {
        this.messagingClients = messagingClients;
    }

    @Override
    public boolean supports(ExternalPlatform platform) {
        return messagingClients.find(platform).isPresent();
    }

    @Override
    public PartnerApiCheckResult verify(PartnerApiCredentials credentials) {
        LivePartnerMessagingClient client = messagingClients.find(credentials.platform())
                .orElseThrow();
        List<String> capabilities = client.capabilities();
        List<String> limitations = List.of("Доступ к messaging API может зависеть от тарифа, партнёрства или прав API-ключа.");
        if (credentials.mockRequested()) {
            return PartnerApiCheckResult.mock(credentials.platform(), client.providerName(), capabilities, limitations);
        }
        if (!credentials.hasToken()) {
            return result(false, credentials.platform(), client.providerName(), "LIVE",
                    "Для " + client.providerName() + " нужен authToken/API key.", capabilities, limitations, Map.of());
        }
        String verifyUrl = firstNonBlank(credentials.outboundEndpoint(), defaultVerifyUrl(credentials.platform()));
        try {
            restClient.get()
                    .uri(URI.create(verifyUrl))
                    .headers(headers -> {
                        if (credentials.platform() == ExternalPlatform.CHANNEX) {
                            headers.set("user-api-key", credentials.authToken().trim());
                        } else {
                            headers.setBearerAuth(credentials.authToken().trim());
                        }
                        if (credentials.platform() == ExternalPlatform.BOOKING
                                && credentials.platformUserId() != null
                                && !credentials.platformUserId().isBlank()) {
                            headers.set("X-Affiliate-Id", credentials.platformUserId().trim());
                        }
                    })
                    .retrieve()
                    .toBodilessEntity();
            return result(true, credentials.platform(), client.providerName(), "LIVE",
                    "Ключ принят, live messaging adapter можно использовать.", capabilities, limitations,
                    Map.of("verifyUrl", verifyUrl));
        } catch (RestClientResponseException e) {
            return result(false, credentials.platform(), client.providerName(), "LIVE",
                    client.providerName() + " отклонил ключ: HTTP " + e.getStatusCode().value(),
                    capabilities, limitations, Map.of("verifyUrl", verifyUrl));
        } catch (Exception e) {
            return result(false, credentials.platform(), client.providerName(), "LIVE",
                    "Не удалось проверить API: " + e.getMessage(),
                    capabilities, limitations, Map.of("verifyUrl", verifyUrl));
        }
    }

    private static String defaultVerifyUrl(ExternalPlatform platform) {
        return switch (platform) {
            case HOSTAWAY -> "https://api.hostaway.com/v1/listings?limit=1";
            case CHANNEX -> "https://app.channex.io/api/v1/properties?limit=1";
            case BOOKING -> "https://demandapi.booking.com/3.1/accommodations/constants";
            case GUESTY -> "https://open-api.guesty.com/v1/listings?limit=1";
            default -> throw new IllegalArgumentException("No live messaging verify URL for " + platform);
        };
    }

    private static PartnerApiCheckResult result(
            boolean success,
            ExternalPlatform platform,
            String provider,
            String mode,
            String message,
            List<String> capabilities,
            List<String> limitations,
            Map<String, Object> details
    ) {
        return new PartnerApiCheckResult(success, platform, provider, mode, message, capabilities, limitations, details);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
