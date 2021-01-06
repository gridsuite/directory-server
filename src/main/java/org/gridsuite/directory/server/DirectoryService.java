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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
        return new ElementAttributes(entity.getId().toString(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    public RootDirectoryAttributes getRootDirectory() {
        return null;
    }

    public DirectoryAttributes createDirectory(CreateDirectoryAttributes createDirectoryAttributes) {
        UUID createdDirectoryUuid = UUID.randomUUID();
        directoryElementRepository.save(new DirectoryElementEntity(createdDirectoryUuid, createDirectoryAttributes.getParentId(),
                                                                   createDirectoryAttributes.getDirectoryName(),
                                                                   ElementType.DIRECTORY.toString(),
                                                                   createDirectoryAttributes.getAccessRights() != null ? createDirectoryAttributes.getAccessRights().isPrivate() : true,
                                                                   createDirectoryAttributes.getOwner()));
        return new DirectoryAttributes(createdDirectoryUuid.toString(), createDirectoryAttributes.getDirectoryName(), createDirectoryAttributes.getAccessRights(), createDirectoryAttributes.getOwner());
    }

    public void addElementToDirectory(String directoryUuid, ElementAttributes elementAttributes) {
        directoryElementRepository.save(new DirectoryElementEntity(UUID.fromString(elementAttributes.getElementUuid()), UUID.fromString(directoryUuid),
                                                                   elementAttributes.getElementName(),
                                                                   elementAttributes.getType().toString(),
                                                                   elementAttributes.getAccessRights().isPrivate(),
                                                                   elementAttributes.getOwner()));
    }

    public Flux<ElementAttributes> listDirectoryContent(String directoryUuid) {
        return directoryElementRepository.findByParentId(UUID.fromString(directoryUuid)).map(DirectoryService::fromEntity);
    }

    public void renameElement(String elementUuid, String newElementName) {
        directoryElementRepository.updateElementName(UUID.fromString(elementUuid), newElementName);
    }

    public void setDirectoryAccessRights(String directoryUuid, AccessRightsAttributes accessRightsAttributes) {
    }

    public void deleteDirectory(String directoryUuid) {
        directoryElementRepository.deleteByParentId(UUID.fromString(directoryUuid));
    }

}
