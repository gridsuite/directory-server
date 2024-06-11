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
            "AND d.stashed = false")
    List<DirectoryElementEntity> findRootDirectories();

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND d.name=:name and d.stashed = false ")
    List<DirectoryElementEntity> findRootDirectoriesByName(String name);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.type = 'DIRECTORY' AND d.name=:name AND d.parentId=:parentId AND d.stashed = false")
    List<DirectoryElementEntity> findDirectoriesByNameAndParentId(String name, UUID parentId);

    @Query("SELECT name FROM DirectoryElementEntity WHERE parentId=:parentId AND type=:type AND name like :name% and stashed = false ")
    List<String> getNameByTypeAndParentIdAndNameStartWith(String type, UUID parentId, String name);

    boolean existsByIdAndOwnerOrId(UUID id, String owner, UUID id2);

    interface SubDirectoryCount {
        UUID getId();

        Long getCount();
    }

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) AND d.stashed = FALSE GROUP BY d.parentId")
    List<SubDirectoryCount> getSubDirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes);

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


    @Query(nativeQuery = true, value =
    "WITH RECURSIVE DescendantHierarchy (element_id, parent_element_id) AS (" +
            "  SELECT" +
            "    id AS element_id, parent_id AS parent_element_id" +
            "  FROM element where id = :elementId" +
            "  UNION ALL" +
            "  select e.id AS element_id, e.parent_id AS parent_element_id" +
            "  FROM element e" +
            "  INNER JOIN" +
            "    DescendantHierarchy dh" +
            "    ON dh.element_id = e.parent_id" +
            "    WHERE e.type = 'DIRECTORY' )" +
            "SELECT * FROM element e" +
            "JOIN" +
            "  DescendantHierarchy dh" +
            "  ON e.id = dh.element_id")
    List<DirectoryElementEntity> findAllDescendants(@Param("elementId") UUID elementId);
}


// d04145fd-1bb6-401e-93d4-668688884760|d2_1|jamal|d00b68ff-ab0b-4d4a-a628-bb13740c9622|DIRECTORY|           |2024-06-06 15:34:42.413 +0200|2024-06-06 15:34:42.413 +0200|jamal           |          |false  |d04145fd-1bb6-401e-93d4-668688884760|d00b68ff-ab0b-4d4a-a628-bb13740c9622|
// b4aaa9c2-9c78-40ac-958a-2a9144ddfbcc|d3_1|jamal|d04145fd-1bb6-401e-93d4-668688884760|DIRECTORY|           |2024-06-06 15:35:17.627 +0200|2024-06-06 15:44:39.803 +0200|jamal           |          |false  |b4aaa9c2-9c78-40ac-958a-2a9144ddfbcc|d04145fd-1bb6-401e-93d4-668688884760|
// 1737b1f3-eaf7-4b99-8bea-ea3ad757e596|d3_2|jamal|d04145fd-1bb6-401e-93d4-668688884760|DIRECTORY|           |2024-06-06 15:35:28.094 +0200|2024-06-06 15:44:32.764 +0200|jamal           |          |false  |1737b1f3-eaf7-4b99-8bea-ea3ad757e596|d04145fd-1bb6-401e-93d4-668688884760|
// b8ceaf5c-f586-4b7f-9add-56844c0454a5|d3_3|jamal|d04145fd-1bb6-401e-93d4-668688884760|DIRECTORY|           |2024-06-06 15:35:36.860 +0200|2024-06-06 15:35:36.860 +0200|jamal           |          |false  |b8ceaf5c-f586-4b7f-9add-56844c0454a5|d04145fd-1bb6-401e-93d4-668688884760|
// f683d999-0d77-4992-a5cd-210028b49f47|d4_1|jamal|b4aaa9c2-9c78-40ac-958a-2a9144ddfbcc|DIRECTORY|           |2024-06-06 15:45:22.806 +0200|2024-06-06 15:45:22.806 +0200|jamal           |          |false  |f683d999-0d77-4992-a5cd-210028b49f47|b4aaa9c2-9c78-40ac-958a-2a9144ddfbcc|
// 2ac56f0b-5dc9-4ced-9003-127186fb802a|d5_1|jamal|f683d999-0d77-4992-a5cd-210028b49f47|DIRECTORY|           |2024-06-07 15:07:37.319 +0200|2024-06-07 15:07:37.319 +0200|jamal           |          |false  |2ac56f0b-5dc9-4ced-9003-127186fb802a|f683d999-0d77-4992-a5cd-210028b49f47|