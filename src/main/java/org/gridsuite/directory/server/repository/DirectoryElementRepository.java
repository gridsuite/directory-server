/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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

    List<DirectoryElementEntity> findAllByParentId(UUID parentId);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND (d.isPrivate='false' or d.owner=:owner)")
    List<DirectoryElementEntity> findRootDirectoriesByUserId(String owner);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND d.name=:name")
    List<DirectoryElementEntity> findRootDirectoriesByName(String name);

    @Query("SELECT name FROM DirectoryElementEntity WHERE parentId=:parentId AND type=:type AND name like :name%")
    List<String> getNameByTypeAndParentIdAndNameStartWith(String type, UUID parentId, String name);

    boolean existsByIdAndOwnerOrIsPrivateAndId(UUID id, String owner, boolean isPrivate, UUID id2);

    default boolean canRead(UUID id, String userId) {
        return existsByIdAndOwnerOrIsPrivateAndId(id, userId, false, id);
    }

    interface SubDirectoryCount {
        UUID getId();

        Long getCount();
    }

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND d.type = 'DIRECTORY' GROUP BY d.parentId")
    List<SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories);

    @Transactional
    void deleteById(UUID id);

    List<DirectoryElementEntity> findByNameAndParentIdAndType(String name, UUID parentId, String type);
}
