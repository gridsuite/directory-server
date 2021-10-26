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
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@Repository
public interface DirectoryElementRepository extends JpaRepository<DirectoryElementEntity, UUID> {

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId = :parentId AND (d.isPrivate='false' or d.owner=:userId)")
    List<DirectoryElementEntity> findDirectoryContentByUserId(UUID parentId, String userId);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND (d.isPrivate='false' or d.owner=:owner)")
    List<DirectoryElementEntity> findRootDirectoriesByUserId(String owner);

    interface SubDirectoryCount {
        UUID getId();

        Long getCount();
    }

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.isPrivate='false' or d.owner=:userId) AND d.type = 'DIRECTORY' GROUP BY d.parentId")
    List<SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, String userId);

    @Transactional
    @Modifying
    @Query("UPDATE DirectoryElementEntity SET name = :newElementName WHERE id = :elementUuid")
    void updateElementName(UUID elementUuid, String newElementName);

    @Transactional
    @Modifying
    @Query("UPDATE DirectoryElementEntity SET isPrivate = :isPrivate WHERE id = :elementUuid")
    void updateElementAccessRights(UUID elementUuid, boolean isPrivate);

    @Transactional
    @Modifying
    @Query("UPDATE DirectoryElementEntity SET type = :newTypeName WHERE id = :elementUuid")
    void updateElementType(UUID elementUuid, String newTypeName);

    @Transactional
    void deleteById(UUID id);

}
