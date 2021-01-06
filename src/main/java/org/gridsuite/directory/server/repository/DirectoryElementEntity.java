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
public class DirectoryElementEntity implements Serializable {

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
}
