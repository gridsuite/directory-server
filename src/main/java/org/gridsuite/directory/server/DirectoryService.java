/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.services.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryException.Type.IS_DIRECTORY;
import static org.gridsuite.directory.server.DirectoryException.Type.NOT_ALLOWED;
import static org.gridsuite.directory.server.DirectoryException.Type.NOT_DIRECTORY;
import static org.gridsuite.directory.server.DirectoryException.Type.NOT_FOUND;
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
    public static final String DIRECTORY = "DIRECTORY";
    public static final String ELEMENT = "ELEMENT";
    static final String HEADER_USER_ID = "userId";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_DIRECTORIES = "directories";
    static final String HEADER_DIRECTORY_UUID = "directoryUuid";
    static final String HEADER_IS_PUBLIC_DIRECTORY = "isPublicDirectory";
    static final String HEADER_IS_ROOT_DIRECTORY = "isRootDirectory";
    static final String HEADER_ERROR = "error";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_NOTIFICATION_TYPE = "notificationType";
    static final String HEADER_ELEMENT_NAME = "elementName";
    private static final String CATEGORY_BROKER_OUTPUT = DirectoryService.class.getName() + ".output-broker-messages";
    private static final String CATEGORY_BROKER_INPUT = DirectoryService.class.getName() + ".input-broker-messages";
    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);
    private final DirectoryElementRepository directoryElementRepository;
    private final StreamBridge studyUpdatePublisher;

    private StudyService studyService;

    public DirectoryService(
        DirectoryElementRepository directoryElementRepository,
        StreamBridge studyUpdatePublisher,
        StudyService studyService) {
        this.directoryElementRepository = directoryElementRepository;

        this.studyUpdatePublisher = studyUpdatePublisher;

        this.studyService = studyService;
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
                if (studyUuidHeader != null) {
                    UUID studyUuid = UUID.fromString(studyUuidHeader);
                    UUID parentUuid = getParentUuid(studyUuid);
                    Optional<DirectoryElementEntity> elementEntity = getElementEntity(studyUuid);
                    String elementName = elementEntity.map(DirectoryElementEntity::getName).orElse(null);
                    if (error != null && elementName != null) {
                        deleteElement(studyUuid, userId);
                    }
                    boolean isPrivate = isPrivateForNotification(parentUuid, isPrivateDirectory(studyUuid));
                    emitDirectoryChanged(parentUuid, elementName, userId, error, isPrivate, parentUuid == null, NotificationType.UPDATE_DIRECTORY);
                }
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            }
        };
    }

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        studyUpdatePublisher.send("publishDirectoryUpdate-out-0", message);
    }

    private void emitDirectoryChanged(UUID directoryUuid, String elementName, String userId, Boolean isPrivate, boolean isRoot, NotificationType notificationType) {
        emitDirectoryChanged(directoryUuid, elementName, userId, null, isPrivate, isRoot, notificationType);
    }

    private void emitDirectoryChanged(UUID directoryUuid, String elementName, String userId, String error, Boolean isPrivate, boolean isRoot, NotificationType notificationType) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_DIRECTORY_UUID, directoryUuid)
            .setHeader(HEADER_ELEMENT_NAME, elementName)
            .setHeader(HEADER_IS_ROOT_DIRECTORY, isRoot)
            .setHeader(HEADER_IS_PUBLIC_DIRECTORY, isPrivate == null || !isPrivate) // null may only come from borked REST request
            .setHeader(HEADER_NOTIFICATION_TYPE, notificationType)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES)
            .setHeader(HEADER_ERROR, error);
        sendUpdateMessage(messageBuilder.build());
    }

    /* methods */
    public ElementAttributes createElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId) {
        if (elementAttributes.getElementName().isBlank()) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        assertElementNotExist(parentDirectoryUuid, elementAttributes.getElementName(), elementAttributes.getType());
        assertAccessibleDirectory(parentDirectoryUuid, userId);
        ElementAttributes result = insertElement(elementAttributes, parentDirectoryUuid);
        var isCurrentElementPrivate = elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null;
        emitDirectoryChanged(parentDirectoryUuid,
                             elementAttributes.getElementName(),
                             userId,
                             isPrivateForNotification(parentDirectoryUuid, isCurrentElementPrivate),
                            false,
                             NotificationType.UPDATE_DIRECTORY);
        return result;
    }

    private void assertElementNotExist(UUID parentDirectoryUuid, String elementName, String type) {
        if (Boolean.TRUE.equals(isElementExists(parentDirectoryUuid, elementName, type))) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void assertRootDirectoryNotExist(String rootName) {
        if (Boolean.TRUE.equals(isRootDirectoryExist(rootName))) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    private void assertAccessibleDirectory(UUID dirUuid, String user) {
        if (!getElement(dirUuid).isAllowed(user)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
    }

    /* methods */
    private ElementAttributes insertElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid) {
        return toElementAttributes(directoryElementRepository.save(
                new DirectoryElementEntity(elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                                           parentDirectoryUuid,
                                           elementAttributes.getElementName(),
                                           elementAttributes.getType(),
                                           elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null,
                                           elementAttributes.getOwner(),
                                           elementAttributes.getDescription()
                        )
                )
        );
    }

    public ElementAttributes createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        if (rootDirectoryAttributes.getElementName().isBlank()) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        assertRootDirectoryNotExist(rootDirectoryAttributes.getElementName());
        ElementAttributes elementAttributes = insertElement(toElementAttributes(rootDirectoryAttributes), null);
        emitDirectoryChanged(elementAttributes.getElementUuid(), elementAttributes.getElementName(), userId, elementAttributes.getAccessRights().isPrivate(), true, NotificationType.ADD_DIRECTORY);
        return elementAttributes;
    }

    private Map<UUID, Long> getSubDirectoriesInfos(List<UUID> subDirectories) {
        List<DirectoryElementRepository.SubDirectoryCount> subdirectoriesCountsList = directoryElementRepository.getSubdirectoriesCounts(subDirectories);
        Map<UUID, Long> subdirectoriesCountsMap = new HashMap<>();
        subdirectoriesCountsList.forEach(e -> subdirectoriesCountsMap.put(e.getId(), e.getCount()));
        return subdirectoriesCountsMap;
    }

    public List<ElementAttributes> getDirectoryElements(UUID directoryUuid, String userId) {
        ElementAttributes elementAttributes = getElement(directoryUuid);
        if (elementAttributes == null) {
            throw DirectoryException.createElementNotFound(DIRECTORY, directoryUuid);
        }

        if (!elementAttributes.isAllowed(userId)) {
            return List.of();
        }

        return getDirectoryElementsStream(directoryUuid, userId).collect(Collectors.toList());
    }

    private Stream<ElementAttributes> getDirectoryElementsStream(UUID directoryUuid, String userId) {
        return getAllDirectoryElementsStream(directoryUuid)
                .filter(elementAttributes -> !elementAttributes.getType().equals(DIRECTORY) || elementAttributes.isAllowed(userId));
    }

    private Stream<ElementAttributes> getAllDirectoryElementsStream(UUID directoryUuid) {
        List<DirectoryElementEntity> directoryElements = directoryElementRepository.findAllByParentId(directoryUuid);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesInfos(directoryElements.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()));
        return directoryElements
                .stream()
                .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)));
    }

    public List<ElementAttributes> getRootDirectories(String userId) {
        List<DirectoryElementEntity> directoryElements = directoryElementRepository.findRootDirectoriesByUserId(userId);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesInfos(directoryElements.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()));
        return directoryElements.stream()
                .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)))
                .collect(Collectors.toList());
    }

    public void updateElement(UUID elementUuid, ElementAttributes newElementAttributes, String userId) {
        DirectoryElementEntity directoryElement = getDirectoryElementEntity(elementUuid);
        if (!isElementUpdatable(toElementAttributes(directoryElement), userId, false) ||
            !directoryElement.isAttributesUpdatable(newElementAttributes, userId) ||
            (!directoryElement.getName().equals(newElementAttributes.getElementName()) &&
             directoryHasElementOfNameAndType(directoryElement.getParentId(), userId, newElementAttributes.getElementName(), directoryElement.getType()))) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        DirectoryElementEntity elementEntity = directoryElementRepository.save(directoryElement.update(newElementAttributes));
        emitDirectoryChanged(
                elementEntity.getParentId() == null ? elementUuid : elementEntity.getParentId(),
                elementEntity.getName(),
                userId,
                // second parameter should be always false when we change the access mode of a folder because we should notify all clients
                isPrivateForNotification(elementEntity.getParentId(), false),
                elementEntity.getParentId() == null,
                NotificationType.UPDATE_DIRECTORY
        );
        //true if we updated a study name
        if (elementEntity.getType().equals(STUDY) && StringUtils.isNotBlank(newElementAttributes.getElementName())) {
            studyService.notifyStudyUpdate(elementUuid, userId);
        }
    }

    public void updateElementDirectory(UUID elementUuid, UUID newDirectoryUuid, String userId) {
        Optional<DirectoryElementEntity> optElement = getElementEntity(elementUuid);
        Optional<DirectoryElementEntity> optNewDirectory = getElementEntity(newDirectoryUuid);
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

        oldDirectory = getElementEntity(element.getParentId()).get();
        if (!newDirectory.getIsPrivate().equals(oldDirectory.getIsPrivate())) {
            throw DirectoryException.createDirectoryWithDifferentAccessRights(elementUuid, newDirectoryUuid);
        }

        element.setParentId(newDirectoryUuid);
        directoryElementRepository.save(element);

        emitDirectoryChanged(
            element.getParentId(),
            element.getName(),
            userId,
            isPrivateForNotification(element.getParentId(), false),
            isRootDirectory(element.getId()),
            NotificationType.UPDATE_DIRECTORY
        );

        emitDirectoryChanged(
            oldDirectory.getId(),
            element.getName(),
            userId,
            isPrivateForNotification(oldDirectory.getId(), false),
            isRootDirectory(element.getId()),
            NotificationType.UPDATE_DIRECTORY
        );

        if (element.getType().equals(STUDY)) {
            studyService.notifyStudyUpdate(elementUuid, userId);
        }
    }

    private boolean directoryHasElementOfNameAndType(UUID directoryUUID, String userId, String elementName, String elementType) {
        return getDirectoryElementsStream(directoryUUID, userId)
            .anyMatch(
                e -> e.getType().equals(elementType)
                    && e.getElementName().equals(elementName)
            );
    }

    private boolean isElementUpdatable(ElementAttributes element, String userId, boolean forDeletion) {
        if (element.getType().equals(DIRECTORY)) {
            return element.isAllowed(userId) &&
                (!forDeletion || getDirectoryElementsStream(element.getElementUuid(), userId)
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
        UUID parentUuid = getParentUuid(elementUuid);
        deleteObject(elementAttributes);
        var isCurrentElementPrivate = elementAttributes.getAccessRights() != null ? elementAttributes.getAccessRights().isPrivate() : null;
        boolean isPrivate = isPrivateForNotification(parentUuid, isCurrentElementPrivate);
        emitDirectoryChanged(parentUuid == null ? elementUuid : parentUuid, elementAttributes.getElementName(), userId, isPrivate, parentUuid == null,
                parentUuid == null ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY);
    }

    private void deleteObject(ElementAttributes elementAttributes) {
        if (elementAttributes.getType().equals(DIRECTORY)) {
            deleteSubElements(elementAttributes.getElementUuid());
        }
        directoryElementRepository.deleteById(elementAttributes.getElementUuid());
    }

    private void deleteSubElements(UUID elementUuid) {
        getAllDirectoryElementsStream(elementUuid).forEach(this::deleteObject);
    }

    /***
     * Retrieve path of an element
     * @param elementUuid
     * @param userId
     * @return ElementAttributes of element and all it's parents up to root directory
     */
    public List<ElementAttributes> getPath(UUID elementUuid, String userId) {
        Optional<DirectoryElementEntity> currentElementOpt = getElementEntity(elementUuid);
        ArrayList<ElementAttributes> path = new ArrayList<>();
        boolean allowed;
        if (currentElementOpt.isEmpty()) {
            throw DirectoryException.createElementNotFound(ELEMENT, elementUuid);
        }
        DirectoryElementEntity currentElement = currentElementOpt.get();

        if (currentElement.getType().equals(DIRECTORY)) {
            allowed = toElementAttributes(currentElement).isAllowed(userId);
        } else {
            allowed = toElementAttributes(getElementEntity(currentElement.getParentId()).get()).isAllowed(userId);
        }

        if (!allowed) {
            throw new DirectoryException(NOT_ALLOWED);
        }

        path.add(toElementAttributes(currentElement));

        while (currentElement.getParentId() != null) {
            currentElement = getElementEntity(currentElement.getParentId()).get();
            ElementAttributes currentElementAttributes = toElementAttributes(currentElement);
            path.add(currentElementAttributes);
        }

        return path;
    }

    public ElementAttributes getElement(UUID elementUuid) {
        return toElementAttributes(getDirectoryElementEntity(elementUuid));
    }

    private DirectoryElementEntity getDirectoryElementEntity(UUID elementUuid) {
        return getElementEntity(elementUuid).orElseThrow(() -> DirectoryException.createElementNotFound(ELEMENT, elementUuid));
    }

    private Optional<DirectoryElementEntity> getElementEntity(UUID elementUuid) {
        return directoryElementRepository.findById(elementUuid);
    }

    private ElementAttributes getParentElement(UUID elementUuid) {
        return Stream.of(getParentUuid(elementUuid))
            .filter(Objects::nonNull)
            .map(this::getElementEntity)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ElementAttributes::toElementAttributes)
            .findFirst()
            .orElseThrow(() -> DirectoryException.createElementNotFound("Parent of", elementUuid));
    }

    private UUID getParentUuid(UUID elementUuid) {
        return directoryElementRepository
            .findById(elementUuid)
            .map(DirectoryElementEntity::getParentId)
            .orElse(null);
    }

    private boolean isPrivateDirectory(UUID directoryUuid) {
        // TODO replace orElse by the commented line (orElseThrow)
        // Should be done after deleting the notification sent by the study server on delete (!)
        return directoryElementRepository.findById(directoryUuid).map(DirectoryElementEntity::getIsPrivate).orElse(false);
        //.orElseThrow(() -> new DirectoryServerException(directoryUuid + " not found!"));
    }

    private boolean isRootDirectory(UUID directoryUuid) {
        return getParentUuid(directoryUuid) == null;
    }

    private boolean isPrivateForNotification(UUID parentDirectoryUuid, Boolean isCurrentElementPrivate) {
        if (parentDirectoryUuid == null) {
            return isCurrentElementPrivate != null && isCurrentElementPrivate; // null may only come from borked REST request
        } else {
            return isPrivateDirectory(parentDirectoryUuid);
        }
    }

    private Boolean isRootDirectoryExist(String rootName) {
        return !directoryElementRepository.findRootDirectoriesByName(rootName).isEmpty();
    }

    private Boolean isElementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return !directoryElementRepository.findByNameAndParentIdAndType(elementName, parentDirectoryUuid, type).isEmpty();
    }

    public boolean rootDirectoryExists(String rootDirectoryName) {
        return !directoryElementRepository.findRootDirectoriesByName(rootDirectoryName).isEmpty();
    }

    public boolean elementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return !directoryElementRepository.findByNameAndParentIdAndType(elementName, parentDirectoryUuid, type).isEmpty();
    }

    public List<ElementAttributes> getElements(List<UUID> ids, boolean strictMode) {
        List<DirectoryElementEntity> elementEntities = directoryElementRepository.findAllById(ids);
        if (strictMode && elementEntities.size() != ids.stream().distinct().count()) {
            throw new DirectoryException(NOT_FOUND);
        }
        return elementEntities.stream().map(ElementAttributes::toElementAttributes).collect(Collectors.toList());
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
        UUID parentUuid = getParentUuid(elementUuid);
        if (elementAttributes.getAccessRights().isPrivate() == null) {
            ElementAttributes parentDirectory = getElement(parentUuid);
            emitDirectoryChanged(parentUuid, elementAttributes.getElementName(), userId, parentDirectory.getAccessRights().isPrivate(), parentUuid == null, NotificationType.UPDATE_DIRECTORY);
        } else {
            emitDirectoryChanged(parentUuid, elementAttributes.getElementName(), userId, elementAttributes.getAccessRights().isPrivate(), parentUuid == null, NotificationType.UPDATE_DIRECTORY);
        }
    }

    public void areElementsAccessible(@NonNull String userId, @NonNull List<UUID> elementUuids) {
        getElements(elementUuids, true).stream()
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
        if (!directoryElementRepository.canRead(directoryUuid, userId)) {
            throw new DirectoryException(NOT_ALLOWED);
        }
        var idLikes = new HashSet<>(directoryElementRepository.getNameByTypeAndParentIdAndNameStartWith(elementType, directoryUuid, elementName));
        if (!idLikes.contains(elementName)) {
            return elementName;
        }
        int i = 1;
        while (idLikes.contains(nameCandidate(elementName, i))) {
            ++i;
        }
        return nameCandidate(elementName, i);
    }
}
