/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import com.google.common.collect.Lists;
import lombok.NonNull;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class DirectoryRepositoryService {
    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryElementInfosRepository directoryElementInfosRepository;

    @Value("${spring.data.elasticsearch.partition-size:10000}")
    private int partitionSize;

    public DirectoryRepositoryService(
            DirectoryElementRepository directoryElementRepository,
            DirectoryElementInfosRepository directoryElementInfosRepository) {
        this.directoryElementRepository = directoryElementRepository;
        this.directoryElementInfosRepository = directoryElementInfosRepository;
    }

    public Optional<DirectoryElementEntity> getElementEntity(UUID elementUuid) {
        return directoryElementRepository.findById(elementUuid);
    }

    public boolean isRootDirectory(UUID directoryUuid) {
        return getParentUuid(directoryUuid) == null;
    }

    public boolean isPrivateDirectory(UUID directoryUuid) {
        // TODO replace orElse by the commented line (orElseThrow)
        // Should be done after deleting the notification sent by the study server on delete (!)
        return directoryElementRepository.findById(directoryUuid).map(DirectoryElementEntity::getIsPrivate).orElse(false);
        //.orElseThrow(() -> new DirectoryServerException(directoryUuid + " not found!"));
    }

    public boolean isRootDirectoryExist(String rootName) {
        return !directoryElementRepository.findRootDirectoriesByName(rootName).isEmpty();
    }

    public boolean isElementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return !directoryElementRepository.findByNameAndParentIdAndTypeAndStashed(elementName, parentDirectoryUuid, type, false).isEmpty();
    }

    public void saveStashedElements(@NonNull List<DirectoryElementEntity> directoryElementEntities) {
        directoryElementRepository.saveAll(directoryElementEntities);
        directoryElementInfosRepository.deleteAllById(directoryElementEntities.stream().map(DirectoryElementEntity::getId).toList());
    }

    public void saveRestoredElements(@NonNull List<DirectoryElementEntity> directoryElementEntities) {
        directoryElementRepository.saveAll(directoryElementEntities);
        saveElementsInfos(directoryElementEntities.stream().map(DirectoryElementEntity::toDirectoryElementInfos).toList());
    }

    public void saveElementsInfos(@NonNull List<DirectoryElementInfos> directoryElementInfos) {
        Lists.partition(directoryElementInfos, partitionSize)
                .parallelStream()
                .forEach(directoryElementInfosRepository::saveAll);
    }

    public DirectoryElementEntity saveElement(DirectoryElementEntity elementEntity) {
        DirectoryElementEntity savedElementEntity = directoryElementRepository.save(elementEntity);
        directoryElementInfosRepository.save(savedElementEntity.toDirectoryElementInfos());
        return savedElementEntity;
    }

    public void deleteElement(UUID elementUuid) {
        directoryElementRepository.deleteById(elementUuid);
        directoryElementInfosRepository.deleteById(elementUuid);
    }

    public void deleteElements(List<UUID> elementUuids) {
        directoryElementRepository.deleteAllById(elementUuids);
        directoryElementInfosRepository.deleteAllById(elementUuids);
    }

    public boolean canRead(UUID id, String userId) {
        return directoryElementRepository.existsByIdAndOwnerOrIsPrivateAndId(id, userId, false, id);
    }

    public void reindexAllElements() {
        saveElementsInfos(directoryElementRepository.findAllByStashed(false).stream()
                .map(DirectoryElementEntity::toDirectoryElementInfos)
                .toList());
    }

    public UUID getParentUuid(UUID elementUuid) {
        return directoryElementRepository
                .findById(elementUuid)
                .map(DirectoryElementEntity::getParentId)
                .orElse(null);
    }

    public List<DirectoryElementRepository.SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes) {
        return directoryElementRepository.getSubdirectoriesCounts(subDirectories, elementTypes);
    }

    public List<DirectoryElementRepository.SubDirectoryCount> getSubdirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes, String owner) {
        return directoryElementRepository.getSubdirectoriesCounts(subDirectories, elementTypes, owner);
    }

    public List<DirectoryElementEntity> findAllByIdInAndStashed(List<UUID> uuids, boolean stashed) {
        return directoryElementRepository.findAllByIdInAndStashed(uuids, stashed);
    }

    public List<DirectoryElementEntity> findAllDescendantsWithSameStashDate(UUID elementId, String userId) {
        return directoryElementRepository.findAllDescendantsWithSameStashDate(elementId, userId);
    }

    public List<DirectoryElementEntity> findAllByParentIdAndStashedAndStashDate(UUID parentId, boolean stashed, LocalDateTime stashDate) {
        return directoryElementRepository.findAllByParentIdAndStashedAndStashDate(parentId, stashed, stashDate);
    }

    public List<DirectoryElementEntity> findRootDirectoriesByUserId(String owner) {
        return directoryElementRepository.findRootDirectoriesByUserId(owner);
    }

    public List<DirectoryElementEntity> findRootDirectoriesByName(String name) {
        return directoryElementRepository.findRootDirectoriesByName(name);
    }

    public List<DirectoryElementEntity> findDirectoriesByNameAndParentId(String name, UUID parentId) {
        return directoryElementRepository.findDirectoriesByNameAndParentId(name, parentId);
    }

    public List<String> getNameByTypeAndParentIdAndNameStartWith(String type, UUID parentId, String name) {
        return directoryElementRepository.getNameByTypeAndParentIdAndNameStartWith(type, parentId, name);
    }

    public List<DirectoryElementEntity> findAllStashedElements(List<UUID> uuids, boolean stashed, String userId) {
        return directoryElementRepository.findAllStashedElements(uuids, stashed, userId);
    }

    public List<DirectoryElementEntity> findAllDescendants(UUID elementId, String userId) {
        return directoryElementRepository.findAllDescendants(elementId, userId);
    }

    public List<DirectoryElementEntity> getElementsStashed(String userId) {
        return directoryElementRepository.getElementsStashed(userId);
    }

    public Long countDescendants(UUID elementId, String userId) {
        return directoryElementRepository.countDescendants(elementId, userId);
    }
}
