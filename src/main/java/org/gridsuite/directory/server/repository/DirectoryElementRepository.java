/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Repository
public interface DirectoryElementRepository extends CassandraRepository<DirectoryElementEntity, DirectoryElementKey> {
    List<DirectoryElementEntity> findByDirectoryElementKeyId(UUID directoryId);

    void deleteByDirectoryElementKeyId(UUID directoryId);
}
