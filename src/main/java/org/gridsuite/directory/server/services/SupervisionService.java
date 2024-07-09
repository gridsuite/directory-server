package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SupervisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionService.class);

    private final DirectoryRepositoryService repositoryService;
    private final DirectoryElementInfosRepository directoryElementInfosRepository;

    public SupervisionService(DirectoryRepositoryService repositoryService, DirectoryElementInfosRepository directoryElementInfosRepository) {
        this.repositoryService = repositoryService;
        this.directoryElementInfosRepository = directoryElementInfosRepository;
    }

    // delete all directory elements without checking owner
    public void deleteElementsByIds(List<UUID> uuids) {
        repositoryService.deleteElements(uuids);
    }

    public long getIndexedDirectoryElementsCount() {
        return directoryElementInfosRepository.count();
    }

    @Transactional
    public long deleteIndexedDirectoryElements() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        long nbIndexesToDelete = getIndexedDirectoryElementsCount();
        directoryElementInfosRepository.deleteAll();
        LOGGER.trace("Indexed directory elements deletion : {} seconds", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nbIndexesToDelete;
    }

    @Transactional
    public void reindexElements() {
        repositoryService.reindexElements();
    }
}
