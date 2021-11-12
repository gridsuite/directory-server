/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;

import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ElementAttributes {
    private UUID elementUuid;

    private String elementName;

    private String type;

    private AccessRightsAttributes accessRights;

    private String owner;

    private long subdirectoriesCount;

    /* converters */
    public static ElementAttributes toElementAttributes(DirectoryElementEntity entity) {
        return toElementAttributes(entity, 0L);
    }

    public static ElementAttributes toElementAttributes(DirectoryElementEntity entity, long subDirectoriesCount) {
        return new ElementAttributes(entity.getId(), entity.getName(), entity.getType(),
            new AccessRightsAttributes(entity.isPrivate()), entity.getOwner(), subDirectoriesCount);
    }

    public static ElementAttributes toElementAttributes(RootDirectoryAttributes rootDirectoryAttributes) {
        return new ElementAttributes(null, rootDirectoryAttributes.getElementName(), DIRECTORY,
            rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner(), 0L);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, String elementName, String elementType, boolean isPrivate, String userId) {
        return toElementAttributes(elementUuid, elementName, elementType, isPrivate, userId, 0L);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, String elementName, String elementType, boolean isPrivate, String userId, long subdirectoriesCount) {
        return new ElementAttributes(elementUuid, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, subdirectoriesCount);
    }

    public static ElementAttributes toElementAttributes(String elementName, String elementType, boolean isPrivate, String userId) {
        return new ElementAttributes(null, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, 0L);
    }
}
