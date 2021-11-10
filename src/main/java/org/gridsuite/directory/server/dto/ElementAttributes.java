/*
  Copyright (c) 2020, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;

import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ElementAttributes {

    private UUID elementUuid;

    private String elementName;

    private String type;

    private AccessRightsAttributes accessRights;

    private String owner;

    private Long subdirectoriesCount;

    private String description;

    public boolean isAllowed(@NonNull String userId) {
        return owner.equals(userId) || !accessRights.isPrivate();
    }

    public static ElementAttributes toElementAttributes(@NonNull DirectoryElementEntity entity) {
        return toElementAttributes(entity, 0L);
    }

    public static ElementAttributes toElementAttributes(@NonNull DirectoryElementEntity entity, long subDirectoriesCount) {
        return toElementAttributes(entity.getId(), entity.getName(), entity.getType(), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner(), subDirectoriesCount, entity.getDescription());
    }

    public static ElementAttributes toElementAttributes(@NonNull RootDirectoryAttributes rootDirectoryAttributes) {
        return toElementAttributes(null, rootDirectoryAttributes.getElementName(), DIRECTORY, rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner(), 0L, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType, boolean isPrivate, @NonNull String userId) {
        return toElementAttributes(elementUuid, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, 0L, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        boolean isPrivate, @NonNull String userId, @NonNull String elementDescription) {
        return toElementAttributes(elementUuid, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, 0L, elementDescription);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull AccessRightsAttributes accessRights, @NonNull String userId,
                                                        long subdirectoriesCount, String elementDescription) {
        return ElementAttributes.builder().elementUuid(elementUuid).elementName(elementName)
            .type(elementType).accessRights(accessRights).owner(userId)
            .subdirectoriesCount(subdirectoriesCount).description(elementDescription)
            .build();
    }
}
