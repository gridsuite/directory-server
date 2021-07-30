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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
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

    private WebClient webClient;
    private String studyServerBaseUri;

    private final DirectoryElementRepository directoryElementRepository;

    public DirectoryService(
            DirectoryElementRepository directoryElementRepository,
            @Value("${backing-services.study-server.base-uri:http://study-server/}") String studyServerBaseUri,
            WebClient.Builder webClientBuilder) {
        this.directoryElementRepository = directoryElementRepository;
        this.studyServerBaseUri = studyServerBaseUri;

        this.webClient = webClientBuilder.build();
    }

    private static ElementAttributes toElementAttributes(DirectoryElementEntity entity) {
        return new ElementAttributes(entity.getId(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    public Mono<ElementAttributes> createElement(ElementAttributes elementAttributes, UUID directoryUuid) {
        return Mono.fromCallable(() -> toElementAttributes(directoryElementRepository.save(new DirectoryElementEntity(
                elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                directoryUuid,
                elementAttributes.getElementName(),
                elementAttributes.getType().toString(),
                elementAttributes.getAccessRights() == null || elementAttributes.getAccessRights().isPrivate(),
                elementAttributes.getOwner()))));
    }

    public Mono<ElementAttributes> createRootDirectory(RootDirectoryAttributes rootDirectoryAttributes, UUID directoryUuid) {
        ElementAttributes elementAttributes = new ElementAttributes(null, rootDirectoryAttributes.getElementName(), ElementType.DIRECTORY,
                rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner());
        return createElement(elementAttributes, directoryUuid);
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
            //todo emit
        });
    }

    public Mono<Void> setAccessRights(UUID elementUuid, boolean isPrivate, String userId) {
        return getElementInfos(elementUuid).flatMap(elementAttributes -> {
            if (elementAttributes.getType().equals(ElementType.STUDY)) {
                return setStudyAccessRight(elementUuid, userId, isPrivate);
            }
            return Mono.empty();
        }).doOnSuccess(unused -> {
            directoryElementRepository.updateElementAccessRights(elementUuid, isPrivate);
            //TODO emit
        });
    }

    public Mono<Void> deleteElement(UUID elementUuid, String userId) {
        return getElementInfos(elementUuid).map(elementAttributes -> {
            deleteObject(elementAttributes, userId);
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

    private Mono<Void> deleteFromStudyServer(UUID studyUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION + "/studies/{studyUuid}")
                .buildAndExpand(studyUuid)
                .toUriString();

        return webClient.delete()
                .uri(studyServerBaseUri + path)
                .header("userId", userId)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, r -> Mono.empty())
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
                .header("userId", userId)
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
                .header("userId", userId)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(STUDY_NOT_FOUND)))
                .onStatus(httpStatus -> httpStatus == HttpStatus.FORBIDDEN, clientResponse -> Mono.error(new DirectoryException(NOT_ALLOWED)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public void setStudyServerBaseUri(String studyServerBaseUri) {
        this.studyServerBaseUri = studyServerBaseUri;
    }

    public Mono<ElementAttributes> getElementInfos(UUID directoryUuid) {
        return Mono.fromCallable(() -> directoryElementRepository.findById(directoryUuid).map(DirectoryService::toElementAttributes).orElse(null));
    }
}
