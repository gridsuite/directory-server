package org.gridsuite.directory.server.services;

import lombok.extern.slf4j.Slf4j;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;

@Service
@Slf4j
public class SupervisionService {
    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryRepositoryService repositoryService;
    private final ElasticsearchTemplate esTemplate;

    public SupervisionService(DirectoryRepositoryService repositoryService, DirectoryElementRepository directoryElementRepository,
                              ElasticsearchTemplate elasticsearchTemplate) {
        this.repositoryService = repositoryService;
        this.directoryElementRepository = directoryElementRepository;
        this.esTemplate = elasticsearchTemplate;
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

    public boolean recreateIndexDirectoryElementInfos() {
        final IndexOperations idxDirectoryElementInfos = esTemplate.indexOps(DirectoryElementInfos.class);
        final String idxDirectoryElementInfosName = idxDirectoryElementInfos.getIndexCoordinates().getIndexName();
        log.warn("Recreating ElasticSearch index {}", idxDirectoryElementInfosName);
        if (idxDirectoryElementInfos.exists()) {
            log.info("Index {} found, delete it.", idxDirectoryElementInfosName);
            if (idxDirectoryElementInfos.delete()) {
                log.info("Successfully delete index {}", idxDirectoryElementInfosName);
            } else {
                log.error("A problem seems to happen when deleting index {}...", idxDirectoryElementInfosName);
                return false;
            }
        }
        if (idxDirectoryElementInfos.createWithMapping()) {
            log.info("Index {} successfully recreated!", idxDirectoryElementInfosName);
        } else {
            log.info("An error happen while re-creating index {}...", idxDirectoryElementInfosName);
            return false;
        }
        log.info("Re-indexing all elements of {}", idxDirectoryElementInfosName);
        repositoryService.reindexAllElements();
        return true;
    }
}
