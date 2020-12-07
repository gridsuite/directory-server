/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.datastax.driver.core.utils.UUIDs;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.CreateDirectoryAttributes;
import org.gridsuite.directory.server.dto.DirectoryAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementKey;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        return new ElementAttributes(entity.getDirectoryElementKey().getChildId().toString(), entity.getChildName(), ElementType.valueOf(entity.getChildType()), new AccessRightsAttributes(entity.isPrivate()));
    }

    public DirectoryAttributes createDirectory(CreateDirectoryAttributes createDirectoryAttributes) {
        UUID createdDirectoryUuid = UUIDs.timeBased();
        directoryElementRepository.insert(new DirectoryElementEntity(new DirectoryElementKey(createDirectoryAttributes.getParentId(), createdDirectoryUuid),
                                                                            createDirectoryAttributes.getDirectoryName(),
                                                                            ElementType.DIRECTORY.toString(),
                                                                            createDirectoryAttributes.getAccessRights() != null ? createDirectoryAttributes.getAccessRights().isPrivate() : true));
        return new DirectoryAttributes(createdDirectoryUuid.toString(), createDirectoryAttributes.getDirectoryName(), createDirectoryAttributes.getAccessRights());
    }

    public void addElementToDirectory(String directoryUuid, ElementAttributes elementAttributes) {
        directoryElementRepository.insert(new DirectoryElementEntity(new DirectoryElementKey(UUID.fromString(directoryUuid), UUID.fromString(elementAttributes.getElementUuid())),
                elementAttributes.getElementName(),
                elementAttributes.getType().toString(),
                elementAttributes.getAccessRights().isPrivate()));
    }

    public List<ElementAttributes> listDirectoryContent(String directoryUuid) {
        return directoryElementRepository.findByDirectoryElementKeyId(UUID.fromString(directoryUuid)).stream().map(DirectoryService::fromEntity).collect(Collectors.toList());

    }

    public void renameElement(String directoryUuid, String elementUuid, String newElementName) {
    }

    public void setDirectoryAccessRights(String directoryUuid, AccessRightsAttributes accessRightsAttributes) {
    }

    public void deleteDirectory(String directoryUuid) {
        directoryElementRepository.deleteByDirectoryElementKeyId(UUID.fromString(directoryUuid));
    }

}
