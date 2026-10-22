package com.mfg.ims.common.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/**
 * SSRF guard for webhook targets. A target must be an https URL without credentials whose host resolves only to
 * public addresses: loopback, link-local (including the cloud metadata address 169.254.169.254), private, carrier-grade
 * NAT, unique-local, multicast, unspecified and reserved ranges are all refused. The check runs when a subscription is
 * saved and again immediately before every delivery. A DNS answer can still change between that check and the
 * connection, so production should also restrict egress at the network layer.
 *
 * <p>{@code ims.integration.webhook.allow-private-targets} (default false) relaxes the address and scheme checks for
 * local development and the integration tests only.
 */
@Component
public class WebhookTargetValidator {

    private final boolean allowPrivateTargets;

    public WebhookTargetValidator(
            @Value("${ims.integration.webhook.allow-private-targets:false}") boolean allowPrivateTargets) {
        this.allowPrivateTargets = allowPrivateTargets;
    }

    /** @throws BusinessRuleException with a reason that never echoes resolved addresses */
    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw invalid("the URL is not valid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        boolean schemeOk = scheme.equals("https") || (allowPrivateTargets && scheme.equals("http"));
        if (!schemeOk) {
            throw invalid("only https URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw invalid("credentials in the URL are not allowed");
        }
        if (uri.getFragment() != null) {
            throw invalid("a URL fragment is not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw invalid("the URL has no host");
        }
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw invalid("the port is out of range");
        }
        if (allowPrivateTargets) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw invalid("the host does not resolve");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw invalid("the host resolves to an address that is not publicly routable");
            }
        }
    }

    static boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            int b2 = b[2] & 0xFF;
            return b0 == 0                                   // 0.0.0.0/8
                    || (b0 == 100 && (b1 & 0xC0) == 64)      // 100.64.0.0/10 carrier-grade NAT
                    || (b0 == 192 && b1 == 0 && b2 == 0)     // 192.0.0.0/24 IETF protocol assignments
                    || (b0 == 198 && (b1 & 0xFE) == 18)      // 198.18.0.0/15 benchmarking
                    || b0 >= 240;                            // 240.0.0.0/4 reserved and broadcast
        }
        return (b[0] & 0xFE) == 0xFC;                        // fc00::/7 unique local
    }

    private static BusinessRuleException invalid(String reason) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Webhook target refused: " + reason);
    }
}
