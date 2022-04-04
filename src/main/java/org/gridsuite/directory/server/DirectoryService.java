/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import lombok.NonNull;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Pair;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryException.Type.NOT_ALLOWED;
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

    public DirectoryService(
        DirectoryElementRepository directoryElementRepository,
        StreamBridge studyUpdatePublisher) {
        this.directoryElementRepository = directoryElementRepository;

        this.studyUpdatePublisher = studyUpdatePublisher;
    }

    /* notifications */
    @Bean
    public Consumer<Flux<Message<String>>> consumeStudyUpdate() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE).flatMap(message -> {
            String studyUuidHeader = message.getHeaders().get(HEADER_STUDY_UUID, String.class);
            String error = message.getHeaders().get(HEADER_ERROR, String.class);
            String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
            if (studyUuidHeader != null) {
                UUID studyUuid = UUID.fromString(studyUuidHeader);
                UUID parentUuid = getParentUuid(studyUuid);
                Optional<DirectoryElementEntity> elementEntity = getElementEntity(studyUuid);
                String elementName = elementEntity.map(DirectoryElementEntity::getName).orElse(null);
                if (error != null && elementName != null) {
                    deleteElement(studyUuid, userId).subscribe();
                }
                boolean isPrivate = isPrivateForNotification(parentUuid, isPrivateDirectory(studyUuid));
                emitDirectoryChanged(parentUuid, elementName, userId, error, isPrivate, parentUuid == null, NotificationType.UPDATE_DIRECTORY);
            }
            return Mono.empty();
        }).doOnError(throwable -> LOGGER.error(throwable.toString(), throwable)).subscribe();
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
    public Mono<ElementAttributes> createElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId) {
        return assertElementNotExist(parentDirectoryUuid, elementAttributes.getElementName(), elementAttributes.getType())
            .and(assertAccessibleDirectory(parentDirectoryUuid, userId))
            .then(insertElement(elementAttributes, parentDirectoryUuid))
            .doOnSuccess(unused -> {
                var isCurrentElementPrivate = elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null;
                emitDirectoryChanged(
                        parentDirectoryUuid,
                        elementAttributes.getElementName(),
                        userId,
                        isPrivateForNotification(parentDirectoryUuid, isCurrentElementPrivate),
                        false,
                        NotificationType.UPDATE_DIRECTORY);
            });
    }

    private Mono<Void> assertElementNotExist(UUID parentDirectoryUuid, String elementName, String type) {
        return isElementExists(parentDirectoryUuid, elementName, type)
            .flatMap(exist -> Boolean.TRUE.equals(exist) ? Mono.error(new DirectoryException(NOT_ALLOWED)) : Mono.empty());
    }

    private Mono<Void> assertRootDirectoryNotExist(String rootName) {
        return isRootDirectoryExist(rootName)
            .flatMap(exist -> Boolean.TRUE.equals(exist) ? Mono.error(new DirectoryException(NOT_ALLOWED)) : Mono.empty());
    }

    private Mono<Void> assertAccessibleDirectory(UUID dirUuid, String user) {
        return getElement(dirUuid)
            .filter(e -> e.isAllowed(user))
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_ALLOWED)))
            .then();
    }

    /* methods */
    private Mono<ElementAttributes> insertElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid) {
        return Mono.fromCallable(() -> toElementAttributes(
            directoryElementRepository.save(
                new DirectoryElementEntity(
                    elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                    parentDirectoryUuid,
                    elementAttributes.getElementName(),
                    elementAttributes.getType(),
                    elementAttributes.getType().equals(DIRECTORY) ? elementAttributes.getAccessRights().getIsPrivate() : null,
                    elementAttributes.getOwner(),
                    elementAttributes.getDescription()
                )
            )
        ));
    }

    public Mono<ElementAttributes> createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        return assertRootDirectoryNotExist(rootDirectoryAttributes.getElementName())
            .then(insertElement(toElementAttributes(rootDirectoryAttributes), null))
            .doOnSuccess(element ->
                emitDirectoryChanged(element.getElementUuid(), element.getElementName(), userId, element.getAccessRights().isPrivate(), true, NotificationType.ADD_DIRECTORY)
            );
    }

    private Map<UUID, Long> getSubDirectoriesInfos(List<UUID> subDirectories) {
        List<DirectoryElementRepository.SubDirectoryCount> subdirectoriesCountsList = directoryElementRepository.getSubdirectoriesCounts(subDirectories);
        Map<UUID, Long> subdirectoriesCountsMap = new HashMap<>();
        subdirectoriesCountsList.forEach(e -> subdirectoriesCountsMap.put(e.getId(), e.getCount()));
        return subdirectoriesCountsMap;
    }

    public Flux<ElementAttributes> getDirectoryElements(UUID directoryUuid, String userId) {
        return getElement(directoryUuid)
            .switchIfEmpty(Mono.error(DirectoryException.createElementNotFound(DIRECTORY, directoryUuid)))
            .filter(d -> d.isAllowed(userId))
            .flatMapIterable(d -> getDirectoryElementsStream(directoryUuid, userId).collect(Collectors.toList()));
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

    public Flux<ElementAttributes> getRootDirectories(String userId) {
        List<DirectoryElementEntity> directoryElements = directoryElementRepository.findRootDirectoriesByUserId(userId);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesInfos(directoryElements.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()));
        return Flux.fromStream(directoryElements.stream().map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L))));
    }

    // TODO test on name change if not already exist
    public Mono<Void> updateElement(UUID elementUuid, ElementAttributes newElementAttributes, String userId) {
        return getElementEntityMono(elementUuid)
            .switchIfEmpty(Mono.error(DirectoryException.createElementNotFound(ELEMENT, elementUuid)))
            .filter(e -> isElementUpdatable(toElementAttributes(e), userId, false))
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_ALLOWED)))
            .filter(e -> e.isAttributesUpdatable(newElementAttributes, userId))
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_ALLOWED)))
            .map(e -> e.update(newElementAttributes))
            .map(directoryElementRepository::save)
            .doOnSuccess(elementEntity -> emitDirectoryChanged(
                            elementEntity.getParentId() == null ? elementUuid : elementEntity.getParentId(),
                            elementEntity.getName(),
                            userId,
                            // second parameter should be always false when we change the access mode of a folder because we should notify all clients
                            isPrivateForNotification(elementEntity.getParentId(), false),
                            elementEntity.getParentId() == null,
                            NotificationType.UPDATE_DIRECTORY
                    )
            )
            .then();
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

    public Mono<Void> deleteElement(UUID elementUuid, String userId) {
        return getElementEntityMono(elementUuid)
            .switchIfEmpty(Mono.error(DirectoryException.createElementNotFound(ELEMENT, elementUuid)))
            .map(ElementAttributes::toElementAttributes)
            .filter(e -> isElementUpdatable(e, userId, true))
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_ALLOWED)))
            .map(e -> {
                UUID parentUuid = getParentUuid(elementUuid);
                deleteObject(e);
                return Pair.of(e, Optional.ofNullable(parentUuid));
            })
            .doOnSuccess(p -> {
                UUID parentUuid = p.getSecond().orElse(null);
                var isCurrentElementPrivate = p.getFirst().getAccessRights() != null ? p.getFirst().getAccessRights().isPrivate() : null;
                boolean isPrivate = isPrivateForNotification(parentUuid, isCurrentElementPrivate);
                emitDirectoryChanged(parentUuid == null ? elementUuid : parentUuid, p.getFirst().getElementName(), userId, isPrivate, parentUuid == null,
                    parentUuid == null ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY);
            })
            .then();
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
     * Retrieve informations of an element's parents, filtered by user's access rights
     * @param elementUuid
     * @param userId
     * @return ElementAttributes of element and all it's parents up to root directory, filtered by user's access rights
     */
    public List<ElementAttributes> getElementParents(UUID elementUuid, String userId) {
        Optional<DirectoryElementEntity> elementOpt = getElementEntity(elementUuid);
        if (elementOpt.isEmpty()) {
            throw DirectoryException.createElementNotFound(ELEMENT, elementUuid);
        }
        DirectoryElementEntity element = elementOpt.get();

        ArrayList<ElementAttributes> elementParents = new ArrayList<ElementAttributes>();
        elementParents.add(toElementAttributes(element));

        while (element.getParentId() != null) {
            element = getElementEntity(element.getParentId()).get();
            ElementAttributes currentElementAttributes = toElementAttributes(element);
            if (currentElementAttributes.isAllowed(userId)) {
                elementParents.add(currentElementAttributes);
            }
        }

        return elementParents;
    }

    public Mono<ElementAttributes> getElement(UUID elementUuid) {
        return getElementEntityMono(elementUuid).map(ElementAttributes::toElementAttributes)
            .switchIfEmpty(Mono.error(DirectoryException.createElementNotFound(ELEMENT, elementUuid)));
    }

    private Mono<DirectoryElementEntity> getElementEntityMono(UUID elementUuid) {
        return Mono.fromCallable(() -> getElementEntity(elementUuid)).flatMap(Mono::justOrEmpty);
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

    private boolean isPrivateForNotification(UUID parentDirectoryUuid, Boolean isCurrentElementPrivate) {
        if (parentDirectoryUuid == null) {
            return isCurrentElementPrivate != null && isCurrentElementPrivate; // null may only come from borked REST request
        } else {
            return isPrivateDirectory(parentDirectoryUuid);
        }
    }

    private Mono<Boolean> isRootDirectoryExist(String rootName) {
        return Mono.fromCallable(() ->
            !directoryElementRepository.findRootDirectoriesByName(rootName).isEmpty()
        );
    }

    private Mono<Boolean> isElementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return Mono.fromCallable(() ->
            !directoryElementRepository.findByNameAndParentIdAndType(elementName, parentDirectoryUuid, type).isEmpty()
        );
    }

    public Mono<Void> elementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return isElementExists(parentDirectoryUuid, elementName, type)
            .flatMap(exist -> Boolean.TRUE.equals(exist) ? Mono.empty() : Mono.error(new DirectoryException(NOT_FOUND)));
    }

    public Flux<ElementAttributes> getElements(List<UUID> ids, boolean strictMode) {
        return Mono.fromCallable(() -> directoryElementRepository.findAllById(ids))
            .flatMapMany(elementEntities ->
                strictMode && elementEntities.size() != ids.stream().distinct().count() ?
                    Flux.error(new DirectoryException(NOT_FOUND)) :
                    Flux.fromStream(elementEntities.stream().map(ElementAttributes::toElementAttributes)));
    }

    public Mono<Void> notify(@NonNull String notificationName, @NonNull UUID elementUuid, @NonNull String userId) {
        NotificationType notification;
        try {
            notification = NotificationType.valueOf(notificationName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.error(DirectoryException.createNotificationUnknown(notificationName));
        }

        if (notification == NotificationType.UPDATE_DIRECTORY) {
            return emitDirectoryChangedNotification(elementUuid, userId);
        } else {
            return Mono.error(DirectoryException.createNotificationUnknown(notification.name()));
        }
    }

    public Mono<Void> emitDirectoryChangedNotification(UUID elementUuid, String userId) {
        return getElement(elementUuid)
            .doOnNext(elementAttributes -> {
                UUID parentUuid = getParentUuid(elementUuid);
                if (elementAttributes.getAccessRights().isPrivate() == null) {
                    getElement(parentUuid).doOnNext(parentDirectory -> emitDirectoryChanged(parentUuid, elementAttributes.getElementName(), userId, parentDirectory.getAccessRights().isPrivate(), parentUuid == null, NotificationType.UPDATE_DIRECTORY)).subscribe();
                } else {
                    emitDirectoryChanged(parentUuid, elementAttributes.getElementName(), userId, elementAttributes.getAccessRights().isPrivate(), parentUuid == null, NotificationType.UPDATE_DIRECTORY);
                }
            })
            .then();
    }

    public Mono<Void> areElementsAccessible(@NonNull String userId, @NonNull List<UUID> elementUuids) {
        return getElements(elementUuids, true)
            .map(e -> e.getType().equals(DIRECTORY) ? e : getParentElement(e.getElementUuid()))
            .flatMap(e -> e.isAllowed(userId) ? Mono.empty() : Mono.error(new DirectoryException(NOT_ALLOWED)))
            .then();
    }
}
