package org.gridsuite.directory.server.elasticsearch;

import lombok.NonNull;
import org.gridsuite.directory.server.dto.DirectoryElementInfos;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface DirectoryElementInfosRepository extends ElasticsearchRepository<DirectoryElementInfos, String> {

    List<DirectoryElementInfos> findAllByName(@NonNull String name);

}
