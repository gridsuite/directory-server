/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Repository
public interface DirectoryElementRepository extends ReactiveCrudRepository<DirectoryElementEntity, UUID> {

    Flux<DirectoryElementEntity> findByParentId(UUID parentId);

    @Query("UPDATE element SET name = :newElementName WHERE id = :elementUuid IF EXISTS")
    boolean updateElementName(UUID elementUuid, String newElementName);

    Mono<Void> deleteByParentId(UUID parentId);

}
