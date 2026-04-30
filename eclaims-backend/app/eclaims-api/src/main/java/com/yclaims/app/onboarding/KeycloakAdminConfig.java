package com.yclaims.app.onboarding;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak Admin REST client configuration.
 * Used exclusively by OnboardingApplicationService for customer self-registration.
 *
 * Enterprise note: In production, replace with provisioning API of chosen CIAM
 * (Okta Management API, Auth0 Management API, ForgeRock AM, etc.).
 * The OnboardingApplicationService depends on the Keycloak bean via constructor injection
 * so it can be swapped by changing only this configuration class.
 */
@Configuration
public class KeycloakAdminConfig {

    @Value("${eclaims.keycloak.admin-url:http://localhost:8080}")
    private String adminUrl;

    @Value("${eclaims.keycloak.realm:eclaims}")
    private String realm;

    @Value("${eclaims.keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${eclaims.keycloak.admin-password:admin}")
    private String adminPassword;

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(adminUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    @Bean
    public String keycloakTargetRealm() {
        return realm;
    }
}
