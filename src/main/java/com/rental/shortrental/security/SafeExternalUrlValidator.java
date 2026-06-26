package com.rental.shortrental.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

@Component
public class SafeExternalUrlValidator {

    private final boolean blockPrivateHosts;

    public SafeExternalUrlValidator(
            @Value("${app.security.external-url.block-private-hosts:true}") boolean blockPrivateHosts
    ) {
        this.blockPrivateHosts = blockPrivateHosts;
    }

    public URI requireSafeHttpUrl(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " is required");
        }

        URI uri = parse(value.trim(), fieldName);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw badRequest(fieldName + " must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw badRequest(fieldName + " must contain host");
        }

        if (blockPrivateHosts) {
            ensurePublicHost(uri.getHost(), fieldName);
        }
        return uri;
    }

    public String normalizeOptionalExternalEndpoint(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("://")) {
            return requireSafeHttpUrl(trimmed, fieldName).toString();
        }
        return trimmed;
    }

    private URI parse(String value, String fieldName) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw badRequest(fieldName + " has invalid URL format");
        }
    }

    private void ensurePublicHost(String host, String fieldName) {
        String normalizedHost = host.trim().toLowerCase();
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw badRequest(fieldName + " cannot point to localhost");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw badRequest(fieldName + " host cannot be resolved");
        }

        for (InetAddress address : addresses) {
            if (isPrivateOrLocal(address)) {
                throw badRequest(fieldName + " cannot point to private or local network");
            }
        }
    }

    private boolean isPrivateOrLocal(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isUniqueLocalIpv6(address);
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte first = address.getAddress()[0];
        return (first & 0xfe) == 0xfc;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
