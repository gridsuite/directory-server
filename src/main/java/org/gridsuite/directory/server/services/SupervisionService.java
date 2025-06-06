package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
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
    private final ElasticsearchOperations elasticsearchOperations;

    public SupervisionService(DirectoryRepositoryService repositoryService, DirectoryElementInfosRepository directoryElementInfosRepository, ElasticsearchOperations elasticsearchOperations) {
        this.repositoryService = repositoryService;
        this.directoryElementInfosRepository = directoryElementInfosRepository;
        this.elasticsearchOperations = elasticsearchOperations;
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
