/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RenameStudyAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryException.Type.NOT_ALLOWED;
import static org.gridsuite.directory.server.DirectoryException.Type.STUDY_NOT_FOUND;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
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

    private final StreamBridge studyUpdatePublisher;

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);

    public DirectoryService(
            DirectoryElementRepository directoryElementRepository,
            @Value("${backing-services.study-server.base-uri:http://study-server/}") String studyServerBaseUri,
            WebClient.Builder webClientBuilder, StreamBridge studyUpdatePublisher) {
        this.directoryElementRepository = directoryElementRepository;
        this.studyServerBaseUri = studyServerBaseUri;

        this.webClient = webClientBuilder.build();
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
                emitDirectoryChanged(parentUuid, userId, error, isPrivate, parentUuid == null);
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

    private void emitDirectoryChanged(UUID directoryUuid, String userId, boolean isPrivate, boolean isRoot) {
        emitDirectoryChanged(directoryUuid, userId, null, isPrivate, isRoot);
    }

    private void emitDirectoryChanged(UUID directoryUuid, String userId, String error, boolean isPrivate, boolean isRoot) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_DIRECTORY_UUID, directoryUuid)
                .setHeader(HEADER_IS_ROOT_DIRECTORY, isRoot)
                .setHeader(HEADER_IS_PUBLIC_DIRECTORY, !isPrivate)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES)
                .setHeader(HEADER_ERROR, error);
        sendUpdateMessage(messageBuilder.build());
    }

    /* converters */

    private static ElementAttributes toElementAttributes(DirectoryElementEntity entity) {
        return new ElementAttributes(entity.getId(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    /* methods */
    public Mono<ElementAttributes> createElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid, String userId) {
        return insertElement(elementAttributes, parentDirectoryUuid).doOnSuccess(unused -> emitDirectoryChanged(
               parentDirectoryUuid == null ? elementAttributes.getElementUuid() : parentDirectoryUuid,
               userId,
               isPrivateForNotification(parentDirectoryUuid, elementAttributes.getAccessRights().isPrivate()),
               parentDirectoryUuid == null));
    }

    /* methods */
    private Mono<ElementAttributes> insertElement(ElementAttributes elementAttributes, UUID parentDirectoryUuid) {
        return Mono.fromCallable(() -> toElementAttributes(directoryElementRepository.save(new DirectoryElementEntity(
                elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                parentDirectoryUuid,
                elementAttributes.getElementName(),
                elementAttributes.getType().toString(),
                elementAttributes.getAccessRights() == null || elementAttributes.getAccessRights().isPrivate(),
                elementAttributes.getOwner()))));
    }

    public Mono<ElementAttributes> createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, String userId) {
        ElementAttributes elementAttributes = new ElementAttributes(null, rootDirectoryAttributes.getElementName(), ElementType.DIRECTORY,
                rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner());
        return insertElement(elementAttributes, null).doOnSuccess(element ->
                emitDirectoryChanged(element.getElementUuid(), userId, element.getAccessRights().isPrivate(), true));
    }

    public Flux<ElementAttributes> listDirectoryContent(UUID directoryUuid, String userId) {
        return Flux.fromStream(directoryContentStream(directoryUuid, userId));
    }

    private Stream<ElementAttributes> directoryContentStream(UUID directoryUuid, String userId) {
        return directoryElementRepository.findDirectoryContentByUserId(directoryUuid, userId).stream().map(DirectoryService::toElementAttributes);
    }

    public Flux<ElementAttributes> getRootDirectories(String userId) {
        return Flux.fromStream(directoryElementRepository.findRootDirectoriesByUserId(userId).stream().map(DirectoryService::toElementAttributes));
    }

    public Mono<Void> renameElement(UUID elementUuid, String newElementName, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
            if (elementAttributes.getType().equals(ElementType.STUDY)) {
                return renameStudy(elementUuid, userId, newElementName);
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
                                directoryElementEntity.getParentId() == null
                        );
                    });
        });
    }

    public Mono<Void> setAccessRights(UUID elementUuid, boolean newIsPrivate, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
            if (elementAttributes.getType().equals(ElementType.STUDY)) {
                return setStudyAccessRight(elementUuid, userId, newIsPrivate);
            }
            return Mono.empty();
        }).doOnSuccess(unused -> {
            directoryElementRepository.updateElementAccessRights(elementUuid, newIsPrivate);
            directoryElementRepository.findById(elementUuid).ifPresent(directoryElementEntity -> {
                boolean isPrivate = isPrivateForNotification(directoryElementEntity.getParentId(), directoryElementEntity.isPrivate());
                emitDirectoryChanged(
                        directoryElementEntity.getParentId() == null ? directoryElementEntity.getId() : directoryElementEntity.getParentId(),
                        userId,
                        isPrivate,
                        directoryElementEntity.getParentId() == null);
            });
        });
    }

    public Mono<Void> deleteElement(UUID elementUuid, String userId) {
        return getElementInfos(elementUuid).map(elementAttributes -> {
            UUID parentUuid = getParentUuid(elementUuid);
            deleteObject(elementAttributes, userId);
            boolean isPrivate = isPrivateForNotification(parentUuid, elementAttributes.getAccessRights().isPrivate());
            emitDirectoryChanged(parentUuid == null ? elementUuid : parentUuid, userId, isPrivate, parentUuid == null);
            return elementAttributes;
        }).then();
    }

    private void deleteObject(ElementAttributes elementAttributes, String userId) {
        if (elementAttributes.getType().equals(ElementType.STUDY)) {
            deleteFromStudyServer(elementAttributes.getElementUuid(), userId).subscribe();
        } else {
            // directory
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
        return Mono.fromCallable(() -> directoryElementRepository.findById(directoryUuid).map(DirectoryService::toElementAttributes).orElse(null));
    }

    private UUID getParentUuid(UUID directoryUuid) {
        return directoryElementRepository.findById(directoryUuid).map(DirectoryElementEntity::getParentId).orElse(null);
    }

    private boolean isPrivateDirectory(UUID directoryUuid) {
        return directoryElementRepository.findById(directoryUuid).map(DirectoryElementEntity::isPrivate).orElseThrow(() -> new DirectoryServerException(directoryUuid + " not found!"));
    }

    private boolean isPrivateForNotification(UUID parentDirectoryUuid, boolean isCurrentElementPrivate) {
        if (parentDirectoryUuid == null) {
            return isCurrentElementPrivate;
        } else {
            return isPrivateDirectory(parentDirectoryUuid);
        }
    }

    /* handle STUDY objects */

    private Mono<Void> deleteFromStudyServer(UUID studyUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION + "/studies/{studyUuid}")
                .buildAndExpand(studyUuid)
                .toUriString();

        return webClient.delete()
                .uri(studyServerBaseUri + path)
                .header(HEADER_USER_ID, userId)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, r -> Mono.empty())
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<Void> insertStudyWithExistingCaseFile(UUID studyUuid, String studyName, String description, String userId, Boolean isPrivate, UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION +
                "/studies/{studyName}/cases/{caseUuid}?description={description}&isPrivate={isPrivate}&studyUuid={studyUuid}")
                .buildAndExpand(studyName, caseUuid, description, isPrivate, studyUuid)
                .toUriString();

        return webClient.post()
                .uri(studyServerBaseUri + path)
                .header(HEADER_USER_ID, userId)
                .retrieve()
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<Void> renameStudy(UUID studyUuid, String userId, String newElementName) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION + "/studies/{studyUuid}/rename")
                .buildAndExpand(studyUuid)
                .toUriString();

        return webClient.post()
                .uri(studyServerBaseUri + path)
                .header(HEADER_USER_ID, userId)
                .body(BodyInserters.fromValue(new RenameStudyAttributes(newElementName)))
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(STUDY_NOT_FOUND)))
                .onStatus(httpStatus -> httpStatus == HttpStatus.FORBIDDEN, clientResponse -> Mono.error(new DirectoryException(NOT_ALLOWED)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<Void> setStudyAccessRight(UUID studyUuid, String userId, boolean isPrivate) {
        String path;
        if (isPrivate) {
            path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION + "/studies/{studyUuid}/private")
                    .buildAndExpand(studyUuid)
                    .toUriString();
        } else {
            path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION + "/studies/{studyUuid}/public")
                    .buildAndExpand(studyUuid)
                    .toUriString();
        }
        return webClient.post()
                .uri(studyServerBaseUri + path)
                .header(HEADER_USER_ID, userId)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(STUDY_NOT_FOUND)))
                .onStatus(httpStatus -> httpStatus == HttpStatus.FORBIDDEN, clientResponse -> Mono.error(new DirectoryException(NOT_ALLOWED)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    private Mono<Void> insertStudyWithCaseFile(UUID studyUuid, String studyName, String description, String userId, Boolean isPrivate, Mono<FilePart> caseFile) {
        return caseFile.flatMap(file -> {
            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            multipartBodyBuilder.part("caseFile", file);

            String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION +
                    "/studies/{studyName}?description={description}&isPrivate={isPrivate}&studyUuid={studyUuid}")
                    .buildAndExpand(studyName, description, isPrivate, studyUuid)
                    .toUriString();

            return webClient.post()
                    .uri(studyServerBaseUri + path)
                    .header(HEADER_USER_ID, userId)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA.toString())
                    .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .publishOn(Schedulers.boundedElastic())
                    .log(ROOT_CATEGORY_REACTOR, Level.FINE);
        });
    }

    public Mono<Void> createStudy(String studyName, UUID caseUuid, String description, String userId, Boolean isPrivate, UUID parentDirectoryUuid) {
        ElementAttributes elementAttributes = new ElementAttributes(null, studyName, ElementType.STUDY,
                new AccessRightsAttributes(isPrivate), userId);
        return insertElement(elementAttributes, parentDirectoryUuid).flatMap(elementAttributes1 -> {
            emitDirectoryChanged(parentDirectoryUuid, userId, isPrivateDirectory(parentDirectoryUuid), false);
            return insertStudyWithExistingCaseFile(elementAttributes1.getElementUuid(), studyName, description, userId, isPrivate, caseUuid)
                    .doOnError(err -> {
                        deleteElement(elementAttributes1.getElementUuid(), userId);
                        emitDirectoryChanged(parentDirectoryUuid, userId, isPrivateDirectory(parentDirectoryUuid), false);
                    });
        });
    }

    public Mono<Void> createStudy(String studyName, Mono<FilePart> caseFile, String description, String userId, Boolean isPrivate, UUID parentDirectoryUuid) {
        ElementAttributes elementAttributes = new ElementAttributes(null, studyName, ElementType.STUDY,
                new AccessRightsAttributes(isPrivate), userId);
        return insertElement(elementAttributes, parentDirectoryUuid).flatMap(elementAttributes1 -> {
            // notification here
            emitDirectoryChanged(parentDirectoryUuid, userId, isPrivateDirectory(parentDirectoryUuid), false);
            return insertStudyWithCaseFile(elementAttributes1.getElementUuid(), studyName, description, userId, isPrivate, caseFile)
                    .doOnError(err -> {
                        deleteElement(elementAttributes1.getElementUuid(), userId).subscribe();
                        emitDirectoryChanged(parentDirectoryUuid, userId, isPrivateDirectory(parentDirectoryUuid), false);
                    });
        });
    }
}
