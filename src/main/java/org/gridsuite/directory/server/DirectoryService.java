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
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryException.Type.NOT_ALLOWED;
import static org.gridsuite.directory.server.DirectoryException.Type.NOT_FOUND;
import static org.gridsuite.directory.server.dto.ElementAttributes.Notification.UPDATE_DIRECTORY;
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
    static final String HEADER_USER_ID = "userId";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_DIRECTORIES = "directories";
    static final String HEADER_DIRECTORY_UUID = "directoryUuid";
    static final String HEADER_IS_PUBLIC_DIRECTORY = "isPublicDirectory";
    static final String HEADER_IS_ROOT_DIRECTORY = "isRootDirectory";
    static final String HEADER_ERROR = "error";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_NOTIFICATION_TYPE = "notificationType";
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
                if (error != null) {
                    deleteElement(studyUuid, userId).subscribe();
                }
                boolean isPrivate = isPrivateForNotification(parentUuid, isPrivateDirectory(studyUuid));
                emitDirectoryChanged(parentUuid, userId, error, isPrivate, parentUuid == null, NotificationType.UPDATE_DIRECTORY);
            }
            return Mono.empty();
        }).doOnError(throwable -> LOGGER.error(throwable.toString(), throwable)).subscribe();
    }

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        studyUpdatePublisher.send("publishDirectoryUpdate-out-0", message);
    }

    private void emitDirectoryChanged(UUID directoryUuid, String userId, boolean isPrivate, boolean isRoot, NotificationType notificationType) {
        emitDirectoryChanged(directoryUuid, userId, null, isPrivate, isRoot, notificationType);
    }

    private void emitDirectoryChanged(UUID directoryUuid, String userId, String error, boolean isPrivate, boolean isRoot, NotificationType notificationType) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_DIRECTORY_UUID, directoryUuid)
            .setHeader(HEADER_IS_ROOT_DIRECTORY, isRoot)
            .setHeader(HEADER_IS_PUBLIC_DIRECTORY, !isPrivate)
            .setHeader(HEADER_NOTIFICATION_TYPE, notificationType)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES)
            .setHeader(HEADER_ERROR, error);
        sendUpdateMessage(messageBuilder.build());
    }

    /* methods */
    public Mono<ElementAttributes> createElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId) {
        return insertElement(elementAttributes, parentDirectoryUuid).doOnSuccess(unused -> emitDirectoryChanged(
            parentDirectoryUuid,
            userId,
            isPrivateForNotification(parentDirectoryUuid, elementAttributes.getAccessRights().isPrivate()),
            false,
            NotificationType.UPDATE_DIRECTORY));
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
                    elementAttributes.getAccessRights().isPrivate(),
                    elementAttributes.getOwner(),
                    elementAttributes.getDescription()
                )
            )
        ));
    }

    public Mono<ElementAttributes> createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        return insertElement(toElementAttributes(rootDirectoryAttributes), null)
            .doOnSuccess(element ->
                emitDirectoryChanged(element.getElementUuid(), userId, element.getAccessRights().isPrivate(), true, NotificationType.ADD_DIRECTORY)
            );
    }

    public Flux<ElementAttributes> getDirectoryElements(UUID directoryUuid, String userId) {
        return Flux.fromStream(directoryContentStream(directoryUuid, userId));
    }

    public Map<UUID, Long> getSubDirectoriesInfos(List<UUID> subDirectories, String userId) {
        List<DirectoryElementRepository.SubDirectoryCount> subdirectoriesCountsList = directoryElementRepository.getSubdirectoriesCounts(subDirectories, userId);
        Map<UUID, Long> subdirectoriesCountsMap = new HashMap<>();
        subdirectoriesCountsList.forEach(e -> subdirectoriesCountsMap.put(e.getId(), e.getCount()));
        return subdirectoriesCountsMap;
    }

    private Stream<ElementAttributes> directoryContentStream(UUID directoryUuid, String userId) {
        List<DirectoryElementEntity> directoryElements = directoryElementRepository.findDirectoryContentByUserId(directoryUuid, userId);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesInfos(directoryElements.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()), userId);
        return directoryElements
            .stream()
            .map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)));
    }

    public Flux<ElementAttributes> getRootDirectories(String userId) {
        List<DirectoryElementEntity> directoryElements = directoryElementRepository.findRootDirectoriesByUserId(userId);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesInfos(directoryElements.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()), userId);
        return Flux.fromStream(directoryElements.stream().map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L))));
    }

    public Mono<Void> updateElement(UUID elementUuid, ElementAttributes newElementAttributes, String userId) {
        return getElementEntity(elementUuid)
            .filter(elementEntity -> userId.equals(elementEntity.getOwner()))
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_ALLOWED)))
            .filter(e -> e.isAttributesUpdatable(newElementAttributes))
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_ALLOWED)))
            .map(e -> e.update(newElementAttributes))
            .map(directoryElementRepository::save)
            .doOnSuccess(elementEntity ->
                emitDirectoryChanged(
                    elementEntity.getParentId() == null ? elementUuid : elementEntity.getParentId(),
                    userId,
                    // second parameter should be always false when we change the access mode of a folder because we should notify all clients
                    isPrivateForNotification(elementEntity.getParentId(), newElementAttributes.getAccessRights() == null && elementEntity.isPrivate()),
                    elementEntity.getParentId() == null,
                    NotificationType.UPDATE_DIRECTORY
                )
            )
            .then();
    }

    public Mono<Void> deleteElement(UUID elementUuid, String userId) {
        return getElement(elementUuid).flatMap(elementAttributes -> {
            if (!userId.equals(elementAttributes.getOwner())) {
                return Mono.error(new DirectoryException(NOT_ALLOWED));
            }
            UUID parentUuid = getParentUuid(elementUuid);
            deleteObject(elementAttributes, userId);
            boolean isPrivate = isPrivateForNotification(parentUuid, elementAttributes.getAccessRights().isPrivate());
            emitDirectoryChanged(parentUuid == null ? elementUuid : parentUuid, userId, isPrivate, parentUuid == null,
                parentUuid == null ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY);
            return Mono.empty();
        });
    }

    private void deleteObject(ElementAttributes elementAttributes, String userId) {
        if (elementAttributes.getType().equals(DIRECTORY)) {
            deleteSubElements(elementAttributes.getElementUuid(), userId);
        }
        directoryElementRepository.deleteById(elementAttributes.getElementUuid());
    }

    private void deleteSubElements(UUID elementUuid, String userId) {
        directoryContentStream(elementUuid, userId).forEach(elementAttributes -> deleteObject(elementAttributes, userId));
    }

    public Mono<ElementAttributes> getElement(UUID elementUuid) {
        return getElementEntity(elementUuid).map(ElementAttributes::toElementAttributes);
    }

    private Mono<DirectoryElementEntity> getElementEntity(UUID elementUuid) {
        return Mono.fromCallable(() -> directoryElementRepository.findById(elementUuid))
            .flatMap(Mono::justOrEmpty)
            .switchIfEmpty(Mono.error(new DirectoryException(NOT_FOUND)));
    }

    private UUID getParentUuid(UUID directoryUuid) {
        return directoryElementRepository.findById(directoryUuid).map(DirectoryElementEntity::getParentId).orElse(null);
    }

    private boolean isPrivateDirectory(UUID directoryUuid) {
        // TODO replace orElse by the commented line (orElseThrow)
        // Should be done after deleting the notification sent by the study server on delete (!)
        return directoryElementRepository.findById(directoryUuid).map(DirectoryElementEntity::isPrivate).orElse(false);
        //.orElseThrow(() -> new DirectoryServerException(directoryUuid + " not found!"));
    }

    private boolean isPrivateForNotification(UUID parentDirectoryUuid, boolean isCurrentElementPrivate) {
        if (parentDirectoryUuid == null) {
            return isCurrentElementPrivate;
        } else {
            return isPrivateDirectory(parentDirectoryUuid);
        }
    }

    public Mono<Void> elementExists(UUID parentDirectoryUuid, String elementName, String userId) {
        return directoryElementRepository.findDirectoryContentByUserId(parentDirectoryUuid, userId).stream().anyMatch(e -> e.getName().equals(elementName))
            ? Mono.empty() : Mono.error(new DirectoryException(NOT_FOUND));
    }

    public Flux<ElementAttributes> getElements(List<UUID> ids) {
        return Flux.fromStream(() -> directoryElementRepository.findAllById(ids).stream().map(ElementAttributes::toElementAttributes));
    }

    public Mono<Void> notify(@NonNull String notificationName, @NonNull UUID elementUuid, @NonNull String userId) {
        ElementAttributes.Notification notification;
        try {
            notification = ElementAttributes.Notification.valueOf(notificationName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.error(DirectoryException.createActionUnknown(notificationName));
        }
        if (notification == UPDATE_DIRECTORY) {
            return emitDirectoryChangedNotification(elementUuid, userId);
        } else {
            return Mono.error(DirectoryException.createNotificationUnknown(notification.name()));
        }
    }

    public Mono<Void> emitDirectoryChangedNotification(UUID elementUuid, String userId) {
        return getElement(elementUuid)
            .doOnNext(elementAttributes -> {
                UUID parentUuid = getParentUuid(elementUuid);
                emitDirectoryChanged(parentUuid, userId, elementAttributes.getAccessRights().isPrivate(), parentUuid == null, NotificationType.UPDATE_DIRECTORY);
            })
            .then();
    }
}
