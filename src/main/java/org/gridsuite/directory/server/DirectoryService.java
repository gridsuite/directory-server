/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.DirectoryAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Service
class DirectoryService {

    private final DirectoryElementRepository directoryElementRepository;

    DirectoryService(DirectoryElementRepository directoryElementRepository) {
        this.directoryElementRepository = directoryElementRepository;
    }

    private static ElementAttributes fromEntity(DirectoryElementEntity entity) {
        return new ElementAttributes(entity.getId(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    public Mono<DirectoryElementEntity> createDirectory(DirectoryAttributes directoryAttributes) {
        return Mono.fromCallable(() -> directoryElementRepository.save(new DirectoryElementEntity(UUID.randomUUID(), directoryAttributes.getParentId(),
                                                                   directoryAttributes.getDirectoryName(),
                                                                   ElementType.DIRECTORY.toString(),
                                                                   directoryAttributes.getAccessRights() == null || directoryAttributes.getAccessRights().isPrivate(),
                                                                   directoryAttributes.getOwner())));
    }

    public Mono<DirectoryElementEntity> addElementToDirectory(Optional<String> directoryUuid, ElementAttributes elementAttributes) {
        return Mono.just(directoryElementRepository.save(new DirectoryElementEntity(elementAttributes.getElementUuid(),
                                                                   directoryUuid.isPresent() ? UUID.fromString(directoryUuid.get()) : null,
                                                                   elementAttributes.getElementName(),
                                                                   elementAttributes.getType().toString(),
                                                                   elementAttributes.getAccessRights().isPrivate(),
                                                                   elementAttributes.getOwner())));
    }

    public Flux<ElementAttributes> listDirectoryContent(String directoryUuid) {
        return Flux.fromStream(directoryContentStream(directoryUuid));
    }

    private Stream<ElementAttributes> directoryContentStream(String directoryUuid) {
        return directoryElementRepository.findByParentId(UUID.fromString(directoryUuid)).stream().map(DirectoryService::fromEntity);
    }

    public Mono<ElementAttributes> getDirectoryInfos(String directoryUuid) {
        return Mono.fromCallable(() -> directoryElementRepository.findById(UUID.fromString(directoryUuid)).map(DirectoryService::fromEntity).orElse(null));
    }

    public Flux<ElementAttributes> getRootDirectories() {
        return Flux.fromStream(directoryElementRepository.findByParentId(null).stream().map(DirectoryService::fromEntity));
    }

    public Mono<Void> renameElement(String elementUuid, String newElementName) {
        return Mono.fromRunnable(() -> directoryElementRepository.updateElementName(UUID.fromString(elementUuid), newElementName));
    }

    public Mono<Void> setDirectoryAccessRights(String directoryUuid, AccessRightsAttributes accessRightsAttributes) {
        return Mono.fromRunnable(() -> directoryElementRepository.updateElementAccessRights(UUID.fromString(directoryUuid), accessRightsAttributes.isPrivate()));
    }

    public Mono<Void> deleteElement(String elementUuid) {
        return Mono.fromRunnable(() -> {
            directoryElementRepository.deleteById(UUID.fromString(elementUuid));
        });
    }

    private void deleteElementTree(String elementUuid) {
        directoryContentStream(elementUuid).map(e -> e.getElementUuid()).forEach(uuid -> {
            deleteElementTree(uuid.toString());
        });
        directoryElementRepository.deleteById(UUID.fromString(elementUuid));
    }

}
