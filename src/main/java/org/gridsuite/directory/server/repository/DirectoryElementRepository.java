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
import org.springframework.data.repository.query.Param;
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

    List<DirectoryElementEntity> findAllByStashed(boolean stashed);

    List<DirectoryElementEntity> findAllByParentIdAndStashed(UUID parentId, boolean stashed);

    List<DirectoryElementEntity> findAllByIdInAndStashed(List<UUID> uuids, boolean stashed);

    List<DirectoryElementEntity> findAllByIdInAndParentIdAndTypeNotAndStashed(List<UUID> uuids, UUID parentUuid, String type, boolean stashed);

    @Modifying
    @Transactional
    @Query("DELETE FROM DirectoryElementEntity d WHERE d.id IN :elementsUuids")
    void deleteAllById(List<UUID> elementsUuids);

    @Query("SELECT d FROM DirectoryElementEntity d " +
            "WHERE d.parentId IS NULL " +
            "AND d.type = 'DIRECTORY' " +
           // "AND (d.isPrivate=false or d.owner=:owner) " +
            //todo: check this
           // "AND d.owner=:owner " +
            "AND d.stashed = false")
    List<DirectoryElementEntity> findRootDirectoriesByUserId(String owner);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND d.name=:name and d.stashed = false ")
    List<DirectoryElementEntity> findRootDirectoriesByName(String name);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.type = 'DIRECTORY' AND d.name=:name AND d.parentId=:parentId AND d.stashed = false")
    List<DirectoryElementEntity> findDirectoriesByNameAndParentId(String name, UUID parentId);

    @Query("SELECT name FROM DirectoryElementEntity WHERE parentId=:parentId AND type=:type AND name like :name% and stashed = false ")
    List<String> getNameByTypeAndParentIdAndNameStartWith(String type, UUID parentId, String name);

    //todo: change this
   // boolean existsByIdAndOwnerOrIsPrivateAndId(UUID id, String owner, boolean isPrivate, UUID id2);
    boolean existsByIdAndOwnerOrId(UUID id, String owner, UUID id2);

    interface SubDirectoryCount {
        UUID getId();

        Long getCount();
    }

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) AND d.stashed = FALSE GROUP BY d.parentId")
    List<SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes);

    // todo :change the query
    //@Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) AND (d.isPrivate=false or d.owner=:owner) AND d.stashed = FALSE GROUP BY d.parentId")
    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) AND d.owner=:owner AND d.stashed = FALSE GROUP BY d.parentId")
    List<SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes, String owner);

    @Transactional
    void deleteById(UUID id);

    List<DirectoryElementEntity> findByNameAndParentIdAndTypeAndStashed(String name, UUID parentId, String type, boolean stashed);

    @Query(nativeQuery = true, value =
            "WITH RECURSIVE ElementHierarchy (element_id, parent_element_id) AS ( " +
                    "  SELECT id AS element_id, parent_id AS parent_element_id FROM element WHERE id = :elementId " +
                    "  UNION ALL " +
                    "  SELECT e.id AS element_id, e.parent_id AS parent_element_id " +
                    "  FROM element e " +
                    "  INNER JOIN ElementHierarchy ON ElementHierarchy.parent_element_id = e.id WHERE e.parent_id IS NOT NULL) " +
                    "SELECT * FROM element e " +
                    "JOIN ElementHierarchy eh ON e.id = eh.parent_element_id " +
                    "WHERE e.stashed = false ")
    List<DirectoryElementEntity> findAllAscendants(@Param("elementId") UUID elementId);
}
