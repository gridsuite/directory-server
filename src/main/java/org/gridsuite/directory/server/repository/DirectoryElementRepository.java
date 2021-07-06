/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Repository
public interface DirectoryElementRepository extends JpaRepository<DirectoryElementEntity, UUID> {

    List<DirectoryElementEntity> findByParentId(UUID parentId);

    @Transactional
    @Modifying
    @Query("UPDATE DirectoryElementEntity SET name = :newElementName WHERE id = :elementUuid")
    void updateElementName(UUID elementUuid, String newElementName);

    @Transactional
    @Modifying
    @Query("UPDATE DirectoryElementEntity SET isPrivate = :isPrivate WHERE id = :elementUuid")
    void updateElementAccessRights(UUID elementUuid, boolean isPrivate);

    @Transactional
    void deleteById(UUID id);

}
