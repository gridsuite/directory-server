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

import java.time.Instant;
import java.util.UUID;

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

    private String owner;

    private Long subdirectoriesCount;

    private String description;

    private Instant creationDate;

    private Instant lastModificationDate;

    private String lastModifiedBy;

    public boolean isOwnedBy(@NonNull String userId) {
        return owner.equals(userId);
    }

    public static ElementAttributes toElementAttributes(@NonNull DirectoryElementEntity entity) {
        return toElementAttributes(entity, 0L);
    }

    public static ElementAttributes toElementAttributes(@NonNull DirectoryElementEntity entity, long subDirectoriesCount) {
        return toElementAttributes(entity.getId(), entity.getName(), entity.getType(), entity.getOwner(), subDirectoriesCount, entity.getDescription(), entity.getCreationDate(), entity.getLastModificationDate(), entity.getLastModifiedBy());
    }

    public static ElementAttributes toElementAttributes(@NonNull RootDirectoryAttributes rootDirectoryAttributes) {
        return toElementAttributes(null, rootDirectoryAttributes.getElementName(), DIRECTORY, rootDirectoryAttributes.getOwner(), 0L, null, rootDirectoryAttributes.getCreationDate(), rootDirectoryAttributes.getLastModificationDate(), rootDirectoryAttributes.getLastModifiedBy());
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType, @NonNull String userId) {
        return toElementAttributes(elementUuid, elementName, elementType, userId, 0L, null, null, null, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull String userId, @NonNull String elementDescription) {
        return toElementAttributes(elementUuid, elementName, elementType, userId, 0L, elementDescription, null, null, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull String userId, String elementDescription, Instant creationDate, Instant lastModificationDate, String lastModifiedBy) {
        return toElementAttributes(elementUuid, elementName, elementType, userId, 0L, elementDescription, creationDate, lastModificationDate, lastModifiedBy);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull String userId,
                                                        long subdirectoriesCount, String elementDescription, Instant creationDate, Instant lastModificationDate, String lastModifiedBy) {
        return ElementAttributes.builder().elementUuid(elementUuid).elementName(elementName)
            .type(elementType).owner(userId).creationDate(creationDate)
            .subdirectoriesCount(subdirectoriesCount).description(elementDescription)
            .lastModificationDate(lastModificationDate).lastModifiedBy(lastModifiedBy)
            .build();
    }
}
