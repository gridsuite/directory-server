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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);

    private final DirectoryElementRepository directoryElementRepository;

    DirectoryService(DirectoryElementRepository directoryElementRepository) {
        this.directoryElementRepository = directoryElementRepository;
    }

    private static ElementAttributes fromEntity(DirectoryElementEntity entity) {
        return new ElementAttributes(entity.getId(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    public Mono<DirectoryElementEntity> createDirectory(DirectoryAttributes directoryAttributes) {
        Mono<DirectoryElementEntity> createdDirectory = directoryElementRepository.save(new DirectoryElementEntity(null, directoryAttributes.getParentId(),
                                                                   directoryAttributes.getDirectoryName(),
                                                                   ElementType.DIRECTORY.toString(),
                                                                   directoryAttributes.getAccessRights() != null ? directoryAttributes.getAccessRights().isPrivate() : true,
                                                                   directoryAttributes.getOwner(),
                                                        true));

        return createdDirectory;
    }

    public Mono<DirectoryElementEntity> addElementToDirectory(Optional<String> directoryUuid, ElementAttributes elementAttributes) {
        return directoryElementRepository.save(new DirectoryElementEntity(elementAttributes.getElementUuid(),
                                                                   directoryUuid.isPresent() ? UUID.fromString(directoryUuid.get()) : null,
                                                                   elementAttributes.getElementName(),
                                                                   elementAttributes.getType().toString(),
                                                                   elementAttributes.getAccessRights().isPrivate(),
                                                                   elementAttributes.getOwner(),
                                                        true));
    }

    public Flux<ElementAttributes> listDirectoryContent(Optional<String> directoryUuid) {
        return directoryElementRepository.findByParentId(directoryUuid.isPresent() ? UUID.fromString(directoryUuid.get()) : null).map(DirectoryService::fromEntity);
    }

    public Mono<Void> renameElement(String elementUuid, String newElementName) {
        return directoryElementRepository.updateElementName(UUID.fromString(elementUuid), newElementName);
    }

    public void setDirectoryAccessRights(String directoryUuid, AccessRightsAttributes accessRightsAttributes) {
    }

    public Mono<Void> deleteElement(String elementUuid) {
        return directoryElementRepository.deleteById(UUID.fromString(elementUuid));
    }

}
