/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import com.datastax.driver.core.utils.UUIDs;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@PrimaryKeyClass
public class DirectoryElementKey implements Serializable {

    @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED)
    private UUID id = UUIDs.timeBased();

    @PrimaryKeyColumn(name = "child_id", type = PrimaryKeyType.CLUSTERED)
    private UUID childId;

}
