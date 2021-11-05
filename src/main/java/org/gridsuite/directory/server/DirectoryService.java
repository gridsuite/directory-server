/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.*;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryException.Type.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
class DirectoryService {
    private static final String DELIMITER = "/";
    private static final String STUDY_SERVER_API_VERSION = "v1";
    private static final String ROOT_CATEGORY_REACTOR = "reactor.";

    private final WebClient webClient;
    private String studyServerBaseUri;

    private final DirectoryElementRepository directoryElementRepository;

    private static final String CATEGORY_BROKER_OUTPUT = DirectoryService.class.getName() + ".output-broker-messages";
    private static final String CATEGORY_BROKER_INPUT = DirectoryService.class.getName() + ".input-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    static final String HEADER_USER_ID = "userId";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_DIRECTORIES = "directories";
    static final String HEADER_DIRECTORY_UUID = "directoryUuid";
    static final String HEADER_IS_PUBLIC_DIRECTORY = "isPublicDirectory";
    static final String HEADER_IS_ROOT_DIRECTORY = "isRootDirectory";
    static final String HEADER_ERROR = "error";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_NOTIFICATION_TYPE = "notificationType";
    static final String STUDY = "STUDY";
    static final String CONTINGENCY_LIST = "CONTINGENCY_LIST";
    static final String FILTER = "FILTER";
    static final String DIRECTORY = "DIRECTORY";

    private final StreamBridge studyUpdatePublisher;

