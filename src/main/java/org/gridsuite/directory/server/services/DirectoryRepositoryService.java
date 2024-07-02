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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;

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

    public List<DirectoryElementEntity> getElementEntities(List<UUID> uuids, UUID parentUuid) {
        return directoryElementRepository.findAllByIdInAndParentIdAndTypeNotAndStashed(uuids, parentUuid, DIRECTORY, false);
    }

    public boolean isRootDirectory(UUID directoryUuid) {
        return getParentUuid(directoryUuid) == null;
    }

    public boolean isRootDirectoryExist(String rootName) {
        return !directoryElementRepository.findRootDirectoriesByName(rootName).isEmpty();
    }

    public boolean isElementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return !directoryElementRepository.findByNameAndParentIdAndTypeAndStashed(elementName, parentDirectoryUuid, type, false).isEmpty();
    }

    public void saveElementsInfos(@NonNull List<DirectoryElementInfos> directoryElementInfos) {
        Lists.partition(directoryElementInfos, partitionSize)
                .parallelStream()
                .forEach(directoryElementInfosRepository::saveAll);
    }

    private DirectoryElementEntity saveElementsInfo(DirectoryElementEntity elementEntity) {
        directoryElementInfosRepository.save(elementEntity.toDirectoryElementInfos(findElementHierarchy(elementEntity.getParentId())));
        return elementEntity;
    }

    // TODO : remove the entity return
    public DirectoryElementEntity saveElement(DirectoryElementEntity elementEntity) {
        return saveElementsInfo(directoryElementRepository.save(elementEntity));
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
        return directoryElementRepository.existsByIdAndOwnerOrId(id, userId, id);
    }

    public void reindexElements() {
        saveElementsInfos(directoryElementRepository.findAll().stream()
                .map(directoryElementEntity -> directoryElementEntity.toDirectoryElementInfos(findElementHierarchy(directoryElementEntity.getParentId())))
                .toList());
    }

    public UUID getParentUuid(UUID elementUuid) {
        return directoryElementRepository
                .findById(elementUuid)
                .map(DirectoryElementEntity::getParentId)
                .orElse(null);
    }

    public List<DirectoryElementRepository.SubDirectoryCount> getSubDirectoriesCounts(List<UUID> subDirectories, List<String> elementTypes) {
        return directoryElementRepository.getSubDirectoriesCounts(subDirectories, elementTypes);
    }

    public List<DirectoryElementEntity> findAllByIdIn(List<UUID> uuids) {
        return directoryElementRepository.findAllByIdInAndStashed(uuids, false);
    }

    public List<DirectoryElementEntity> findAllByParentId(UUID parentId) {
        return directoryElementRepository.findAllByParentIdAndStashed(parentId, false);
    }

    public List<DirectoryElementEntity> findRootDirectories() {
        return directoryElementRepository.findRootDirectories();
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

    public List<DirectoryElementEntity> findElementHierarchy(UUID elementId) {
        return directoryElementRepository.findElementHierarchy(elementId);
    }

}
