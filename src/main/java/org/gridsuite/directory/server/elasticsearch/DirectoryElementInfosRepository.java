/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
public interface DirectoryElementInfosRepository extends ElasticsearchRepository<DirectoryElementInfos, String> {
    List<DirectoryElementInfos> findAll();
}
