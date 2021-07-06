/**
* Copyright (c) 2020, RTE (http://www.rte-france.com)
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package org.gridsuite.directory.server.repository;

import java.util.UUID;

import lombok.*;

import javax.persistence.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "element")
public class DirectoryElementEntity {

    public DirectoryElementEntity(UUID parentId, String name, String type, boolean isPrivate, String owner) {
        this(null, parentId, name, type, isPrivate, owner);
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "parentId")
    private UUID parentId;

    @Column(name = "name")
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "isPrivate", nullable = false)
    private boolean isPrivate;

    @Column(name = "owner", nullable = false)
    private String owner;
}
