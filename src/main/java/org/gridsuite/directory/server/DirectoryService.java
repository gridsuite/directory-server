/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import lombok.NonNull;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.PermissionType;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.repository.*;
import org.gridsuite.directory.server.services.DirectoryElementInfosService;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.gridsuite.directory.server.services.TimerService;
import org.gridsuite.directory.server.services.UserAdminService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Boolean.TRUE;
import static org.gridsuite.directory.server.DirectoryException.Type.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.gridsuite.directory.server.dto.PermissionType.READ;
import static org.gridsuite.directory.server.dto.PermissionType.WRITE;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class DirectoryService {
    public static final String DIRECTORY = "DIRECTORY";
    public static final String ELEMENT = "ELEMENT";
    public static final String ALL_USERS = "ALL_USERS";
    private static final int ES_PAGE_MAX_SIZE = 50;
    static final int MAX_RETRY = 3;
    static final int DELAY_RETRY = 50;

    private final NotificationService notificationService;
    private final DirectoryRepositoryService repositoryService;
    private final UserAdminService userAdminService;

    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryElementInfosService directoryElementInfosService;
    private final PermissionRepository permissionRepository;
    private final TimerService timerService;

    public DirectoryService(DirectoryRepositoryService repositoryService,
                            NotificationService notificationService,
                            DirectoryElementRepository directoryElementRepository,
                            DirectoryElementInfosService directoryElementInfosService,
                            PermissionRepository permissionRepository,
                            TimerService timerService,
                            UserAdminService userAdminService) {
        this.repositoryService = repositoryService;
        this.notificationService = notificationService;
        this.directoryElementRepository = directoryElementRepository;
        this.directoryElementInfosService = directoryElementInfosService;
        this.permissionRepository = permissionRepository;
        this.timerService = timerService;
        this.userAdminService = userAdminService;
    }

    //TODO: this consumer is the kept here at the moment, but it will be moved to explore server later on
    @Transactional
    public void studyUpdated(UUID studyUuid, String errorMessage, String userId) {
        UUID parentUuid = repositoryService.getParentUuid(studyUuid);
        Optional<DirectoryElementEntity> elementEntity = repositoryService.getElementEntity(studyUuid);
        String elementName = elementEntity.map(DirectoryElementEntity::getName).orElse(null);
        if (errorMessage != null && elementName != null) {
            deleteElementWithNotif(studyUuid, userId);
        }
        // At study creation, if the corresponding element doesn't exist here yet and doesn't have parent
        // then avoid sending a notification with parentUuid=null and isRoot=true
        if (parentUuid != null) {
            notifyDirectoryHasChanged(parentUuid, userId, elementName, errorMessage);
        }
    }

    @Transactional
    public ElementAttributes createElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId, boolean generateNewName) {
        return createElementWithNotif(elementAttributes, parentDirectoryUuid, userId, generateNewName);
    }

    private ElementAttributes createElementWithNotif(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId, boolean generateNewName) {
        if (elementAttributes.getElementName().isBlank()) {
            throw new DirectoryException(NOT_ALLOWED);
        }
        assertDirectoryExist(parentDirectoryUuid);
        DirectoryElementEntity elementEntity = insertElement(elementAttributes, parentDirectoryUuid, userId, generateNewName);
        if (DIRECTORY.equals(elementAttributes.getType())) {
            insertWriteAndReadUserPermission(elementEntity.getId(), userId, "");
            //Grants read permission for all users on created element
            insertReadGlobalUsersPermission(elementEntity.getId());
        }

        // Here we know that parentDirectoryUuid can't be null
        notifyDirectoryHasChanged(parentDirectoryUuid, userId, elementAttributes.getElementName());

        return toElementAttributes(elementEntity);
    }

    public ElementAttributes duplicateElement(UUID elementId, UUID newElementId, UUID targetDirectoryId, String userId) {
        DirectoryElementEntity directoryElementEntity = directoryElementRepository.findById(elementId).orElseThrow(() -> new DirectoryException(NOT_FOUND));
        String elementType = directoryElementEntity.getType();
        UUID parentDirectoryUuid = targetDirectoryId != null ? targetDirectoryId : directoryElementEntity.getParentId();
        ElementAttributes elementAttributes = ElementAttributes.builder()
                .type(elementType)
                .elementUuid(newElementId)
                .owner(userId)
                .description(directoryElementEntity.getDescription())
                .elementName(directoryElementEntity.getName())
                .build();

        assertDirectoryExist(parentDirectoryUuid);
        DirectoryElementEntity elementEntity = insertElement(elementAttributes, parentDirectoryUuid, userId, true);

        // Here we know that parentDirectoryUuid can't be null
        notifyDirectoryHasChanged(parentDirectoryUuid, userId, elementAttributes.getElementName());

        return toElementAttributes(elementEntity);
    }

    private void assertRootDirectoryNotExist(String rootName) {
        if (TRUE.equals(repositoryService.isRootDirectoryExist(rootName))) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void assertDirectoryExist(UUID dirUuid) {
        if (!getElement(dirUuid).getType().equals(DIRECTORY)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private DirectoryElementEntity insertElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid) {
        return insertElement(elementAttributes, parentDirectoryUuid, null, false);
    }

    private DirectoryElementEntity insertElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId, boolean generateNewName) {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        DirectoryElementEntity elementEntity = new DirectoryElementEntity(elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                parentDirectoryUuid,
                elementAttributes.getElementName(),
                elementAttributes.getType(),
                elementAttributes.getOwner(),
                elementAttributes.getDescription(),
                now,
                now,
                elementAttributes.getOwner());

        return tryInsertElement(elementEntity, parentDirectoryUuid, userId, generateNewName);
    }

    private DirectoryElementEntity tryInsertElement(DirectoryElementEntity elementEntity, UUID parentDirectoryUuid, String userId, boolean generateNewName) {
        int retryCount = 0;
        String baseElementName = elementEntity.getName();
        do {
            try {
                if (generateNewName) {
                    elementEntity.setName(getDuplicateNameCandidate(parentDirectoryUuid, baseElementName, elementEntity.getType(), userId));
                }
                return repositoryService.saveElement(elementEntity);
            } catch (DataIntegrityViolationException e) {
                if (generateNewName) {
                    retryCount++;
                } else {
                    break;
                }
            }
        } while (retryCount < MAX_RETRY && timerService.doPause(DELAY_RETRY));

        throw DirectoryException.createElementNameAlreadyExists(elementEntity.getName());
    }

    @Transactional
    public ElementAttributes createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        return createRootDirectoryWithNotif(rootDirectoryAttributes, userId);
    }

    private ElementAttributes createRootDirectoryWithNotif(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        if (rootDirectoryAttributes.getElementName().isBlank()) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        assertRootDirectoryNotExist(rootDirectoryAttributes.getElementName());
        ElementAttributes elementAttributes = toElementAttributes(insertElement(toElementAttributes(rootDirectoryAttributes), null));
        UUID elementUuid = elementAttributes.getElementUuid();
        insertWriteAndReadUserPermission(elementUuid, userId, "");
        insertReadGlobalUsersPermission(elementUuid);
        // here we know a root directory has no parent
        notificationService.emitDirectoryChanged(
                elementUuid,
                elementAttributes.getElementName(),
                userId,
                null,
                true,
                NotificationType.ADD_DIRECTORY
        );
        return elementAttributes;
    }

    @Transactional
    public void createElementInDirectoryPath(String directoryPath, ElementAttributes elementAttributes, String userId) {
        String[] directoryPathSplit = directoryPath.split("/");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID currentDirectoryUuid;
        UUID parentDirectoryUuid = null;

        for (String s : directoryPathSplit) {
            currentDirectoryUuid = getDirectoryUuid(s, parentDirectoryUuid);
            //create the directory if it doesn't exist
            if (currentDirectoryUuid == null) {
                //we create the root directory if it doesn't exist
                if (parentDirectoryUuid == null) {
                    parentDirectoryUuid = createRootDirectoryWithNotif(
                            new RootDirectoryAttributes(
                                    s,
                                    userId,
                                    null,
                                    now,
                                    now,
                                    userId),
                            userId).getElementUuid();
                } else {
                    //and then we create the rest of the path
                    parentDirectoryUuid = createElementWithNotif(
                            toElementAttributes(UUID.randomUUID(), s, DIRECTORY, userId, null, now, now, userId),
                            parentDirectoryUuid,
                            userId, false).getElementUuid();
                }
            } else {
                parentDirectoryUuid = currentDirectoryUuid;
            }
        }
        insertElement(elementAttributes, parentDirectoryUuid);
    }

    private Map<UUID, Long> getSubDirectoriesCounts(List<UUID> subDirectories, List<String> types) {
        List<DirectoryElementRepository.SubDirectoryCount> subdirectoriesCountsList = repositoryService.getSubDirectoriesCounts(subDirectories, types);
        Map<UUID, Long> subdirectoriesCountsMap = new HashMap<>();
        subdirectoriesCountsList.forEach(e -> subdirectoriesCountsMap.put(e.getId(), e.getCount()));
        return subdirectoriesCountsMap;
    }

    public List<ElementAttributes> getDirectoryElements(UUID directoryUuid, List<String> types, Boolean recursive, String userId) {
        if (!userAdminService.isUserAdmin(userId) && !hasReadPermissions(userId, List.of(directoryUuid))) {
            return List.of();
        }
        ElementAttributes elementAttributes = getElement(directoryUuid);
        if (elementAttributes == null) {
            throw DirectoryException.createElementNotFound(DIRECTORY, directoryUuid);
        }
        if (!elementAttributes.getType().equals(DIRECTORY)) {
            return List.of();
        }
        if (TRUE.equals(recursive)) {
            List<DirectoryElementEntity> descendents = repositoryService.findAllDescendants(directoryUuid).stream().toList();
            return descendents
                    .stream()
                    .filter(e -> types.isEmpty() || types.contains(e.getType()))
                    .map(ElementAttributes::toElementAttributes)
                    .toList();
        } else {
            return getAllDirectoryElementsStream(directoryUuid, types).toList();
        }
    }

    private Stream<ElementAttributes> getOnlyElementsStream(UUID directoryUuid, List<String> types) {
        return getAllDirectoryElementsStream(directoryUuid, types)
                .filter(elementAttributes -> !elementAttributes.getType().equals(DIRECTORY));
    }

    private Stream<ElementAttributes> getAllDirectoryElementsStream(UUID directoryUuid, List<String> types) {
        List<DirectoryElementEntity> directoryElements = repositoryService.findAllByParentId(directoryUuid);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesCountsMap(types, directoryElements);
        return directoryElements
                .stream()
                .filter(e -> e.getType().equals(DIRECTORY) || types.isEmpty() || types.contains(e.getType()))
                .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)));
    }

    public List<ElementAttributes> getRootDirectories(List<String> types, String userId) {

        List<DirectoryElementEntity> directoryElements = repositoryService.findRootDirectories();

        if (!userAdminService.isUserAdmin(userId)) {
            directoryElements = directoryElements.stream().filter(directoryElementEntity -> hasReadPermissions(userId, List.of(directoryElementEntity.getId()))).toList();
        }
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesCountsMap(types, directoryElements);
        return directoryElements.stream()
                .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    private Map<UUID, Long> getSubDirectoriesCountsMap(List<String> types, List<DirectoryElementEntity> directoryElements) {
        return getSubDirectoriesCounts(directoryElements.stream().map(DirectoryElementEntity::getId).toList(), types);
    }

    public void updateElement(UUID elementUuid, ElementAttributes newElementAttributes, String userId) {
        DirectoryElementEntity directoryElement = getDirectoryElementEntity(elementUuid);
        if (!directoryElement.isAttributesUpdatable(newElementAttributes, userId) ||
            !directoryElement.getName().equals(newElementAttributes.getElementName()) &&
             directoryHasElementOfNameAndType(directoryElement.getParentId(), newElementAttributes.getElementName(), directoryElement.getType())) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        DirectoryElementEntity elementEntity = repositoryService.saveElement(directoryElement.update(newElementAttributes));

        notifyDirectoryHasChanged(elementEntity.getParentId() == null ? elementUuid : elementEntity.getParentId(), userId, elementEntity.getName());
    }

    @Transactional
    public void updateElementLastModifiedAttributes(UUID elementUuid, Instant lastModificationDate, String lastModifiedBy) {
        DirectoryElementEntity elementToUpdate = getDirectoryElementEntity(elementUuid);
        elementToUpdate.updateModificationAttributes(lastModifiedBy, lastModificationDate);

    }

    @Transactional
    public void moveElementsDirectory(List<UUID> elementsUuids, UUID newDirectoryUuid, String userId) {
        if (elementsUuids.isEmpty()) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        validateNewDirectory(newDirectoryUuid);

        elementsUuids.forEach(elementUuid -> moveElementDirectory(getDirectoryElementEntity(elementUuid), newDirectoryUuid, userId));
    }

    private void moveElementDirectory(DirectoryElementEntity element, UUID newDirectoryUuid, String userId) {
        if (Objects.equals(element.getParentId(), newDirectoryUuid)) { // Same directory ?
            return;
        }

        DirectoryElementEntity oldDirectory = element.getParentId() == null ? null : repositoryService.getElementEntity(element.getParentId()).orElseThrow();
        boolean isDirectory = DIRECTORY.equals(element.getType());
        List<DirectoryElementEntity> descendents = isDirectory ? repositoryService.findAllDescendants(element.getId()).stream().toList() : List.of();

        // validate move elements
        validateElementForMove(element, newDirectoryUuid, descendents.stream().map(DirectoryElementEntity::getId).collect(Collectors.toSet()));

        // we update the parent of the moving element
        updateElementParentDirectory(element, newDirectoryUuid);

        // reindex descendents
        repositoryService.reindexElements(descendents);

        // if it has a parent, we notify it.
        // otherwise, which means it is a root, we send a notification that a root has been deleted (in this case, it moved under a new directory)
        if (oldDirectory != null) {
            notifyDirectoryHasChanged(oldDirectory.getId(), userId, element.getName(), isDirectory);
        } else {
            notifyRootDirectoryDeleted(element.getId(), userId, element.getName(), isDirectory);
        }
        notifyDirectoryHasChanged(newDirectoryUuid, userId, element.getName(), isDirectory);

    }

    private void validateElementForMove(DirectoryElementEntity element, UUID newDirectoryUuid, Set<UUID> descendentsUuids) {
        if (newDirectoryUuid == element.getId() || descendentsUuids.contains(newDirectoryUuid)) {
            throw new DirectoryException(MOVE_IN_DESCENDANT_NOT_ALLOWED);
        }

        if (directoryHasElementOfNameAndType(newDirectoryUuid, element.getName(), element.getType())) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void updateElementParentDirectory(DirectoryElementEntity element, UUID newDirectoryUuid) {
        element.setParentId(newDirectoryUuid);
        repositoryService.saveElement(element);
    }

    private void validateNewDirectory(UUID newDirectoryUuid) {
        DirectoryElementEntity newDirectory = repositoryService.getElementEntity(newDirectoryUuid)
                .orElseThrow(() -> DirectoryException.createElementNotFound(DIRECTORY, newDirectoryUuid));

        if (!newDirectory.getType().equals(DIRECTORY)) {
            throw new DirectoryException(NOT_DIRECTORY);
        }
    }

    private boolean directoryHasElementOfNameAndType(UUID directoryUUID, String elementName, String elementType) {
        return getOnlyElementsStream(directoryUUID, List.of(elementType))
            .anyMatch(
                e -> e.getElementName().equals(elementName)
            );
    }

    @Transactional
    public void deleteElement(UUID elementUuid, String userId) {
        deleteElementWithNotif(elementUuid, userId);
    }

    private void deleteElementWithNotif(UUID elementUuid, String userId) {
        ElementAttributes elementAttributes = getElement(elementUuid);

        UUID parentUuid = repositoryService.getParentUuid(elementUuid);
        deleteElement(elementAttributes, userId);
        if (parentUuid == null) {
            // We can't notify to update the parent directory of a deleted root directory
            // Then we send a specific notification
            notifyRootDirectoryDeleted(elementUuid, userId, elementAttributes.getElementName());
        } else {
            notifyDirectoryHasChanged(parentUuid, userId, elementAttributes.getElementName());
        }
    }

    private void deleteElement(ElementAttributes elementAttributes, String userId) {
        if (elementAttributes.getType().equals(DIRECTORY)) {
            deleteSubElements(elementAttributes.getElementUuid(), userId);
        }
        repositoryService.deleteElement(elementAttributes.getElementUuid());
        permissionRepository.deleteAllByElementId(elementAttributes.getElementUuid());
        notificationService.emitDeletedElement(elementAttributes.getElementUuid(), userId);
    }

    private void deleteSubElements(UUID elementUuid, String userId) {
        getAllDirectoryElementsStream(elementUuid, List.of()).forEach(elementAttributes -> deleteElement(elementAttributes, userId));
    }

    /**
     * Method to delete multiple elements within a single repository - DIRECTORIES can't be deleted this way
     * @param elementsUuids list of elements uuids to delete
     * @param parentDirectoryUuid expected parent uuid of each element - element with another parent UUID won't be deleted
     * @param userId user making the deletion
     */
    public void deleteElements(List<UUID> elementsUuids, UUID parentDirectoryUuid, String userId) {
        // getting elements by "elementUuids", filtered if they don't belong to parentDirectoryUuid, or if they are directories
        List<ElementAttributes> elementsAttributesToDelete = repositoryService.getElementEntities(elementsUuids, parentDirectoryUuid).stream()
            .map(ElementAttributes::toElementAttributes)
            .toList();

        // deleting all elements
        repositoryService.deleteElements(elementsAttributesToDelete.stream().map(ElementAttributes::getElementUuid).toList());

        // extracting elementUuids from this list, to send element deletion notifications
        elementsAttributesToDelete.stream()
            .map(ElementAttributes::getElementUuid)
            .forEach(elementUuid -> notificationService.emitDeletedElement(elementUuid, userId));

        // sending directory update notification
        notifyDirectoryHasChanged(parentDirectoryUuid, userId);
    }

    /***
     * Retrieve path of an element
     * @param elementUuid element uuid
     * @return ElementAttributes of element and all it's parents up to root directory
     */
    public List<ElementAttributes> getPath(UUID elementUuid) {
        List<ElementAttributes> path = repositoryService.getPath(elementUuid).stream().map(ElementAttributes::toElementAttributes).toList();
        if (path.isEmpty()) {
            throw DirectoryException.createElementNotFound(ELEMENT, elementUuid);
        }
        return path;
    }

    public ElementAttributes getElement(UUID elementUuid) {
        return toElementAttributes(getDirectoryElementEntity(elementUuid));
    }

    private DirectoryElementEntity getDirectoryElementEntity(UUID elementUuid) {
        return repositoryService.getElementEntity(elementUuid).orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));
    }

    public UUID getDirectoryUuid(String directoryName, UUID parentDirectoryUuid) {
        List<DirectoryElementEntity> directories;
        //If parentDirectoryUuid is null we search for a rootDirectory
        if (parentDirectoryUuid == null) {
            directories = repositoryService.findRootDirectoriesByName(directoryName);
        } else {
            directories = repositoryService.findDirectoriesByNameAndParentId(directoryName, parentDirectoryUuid);
        }
        if (!directories.isEmpty()) {
            return directories.get(0).getId();
        }
        return null;
    }

    public List<ElementAttributes> getElements(List<UUID> ids, boolean strictMode, List<String> types, String userId) {
        List<DirectoryElementEntity> elementEntities = repositoryService.findAllByIdIn(ids);

        //if the user is not an admin we filter out elements he doesn't have the permission on
        if (!userAdminService.isUserAdmin(userId)) {
            elementEntities = elementEntities.stream().filter(directoryElementEntity ->
                            hasReadPermissions(userId, List.of(directoryElementEntity.getId()))
                    ).toList();
        }

        if (strictMode && elementEntities.size() != ids.stream().distinct().count()) {
            throw new DirectoryException(NOT_FOUND);
        }

        Map<UUID, Long> subElementsCount = getSubDirectoriesCounts(elementEntities.stream().map(DirectoryElementEntity::getId).toList(), types);

        return elementEntities.stream()
                .map(attribute -> toElementAttributes(attribute, subElementsCount.getOrDefault(attribute.getId(), 0L)))
                .toList();
    }

    public int getCasesCount(String userId) {
        return directoryElementRepository.getCasesCountByOwner(userId);
    }

    public void notify(@NonNull String notificationName, @NonNull UUID elementUuid, @NonNull String userId) {
        NotificationType notification;
        try {
            notification = NotificationType.valueOf(notificationName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DirectoryException.createNotificationUnknown(notificationName);
        }

        if (notification == NotificationType.UPDATE_DIRECTORY) {
            ElementAttributes elementAttributes = getElement(elementUuid);
            UUID parentUuid = repositoryService.getParentUuid(elementUuid);

            notifyDirectoryHasChanged(parentUuid != null ? parentUuid : elementUuid, userId, elementAttributes.getElementName());
        } else {
            throw DirectoryException.createNotificationUnknown(notification.name());
        }
    }

    private String nameCandidate(String elementName, int n) {
        return elementName + '(' + n + ')';
    }

    public String getDuplicateNameCandidate(UUID directoryUuid, String elementName, String elementType, String userId) {
        if (!repositoryService.canRead(directoryUuid, userId)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
        var idLikes = new HashSet<>(repositoryService.getNameByTypeAndParentIdAndNameStartWith(elementType, directoryUuid, elementName));
        if (!idLikes.contains(elementName)) {
            return elementName;
        }
        int i = 1;
        while (idLikes.contains(nameCandidate(elementName, i))) {
            ++i;
        }
        return nameCandidate(elementName, i);
    }

    public Page<DirectoryElementInfos> searchElements(@NonNull String userInput, String directoryUuid) {
        Pageable pageRequest = PageRequest.of(0, ES_PAGE_MAX_SIZE);
        return directoryElementInfosService.searchElements(userInput, directoryUuid, pageRequest);
    }

    public UUID getDirectoryUuidFromPath(List<String> directoryPath) {
        UUID parentDirectoryUuid = null;

        for (String s : directoryPath) {
            UUID currentDirectoryUuid = getDirectoryUuid(s, parentDirectoryUuid);
            if (currentDirectoryUuid == null) {
                throw new DirectoryException(NOT_FOUND);
            } else {
                parentDirectoryUuid = currentDirectoryUuid;
            }
        }
        return parentDirectoryUuid;
    }

    // Everytime we make a change inside a directory, we must send a notification
    // to tell the directory has changed, please update its metadata and content data (UPDATE_DIRECTORY)
    // Note: for a root directory without parent directory, if one of its metadata changes (name,...)
    // then we send a notification on this directory
    private void notifyDirectoryHasChanged(UUID directoryUuid, String userId) {
        Objects.requireNonNull(directoryUuid);
        notifyDirectoryHasChanged(directoryUuid, userId, null, null, false);
    }

    private void notifyDirectoryHasChanged(UUID directoryUuid, String userId, String elementName) {
        notifyDirectoryHasChanged(directoryUuid, userId, elementName, false);
    }

    private void notifyDirectoryHasChanged(UUID directoryUuid, String userId, String elementName, boolean isDirectoryMoving) {
        Objects.requireNonNull(directoryUuid);
        notifyDirectoryHasChanged(directoryUuid, userId, elementName, null, isDirectoryMoving);
    }

    private void notifyDirectoryHasChanged(UUID directoryUuid, String userId, String elementName, String error) {
        notifyDirectoryHasChanged(directoryUuid, userId, elementName, error, false);
    }

    private void notifyDirectoryHasChanged(UUID directoryUuid, String userId, String elementName, String error, boolean isDirectoryMoving) {
        Objects.requireNonNull(directoryUuid);
        notificationService.emitDirectoryChanged(
                directoryUuid,
                elementName,
                userId,
                error,
                repositoryService.isRootDirectory(directoryUuid),
                isDirectoryMoving,
                NotificationType.UPDATE_DIRECTORY
        );
    }

    // Root directories don't have parent directories. Then if on is deleted, we must send a specific notification
    private void notifyRootDirectoryDeleted(UUID rootDirectoryUuid, String userId, String elementName) {
        Objects.requireNonNull(rootDirectoryUuid);
        notifyRootDirectoryDeleted(rootDirectoryUuid, userId, elementName, false);
    }

    private void notifyRootDirectoryDeleted(UUID rootDirectoryUuid, String userId, String elementName, boolean isDirectoryMoving) {
        Objects.requireNonNull(rootDirectoryUuid);
        notifyRootDirectoryDeleted(rootDirectoryUuid, userId, elementName, null, isDirectoryMoving);
    }

    private void notifyRootDirectoryDeleted(UUID rootDirectoryUuid, String userId, String elementName, String error, boolean isDirectoryMoving) {
        Objects.requireNonNull(rootDirectoryUuid);
        notificationService.emitDirectoryChanged(
                rootDirectoryUuid,
                elementName,
                userId,
                error,
                true,
                isDirectoryMoving,
                NotificationType.DELETE_DIRECTORY
        );
    }

    public boolean hasPermission(String userId, List<UUID> elementUuids, UUID targetDirectoryUuid, PermissionType permissionType, boolean recursiveCheck) {
        if (permissionType.equals(WRITE)) {
            return hasWritePermission(userId, elementUuids, targetDirectoryUuid, recursiveCheck);
        } else {
            return hasReadPermissions(userId, elementUuids);
        }
    }

    public boolean hasReadPermissions(String userId, List<UUID> elementUuids) {
        List<DirectoryElementEntity> elements = directoryElementRepository.findAllByIdIn(elementUuids);
        return elements.stream().allMatch(element ->
                //If it's a directory we check its own write permission else we check the permission on the element parent directory
                checkPermission(userId, List.of(element.getType().equals(DIRECTORY) ? element.getId() : element.getParentId()), READ)
        );
    }

    private boolean checkPermission(String userId, List<UUID> elementUuids, PermissionType permissionType) {
        return elementUuids.stream().allMatch(uuid -> {
            //Check global permission first
            boolean globalPermission = checkPermission(permissionRepository.findById(new PermissionId(uuid, ALL_USERS, "")), permissionType);
            if (globalPermission) {
                return true;
            }
            //Then check user specific permission
            return checkPermission(permissionRepository.findById(new PermissionId(uuid, userId, "")), permissionType);
        });
    }

    private boolean checkPermission(Optional<PermissionEntity> permissionEntity, PermissionType permissionType) {
        return permissionEntity
                .map(p -> {
                    if (permissionType == WRITE) {
                        return Boolean.TRUE.equals(p.getWrite());
                    }
                    return Boolean.TRUE.equals(p.getRead());
                })
                .orElse(false);
    }

    private boolean hasWritePermission(String userId, List<UUID> elementUuids, UUID targetDirectoryUuid, boolean recursiveCheck) {
        List<DirectoryElementEntity> elements = directoryElementRepository.findAllByIdIn(elementUuids);
        return elements.stream().allMatch(element -> {
            if (recursiveCheck && element.getType().equals(DIRECTORY)) {
                List<UUID> descendentsUuids = repositoryService.findAllDescendants(element.getId()).stream().map(DirectoryElementEntity::getId).toList();
                if(!checkPermission(userId, descendentsUuids, WRITE)) {
                    throw new DirectoryException(CONFLICT);
                }
            }

            //If it's a directory we check its own write permission else we check the permission on the element parent directory
            return checkPermission(userId, List.of(element.getType().equals(DIRECTORY) ? element.getId() : element.getParentId()), WRITE);
        }) && (targetDirectoryUuid == null || checkPermission(userId, List.of(targetDirectoryUuid), WRITE));
    }

    private void insertReadUserPermission(UUID elementUuid, String userId, String userGroupId) {
        PermissionEntity permissionEntity = PermissionEntity.read(elementUuid, userId, userGroupId);
        permissionRepository.save(permissionEntity);
    }

    private void insertWriteAndReadUserPermission(UUID elementUuid, String userId, String userGroupId) {
        PermissionEntity permissionEntity = PermissionEntity.write(elementUuid, userId, userGroupId);
        permissionRepository.save(permissionEntity);
    }

    private void insertReadGlobalUsersPermission(UUID elementUuid) {
        insertReadUserPermission(elementUuid, ALL_USERS, "");
    }
}
