/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import jakarta.validation.constraints.NotNull;
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
import java.util.stream.Collectors;
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
    public static final String ELEMENT = "ELEMENT";
    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String UPDATE_TYPE_STUDIES = "studies";
    private static final String CATEGORY_BROKER_INPUT = DirectoryService.class.getName() + ".input-broker-messages";
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);

    private final StudyService studyService;

    private final NotificationService notificationService;

    private final DirectoryRepositoryService repositoryService;

    private final DirectoryElementInfosService directoryElementInfosService;

    public DirectoryService(DirectoryRepositoryService repositoryService,
                            StudyService studyService, NotificationService notificationService,
                            DirectoryElementInfosService directoryElementInfosService) {
        this.repositoryService = repositoryService;
        this.studyService = studyService;
        this.notificationService = notificationService;
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

        return getDirectoryElementsStream(directoryUuid, userId, types).collect(Collectors.toList());
    }

    private Stream<ElementAttributes> getDirectoryElementsStream(UUID directoryUuid, String userId, List<String> types) {
        return getAllDirectoryElementsStream(directoryUuid, types, userId)
                .filter(elementAttributes -> !elementAttributes.getType().equals(DIRECTORY) || elementAttributes.isAllowed(userId));
    }

    private Stream<ElementAttributes> getAllDirectoryElementsStream(UUID directoryUuid, List<String> types, String userId) {
        List<DirectoryElementEntity> directoryElements = repositoryService.findAllByParentIdAndStashed(directoryUuid, false);
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
        elementToUpdate.setLastModificationDate(lastModificationDate);
        elementToUpdate.setLastModifiedBy(lastModifiedBy);
    }

    public void updateElementDirectory(UUID elementUuid, UUID newDirectoryUuid, String userId) {
        Optional<DirectoryElementEntity> optElement = repositoryService.getElementEntity(elementUuid);
        Optional<DirectoryElementEntity> optNewDirectory = repositoryService.getElementEntity(newDirectoryUuid);
        DirectoryElementEntity oldDirectory;
        DirectoryElementEntity element;
        DirectoryElementEntity newDirectory;
        if (optElement.isEmpty()) {
            throw DirectoryException.createElementNotFound(ELEMENT, elementUuid);
        }
        if (optNewDirectory.isEmpty()) {
            throw DirectoryException.createElementNotFound(DIRECTORY, newDirectoryUuid);
        }
        element = optElement.get();
        newDirectory = optNewDirectory.get();

        if (element.getType().equals(DIRECTORY)) {
            throw new DirectoryException(IS_DIRECTORY);
        }
        if (!newDirectory.getType().equals(DIRECTORY)) {
            throw new DirectoryException(NOT_DIRECTORY);
        }
        if (!isElementUpdatable(toElementAttributes(element), userId, false)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
        if (directoryHasElementOfNameAndType(newDirectoryUuid, userId, element.getName(), element.getType())) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        oldDirectory = repositoryService.getElementEntity(element.getParentId()).orElseThrow();
        element.setParentId(newDirectoryUuid);
        repositoryService.saveElement(element);

        notificationService.emitDirectoryChanged(
                element.getParentId(),
                element.getName(),
                userId,
                null,
                isPrivateForNotification(element.getParentId(), false),
                repositoryService.isRootDirectory(element.getId()),
                NotificationType.UPDATE_DIRECTORY
        );

        notificationService.emitDirectoryChanged(
                oldDirectory.getId(),
                element.getName(),
                userId,
                null,
                isPrivateForNotification(oldDirectory.getId(), false),
                repositoryService.isRootDirectory(element.getId()),
                NotificationType.UPDATE_DIRECTORY
        );

        if (element.getType().equals(STUDY)) {
            studyService.notifyStudyUpdate(elementUuid, userId);
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

    /***
     * Retrieve path of an element
     * @param elementUuid element uuid
     * @param userId owner
     * @return ElementAttributes of element and all it's parents up to root directory
     */
    public List<ElementAttributes> getPath(UUID elementUuid, String userId) {
        DirectoryElementEntity currentElement = repositoryService.getElementEntity(elementUuid)
                .orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));

        boolean allowed = (Objects.equals(currentElement.getType(), DIRECTORY)) ?
                toElementAttributes(currentElement).isAllowed(userId) :
                toElementAttributes(getParentElement(currentElement)).isAllowed(userId);

        if (!allowed) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        List<ElementAttributes> path = new ArrayList<>();
        path.add(toElementAttributes(currentElement));

        List<DirectoryElementEntity> ancestors = repositoryService.findAllAscendants(elementUuid, userId);
        path.addAll(ancestors.stream().map(ElementAttributes::toElementAttributes).toList());

        return path;
    }

    private DirectoryElementEntity getParentElement(@NotNull DirectoryElementEntity currentElement) {
        return repositoryService.getElementEntity(currentElement.getParentId())
                .orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, currentElement.getParentId()));
    }

    public ElementAttributes getElement(UUID elementUuid) {
        return toElementAttributes(getDirectoryElementEntity(elementUuid));
    }

    private DirectoryElementEntity getDirectoryElementEntity(UUID elementUuid) {
        return repositoryService.getElementEntity(elementUuid).orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));
    }

    private ElementAttributes getParentElement(UUID elementUuid) {
        return Stream.of(repositoryService.getParentUuid(elementUuid))
            .filter(Objects::nonNull)
            .map(repositoryService::getElementEntity)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ElementAttributes::toElementAttributes)
            .findFirst()
            .orElseThrow(() -> DirectoryException.createElementNotFound("Parent of", elementUuid));
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
        List<DirectoryElementEntity> elementEntities = repositoryService.findAllByIdInAndStashed(ids, false);

        if (strictMode && elementEntities.size() != ids.stream().distinct().count()) {
            throw new DirectoryException(NOT_FOUND);
        }

        Map<UUID, Long> subElementsCount = getSubElementsCount(elementEntities.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()), types);

        return elementEntities.stream()
                .map(attribute -> toElementAttributes(attribute, subElementsCount.getOrDefault(attribute.getId(), 0L)))
                .collect(Collectors.toList());
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

    public Map<String, UUID> getElementNamesFromPath(List<ElementAttributes> elementAttributesList) {
        return elementAttributesList.stream()
                .filter(elementAttributes -> Objects.equals(elementAttributes.getType(), DIRECTORY))
                .collect(Collectors.toMap(
                        ElementAttributes::getElementName,
                        ElementAttributes::getElementUuid
                ));
    }

    private Map<String, UUID> getPathAndExtractNames(UUID elementUuid, String userId) {
        List<ElementAttributes> elementAttributesList = getPath(elementUuid, userId);
        return getElementNamesFromPath(elementAttributesList);
    }

    @Transactional
    public void reindexAllElements() {
        repositoryService.reindexAllElements();
    }

    public List<DirectoryElementInfos> searchElements(@NonNull String userInput, String userId) {
        return directoryElementInfosService.searchElements(userInput, userId)
                .stream()
                .peek(e -> {
                    Map<String, UUID> path = getPathAndExtractNames(e.getId(), userId);
                    e.setElementName(new ArrayList<>(path.keySet()));
                    e.setElementUuid(new ArrayList<>(path.values()));
                }).toList();
    }

    private List<DirectoryElementEntity> getEntitiesToRestore(List<DirectoryElementEntity> entities,
                                                              List<DirectoryElementEntity> rejectedEntities,
                                                              String userId,
                                                              boolean isParentPrivate) {
        if (isParentPrivate) {
            return getEntitiesCreatedBySameUser(entities, rejectedEntities, userId);
        }

        return entities.stream()
                .filter(entity -> {
                    boolean isPublicElement = entity.getIsPrivate() == null ? !getParentElement(entity.getId()).getAccessRights().isPrivate() : !entity.getIsPrivate();
                    boolean isUpdatable = Objects.equals(userId, entity.getOwner()) || isPublicElement;
                    if (!isUpdatable) {
                        rejectedEntities.add(entity);
                    }
                    return isUpdatable;
                })
                .toList();
    }

    public void restoreElements(List<UUID> elementsUuid, UUID parentUuid, String userId) {
        // Get parent directory
        ElementAttributes parent = getElement(parentUuid);
        if (parent == null) {
            throw new DirectoryException(NOT_FOUND, String.format("The directory '%s' not found !", parentUuid));
        }

        // Get all updatable entities. Entities should be public or created by the user, so it can be restored
        List<DirectoryElementEntity> notUpdatableEntities = new ArrayList<>();
        List<DirectoryElementEntity> allStashedElements = repositoryService.findAllStashedElements(elementsUuid, true, userId);
        List<DirectoryElementEntity> updatableEntities = getEntitiesToRestore(allStashedElements, notUpdatableEntities, userId, parent.getAccessRights().isPrivate());

        List<DirectoryElementEntity> entities = updatableEntities
                .stream()
                .flatMap(entity -> {
                    entity.setParentId(parentUuid);
                    entity.setName(getDuplicateNameCandidate(parentUuid, entity.getName(), entity.getType(), userId));

                    // Retrieve descendants of the current entity
                    List<DirectoryElementEntity> descendants = getEntitiesToRestore(
                            repositoryService.findAllDescendantsWithSameStashDate(entity.getId(), userId),
                            notUpdatableEntities,
                            userId,
                            parent.getAccessRights().isPrivate());

                    // Combine parent and descendants into a single list
                    List<DirectoryElementEntity> result = new ArrayList<>();
                    result.add(entity);
                    result.addAll(descendants);

                    return result.stream().map(e -> e.stashElement(false, null));
                })
                .toList();

        repositoryService.saveRestoredElements(entities);
        notificationService.emitDirectoryChanged(
                parentUuid,
                parent.getElementName(),
                userId,
                null,
                parent.getAccessRights().isPrivate(),
                parentUuid == null,
                NotificationType.UPDATE_DIRECTORY
        );
        emitDirectoryChangedNotification(parentUuid, userId);
        if (!notUpdatableEntities.isEmpty()) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    public void stashElements(List<UUID> elementsUuid, String userId) {
        // we add the same stash date to all the elements that are deleted together
        LocalDateTime stashDate = LocalDateTime.now();
        List<DirectoryElementEntity> entities = repositoryService.findAllByIdInAndStashed(elementsUuid, false);
        List<DirectoryElementEntity> notUpdatableEntities = new ArrayList<>();
        List<DirectoryElementEntity> updatableEntities = getEntitiesCreatedBySameUser(entities, notUpdatableEntities, userId);

        repositoryService.saveStashedElements(updatableEntities.stream()
                .flatMap(entity -> {
                    List<DirectoryElementEntity> descendants = repositoryService.findAllDescendants(entity.getId(), userId);
                    // Combine parent and descendants into a single list
                    List<DirectoryElementEntity> result = new ArrayList<>();
                    result.add(entity);
                    result.addAll(descendants);
                    return result.stream().map(e -> {
                        DirectoryElementEntity stashedElement = e.stashElement(true, stashDate);
                        if (Objects.equals(e.getType(), STUDY)) {
                            notificationService.emitDeletedStudy(entity.getId(), userId);
                        }
                        return stashedElement;
                    });
                })
                .toList());

        updatableEntities.forEach(entity -> {
            UUID parentUuid = repositoryService.getParentUuid(entity.getId());
            notificationService.emitDirectoryChanged(
                    parentUuid == null ? entity.getId() : parentUuid,
                    entity.getName(),
                    userId,
                    null,
                    entity.getIsPrivate(),
                    parentUuid == null,
                    parentUuid == null ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY
            );
        });

        if (!notUpdatableEntities.isEmpty()) {
            throw new DirectoryException(NOT_ALLOWED,
                    String.format("Some or all of the elements can not be deleted : %s",
                            String.join(", ", notUpdatableEntities.stream().map(DirectoryElementEntity::getName).toList())));
        }
    }

    public List<Pair<ElementAttributes, Long>> getStashedElements(String userId) {
        List<DirectoryElementEntity> entities = repositoryService.getElementsStashed(userId);
        return entities.stream()
                .map(entity -> Pair.of(toElementAttributes(entity), repositoryService.countDescendants(entity.getId(), userId) - 1))
                .toList();
    }

    public void deleteElements(List<UUID> elementsUuid, String userId) {
        // Get all updatable entities
        List<DirectoryElementEntity> notUpdatableEntities = new ArrayList<>();
        List<DirectoryElementEntity> updatableEntities = getEntitiesCreatedBySameUser(repositoryService.findAllByIdInAndStashed(elementsUuid, true), notUpdatableEntities, userId);

        // Collect all entities with their descendents in one list
        List<DirectoryElementEntity> allEntities = updatableEntities.stream()
                        .flatMap(entity -> Stream.concat(repositoryService.findAllDescendantsWithSameStashDate(entity.getId(), userId).stream(),
                                                         Stream.of(entity)))
                        .toList();

        // Delete all elements
        repositoryService.deleteElements(allEntities.stream().map(DirectoryElementEntity::getId).toList());

        // Send notification for all deleted elements
        allEntities.forEach(entity -> {
            UUID parentUuid = entity.getParentId();
            notificationService.emitDirectoryChanged(
                    parentUuid == null ? entity.getId() : parentUuid,
                    entity.getName(),
                    userId,
                    null,
                    entity.getIsPrivate(),
                    parentUuid == null,
                    parentUuid == null ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY
            );
            if (STUDY.equals(entity.getType())) {
                notificationService.emitDeletedStudy(entity.getId(), userId);
            }
        });
        if (!notUpdatableEntities.isEmpty()) {
            throw new DirectoryException(NOT_ALLOWED,
                    String.format("Some or all of the elements can not be deleted : %s",
                            String.join(", ", notUpdatableEntities.stream().map(DirectoryElementEntity::getName).toList())));
        }
    }

    private List<DirectoryElementEntity> getEntitiesCreatedBySameUser(List<DirectoryElementEntity> entities,
                                                                      List<DirectoryElementEntity> notUpdatableEntities,
                                                                      String userId) {
        return entities.stream()
                .filter(entity -> {
                    boolean isUpdatable = Objects.equals(userId, entity.getOwner());
                    if (!isUpdatable) {
                        notUpdatableEntities.add(entity);
                    }
                    return isUpdatable;
                })
                .toList();
    }
}
