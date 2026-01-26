/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.DirectoryService;
import org.gridsuite.directory.server.dto.PermissionType;
import org.gridsuite.directory.server.dto.UserGroupDTO;
import org.gridsuite.directory.server.error.DirectoryBusinessErrorCode;
import org.gridsuite.directory.server.error.DirectoryException;
import org.gridsuite.directory.server.repository.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.services.PermissionService.ALL_USERS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private DirectoryElementRepository directoryElementRepository;

    @Mock
    private UserAdminService userAdminService;

    @InjectMocks
    private PermissionService permissionService;

    @ParameterizedTest
    @ArgumentsSource(PermissionsAndElementTypeArguments.class)
    void checkPermissionOnDirectoryWhenItIsAuthorizedByGlobalPermission(
            PermissionType permissionType,
            String elementType
    ) {
        String user = "user";
        DirectoryElementEntity element = mockAnElement(elementType);
        mockGlobalPermission(permissionType, element);
        mockSimpleUser();

        assertDoesNotThrow(() -> permissionService.checkDirectoriesPermission(
                user,
                List.of(element.getId()),
                null,
                permissionType,
                false));
    }

    @ParameterizedTest
    @ArgumentsSource(PermissionsAndElementTypeArguments.class)
    void checkPermissionOnDirectoryWhenItIsAuthorizedByUserPermission(
            PermissionType permissionType,
            String elementType
    ) {
        String user = "user";
        DirectoryElementEntity element = mockAnElement(elementType);
        mockUserPermission(permissionType, element, user);
        mockSimpleUser();

        assertDoesNotThrow(() -> permissionService.checkDirectoriesPermission(
                user,
                List.of(element.getId()),
                null,
                permissionType,
                false));
    }

    @ParameterizedTest
    @ArgumentsSource(PermissionsAndElementTypeArguments.class)
    void checkPermissionOnDirectoryWhenItIsAuthorizedByGroupPermission(
            PermissionType permissionType,
            String elementType
    ) {
        String user = "user";
        String group = "group";
        DirectoryElementEntity element = mockAnElement(elementType);
        UUID groupId = mockGroup(user, group);
        mockGroupPermission(permissionType, element, groupId);
        mockSimpleUser();

        assertDoesNotThrow(() -> permissionService.checkDirectoriesPermission(
                user,
                List.of(element.getId()),
                null,
                permissionType,
                false));
    }

    @ParameterizedTest
    @ArgumentsSource(PermissionsAndElementTypeArguments.class)
    void checkPermissionOnDirectoryWhenItIsAuthorizedByAdmin(PermissionType permissionType, String elementType) {
        String user = "user";
        DirectoryElementEntity element = mockAnElement(elementType);
        mockAdmin();

        assertDoesNotThrow(() -> permissionService.checkDirectoriesPermission(
                user,
                List.of(element.getId()),
                null,
                permissionType,
                false));
    }

    @ParameterizedTest
    @ArgumentsSource(PermissionsAndElementTypeArguments.class)
    void checkPermissionOnDirectoryWhenItIsNotAuthorized(PermissionType permissionType, String elementType) {
        String user = "user";
        List<UUID> elementUuids = List.of(mockAnElement(elementType).getId());
        mockSimpleUser();
        mockNoGroups(user);

        DirectoryException directoryException = assertThrows(
                DirectoryException.class, () -> permissionService.checkDirectoriesPermission(
                        user,
                        elementUuids,
                        null,
                        permissionType,
                        false));
        assertEquals(
                DirectoryBusinessErrorCode.DIRECTORY_PARENT_PERMISSION_DENIED,
                directoryException.getBusinessErrorCode());
    }

    private DirectoryElementEntity mockAnElement(String elementType) {
        UUID elementUuid = UUID.randomUUID();
        UUID parentUuid = UUID.randomUUID();
        DirectoryElementEntity elementEntity = new DirectoryElementEntity();
        elementEntity.setId(elementUuid);
        elementEntity.setParentId(parentUuid);
        elementEntity.setType(elementType);
        lenient().when(directoryElementRepository.findAllByIdIn(List.of(elementUuid)))
                .thenReturn(List.of(elementEntity));
        return elementEntity;
    }

    private void mockGlobalPermission(PermissionType permissionType, DirectoryElementEntity element) {
        mockPermission(element, ALL_USERS, "", permissionType);
    }

    private void mockUserPermission(PermissionType permissionType, DirectoryElementEntity element, String user) {
        mockPermission(element, user, "", permissionType);
    }

    private void mockGroupPermission(PermissionType permissionType, DirectoryElementEntity element, UUID groupId) {
        mockPermission(element, "", groupId.toString(), permissionType);
    }

    private void mockPermission(
            DirectoryElementEntity element,
            String allUsers,
            String userGroupId,
            PermissionType permissionType
    ) {
        UUID elementUuid = element.getType()
                                   .equals(DirectoryService.DIRECTORY) ? element.getId() : element.getParentId();
        PermissionId permissionId = new PermissionId(elementUuid, allUsers, userGroupId);
        PermissionEntity permissionEntity = new PermissionEntity();
        switch (permissionType) {
            case READ -> permissionEntity.setRead(true);
            case WRITE -> permissionEntity.setWrite(true);
            case MANAGE -> permissionEntity.setManage(true);
        }
        lenient().when(permissionRepository.findById(any()))
                .thenReturn(Optional.empty());
        lenient().when(permissionRepository.findById(permissionId))
                .thenReturn(Optional.of(permissionEntity));
    }

    private void mockSimpleUser() {
        lenient().when(roleService.isUserExploreAdmin())
                .thenReturn(false);
    }

    private void mockAdmin() {
        when(roleService.isUserExploreAdmin()).thenReturn(true);
    }

    private void mockNoGroups(String user) {
        when(userAdminService.getUserGroups(user)).thenReturn(List.of());
    }

    private UUID mockGroup(String user, String group) {
        UUID groupId = UUID.randomUUID();
        UserGroupDTO userGroupDTO = new UserGroupDTO(groupId, group, Set.of(user));
        lenient().when(userAdminService.getUserGroups(user))
                .thenReturn(List.of(userGroupDTO));
        return groupId;
    }

    static class PermissionsAndElementTypeArguments implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(PermissionType.READ, DirectoryService.DIRECTORY),
                    Arguments.of(PermissionType.WRITE, DirectoryService.DIRECTORY),
                    Arguments.of(PermissionType.MANAGE, DirectoryService.DIRECTORY),
                    Arguments.of(PermissionType.READ, DirectoryService.ELEMENT),
                    Arguments.of(PermissionType.WRITE, DirectoryService.ELEMENT),
                    Arguments.of(PermissionType.MANAGE, DirectoryService.ELEMENT)
            );
        }
    }
}
