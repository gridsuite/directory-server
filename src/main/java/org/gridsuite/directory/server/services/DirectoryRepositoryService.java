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

import java.util.*;

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
        return directoryElementRepository.findAllByIdInAndParentIdAndTypeNot(uuids, parentUuid, DIRECTORY);
    }

    public boolean isRootDirectory(UUID directoryUuid) {
        return getParentUuid(directoryUuid) == null;
    }

    public boolean isRootDirectoryExist(String rootName) {
        return !directoryElementRepository.findRootDirectoriesByName(rootName).isEmpty();
    }

    public boolean isElementExists(UUID parentDirectoryUuid, String elementName, String type) {
        return !directoryElementRepository.findByNameAndParentIdAndType(elementName, parentDirectoryUuid, type).isEmpty();
    }

    private void saveElementsInfos(List<DirectoryElementEntity> directoryElements) {
        Map<UUID, List<DirectoryElementEntity>> pathsCache = new HashMap<>();
        List<DirectoryElementInfos> directoryElementInfos = directoryElements.stream()
                .map(directoryElementEntity -> directoryElementEntity.toDirectoryElementInfos(getPath(directoryElementEntity.getParentId(), pathsCache)))
                .toList();
        Lists.partition(directoryElementInfos, partitionSize)
                .parallelStream()
                .forEach(directoryElementInfosRepository::saveAll);
    }

    private DirectoryElementEntity saveElementInfos(DirectoryElementEntity elementEntity) {
        directoryElementInfosRepository.save(elementEntity.toDirectoryElementInfos(getPath(elementEntity.getParentId())));
        return elementEntity;
    }

    public DirectoryElementEntity saveElement(DirectoryElementEntity elementEntity) {
        return saveElementInfos(directoryElementRepository.save(elementEntity));
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
        reindexElements(directoryElementRepository.findAll());
    }

    public void reindexElements(@NonNull List<DirectoryElementEntity> elementEntities) {
        saveElementsInfos(elementEntities);
    }

    public UUID getParentUuid(UUID elementUuid) {
        return directoryElementRepository
                .findById(elementUuid)
                .map(DirectoryElementEntity::getParentId)
                .orElse(null);
    }

    public List<DirectoryElementEntity> findAllByIdIn(List<UUID> uuids) {
        return directoryElementRepository.findAllByIdIn(uuids);
    }

    public List<DirectoryElementEntity> findAllByParentId(UUID parentId) {
        return directoryElementRepository.findAllByParentId(parentId);
    }

    public List<DirectoryElementRepository.ElementParentage> findAllByParentIdInAndTypeIn(List<UUID> parentIds, List<String> types) {
        return directoryElementRepository.findAllByParentIdsAndElementTypes(parentIds, types);
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

    public List<DirectoryElementEntity> getPath(UUID elementId) {
        return getPath(elementId, new HashMap<>());
    }

    public List<DirectoryElementEntity> getPath(UUID elementId, Map<UUID, List<DirectoryElementEntity>> pathsCache) {
        // Test null for root directories
        return elementId == null ? List.of() : pathsCache.computeIfAbsent(elementId, directoryElementRepository::findElementHierarchy);
    }

    public List<DirectoryElementEntity> findAllDescendants(@NonNull UUID elementId) {
        return directoryElementRepository.findAllDescendants(elementId);
    }
}
