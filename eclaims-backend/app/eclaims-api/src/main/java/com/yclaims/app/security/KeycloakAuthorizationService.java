package com.yclaims.app.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Delegates authorization decisions to Keycloak Authorization Services via
 * the UMA 2.0 token endpoint with response_mode=decision.
 *
 * Keycloak evaluates which role-based policies apply to the requested
 * resource#scope and returns {"result": true/false}.
 *
 * Results are cached in-memory (Caffeine) for 5 minutes to eliminate per-request
 * HTTP round-trips. Cache is keyed by userId:resource#scope.
 *
 * Policy changes in Keycloak Admin UI take effect within the next cache window (≤5 min).
 * No application code change or redeploy needed to update who can do what.
 */
@Service
@Slf4j
public class KeycloakAuthorizationService {

    @Value("${eclaims.keycloak.admin-url:http://localhost:8080}")
    private String keycloakBaseUrl;

    @Value("${eclaims.keycloak.realm:eclaims}")
    private String realm;

    @Value("${eclaims.keycloak.authz.audience:eclaims-api}")
    private String audience;

    private final RestTemplate restTemplate;

    public KeycloakAuthorizationService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    /**
     * Checks if the given user is permitted to perform {@code scope} on {@code resource}.
     *
     * @param userId    JWT subject (used only as cache key — not sent to Keycloak)
     * @param resource  Keycloak resource name, e.g. {@code "claim"}
     * @param scope     Keycloak scope name, e.g. {@code "submit"}
     * @param userToken Raw Bearer token value from the current request
     * @return {@code true} if Keycloak grants access, {@code false} otherwise (fail-closed)
     */
    @Cacheable(
        value = "authzDecisions",
        cacheManager = "caffeineCacheManager",
        key = "#userId + ':' + #resource + '#' + #scope"
    )
    public boolean isAllowed(String userId, String resource, String scope, String userToken) {
        String tokenEndpoint = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth(userToken);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket");
        body.add("audience", audience);
        body.add("permission", resource + "#" + scope);
        body.add("response_mode", "decision");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenEndpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean result = Boolean.TRUE.equals(response.getBody().get("result"));
                log.debug("Authz decision: user={} {}#{} → {}", userId, resource, scope, result);
                return result;
            }
        } catch (HttpClientErrorException.Forbidden e) {
            log.debug("Authz denied (403): user={} {}#{}", userId, resource, scope);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Authz check returned 401 for user={} — token may be expired", userId);
        } catch (Exception e) {
            log.error("Keycloak authz check failed for user={} {}#{}: {}", userId, resource, scope, e.getMessage());
        }
        return false;
    }
}
