/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.services.DirectoryElementInfosService;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.gridsuite.directory.server.services.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Pair;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryException.Type.*;
import static org.gridsuite.directory.server.NotificationService.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class DirectoryService {
    public static final String STUDY = "STUDY";
    public static final String CONTINGENCY_LIST = "CONTINGENCY_LIST";
    public static final String FILTER = "FILTER";
    public static final String MODIFICATION = "MODIFICATION";
    public static final String DIRECTORY = "DIRECTORY";
    public static final String CASE = "CASE";
    public static final String ELEMENT = "ELEMENT";
    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String UPDATE_TYPE_STUDIES = "studies";
    private static final String CATEGORY_BROKER_INPUT = DirectoryService.class.getName() + ".input-broker-messages";
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);

    private final StudyService studyService;

    private final NotificationService notificationService;

    private final DirectoryRepositoryService repositoryService;

    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryElementInfosService directoryElementInfosService;

    public DirectoryService(DirectoryRepositoryService repositoryService,
                            StudyService studyService,
                            NotificationService notificationService,
                            DirectoryElementRepository directoryElementRepository,
                            DirectoryElementInfosService directoryElementInfosService) {
        this.repositoryService = repositoryService;
        this.studyService = studyService;
        this.notificationService = notificationService;
        this.directoryElementRepository = directoryElementRepository;
        this.directoryElementInfosService = directoryElementInfosService;
    }

    /* notifications */
    @Bean
    public Consumer<Message<String>> consumeStudyUpdate() {
        LOGGER.info(CATEGORY_BROKER_INPUT);
        return message -> {
            try {
                String studyUuidHeader = message.getHeaders().get(HEADER_STUDY_UUID, String.class);
                String error = message.getHeaders().get(HEADER_ERROR, String.class);
                String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
                String updateType = message.getHeaders().get(HEADER_UPDATE_TYPE, String.class);
                // UPDATE_TYPE_STUDIES is the update type used when inserting or duplicating studies, and when a study import fails
                if (UPDATE_TYPE_STUDIES.equals(updateType) && studyUuidHeader != null) {
                    UUID studyUuid = UUID.fromString(studyUuidHeader);

                    UUID parentUuid = repositoryService.getParentUuid(studyUuid);
                    Optional<DirectoryElementEntity> elementEntity = repositoryService.getElementEntity(studyUuid);
                    String elementName = elementEntity.map(DirectoryElementEntity::getName).orElse(null);
                    if (error != null && elementName != null) {
                        deleteElement(studyUuid, userId);
                    }
                    boolean isPrivate = isPrivateForNotification(parentUuid, repositoryService.isPrivateDirectory(studyUuid));
                    notificationService.emitDirectoryChanged(parentUuid, elementName, userId, error, isPrivate, parentUuid == null, NotificationType.UPDATE_DIRECTORY);
                }
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            }
        };
    }

    /* methods */
    public ElementAttributes createElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId, Boolean allowNewName) {
        if (elementAttributes.getElementName().isBlank()) {
            throw new DirectoryException(NOT_ALLOWED);
        }
        if (Boolean.TRUE.equals(allowNewName)) {
            // use another available name if necessary
            elementAttributes.setElementName(getDuplicateNameCandidate(parentDirectoryUuid, elementAttributes.getElementName(), elementAttributes.getType(), userId));
        }
        assertElementNotExist(parentDirectoryUuid, elementAttributes.getElementName(), elementAttributes.getType());
        assertAccessibleDirectory(parentDirectoryUuid, userId);
        DirectoryElementEntity elementEntity = insertElement(elementAttributes, parentDirectoryUuid);
        var isCurrentElementPrivate = elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null;

        notificationService.emitDirectoryChanged(
                parentDirectoryUuid,
                elementAttributes.getElementName(),
                userId,
                null,
                isPrivateForNotification(parentDirectoryUuid, isCurrentElementPrivate),
                false,
                NotificationType.UPDATE_DIRECTORY
        );

        return toElementAttributes(elementEntity);
    }

    public ElementAttributes duplicateElement(UUID elementId, UUID newElementId, UUID targetDirectoryId, String userId) {
        DirectoryElementEntity directoryElementEntity = directoryElementRepository.findById(elementId).orElseThrow(() -> new DirectoryException(NOT_FOUND));
        String elementType = directoryElementEntity.getType();
        UUID parentDirectoryUuid = targetDirectoryId != null ? targetDirectoryId : directoryElementEntity.getParentId();
        String newElementName = getDuplicateNameCandidate(parentDirectoryUuid, directoryElementEntity.getName(), elementType, userId);
        ElementAttributes elementAttributes = ElementAttributes.builder()
                .type(elementType)
                .elementUuid(newElementId)
                .owner(userId)
                .description(directoryElementEntity.getDescription())
                .elementName(newElementName)
                .build();

        assertElementNotExist(parentDirectoryUuid, newElementName, elementType);
        assertAccessibleDirectory(parentDirectoryUuid, userId);
        DirectoryElementEntity elementEntity = insertElement(elementAttributes, parentDirectoryUuid);
        Boolean isCurrentElementPrivate = elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null;

        notificationService.emitDirectoryChanged(
                parentDirectoryUuid,
                elementAttributes.getElementName(),
                userId,
                null,
                isPrivateForNotification(parentDirectoryUuid, isCurrentElementPrivate),
                false,
                NotificationType.UPDATE_DIRECTORY
        );
        return toElementAttributes(elementEntity);
    }

    private void assertElementNotExist(UUID parentDirectoryUuid, String elementName, String type) {
        if (Boolean.TRUE.equals(repositoryService.isElementExists(parentDirectoryUuid, elementName, type))) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void assertRootDirectoryNotExist(String rootName) {
        if (Boolean.TRUE.equals(repositoryService.isRootDirectoryExist(rootName))) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void assertAccessibleDirectory(UUID dirUuid, String user) {
        if (!getElement(dirUuid).isAllowed(user)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    /* methods */
    private DirectoryElementEntity insertElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid) {
        //We need to limit the precision to avoid database precision storage limit issue (postgres has a precision of 6 digits while h2 can go to 9)
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        return repositoryService.saveElement(
                new DirectoryElementEntity(elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                        parentDirectoryUuid,
                        elementAttributes.getElementName(),
                        elementAttributes.getType(),
                        elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null,
                        elementAttributes.getOwner(),
                        elementAttributes.getDescription(),
                        now,
                        now,
                        elementAttributes.getOwner(),
                        false,
                        null
                )
        );
    }

    public ElementAttributes createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        if (rootDirectoryAttributes.getElementName().isBlank()) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        assertRootDirectoryNotExist(rootDirectoryAttributes.getElementName());
        ElementAttributes elementAttributes = toElementAttributes(insertElement(toElementAttributes(rootDirectoryAttributes), null));

        notificationService.emitDirectoryChanged(
                elementAttributes.getElementUuid(),
                elementAttributes.getElementName(),
                userId,
                null,
                elementAttributes.getAccessRights().isPrivate(),
                true,
                NotificationType.ADD_DIRECTORY
        );

        return elementAttributes;
    }

    public void createElementInDirectoryPath(String directoryPath, ElementAttributes elementAttributes, String userId) {
        String[] directoryPathSplit = directoryPath.split("/");
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        UUID currentDirectoryUuid;
        UUID parentDirectoryUuid = null;

        for (String s : directoryPathSplit) {
            currentDirectoryUuid = getDirectoryUuid(s, parentDirectoryUuid);
            //create the directory if it doesn't exist
            if (currentDirectoryUuid == null) {
                //we create the root directory if it doesn't exist
                if (parentDirectoryUuid == null) {
                    parentDirectoryUuid = createRootDirectory(
                            new RootDirectoryAttributes(
                                    s,
                                    new AccessRightsAttributes(false),
                                    userId,
                                    null,
                                    now,
                                    now,
                                    userId),
                            userId).getElementUuid();
                } else {
                    //and then we create the rest of the path
                    parentDirectoryUuid = createElement(
                            toElementAttributes(UUID.randomUUID(), s, DIRECTORY,
                                    false, userId, null, now, now, userId),
                            parentDirectoryUuid,
                            userId, false).getElementUuid();
                }
            } else {
                parentDirectoryUuid = currentDirectoryUuid;
            }
        }
        insertElement(elementAttributes, parentDirectoryUuid);
    }

    private Map<UUID, Long> getSubElementsCount(List<UUID> subDirectories, List<String> types) {
        List<DirectoryElementRepository.SubDirectoryCount> subdirectoriesCountsList = repositoryService.getSubdirectoriesCounts(subDirectories, types);
        Map<UUID, Long> subdirectoriesCountsMap = new HashMap<>();
        subdirectoriesCountsList.forEach(e -> subdirectoriesCountsMap.put(e.getId(), e.getCount()));
        return subdirectoriesCountsMap;
    }

    private Map<UUID, Long> getSubElementsCount(List<UUID> subDirectories, List<String> types, String userId) {
        List<DirectoryElementRepository.SubDirectoryCount> subdirectoriesCountsList = repositoryService.getSubdirectoriesCounts(subDirectories, types, userId);
        Map<UUID, Long> subdirectoriesCountsMap = new HashMap<>();
        subdirectoriesCountsList.forEach(e -> subdirectoriesCountsMap.put(e.getId(), e.getCount()));
        return subdirectoriesCountsMap;
    }

    public List<ElementAttributes> getDirectoryElements(UUID directoryUuid, String userId, List<String> types) {
        ElementAttributes elementAttributes = getElement(directoryUuid);
        if (elementAttributes == null) {
            throw DirectoryException.createElementNotFound(DIRECTORY, directoryUuid);
        }

        if (!elementAttributes.isAllowed(userId)) {
            return List.of();
        }

        return getDirectoryElementsStream(directoryUuid, userId, types).toList();
    }

    private Stream<ElementAttributes> getDirectoryElementsStream(UUID directoryUuid, String userId, List<String> types) {
        return getAllDirectoryElementsStream(directoryUuid, types, userId)
                .filter(elementAttributes -> !elementAttributes.getType().equals(DIRECTORY) || elementAttributes.isAllowed(userId));
    }

    private Stream<ElementAttributes> getAllDirectoryElementsStream(UUID directoryUuid, List<String> types, String userId) {
        List<DirectoryElementEntity> directoryElements = repositoryService.findAllByParentId(directoryUuid);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesCountMap(userId, types, directoryElements);
        return directoryElements
                .stream()
                .filter(e -> e.getType().equals(DIRECTORY) || types.isEmpty() || types.contains(e.getType()))
                .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)));
    }

    public List<ElementAttributes> getRootDirectories(String userId, List<String> types) {
        List<DirectoryElementEntity> directoryElements = repositoryService.findRootDirectoriesByUserId(userId);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesCountMap(userId, types, directoryElements);
        return directoryElements.stream()
                .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    private Map<UUID, Long> getSubDirectoriesCountMap(String userId, List<String> types, List<DirectoryElementEntity> directoryElements) {
        if (!types.isEmpty()) {
            return getSubElementsCount(directoryElements.stream().map(DirectoryElementEntity::getId).toList(), types);
        } else {
            return getSubElementsCount(directoryElements.stream().map(DirectoryElementEntity::getId).toList(), types, userId);
        }
    }

    public void updateElement(UUID elementUuid, ElementAttributes newElementAttributes, String userId) {
        DirectoryElementEntity directoryElement = getDirectoryElementEntity(elementUuid);
        if (!isElementUpdatable(toElementAttributes(directoryElement), userId, false) ||
            !directoryElement.isAttributesUpdatable(newElementAttributes, userId) ||
            !directoryElement.getName().equals(newElementAttributes.getElementName()) &&
             directoryHasElementOfNameAndType(directoryElement.getParentId(), userId, newElementAttributes.getElementName(), directoryElement.getType())) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        DirectoryElementEntity elementEntity = repositoryService.saveElement(directoryElement.update(newElementAttributes));

        notificationService.emitDirectoryChanged(
                elementEntity.getParentId() == null ? elementUuid : elementEntity.getParentId(),
                elementEntity.getName(),
                userId,
                null,
                isPrivateForNotification(elementEntity.getParentId(), false),
                elementEntity.getParentId() == null,
                NotificationType.UPDATE_DIRECTORY
        );

        //true if we updated a study name
        if (elementEntity.getType().equals(STUDY) && StringUtils.isNotBlank(newElementAttributes.getElementName())) {
            studyService.notifyStudyUpdate(elementUuid, userId);
        }
    }

    @Transactional
    public void updateElementLastModifiedAttributes(UUID elementUuid, LocalDateTime lastModificationDate, String lastModifiedBy) {
        DirectoryElementEntity elementToUpdate = getDirectoryElementEntity(elementUuid);
        elementToUpdate.updateModificationAttributes(lastModifiedBy, lastModificationDate);

    }

    public void updateElementsDirectory(List<UUID> elementsUuids, UUID newDirectoryUuid, String userId) {
        if (elementsUuids.isEmpty()) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        validateNewDirectory(newDirectoryUuid);

        elementsUuids.forEach(elementUuid -> updateElementDirectory(elementUuid, newDirectoryUuid, userId));
    }

    private void updateElementDirectory(UUID elementUuid, UUID newDirectoryUuid, String userId) {
        DirectoryElementEntity element = repositoryService.getElementEntity(elementUuid)
                .orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));

        validateElementForUpdate(element, newDirectoryUuid, userId);

        DirectoryElementEntity oldDirectory = repositoryService.getElementEntity(element.getParentId()).orElseThrow();
        updateElementParentDirectory(element, newDirectoryUuid);
        emitDirectoryChangedNotifications(element, oldDirectory, userId);

        notifyStudyUpdateIfApplicable(element, userId);
    }

    private void validateElementForUpdate(DirectoryElementEntity element, UUID newDirectoryUuid, String userId) {
        if (element.getType().equals(DIRECTORY)) {
            throw new DirectoryException(IS_DIRECTORY);
        }
        if (!isElementUpdatable(toElementAttributes(element), userId, false) ||
                directoryHasElementOfNameAndType(newDirectoryUuid, userId, element.getName(), element.getType())) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void updateElementParentDirectory(DirectoryElementEntity element, UUID newDirectoryUuid) {
        element.setParentId(newDirectoryUuid);
        repositoryService.saveElement(element);
    }

    private void emitDirectoryChangedNotifications(DirectoryElementEntity element, DirectoryElementEntity oldDirectory, String userId) {
        boolean isPrivate = isPrivateForNotification(element.getParentId(), false);
        notificationService.emitDirectoryChanged(element.getParentId(), element.getName(), userId, null, isPrivate, repositoryService.isRootDirectory(element.getId()), NotificationType.UPDATE_DIRECTORY);
        notificationService.emitDirectoryChanged(oldDirectory.getId(), element.getName(), userId, null, isPrivateForNotification(oldDirectory.getId(), false), repositoryService.isRootDirectory(element.getId()), NotificationType.UPDATE_DIRECTORY);
    }

    private void notifyStudyUpdateIfApplicable(DirectoryElementEntity element, String userId) {
        if (element.getType().equals(STUDY)) {
            studyService.notifyStudyUpdate(element.getId(), userId);
        }
    }

    private void validateNewDirectory(UUID newDirectoryUuid) {
        DirectoryElementEntity newDirectory = repositoryService.getElementEntity(newDirectoryUuid)
                .orElseThrow(() -> DirectoryException.createElementNotFound(DIRECTORY, newDirectoryUuid));

        if (!newDirectory.getType().equals(DIRECTORY)) {
            throw new DirectoryException(NOT_DIRECTORY);
        }
    }

    private boolean directoryHasElementOfNameAndType(UUID directoryUUID, String userId, String elementName, String elementType) {
        return getDirectoryElementsStream(directoryUUID, userId, List.of(elementType))
            .anyMatch(
                e -> e.getElementName().equals(elementName)
            );
    }

    private boolean isElementUpdatable(ElementAttributes element, String userId, boolean forDeletion) {
        if (element.getType().equals(DIRECTORY)) {
            return element.isAllowed(userId) &&
                (!forDeletion || getDirectoryElementsStream(element.getElementUuid(), userId, List.of())
                    .filter(e -> e.getType().equals(DIRECTORY))
                    .allMatch(e -> isElementUpdatable(e, userId, true))
                );
        } else {
            return getParentElement(element.getElementUuid()).isAllowed(userId);
        }
    }

    public void deleteElement(UUID elementUuid, String userId) {
        ElementAttributes elementAttributes = getElement(elementUuid);

        if (elementAttributes == null || !isElementUpdatable(elementAttributes, userId, true)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
        UUID parentUuid = repositoryService.getParentUuid(elementUuid);
        deleteElement(elementAttributes, userId);
        var isCurrentElementPrivate = elementAttributes.getAccessRights() != null ? elementAttributes.getAccessRights().isPrivate() : null;
        boolean isPrivate = isPrivateForNotification(parentUuid, isCurrentElementPrivate);

        notificationService.emitDirectoryChanged(
                parentUuid == null ? elementUuid : parentUuid,
                elementAttributes.getElementName(),
                userId,
                null,
                isPrivate,
                parentUuid == null,
                parentUuid == null ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY
        );
    }

    private void deleteElement(ElementAttributes elementAttributes, String userId) {
        if (elementAttributes.getType().equals(DIRECTORY)) {
            deleteSubElements(elementAttributes.getElementUuid(), userId);
        }
        repositoryService.deleteElement(elementAttributes.getElementUuid());
        if (STUDY.equals(elementAttributes.getType())) {
            notificationService.emitDeletedStudy(elementAttributes.getElementUuid(), userId);
        }
    }

    private void deleteSubElements(UUID elementUuid, String userId) {
        getAllDirectoryElementsStream(elementUuid, List.of(), userId).forEach(elementAttributes -> deleteElement(elementAttributes, userId));
    }

    /**
     * Method to delete multiple elements within a single repository - DIRECTORIES can't be deleted this way
     * @param elementsUuids list of elements uuids to delete
     * @param parentDirectoryUuid expected parent uuid of each element - element with another parent UUID won't be deleted
     * @param userId user making the deletion
     */
    public void deleteElements(List<UUID> elementsUuids, UUID parentDirectoryUuid, String userId) {
        ElementAttributes parentDirectory = getElement(parentDirectoryUuid);
        // checking if parent uuid can be updated by user
        if (!parentDirectory.isAllowed(userId)) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        // getting elements by "elementUuids", filtered if they don't belong to parentDirectoryUuid, or if they are directories
        List<ElementAttributes> elementsAttributesToDelete = repositoryService.getElementEntities(elementsUuids, parentDirectoryUuid).stream()
            .map(ElementAttributes::toElementAttributes)
            .toList();

        // deleting all elements
        repositoryService.deleteElements(elementsAttributesToDelete.stream().map(ElementAttributes::getElementUuid).toList());

        // extracting studyUuids from this list, to send specific notifications
        elementsAttributesToDelete.stream()
            .filter(element -> STUDY.equals(element.getType())).map(ElementAttributes::getElementUuid)
            .forEach(studyUuid -> notificationService.emitDeletedStudy(studyUuid, userId));

        // sending directory update notification
        boolean isPrivate = repositoryService.isPrivateDirectory(parentDirectoryUuid);
        notificationService.emitDirectoryChanged(
            parentDirectoryUuid,
            null,
            userId,
            null,
            isPrivate,
            false,
            NotificationType.UPDATE_DIRECTORY
        );
    }

    /***
     * Retrieve path of an element
     * @param elementUuid element uuid
     * @return ElementAttributes of element and all it's parents up to root directory
     */
    public List<ElementAttributes> getPath(UUID elementUuid) {
        DirectoryElementEntity currentElement = repositoryService.getElementEntity(elementUuid)
                .orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));

        List<ElementAttributes> path = new ArrayList<>(List.of(toElementAttributes(currentElement)));
        path.addAll(repositoryService.findAllAscendants(elementUuid).stream().map(ElementAttributes::toElementAttributes).toList());

        return path;
    }

    public ElementAttributes getElement(UUID elementUuid) {
        return toElementAttributes(getDirectoryElementEntity(elementUuid));
    }

    private DirectoryElementEntity getDirectoryElementEntity(UUID elementUuid) {
        return repositoryService.getElementEntity(elementUuid).orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));
    }

    private ElementAttributes getParentElement(UUID elementUuid) {
        return repositoryService.getElementEntity(repositoryService.getParentUuid(elementUuid))
                .map(ElementAttributes::toElementAttributes)
                .orElseThrow(() -> DirectoryException.createElementNotFound(DIRECTORY, elementUuid));
    }

    private boolean isPrivateForNotification(UUID parentDirectoryUuid, Boolean isCurrentElementPrivate) {
        if (parentDirectoryUuid == null) {
            return Boolean.TRUE.equals(isCurrentElementPrivate); // null may only come from borked REST request
        } else {
            return repositoryService.isPrivateDirectory(parentDirectoryUuid);
        }
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

    public List<ElementAttributes> getElements(List<UUID> ids, boolean strictMode, List<String> types) {
        List<DirectoryElementEntity> elementEntities = repositoryService.findAllByIdIn(ids);

        if (strictMode && elementEntities.size() != ids.stream().distinct().count()) {
            throw new DirectoryException(NOT_FOUND);
        }

        Map<UUID, Long> subElementsCount = getSubElementsCount(elementEntities.stream().map(DirectoryElementEntity::getId).toList(), types);

        return elementEntities.stream()
                .map(attribute -> toElementAttributes(attribute, subElementsCount.getOrDefault(attribute.getId(), 0L)))
                .toList();
    }

    public void notify(@NonNull String notificationName, @NonNull UUID elementUuid, @NonNull String userId) {
        NotificationType notification;
        try {
            notification = NotificationType.valueOf(notificationName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DirectoryException.createNotificationUnknown(notificationName);
        }

        if (notification == NotificationType.UPDATE_DIRECTORY) {
            emitDirectoryChangedNotification(elementUuid, userId);
        } else {
            throw DirectoryException.createNotificationUnknown(notification.name());
        }
    }

    public void emitDirectoryChangedNotification(UUID elementUuid, String userId) {
        ElementAttributes elementAttributes = getElement(elementUuid);
        UUID parentUuid = repositoryService.getParentUuid(elementUuid);
        Boolean isPrivate = elementAttributes.getAccessRights().isPrivate();
        if (isPrivate == null) { // Then take accessRights from the parent element
            isPrivate = getElement(parentUuid).getAccessRights().isPrivate();
        }
        notificationService.emitDirectoryChanged(
                parentUuid,
                elementAttributes.getElementName(),
                userId,
                null,
                isPrivate,
                parentUuid == null,
                NotificationType.UPDATE_DIRECTORY
        );
    }

    public void areElementsAccessible(@NonNull String userId, @NonNull List<UUID> elementUuids) {
        getElements(elementUuids, true, List.of()).stream()
                .map(e -> e.getType().equals(DIRECTORY) ? e : getParentElement(e.getElementUuid()))
                .forEach(e -> {
                    if (!e.isAllowed(userId)) {
                        throw new DirectoryException(NOT_ALLOWED);
                    }
                });
    }

    public boolean isPathAccessible(String userId, List<ElementAttributes> pathElements) {
        return pathElements.stream().allMatch(e -> !DIRECTORY.equals(e.getType()) || e.isAllowed(userId));
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

    private Pair<List<UUID>, List<String>> getUuidsAndNamesFromPath(List<ElementAttributes> elementAttributesList) {
        List<UUID> uuids = new ArrayList<>(elementAttributesList.size());
        List<String> names = new ArrayList<>(elementAttributesList.size());
        elementAttributesList.stream()
                .filter(elementAttributes -> Objects.equals(elementAttributes.getType(), DIRECTORY))
                .forEach(e -> {
                    uuids.add(e.getElementUuid());
                    names.add(e.getElementName());
                });
        return Pair.of(uuids, names);
    }

    @Transactional
    public void reindexAllElements() {
        repositoryService.reindexAllElements();
    }

    public List<DirectoryElementInfos> searchElements(@NonNull String userInput, String userId) {
        return directoryElementInfosService.searchElements(userInput)
                .stream()
                .map(e -> {
                    Optional<DirectoryElementInfos> elementAccessible = Optional.empty();
                    List<ElementAttributes> path = getPath(e.getParentId());
                    boolean isPathAccessible = isPathAccessible(userId, path);
                    if (isPathAccessible) {
                        Pair<List<UUID>, List<String>> uuidsAndNames = getUuidsAndNamesFromPath(path);
                        e.setPathUuid(uuidsAndNames.getFirst());
                        e.setPathName(uuidsAndNames.getSecond());
                        elementAccessible = Optional.of(e);
                    }
                    return elementAccessible;
                })
                .flatMap(Optional::stream)
                .toList();
    }
}
