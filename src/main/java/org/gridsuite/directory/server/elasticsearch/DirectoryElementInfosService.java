/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import com.google.common.collect.Lists;
import lombok.NonNull;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class DirectoryElementInfosService {

    private final DirectoryElementInfosRepository directoryElementInfosRepository;

    @Value("${spring.data.elasticsearch.partition-size:10000}")
    private int partitionSize;

    public DirectoryElementInfosService(DirectoryElementInfosRepository directoryElementInfosRepository) {
        this.directoryElementInfosRepository = directoryElementInfosRepository;
    }

    public DirectoryElementInfos addDirectoryElementInfos(@NonNull DirectoryElementInfos elementInfos) {
        return directoryElementInfosRepository.save(elementInfos);
    }

    public void addAllElementsInfos(@NonNull List<DirectoryElementInfos> directoryElementInfos) {
        Lists.partition(directoryElementInfos, partitionSize)
                .parallelStream()
                .forEach(directoryElementInfosRepository::saveAll);
    }

    public void updateElementsInfos(@NonNull DirectoryElementInfos directoryElementInfos) {
        directoryElementInfosRepository.deleteById(directoryElementInfos.getId());
        directoryElementInfosRepository.save(directoryElementInfos);
    }

    public Iterable<DirectoryElementInfos> findAllElementInfos() {
        return directoryElementInfosRepository.findAll();
    }

    public void deleteAllElements(List<DirectoryElementInfos> elementsInfos) {
        directoryElementInfosRepository.deleteAll(elementsInfos);
    }

    public void deleteElementsInfos(@NonNull String id) {
        directoryElementInfosRepository.deleteById(id);
    }

    public void deleteAll() {
        directoryElementInfosRepository.deleteAll();
    }
}
