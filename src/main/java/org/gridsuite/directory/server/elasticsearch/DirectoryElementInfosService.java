package org.gridsuite.directory.server.elasticsearch;

import lombok.NonNull;
import org.gridsuite.directory.server.dto.DirectoryElementInfos;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class DirectoryElementInfosService {

    public enum FieldSelector {
        NAME, ID
    }

    private final DirectoryElementInfosRepository directoryElementInfosRepository;

    private final ElasticsearchOperations elasticsearchOperations;

    public DirectoryElementInfosService(DirectoryElementInfosRepository directoryElementInfosRepository, ElasticsearchOperations elasticsearchOperations) {
        this.directoryElementInfosRepository = directoryElementInfosRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public DirectoryElementInfos addDirectoryElementsInfos(@NonNull DirectoryElementInfos directoryElementInfos) {
        return  directoryElementInfosRepository.save(directoryElementInfos);
    }

    public List<DirectoryElementInfos> findAllDirectoryElementInfos(@NonNull String name) {
        return directoryElementInfosRepository.findAllByName(name);
    }

}
