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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@ComponentScan(basePackageClasses = DirectoryElementRepository.class)
@Service
class DirectoryService {

    private final DirectoryElementRepository directoryElementRepository;

    DirectoryService(DirectoryElementRepository directoryElementRepository) {
        this.directoryElementRepository = directoryElementRepository;
    }

    public static ElementAttributes toElementAttributes(DirectoryElementEntity entity) {
        return ElementAttributes.builder()
            .id(entity.getId())
            .name(entity.getName())
            .type(ElementType.valueOf(entity.getType()))
            .accessRights(new AccessRightsAttributes(entity.isPrivate()))
            .owner(entity.getOwner())
            .build();
    }

    public Mono<DirectoryElementEntity> createDirectory(DirectoryAttributes directoryAttributes) {
        return directoryElementRepository.save(new DirectoryElementEntity(null, directoryAttributes.getParentId(),
            directoryAttributes.getName(),
            ElementType.DIRECTORY.toString(),
            directoryAttributes.getAccessRights() == null || directoryAttributes.getAccessRights().isPrivate(),
            directoryAttributes.getOwner(),
            true));
    }

    public Mono<DirectoryElementEntity> addElementToDirectory(Optional<String> directoryUuid, ElementAttributes elementAttributes) {
        return directoryElementRepository.save(new DirectoryElementEntity(elementAttributes.getId(),
            directoryUuid.map(UUID::fromString).orElse(null),
            elementAttributes.getName(),
            elementAttributes.getType().toString(),
            elementAttributes.getAccessRights().isPrivate(),
            elementAttributes.getOwner(),
            true));
    }

    public Flux<ElementAttributes> listDirectoryContent(String directoryUuid) {
        return directoryElementRepository.findByParentId(UUID.fromString(directoryUuid)).map(DirectoryService::toElementAttributes);
    }

    public Flux<ElementAttributes> getRootDirectories() {
        return directoryElementRepository.findByParentId(null).map(DirectoryService::toElementAttributes);
    }

    public Mono<Void> renameElement(String elementUuid, String newElementName) {
        return directoryElementRepository.updateElementName(UUID.fromString(elementUuid), newElementName);
    }

    public Mono<Void> setDirectoryAccessRights(String directoryUuid, AccessRightsAttributes accessRightsAttributes) {
        return directoryElementRepository.updateElementAccessRights(UUID.fromString(directoryUuid), accessRightsAttributes.isPrivate());
    }

    public Mono<Void> deleteElement(String elementUuid) {
        return directoryElementRepository.deleteById(UUID.fromString(elementUuid));
    }

}