    private ContingencyListService actionsService;
    private FilterService filterService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);

    public DirectoryService(
            DirectoryElementRepository directoryElementRepository,
            @Value("${backing-services.study-server.base-uri:http://study-server/}") String studyServerBaseUri,
            WebClient.Builder webClientBuilder, StreamBridge studyUpdatePublisher,
            ContingencyListService actionsService,
            FilterService filterService) {
        this.directoryElementRepository = directoryElementRepository;
        this.studyServerBaseUri = studyServerBaseUri;

        this.webClient = webClientBuilder.build();
        this.studyUpdatePublisher = studyUpdatePublisher;
        this.actionsService = actionsService;
        this.filterService = filterService;
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
        })
        .doOnError(throwable -> LOGGER.error(throwable.toString(), throwable))
        .subscribe();
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

    /* converters */
    private ElementAttributes toElementAttributes(DirectoryElementEntity entity, long subDirectoriesCount) {
        return new ElementAttributes(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                new AccessRightsAttributes(entity.isPrivate()),
                entity.getOwner(),
                subDirectoriesCount
        );
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
        return Mono.fromCallable(() -> toElementAttributes(directoryElementRepository.save(new DirectoryElementEntity(
                elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                parentDirectoryUuid,
                elementAttributes.getElementName(),
                elementAttributes.getType(),
                elementAttributes.getAccessRights() == null || elementAttributes.getAccessRights().isPrivate(),
                elementAttributes.getOwner())), 0L));
    }

    public Mono<ElementAttributes> createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        ElementAttributes elementAttributes = new ElementAttributes(null, rootDirectoryAttributes.getElementName(), DIRECTORY,
                rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner(), 0L);
        return insertElement(elementAttributes, null).doOnSuccess(element ->
                emitDirectoryChanged(element.getElementUuid(), userId, element.getAccessRights().isPrivate(), true, NotificationType.ADD_DIRECTORY));
    }

    public Flux<ElementAttributes> listDirectoryContent(UUID directoryUuid, String userId) {
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
        return directoryElements.stream().map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L)));
    }

    public Flux<ElementAttributes> getRootDirectories(String userId) {
        List<DirectoryElementEntity> directoryElements = directoryElementRepository.findRootDirectoriesByUserId(userId);
        Map<UUID, Long> subdirectoriesCountsMap = getSubDirectoriesInfos(directoryElements.stream().map(DirectoryElementEntity::getId).collect(Collectors.toList()), userId);
        return Flux.fromStream(directoryElements.stream().map(e -> toElementAttributes(e, subdirectoriesCountsMap.getOrDefault(e.getId(), 0L))));
    }

    public Mono<Void> renameElement(UUID elementUuid, String newElementName, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
            if (!userId.equals(elementAttributes.getOwner())) {
                return Mono.error(new DirectoryException(NOT_ALLOWED));
            }
            if (elementAttributes.getType().equals(STUDY)) {
                return renameStudy(elementUuid, userId, newElementName);
            } else if (elementAttributes.getType().equals(CONTINGENCY_LIST)) {
                return actionsService.renameContingencyList(elementUuid, newElementName);
            } else if (elementAttributes.getType().equals(FILTER)) {
                return filterService.renameFilter(elementUuid, newElementName);
            } else {
                return Mono.empty();
            }
        }).doOnSuccess(unused -> {
            directoryElementRepository.updateElementName(elementUuid, newElementName);
            directoryElementRepository.findById(elementUuid)
                    .ifPresent(directoryElementEntity -> {
                        boolean isPrivate = isPrivateForNotification(directoryElementEntity.getParentId(), directoryElementEntity.isPrivate());
                        emitDirectoryChanged(
                                directoryElementEntity.getParentId() == null ? elementUuid : directoryElementEntity.getParentId(),
                                userId,
                                isPrivate,
                                directoryElementEntity.getParentId() == null,
                                NotificationType.UPDATE_DIRECTORY
                        );
                    });
        });
    }

    public Mono<Void> setAccessRights(UUID elementUuid, boolean newIsPrivate, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
            if (!userId.equals(elementAttributes.getOwner())) {
                return Mono.error(new DirectoryException(NOT_ALLOWED));
            }
            directoryElementRepository.updateElementAccessRights(elementUuid, newIsPrivate);
            directoryElementRepository.findById(elementUuid).ifPresent(directoryElementEntity -> {
                boolean isPrivate = isPrivateForNotification(directoryElementEntity.getParentId(), false);
                // second parameter should be always false because when we pass a root folder from public -> private we should notify all clients
                emitDirectoryChanged(
                        directoryElementEntity.getParentId() == null ? directoryElementEntity.getId() : directoryElementEntity.getParentId(),
                        userId,
                        isPrivate,
                        directoryElementEntity.getParentId() == null,
                        NotificationType.UPDATE_DIRECTORY);
            });
            return Mono.empty();
        });
    }

    public Mono<Void> deleteElement(UUID elementUuid, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
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

    public void setStudyServerBaseUri(String studyServerBaseUri) {
        this.studyServerBaseUri = studyServerBaseUri;
    }

    public Mono<ElementAttributes> getElementInfos(UUID directoryUuid) {
        return Mono.fromCallable(() -> directoryElementRepository.findById(directoryUuid).map(e -> toElementAttributes(e, 0L)).orElse(null));
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

    //TODO remove when names are removed from study-server
    private Mono<Void> renameStudy(UUID studyUuid, String userId, String newElementName) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION + "/studies/{studyUuid}/rename")
                .buildAndExpand(studyUuid)
                .toUriString();

        return webClient.post()
                .uri(studyServerBaseUri + path)
                .header(HEADER_USER_ID, userId)
                .body(BodyInserters.fromValue(new RenameElementAttributes(newElementName)))
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(STUDY_NOT_FOUND)))
                .onStatus(httpStatus -> httpStatus == HttpStatus.FORBIDDEN, clientResponse -> Mono.error(new DirectoryException(NOT_ALLOWED)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Flux<ElementAttributes> getElementsAttribute(List<UUID> ids) {
        return Flux.fromStream(() -> directoryElementRepository.findAllById(ids).stream().map(e -> toElementAttributes(e, 0)));
    }

    public Mono<Void> sendUpdateTypeNotification(UUID elementUuid, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
            UUID parentUuid = getParentUuid(elementUuid);
            emitDirectoryChanged(parentUuid, userId, elementAttributes.getAccessRights().isPrivate(), parentUuid == null,
                    NotificationType.UPDATE_DIRECTORY);
            return Mono.empty();
        });
    }
}
