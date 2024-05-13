/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.directory.server.dto.ElementAttributes;

import jakarta.persistence.*;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "element", indexes = {@Index(name = "directoryElementEntity_parentId_index", columnList = "parentId")})
public class DirectoryElementEntity {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "parentId")
    private UUID parentId;

    @Column(name = "name", columnDefinition = "CLOB")
    private String name;

    @Column(name = "type", length = 30, nullable = false)
    private String type;

    @Column(name = "isPrivate")
    private Boolean isPrivate;

    @Column(name = "owner", length = 80, nullable = false)
    private String owner;

    @Column(name = "description", columnDefinition = "CLOB")
    private String description;

    @Column(name = "creationDate")
    private LocalDateTime creationDate;

    @Column(name = "lastModificationDate")
    private LocalDateTime lastModificationDate;

    @Column(name = "lastModifiedBy")
    private String lastModifiedBy;

    @Column(name = "stashed")
    private boolean stashed;

    @Column(name = "stash_date")
    private LocalDateTime stashDate;

    public DirectoryElementEntity update(@NonNull ElementAttributes newElementAttributes) {
        boolean isElementNameUpdated = StringUtils.isNotBlank(newElementAttributes.getElementName());
        if (isElementNameUpdated) {
            this.name = newElementAttributes.getElementName();
        }

        if (Objects.nonNull(newElementAttributes.getAccessRights())) {
            this.isPrivate = newElementAttributes.getAccessRights().isPrivate();
        }

        boolean isDescriptionUpdated = Objects.nonNull(newElementAttributes.getDescription());
        if (isDescriptionUpdated) {
            this.description = newElementAttributes.getDescription();
        }
        if (isDescriptionUpdated || isElementNameUpdated) {
            updateModificationAttributes(lastModifiedBy, LocalDateTime.now(ZoneOffset.UTC));
        }
        return this;
    }

    public void updateModificationAttributes(String lastModifiedBy,
                                             LocalDateTime lastModificationDate) {
        this.setLastModificationDate(lastModificationDate);
        this.setLastModifiedBy(lastModifiedBy);
    }

    public boolean isAttributesUpdatable(@NonNull ElementAttributes newElementAttributes, String userId) {
        return (// Updatable attributes
            Objects.nonNull(newElementAttributes.getDescription()) ||
            StringUtils.isNotBlank(newElementAttributes.getElementName()) ||
                    //Only the owner can update the accessRights of a directory (to avoid user locking themselves out of a directory they don't own
                    type.equals(DIRECTORY) && Objects.nonNull(newElementAttributes.getAccessRights()) && userId.equals(owner))
            && // Non updatable attributes
            Objects.isNull(newElementAttributes.getElementUuid()) &&
            Objects.isNull(newElementAttributes.getType()) &&
            Objects.isNull(newElementAttributes.getOwner()) &&
            Objects.isNull(newElementAttributes.getSubdirectoriesCount()) &&
            Objects.isNull(newElementAttributes.getCreationDate()) &&
            Objects.isNull(newElementAttributes.getLastModificationDate()) &&
            Objects.isNull(newElementAttributes.getLastModifiedBy());
    }

    public DirectoryElementInfos toDirectoryElementInfos() {
        return DirectoryElementInfos.builder()
                .id(getId())
                .name(getName())
                .owner(getOwner())
                .parentId(getParentId() == null ? getId() : getParentId())
                .type(getType())
                .isPrivate(getIsPrivate())
                .lastModificationDate(getLastModificationDate())
                .build();
    }
}
