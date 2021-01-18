/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.CreateDirectoryAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.repository.DirectoryRootEntity;
import org.gridsuite.directory.server.repository.DirectoryRootRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@ComponentScan(basePackageClasses = DirectoryElementRepository.class)
@Service
class DirectoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryService.class);

    private final DirectoryElementRepository directoryElementRepository;

    private final DirectoryRootRepository directoryRootRepository;

    DirectoryService(DirectoryRootRepository directoryRootRepository, DirectoryElementRepository directoryElementRepository) {
        this.directoryRootRepository = directoryRootRepository;
        this.directoryElementRepository = directoryElementRepository;
    }

    private static ElementAttributes fromEntity(DirectoryElementEntity entity) {
        return new ElementAttributes(entity.getId().toString(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    public Mono<DirectoryRootEntity> getRootDirectory() {
        return directoryRootRepository.findAll().single();
    }

    public Mono<DirectoryElementEntity> createDirectory(CreateDirectoryAttributes createDirectoryAttributes) {
        Mono<DirectoryElementEntity> createdDirectory = directoryElementRepository.save(new DirectoryElementEntity(null, createDirectoryAttributes.getParentId(),
                                                                   createDirectoryAttributes.getDirectoryName(),
                                                                   ElementType.DIRECTORY.toString(),
                                                                   createDirectoryAttributes.getAccessRights() != null ? createDirectoryAttributes.getAccessRights().isPrivate() : true,
                                                                   createDirectoryAttributes.getOwner()));

        return createdDirectory;
    }

    public Mono<DirectoryElementEntity> addElementToDirectory(String directoryUuid, ElementAttributes elementAttributes) {
        return directoryElementRepository.save(new DirectoryElementEntity(null,
                                                                   directoryUuid != null ? UUID.fromString(directoryUuid) : null,
                                                                   elementAttributes.getElementName(),
                                                                   elementAttributes.getType().toString(),
                                                                   elementAttributes.getAccessRights().isPrivate(),
                                                                   elementAttributes.getOwner()));
    }

    public Flux<ElementAttributes> listDirectoryContent(String directoryUuid) {
        return directoryElementRepository.findByParentId(directoryUuid != null ? UUID.fromString(directoryUuid) : null).map(DirectoryService::fromEntity);
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
