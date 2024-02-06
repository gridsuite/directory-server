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
import org.gridsuite.directory.server.DirectoryException;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryException.Type.NOT_DIRECTORY;
import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Slimane Amar <slimane.amar at rte-france.com>
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

    private ZonedDateTime creationDate;

    private ZonedDateTime lastModificationDate;

    private String lastModifiedBy;

    public boolean isAllowed(@NonNull String userId) {
        if (!type.equals(DIRECTORY)) {
            throw new DirectoryException(NOT_DIRECTORY);
        }
        return owner.equals(userId) || !accessRights.isPrivate();
    }

    public static ElementAttributes toElementAttributes(@NonNull DirectoryElementEntity entity) {
        return toElementAttributes(entity, 0L);
    }

    public static ElementAttributes toElementAttributes(@NonNull DirectoryElementEntity entity, long subDirectoriesCount) {
        return toElementAttributes(entity.getId(), entity.getName(), entity.getType(), new AccessRightsAttributes(entity.getIsPrivate()), entity.getOwner(), subDirectoriesCount, entity.getDescription(), ZonedDateTime.ofInstant(entity.getCreationDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), ZonedDateTime.ofInstant(entity.getLastModificationDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), entity.getLastModifiedBy());
    }

    public static ElementAttributes toElementAttributes(@NonNull RootDirectoryAttributes rootDirectoryAttributes) {
        return toElementAttributes(null, rootDirectoryAttributes.getElementName(), DIRECTORY, rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner(), 0L, null, rootDirectoryAttributes.getCreationDate(), rootDirectoryAttributes.getLastModificationDate(), rootDirectoryAttributes.getLastModifiedBy());
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType, Boolean isPrivate, @NonNull String userId) {
        return toElementAttributes(elementUuid, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, 0L, null, null, null, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        Boolean isPrivate, @NonNull String userId, @NonNull String elementDescription) {
        return toElementAttributes(elementUuid, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, 0L, elementDescription, null, null, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        Boolean isPrivate, @NonNull String userId, String elementDescription, ZonedDateTime creationDate, ZonedDateTime lastModificationDate, String lastModifiedBy) {
        return toElementAttributes(elementUuid, elementName, elementType, new AccessRightsAttributes(isPrivate), userId, 0L, elementDescription, creationDate, lastModificationDate, lastModifiedBy);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull AccessRightsAttributes accessRights, @NonNull String userId,
                                                        long subdirectoriesCount, String elementDescription, ZonedDateTime creationDate, ZonedDateTime lastModificationDate, String lastModifiedBy) {
        return ElementAttributes.builder().elementUuid(elementUuid).elementName(elementName)
            .type(elementType).accessRights(accessRights).owner(userId).creationDate(creationDate)
            .subdirectoriesCount(subdirectoriesCount).description(elementDescription)
            .lastModificationDate(lastModificationDate).lastModifiedBy(lastModifiedBy)
            .build();
    }

    public static DirectoryElementInfos toDirectoryElementInfos(DirectoryElementEntity entity) {
        return DirectoryElementInfos.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .parentId(entity.getParentId().toString())
                .type(entity.getType())
                .lastModificationDate(entity.getLastModificationDate())
                .build();
    }
}
