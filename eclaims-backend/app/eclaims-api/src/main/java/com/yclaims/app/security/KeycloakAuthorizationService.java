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
 * Evaluates resource+scope permissions via Keycloak UMA 2.0 token endpoint.
 * Decisions are cached (Caffeine, 5 min) to avoid per-request HTTP round-trips.
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
                log.debug("Authz decision: user={} {}#{} -> {}", userId, resource, scope, result);
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
