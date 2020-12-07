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
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@Table("element")
public class DirectoryElementEntity implements Serializable {

    @PrimaryKey private DirectoryElementKey directoryElementKey;

    @Column("child_name")
    private String childName;

    @Column("child_type")
    private String childType;

    @Column("child_isPrivate")
    private boolean isPrivate;
}
