package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;

@Service
public class SupervisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionService.class);

    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryRepositoryService repositoryService;
    private final DirectoryElementInfosService directoryElementInfosService;

    public SupervisionService(DirectoryRepositoryService repositoryService, DirectoryElementInfosService directoryElementInfosService, DirectoryElementRepository directoryElementRepository) {
        this.repositoryService = repositoryService;
        this.directoryElementRepository = directoryElementRepository;
        this.directoryElementInfosService = directoryElementInfosService;
    }

    public List<ElementAttributes> getStashedElementsAttributes() {
        List<DirectoryElementEntity> entities = getStashedElements();
        return entities.stream()
            .map(entity -> toElementAttributes(entity))
            .toList();
    }

    // delete all directory elements without checking owner
    public void deleteElementsByIds(List<UUID> uuids) {
        repositoryService.deleteElements(uuids);
    }

    public List<DirectoryElementEntity> getStashedElements() {
        return directoryElementRepository.findAllByStashed(true);
    }

    public Long getIndexedDirectoryElementsCount() {
        return directoryElementInfosService.getDirectoryElementsInfosCount();
    }

    public Long getIndexedDirectoryElementsCount(UUID directoryUuid) {
        if (directoryUuid == null) {
            return getIndexedDirectoryElementsCount();
        }
        return directoryElementInfosService.getDirectoryElementsInfosCount(directoryUuid);
    }

    @Transactional
    public Long deleteIndexedDirectoryElements(@NonNull UUID directoryUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        Long nbIndexesToDelete = getIndexedDirectoryElementsCount(directoryUuid);
        directoryElementInfosService.deleteAllByParentId(directoryUuid);
        LOGGER.trace("Indexed directory elements deletion for directory \"{}\": {} seconds", directoryUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nbIndexesToDelete;
    }

    @Transactional
    public List<ElementAttributes> getDirectories() {
        return repositoryService.getDirectories().stream()
                    .map(ElementAttributes::toElementAttributes)
                    .toList();
    }

    @Transactional
    public void reindexElements(UUID directoryUuid) {
        repositoryService.reindexElements(directoryUuid);
    }
}
