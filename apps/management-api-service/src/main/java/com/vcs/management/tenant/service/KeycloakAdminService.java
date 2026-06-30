package com.vcs.management.tenant.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String adminRealm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.username}")
    private String username;

    @Value("${keycloak.admin.password}")
    private String password;

    private static final String TARGET_REALM = "soc-platform";
    private static final String DEFAULT_TENANT_PASSWORD = "tenant123";
    private static final String TENANT_ROLE = "TENANT";

    private Keycloak keycloak;

    @PostConstruct
    public void init() {
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)
                .clientId(clientId)
                .username(username)
                .password(password)
                .build();
    }

    @PreDestroy
    public void cleanup() {
        if (this.keycloak != null) {
            this.keycloak.close();
        }
    }

    public void createTenantUser(String tenantName, String tenantId, String customUsername) {
        String generatedUsername = (customUsername != null && !customUsername.isBlank())
                ? customUsername.trim()
                : tenantName.replaceAll("\\s+", "").toLowerCase();

        UserRepresentation user = new UserRepresentation();
        user.setUsername(generatedUsername);
        user.setEnabled(true);
        user.setFirstName(tenantName);
        user.setAttributes(Map.of("tenantId", List.of(tenantId)));

        RealmResource realmResource = keycloak.realm(TARGET_REALM);
        UsersResource usersResource = realmResource.users();

        try (Response response = usersResource.create(user)) {
            if (response.getStatus() == 201) {
                String userId = CreatedResponseUtil.getCreatedId(response);
                log.info("Created Keycloak user with id: {}", userId);

                UserResource userResource = usersResource.get(userId);

                // Set password
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(DEFAULT_TENANT_PASSWORD);
                credential.setTemporary(false);
                userResource.resetPassword(credential);

                // Assign TENANT role
                RoleRepresentation tenantRole = realmResource.roles().get(TENANT_ROLE).toRepresentation();
                userResource.roles().realmLevel().add(Collections.singletonList(tenantRole));

                // Force update attributes
                UserRepresentation createdUser = userResource.toRepresentation();
                createdUser.singleAttribute("tenantId", tenantId);
                userResource.update(createdUser);
                
                log.info("Assigned TENANT role, set attributes, and initialized password for user: {}", generatedUsername);
            } else {
                log.error("Failed to create user. Status: {}, Reason: {}", response.getStatus(), response.getStatusInfo().getReasonPhrase());
            }
        } catch (Exception e) {
            log.error("Exception while creating Keycloak user", e);
        }
    }
}
