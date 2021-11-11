/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.gridsuite.directory.server.ElementType;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@SuperBuilder
@Getter
@NoArgsConstructor
@ToString
public class ElementAttributes {
    private UUID elementUuid;

    private String elementName;

    private ElementType type;

    private AccessRightsAttributes accessRights;

    private String owner;

    private long subdirectoriesCount = 0L;

    /* converters */
    public static ElementAttributes toElementAttributes(DirectoryElementEntity entity) {
        return toElementAttributes(entity, 0L);
    }

    public static ElementAttributes toElementAttributes(DirectoryElementEntity entity, long subDirectoriesCount) {
        return ElementAttributes.builder()
            .elementUuid(entity.getId())
            .elementName(entity.getName())
            .type(ElementType.valueOf(entity.getType()))
            .accessRights(AccessRightsAttributes.builder().isPrivate(entity.isPrivate()).build())
            .owner(entity.getOwner())
            .subdirectoriesCount(subDirectoriesCount)
            .build();
    }

    public static ElementAttributes toElementAttributes(RootDirectoryAttributes rootDirectoryAttributes) {
        return ElementAttributes.builder()
            .elementUuid(null)
            .elementName(rootDirectoryAttributes.getElementName())
            .type(ElementType.DIRECTORY)
            .accessRights(rootDirectoryAttributes.getAccessRights())
            .owner(rootDirectoryAttributes.getOwner())
            .subdirectoriesCount(0L)
            .build();
    }

    public static ElementAttributes toElementAttributes(String elementName, ElementType elementType, boolean isPrivate, String userId) {
        return ElementAttributes.builder()
            .elementUuid(null)
            .elementName(elementName)
            .type(elementType)
            .accessRights(AccessRightsAttributes.builder().isPrivate(isPrivate).build())
            .owner(userId)
            .subdirectoriesCount(0L)
            .build();
    }
}
