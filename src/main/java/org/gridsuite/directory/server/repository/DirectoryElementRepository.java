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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@Repository
public interface DirectoryElementRepository extends JpaRepository<DirectoryElementEntity, UUID> {

    List<DirectoryElementEntity> findAllByStashed(boolean stashed);

    List<DirectoryElementEntity> findAllByParentIdAndStashedAndStashDate(UUID parentId, boolean stashed, LocalDateTime stashDate);

    List<DirectoryElementEntity> findAllByIdInAndStashed(List<UUID> uuids, boolean stashed);

    @Modifying
    @Transactional
    @Query("DELETE FROM DirectoryElementEntity d WHERE d.id IN :elementsUuids")
    void deleteAllById(List<UUID> elementsUuids);

    @Query("SELECT d FROM DirectoryElementEntity d " +
            "WHERE d.parentId IS NULL " +
            "AND d.type = 'DIRECTORY' " +
            "AND (d.isPrivate=false or d.owner=:owner) " +
            "AND d.stashed = false")
    List<DirectoryElementEntity> findRootDirectoriesByUserId(String owner);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.parentId IS NULL AND d.type = 'DIRECTORY' AND d.name=:name and d.stashed = false ")
    List<DirectoryElementEntity> findRootDirectoriesByName(String name);

    @Query("SELECT d FROM DirectoryElementEntity d  WHERE d.type = 'DIRECTORY' AND d.name=:name AND d.parentId=:parentId AND d.stashed = false")
    List<DirectoryElementEntity> findDirectoriesByNameAndParentId(String name, UUID parentId);

    @Query("SELECT name FROM DirectoryElementEntity WHERE parentId=:parentId AND type=:type AND name like :name% and stashed = false ")
    List<String> getNameByTypeAndParentIdAndNameStartWith(String type, UUID parentId, String name);

    boolean existsByIdAndOwnerOrIsPrivate(UUID id, String owner, boolean isPrivate);

    interface SubDirectoryCount {
        UUID getId();

        Long getCount();
    }

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) AND d.stashed = FALSE GROUP BY d.parentId")
    List<SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes);

    @Query("SELECT d.parentId AS id, COUNT(*) AS count FROM DirectoryElementEntity d WHERE d.parentId IN :subDirectories AND (d.type = 'DIRECTORY' OR d.type IN :elementTypes) AND (d.isPrivate=false or d.owner=:owner) AND d.stashed = FALSE GROUP BY d.parentId")
    List<SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes, String owner);

    @Transactional
    void deleteById(UUID id);

    List<DirectoryElementEntity> findByNameAndParentIdAndTypeAndStashed(String name, UUID parentId, String type, boolean stashed);

    @Query("SELECT e FROM DirectoryElementEntity e " +
            "WHERE e.id IN :uuids " +
            "AND e.stashed = :stashed " +
            "AND (e.owner = :userId OR e.isPrivate = false OR (e.isPrivate IS NULL AND NOT EXISTS (SELECT 1 FROM DirectoryElementEntity parent WHERE parent.id = e.parentId AND parent.isPrivate = true)))")
    List<DirectoryElementEntity> findAllStashedElements(@Param("uuids") List<UUID> uuids,
                                                        @Param("stashed") boolean stashed,
                                                        @Param("userId") String userId);

    // We select all stashed elements that do not have a parent, or have a parent that is not deleted, or a parent that is deleted in different operation
    @Query("SELECT e FROM DirectoryElementEntity e " +
            "WHERE e.stashed = true AND " +
            "(e.isPrivate = false or e.owner = :userId or (e.isPrivate IS NULL AND NOT EXISTS (SELECT 1 FROM DirectoryElementEntity parent WHERE parent.id = e.parentId AND parent.isPrivate = true))) " +
            "AND (" +
            "      e.parentId IS NULL OR " + // Element has no parent
            "      NOT EXISTS (SELECT 1 FROM DirectoryElementEntity parent WHERE parent.id = e.parentId AND parent.stashed = true) OR " + // Parent is not stashed
            "      NOT EXISTS (SELECT 1 FROM DirectoryElementEntity parent WHERE parent.id = e.parentId AND parent.stashDate = e.stashDate)" + // Parent has different stash date
            ")")
    List<DirectoryElementEntity> getElementsStashed(String userId);

    // This query to count all the deleted descendants of each element
    // It uses CTE (Common Table Expression) which is temporary result set that we use to count all descendents of an element
    @Query(nativeQuery = true, value =
            "WITH RECURSIVE ElementHierarchy (element_id, parent_element_id) AS (" +
                    "   SELECT id AS element_id, parent_id AS parent_element_id FROM element WHERE id = :elementId " +
                    "   UNION ALL " +
                    "   SELECT e_child.id AS element_id, e_child.parent_id AS parent_element_id FROM element e_child " +
                    "   INNER JOIN ElementHierarchy e_parent ON e_parent.element_id = e_child.parent_id WHERE e_child.parent_id IS NOT NULL) " +
                    "SELECT COUNT(e.element_id) " +
                    "FROM ElementHierarchy e " +
                    "INNER JOIN element el ON e.element_id = el.id " +
                    "WHERE (el.is_private = false OR el.owner = :userId OR (el.is_private IS NULL AND NOT EXISTS (SELECT 1 FROM element WHERE id = e.parent_element_id AND is_private = true))) AND el.stashed = true")
    Long countDescendants(@Param("elementId") UUID elementId, @Param("userId") String userId);

    @Query(nativeQuery = true, value =
            "WITH RECURSIVE ElementHierarchy (element_id, parent_element_id) AS ( " +
                    "  SELECT id AS element_id, parent_id AS parent_element_id FROM element WHERE id = :elementId " +
                    "  UNION ALL " +
                    "  SELECT e.id, e.parent_id FROM element e " +
                    "  INNER JOIN ElementHierarchy ON ElementHierarchy.parent_element_id = e.id WHERE e.parent_id IS NOT NULL) " +
                    "SELECT * FROM element e " +
                    "WHERE e.id IN (SELECT id FROM ElementHierarchy) " +
                    "AND e.stashed = true " +
                    "AND (e.is_private = false OR e.owner = :userId OR (e.is_private IS NULL AND NOT EXISTS (SELECT 1 FROM element WHERE id = e.parent_id AND is_private = true))) " +
                    "AND e.id != :elementId " +
                    "AND e.stash_date = (SELECT stash_date FROM element WHERE id = :elementId)")
    List<DirectoryElementEntity> findAllDescendantsWithSameStashDate(@Param("elementId") UUID elementId, @Param("userId")String userId);

    @Query(nativeQuery = true, value =
            "WITH RECURSIVE ElementHierarchy (element_id, parent_element_id) AS ( " +
                    "  SELECT id AS element_id, parent_id AS parent_element_id FROM element WHERE id = :elementId " +
                    "  UNION ALL " +
                    "  SELECT e.id AS element_id, e.parent_id AS parent_element_id " +
                    "  FROM element e " +
                    "  INNER JOIN ElementHierarchy ON ElementHierarchy.element_id = e.parent_id WHERE e.parent_id IS NOT NULL) " +
                    "SELECT * FROM element e " +
                    "JOIN ElementHierarchy eh ON e.parent_id = eh.element_id " +
                    "WHERE e.stashed = false " +
                    "AND (e.is_private = false OR e.owner = :userId OR (e.is_private IS NULL AND NOT EXISTS (SELECT 1 FROM element WHERE id = eh.parent_element_id AND is_private = true)))")
    List<DirectoryElementEntity> findAllDescendants(@Param("elementId") UUID elementId, @Param("userId") String userId);
}
