/**
* Copyright (c) 2020, RTE (http://www.rte-france.com)
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package org.gridsuite.directory.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@Table("element")
public class DirectoryElementEntity implements Serializable, Persistable<UUID> {

    @PersistenceConstructor
    public DirectoryElementEntity(UUID id, UUID parentId, String name, String type, boolean isPrivate, String owner) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.type = type;
        this.isPrivate = isPrivate;
        this.owner = owner;
        this.newElement = false;
    }

    @Id
    @Column("id")
    private UUID id;

    @Column("parentId")
    private UUID parentId;

    @Column("name")
    private String name;

    @Column("type")
    private String type;

    @Column("isPrivate")
    private boolean isPrivate;

    @Column("owner")
    private String owner;

    @Transient
    private boolean newElement;

    @Override
    @Transient
    public boolean isNew() {
        if (newElement && id == null) {
            id = UUID.randomUUID();
        }
        return newElement;
    }
}
