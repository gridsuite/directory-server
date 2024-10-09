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

    List<DirectoryElementEntity> findAllByParentId(UUID parentId);

    List<DirectoryElementEntity> findAllByIdIn(List<UUID> uuids);

    List<DirectoryElementEntity> findAllByIdInAndParentIdAndTypeNot(List<UUID> uuids, UUID parentUuid, String type);

    @Modifying
    @Transactional
    @Query("DELETE FROM DirectoryElementEntity d WHERE d.id IN :elementsUuids")
    void deleteAllById(List<UUID> elementsUuids);

    @Query("SELECT d FROM DirectoryElementEntity d " +
            "WHERE d.parentId IS NULL " +
            "AND d.type = 'DIRECTORY'")
    List<DirectoryElementEntity> findRootDirectories();

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND d.name=:name")
    List<DirectoryElementEntity> findRootDirectoriesByName(String name);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.type = 'DIRECTORY' AND d.name=:name AND d.parentId=:parentId")
    List<DirectoryElementEntity> findDirectoriesByNameAndParentId(String name, UUID parentId);

    @Query("SELECT name FROM DirectoryElementEntity WHERE parentId=:parentId AND type=:type AND name like :name%")
    List<String> getNameByTypeAndParentIdAndNameStartWith(String type, UUID parentId, String name);

    //We also count when type = study because every study is linked to a case that is not visible in DB
    //TODO: this will be changed on another US
    @Query("SELECT count(*) FROM DirectoryElementEntity WHERE owner=:owner AND (type='CASE' OR type='STUDY')")
    int getCasesCountByOwner(String owner);

    boolean existsByIdAndOwnerOrId(UUID id, String owner, UUID id2);

    interface SubDirectoryCount {
        UUID getId();

        Long getCount();
    }

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) GROUP BY d.parentId")
    List<SubDirectoryCount> getSubDirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes);

    @Transactional
    void deleteById(UUID id);

    List<DirectoryElementEntity> findByNameAndParentIdAndType(String name, UUID parentId, String type);


    //When using UNION, there is no guarantee order in which the rows are actually returned
    //https://www.postgresql.org/docs/current/queries-union.html
    @Query(nativeQuery = true, value =
            "WITH RECURSIVE ElementHierarchy (element_id, parent_element_id, depth) AS ( " +
                    "  SELECT id AS element_id, parent_id AS parent_element_id, 0 AS depth" +
                    "  FROM element " +
                    "  WHERE id = :elementId " +
                    "  UNION ALL " +
                    "  SELECT e.id AS element_id, e.parent_id AS parent_element_id, eh.depth + 1" +
                    "  FROM element e " +
                    "  INNER JOIN ElementHierarchy eh ON eh.parent_element_id = e.id " +
                    ") " +
                    "SELECT * FROM element e " +
                    "WHERE e.id in (SELECT eh.element_id from ElementHierarchy eh) " +
                    "ORDER BY (SELECT depth FROM ElementHierarchy WHERE element_id = e.id) DESC")
    List<DirectoryElementEntity> findElementHierarchy(@Param("elementId") UUID elementId);

    @Query(nativeQuery = true, value =
            "WITH RECURSIVE DescendantHierarchy (element_id, parent_element_id, depth) AS (" +
                    "  SELECT id AS element_id, parent_id AS parent_element_id, 0 AS depth" +
                    "  FROM element WHERE id = :elementId" +
                    "  UNION ALL" +
                    "  SELECT e.id AS element_id, e.parent_id AS parent_element_id, dh.depth + 1" +
                    "  FROM element e" +
                    "  INNER JOIN DescendantHierarchy dh ON dh.element_id = e.parent_id)" +
                    "SELECT * FROM element e " +
                    "WHERE e.id IN (SELECT dh.element_id FROM DescendantHierarchy dh) AND e.id != :elementId"
    )
    List<DirectoryElementEntity> findAllDescendants(@Param("elementId") UUID elementId);
}
