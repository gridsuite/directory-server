/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.PermissionDTO;
import org.gridsuite.directory.server.dto.PermissionType;
import org.gridsuite.directory.server.dto.UserGroupDTO;
import org.gridsuite.directory.server.error.DirectoryException;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.repository.PermissionEntity;
import org.gridsuite.directory.server.repository.PermissionId;
import org.gridsuite.directory.server.repository.PermissionRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.directory.server.dto.PermissionType.MANAGE;
import static org.gridsuite.directory.server.dto.PermissionType.READ;
import static org.gridsuite.directory.server.dto.PermissionType.WRITE;
import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.error.DirectoryBusinessErrorCode.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class PermissionService {
    public static final String ALL_USERS = "ALL_USERS";

    private final PermissionRepository permissionRepository;
    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryRepositoryService directoryRepositoryService;
    private final UserAdminService userAdminService;
    private final RoleService roleService;

    public PermissionService(PermissionRepository permissionRepository,
                             DirectoryElementRepository directoryElementRepository,
                             DirectoryRepositoryService directoryRepositoryService,
                             UserAdminService userAdminService,
                             RoleService roleService) {
        this.permissionRepository = permissionRepository;
        this.directoryElementRepository = directoryElementRepository;
        this.directoryRepositoryService = directoryRepositoryService;
        this.userAdminService = userAdminService;
        this.roleService = roleService;
    }

    /**
     * Checks if a user has the specified permission on given elements.
     * Checks parent permissions first, then target directory, then child permissions if recursive check is enabled.
     *
     * @param userId              User ID checking permissions for
     * @param elementUuids        List of element UUIDs to check permissions on
     * @param targetDirectoryUuid Optional target directory UUID (for move operations)
     * @param permissionType      Type of permission to check (READ, WRITE, MANAGE)
     * @param recursiveCheck      Whether to check permissions recursively on children
     */
    public void checkDirectoriesPermission(String userId, List<UUID> elementUuids, UUID targetDirectoryUuid,
                                                            PermissionType permissionType, boolean recursiveCheck) {
        switch (permissionType) {
            case READ -> checkReadPermission(userId, elementUuids);
            case WRITE -> checkWritePermission(userId, elementUuids, targetDirectoryUuid, recursiveCheck);
            case MANAGE -> checkManagePermission(userId, elementUuids);
        }
    }

    public boolean hasReadPermissions(String userId, List<UUID> elementUuids) {
        return roleService.isUserExploreAdmin() || directoryElementRepository.findAllByIdIn(elementUuids).stream().allMatch(element ->
            //If it's a directory we check its own write permission else we check the permission on the element parent directory
            checkPermission(userId, List.of(element.getType().equals(DIRECTORY) ? element.getId() : element.getParentId()), READ)
        );
    }

    public boolean hasManagePermission(String userId, List<UUID> elementUuids) {
        return roleService.isUserExploreAdmin() || directoryElementRepository.findAllByIdIn(elementUuids).stream().allMatch(element ->
            //If it's a directory we check its own write permission else we check the permission on the element parent directory
            checkPermission(userId, List.of(element.getType().equals(DIRECTORY) ? element.getId() : element.getParentId()), MANAGE)
        );
    }

    public void validateReadAccess(UUID directoryUuid, String userId) {
        if (!hasReadPermissions(userId, List.of(directoryUuid))) {
            throw DirectoryException.of(DIRECTORY_PERMISSION_DENIED, "User '%s' is not allowed to view directory '%s'", userId, directoryUuid);
        }
    }

    public void validatePermissionUpdateAccess(UUID directoryUuid, String userId) {
        if (!hasManagePermission(userId, List.of(directoryUuid))) {
            throw DirectoryException.of(DIRECTORY_PERMISSION_DENIED, "User '%s' is not allowed to update permissions on directory '%s'", userId, directoryUuid);
        }
    }

    /**
     * Retrieves the permission settings for a directory, organized by permission type.
     * Returns exactly one PermissionDTO for each permission type (READ, WRITE, MANAGE).
     *
     * @param directoryUuid The UUID of the directory
     * @return A list of exactly three permission DTOs (READ, WRITE, MANAGE)
     */
    public List<PermissionDTO> getDirectoryPermissions(UUID directoryUuid) {
        List<PermissionEntity> permissions = permissionRepository.findAllByElementId(directoryUuid);

        PermissionType allUsersPermissionLevel = extractGlobalPermissionLevel(permissions);
        Map<String, PermissionType> groupPermissionLevels = extractGroupPermissionLevels(permissions);

        return Arrays.stream(PermissionType.values())
            .map(type -> createPermissionDto(type, allUsersPermissionLevel, groupPermissionLevels))
            .collect(Collectors.toList());
    }

    public void updateDirectoryPermissions(UUID directoryUuid, List<PermissionDTO> permissions, String owner) {
        // Remove all permissions for this directory except for the owner
        permissionRepository.deleteAllByElementIdAndUserIdNot(directoryUuid, owner);

        // Apply new permissions based on the provided DTOs
        PermissionConfiguration config = buildPermissionConfiguration(permissions);
        applyPermissionConfiguration(directoryUuid, config);
    }

    public void deleteAllPermissionsForElement(UUID elementUuid) {
        permissionRepository.deleteAllByElementId(elementUuid);
    }

    public void grantOwnerManagePermission(UUID elementUuid, String ownerId) {
        permissionRepository.save(PermissionEntity.manage(elementUuid, ownerId, ""));
    }

    public void grantReadPermissionToAllUsers(UUID elementUuid) {
        permissionRepository.save(PermissionEntity.read(elementUuid, ALL_USERS, ""));
    }

    private void checkReadPermission(String userId, List<UUID> elementUuids) {
        if (!hasReadPermissions(userId, elementUuids)) {
            throw new DirectoryException(
                    DIRECTORY_PARENT_PERMISSION_DENIED,
                    "User " + userId + " does not have read permission on parent folder"
            );
        }
    }

    private void checkManagePermission(String userId, List<UUID> elementUuids) {
        if (!hasManagePermission(userId, elementUuids)) {
            throw new DirectoryException(
                    DIRECTORY_PARENT_PERMISSION_DENIED,
                    "User " + userId + " does not have manage permission on parent folder"
            );
        }
    }

    private void checkWritePermission(String userId, List<UUID> elementUuids, UUID targetDirectoryUuid, boolean recursiveCheck) {
        List<DirectoryElementEntity> elements = directoryElementRepository.findAllByIdIn(elementUuids);

        // First, check parent permissions
        for (DirectoryElementEntity element : elements) {
            UUID idToCheck = element.getType().equals(DIRECTORY) ? element.getId() : element.getParentId();
            if (!checkPermission(userId, List.of(idToCheck), WRITE)) {
                throw new DirectoryException(
                        DIRECTORY_PARENT_PERMISSION_DENIED,
                        "User " + userId + " does not have write permission on parent folder"
                );
            }
        }

        // Next, check target directory permission if specified
        if (targetDirectoryUuid != null && !checkPermission(userId, List.of(targetDirectoryUuid), WRITE)) {
            throw new DirectoryException(
                    DIRECTORY_TARGET_PERMISSION_DENIED,
                    "User " + userId + " does not have write permission on target folder"
            );
        }

        // Finally, check child permissions if recursive check is enabled
        if (recursiveCheck) {
            for (DirectoryElementEntity element : elements) {
                if (element.getType().equals(DIRECTORY)) {
                    List<UUID> descendantsUuids = directoryRepositoryService.findAllDescendants(element.getId())
                        .stream()
                        .filter(e -> e.getType().equals(DIRECTORY))
                        .map(DirectoryElementEntity::getId)
                        .toList();
                    if (!descendantsUuids.isEmpty() && !checkPermission(userId, descendantsUuids, WRITE)) {
                        throw new DirectoryException(
                                DIRECTORY_CHILD_PERMISSION_DENIED,
                                "User " + userId + " does not have write permission on descendant folder"
                        );
                    }
                }
            }
        }
    }

    private boolean checkPermission(String userId, List<UUID> elementUuids, PermissionType permissionType) {
        return elementUuids.stream().allMatch(uuid -> {
            //Check global permission first
            boolean globalPermission = checkPermission(permissionRepository.findById(new PermissionId(uuid, ALL_USERS, "")), permissionType);
            if (globalPermission) {
                return true;
            }

            //Then check user specific permission
            boolean userPermission = checkPermission(permissionRepository.findById(new PermissionId(uuid, userId, "")), permissionType);
            if (userPermission) {
                return true;
            }

            //Finally check group permission
            return userAdminService.getUserGroups(userId)
                .stream()
                .map(UserGroupDTO::id)
                .anyMatch(groupId ->
                    checkPermission(permissionRepository.findById(new PermissionId(uuid, "", groupId.toString())), permissionType)
                );
        });
    }

    private boolean checkPermission(Optional<PermissionEntity> permissionEntity, PermissionType permissionType) {
        return permissionEntity
            .map(p -> switch (permissionType) {
                case READ -> Boolean.TRUE.equals(p.getRead());
                case WRITE -> Boolean.TRUE.equals(p.getWrite());
                case MANAGE -> Boolean.TRUE.equals(p.getManage());
            })
            .orElse(false);
    }

    /**
     * Creates a PermissionDTO for the specified permission type
     * If allUsers is true for this permission, groups list will be empty
     * If allUsers is false, groups list will contain only groups with exactly this permission type
     */
    private PermissionDTO createPermissionDto(PermissionType permissionType,
                                              PermissionType allUsersPermissionLevel,
                                              Map<String, PermissionType> groupPermissionLevels) {
        boolean hasAllUsersPermission = hasPermissionLevel(allUsersPermissionLevel, permissionType);

        List<UUID> groupsWithPermission = hasAllUsersPermission
            ? Collections.emptyList()
            : getGroupsWithExactPermission(groupPermissionLevels, permissionType);

        return new PermissionDTO(hasAllUsersPermission, groupsWithPermission, permissionType);
    }

    /**
     * Gets all groups that have exactly the specified permission type
     */
    private List<UUID> getGroupsWithExactPermission(Map<String, PermissionType> groupPermissionLevels,
                                                    PermissionType exactPermissionType) {
        return groupPermissionLevels.entrySet().stream()
            .filter(entry -> entry.getValue() == exactPermissionType)
            .map(entry -> {
                try {
                    return UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Checks if the given permission level meets or exceeds the required permission
     */
    private boolean hasPermissionLevel(PermissionType actualLevel, PermissionType requiredLevel) {
        if (actualLevel == null) {
            return false;
        }

        return switch (requiredLevel) {
            case READ -> actualLevel == PermissionType.READ ||
                actualLevel == PermissionType.WRITE ||
                actualLevel == PermissionType.MANAGE;
            case WRITE -> actualLevel == PermissionType.WRITE ||
                actualLevel == PermissionType.MANAGE;
            case MANAGE -> actualLevel == PermissionType.MANAGE;
        };
    }

    /**
     * Extracts the highest permission level for ALL_USERS from permissions list
     */
    private PermissionType extractGlobalPermissionLevel(List<PermissionEntity> permissions) {
        return permissions.stream()
            .filter(p -> ALL_USERS.equals(p.getUserId()))
            .findFirst()
            .map(this::determineHighestPermission)
            .orElse(null);
    }

    /**
     * Extracts and consolidates group permissions, keeping only the highest permission level for each group
     */
    private Map<String, PermissionType> extractGroupPermissionLevels(List<PermissionEntity> permissions) {
        return permissions.stream()
            .filter(p -> !p.getUserGroupId().isEmpty())
            .collect(Collectors.toMap(
                PermissionEntity::getUserGroupId,
                this::determineHighestPermission,
                (existing, replacement) -> shouldUpdatePermission(existing, replacement) ? replacement : existing,
                HashMap::new
            ));
    }

    /**
     * Determines the highest permission level for a permission entity
     *
     * @param permission The permission entity to evaluate
     * @return The highest permission type or null if no permissions
     */
    private PermissionType determineHighestPermission(PermissionEntity permission) {
        if (permission == null) {
            return null;
        }

        if (Boolean.TRUE.equals(permission.getManage())) {
            return PermissionType.MANAGE;
        } else if (Boolean.TRUE.equals(permission.getWrite())) {
            return PermissionType.WRITE;
        } else if (Boolean.TRUE.equals(permission.getRead())) {
            return PermissionType.READ;
        }

        return null;
    }

    /**
     * Determines if a permission should be updated based on hierarchy
     * returns true if the proposed permission is higher than the current permission
     */
    private boolean shouldUpdatePermission(PermissionType current, PermissionType proposed) {
        if (proposed == null) {
            return false;
        }

        if (current == null) {
            return true;
        }

        return switch (current) {
            case READ -> proposed == PermissionType.WRITE || proposed == PermissionType.MANAGE;
            case WRITE -> proposed == PermissionType.MANAGE;
            case MANAGE -> false;
        };
    }

    /**
     * Builds a permission configuration from DTO list, resolving conflicts
     */
    private PermissionConfiguration buildPermissionConfiguration(List<PermissionDTO> permissions) {
        boolean allUsersRead = false;
        boolean allUsersWrite = false;
        boolean allUsersManage = false;
        Map<String, PermissionType> groupPermissions = new HashMap<>();

        for (PermissionDTO dto : permissions) {
            if (dto.isAllUsers()) {
                // Process permissions for all users
                switch (dto.getType()) {
                    case READ -> allUsersRead = true;
                    case WRITE -> allUsersWrite = true;
                    case MANAGE -> allUsersManage = true;
                }
            } else if (dto.getGroups() != null && !dto.getGroups().isEmpty()) {
                // Process group permissions
                processGroupPermissions(dto, groupPermissions);
            }
        }

        return new PermissionConfiguration(allUsersRead, allUsersWrite, allUsersManage, groupPermissions);
    }

    /**
     * Processes group permissions and resolves conflicts (same group with different permissions)
     */
    private void processGroupPermissions(PermissionDTO dto, Map<String, PermissionType> groupPermissions) {
        for (UUID groupId : dto.getGroups()) {
            String groupIdStr = groupId.toString();
            PermissionType currentHighest = groupPermissions.getOrDefault(groupIdStr, null);

            if (shouldUpdatePermission(currentHighest, dto.getType())) {
                groupPermissions.put(groupIdStr, dto.getType());
            }
        }
    }

    /**
     * Applies the resolved permission configuration to the directory
     */
    private void applyPermissionConfiguration(UUID directoryUuid, PermissionConfiguration config) {
        if (config.allUsersManage()) {
            addPermissionForAllUsers(directoryUuid, PermissionType.MANAGE);
        } else if (config.allUsersWrite()) {
            addPermissionForAllUsers(directoryUuid, PermissionType.WRITE);
            applyGroupPermissions(directoryUuid, config.groupPermissions(), Set.of(PermissionType.MANAGE));
        } else if (config.allUsersRead()) {
            addPermissionForAllUsers(directoryUuid, PermissionType.READ);
            applyGroupPermissions(directoryUuid, config.groupPermissions(), Set.of(PermissionType.WRITE, PermissionType.MANAGE));
        } else {
            // Apply group permissions
            config.groupPermissions().forEach((groupId, permissionType) ->
                addPermissionForGroup(directoryUuid, groupId, permissionType)
            );
        }
    }

    private void addPermissionForAllUsers(UUID elementUuid, PermissionType permissionType) {
        PermissionEntity permissionEntity = switch (permissionType) {
            case READ -> PermissionEntity.read(elementUuid, ALL_USERS, "");
            case WRITE -> PermissionEntity.write(elementUuid, ALL_USERS, "");
            case MANAGE -> PermissionEntity.manage(elementUuid, ALL_USERS, "");
        };
        permissionRepository.save(permissionEntity);
    }

    private void addPermissionForGroup(UUID elementUuid, String groupId, PermissionType permissionType) {
        PermissionEntity permissionEntity = switch (permissionType) {
            case READ -> PermissionEntity.read(elementUuid, "", groupId);
            case WRITE -> PermissionEntity.write(elementUuid, "", groupId);
            case MANAGE -> PermissionEntity.manage(elementUuid, "", groupId);
        };
        permissionRepository.save(permissionEntity);
    }

    /**
     * Applies permissions to groups belonging to the target permissions
     */
    private void applyGroupPermissions(UUID directoryUuid, Map<String, PermissionType> groupPermissions, Set<PermissionType> targetPermissions) {
        groupPermissions.entrySet().stream()
            .filter(entry -> targetPermissions.contains(entry.getValue()))
            .forEach(entry -> addPermissionForGroup(directoryUuid, entry.getKey(), entry.getValue()));
    }

    /**
     * Represents the resolved permission configuration
     */
    private record PermissionConfiguration(boolean allUsersRead, boolean allUsersWrite, boolean allUsersManage,
                                           Map<String, PermissionType> groupPermissions) {
    }
}
