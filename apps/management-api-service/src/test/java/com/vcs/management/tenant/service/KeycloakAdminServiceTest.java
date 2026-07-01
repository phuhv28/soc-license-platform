package com.vcs.management.tenant.service;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceTest {

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    @Mock
    private RolesResource rolesResource;

    @Mock
    private RoleResource roleResource;

    @Mock
    private RoleMappingResource roleMappingResource;
    
    @Mock
    private RoleScopeResource roleScopeResource;

    private KeycloakAdminService keycloakAdminService;

    @BeforeEach
    void setUp() {
        keycloakAdminService = new KeycloakAdminService();
        ReflectionTestUtils.setField(keycloakAdminService, "keycloak", keycloak);
    }

    @Test
    @DisplayName("Should successfully create a Keycloak user")
    void testCreateTenantUser_Success() {
        // Arrange Keycloak mocks
        when(keycloak.realm("soc-platform")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getLocation()).thenReturn(URI.create("http://localhost/auth/admin/realms/soc-platform/users/user-123-id"));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(mockResponse);

        // Mock getting created user
        when(usersResource.get("user-123-id")).thenReturn(userResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("TENANT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());

        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        
        UserRepresentation returnedUser = new UserRepresentation();
        when(userResource.toRepresentation()).thenReturn(returnedUser);

        // Act
        String tenantId = UUID.randomUUID().toString();
        keycloakAdminService.createTenantUser("Test Tenant", tenantId, "custom_user");

        // Assert user creation arguments
        ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        UserRepresentation capturedUser = userCaptor.getValue();
        assertEquals("custom_user", capturedUser.getUsername());
        assertEquals("Test Tenant", capturedUser.getFirstName());
        assertEquals(tenantId, capturedUser.getAttributes().get("tenantId").get(0));

        // Assert role added and attributes updated
        verify(userResource).resetPassword(any());
        verify(roleScopeResource).add(anyList());
        verify(userResource).update(any(UserRepresentation.class));
    }

    @Test
    @DisplayName("Should handle creation failure gracefully (e.g. 409 Conflict)")
    void testCreateTenantUser_Failure() {
        // Arrange Keycloak mocks
        when(keycloak.realm("soc-platform")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        // Mock response
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(409); // Conflict
        Response.StatusType statusInfo = mock(Response.StatusType.class);
        when(mockResponse.getStatusInfo()).thenReturn(statusInfo);
        when(statusInfo.getReasonPhrase()).thenReturn("Conflict");
        
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(mockResponse);

        // Act
        keycloakAdminService.createTenantUser("Test Tenant", UUID.randomUUID().toString(), null);

        // Assert
        verify(usersResource).create(any(UserRepresentation.class));
        verify(mockResponse).getStatusInfo(); // Logs the error
        verifyNoInteractions(userResource); // Should not proceed to set password or roles
    }

    @Test
    @DisplayName("Should handle exception during Keycloak communication")
    void testCreateTenantUser_Exception() {
        // Arrange
        when(keycloak.realm(anyString())).thenThrow(new RuntimeException("Connection Refused"));

        // Act (Should not throw exception to caller)
        keycloakAdminService.createTenantUser("Test Tenant", UUID.randomUUID().toString(), null);

        // Assert
        verify(keycloak).realm("soc-platform");
        verifyNoInteractions(usersResource);
    }
}
