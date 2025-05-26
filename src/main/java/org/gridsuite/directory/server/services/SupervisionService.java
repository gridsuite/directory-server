package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SupervisionService {
    private final DirectoryRepositoryService repositoryService;
    private final DirectoryElementInfosRepository directoryElementInfosRepository;
    private final DirectoryElementRepository directoryElementRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public SupervisionService(
            DirectoryRepositoryService repositoryService,
            DirectoryElementInfosRepository directoryElementInfosRepository,
            ElasticsearchOperations elasticsearchOperations,
            DirectoryElementRepository directoryElementRepository
    ) {
        this.repositoryService = repositoryService;
        this.directoryElementInfosRepository = directoryElementInfosRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.directoryElementRepository = directoryElementRepository;
    }

    @Transactional(readOnly = true)
    public List<ElementAttributes> getAllElementsByType(String type) {
        if (type != null) {
            return directoryElementRepository.findAllByType(type).stream().map(ElementAttributes::toElementAttributes).toList();
        } else {
            return directoryElementRepository.findAll().stream().map(ElementAttributes::toElementAttributes).toList();
        }
    }

    // delete all directory elements without checking owner
    public void deleteElementsByIds(List<UUID> uuids) {
        repositoryService.deleteElements(uuids);
    }

    public long getIndexedDirectoryElementsCount() {
        return directoryElementInfosRepository.count();
    }

    @Transactional
    public void reindexElements() {
        repositoryService.reindexElements();
    }

    public void recreateIndex() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(DirectoryElementInfos.class);
        boolean isDeleted = indexOperations.delete();
        if (!isDeleted) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete elements ElasticSearch index");
        }

        boolean isCreated = indexOperations.createWithMapping();
        if (!isCreated) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create elements ElasticSearch index");
        }
    }
}
