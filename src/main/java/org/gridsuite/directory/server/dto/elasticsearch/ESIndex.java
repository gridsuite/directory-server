/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto.elasticsearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
@Component
@Data
public final class ESIndex {

    public static final String DIRECTORY_ELEMENT_INFOS_INDEX_NAME = "${powsybl-ws.elasticsearch.index.prefix}directory-elements";

    private String directoryElementsIndexName;

    public ESIndex(@Value(DIRECTORY_ELEMENT_INFOS_INDEX_NAME) String directoryElementsIndexName) {
        this.directoryElementsIndexName = directoryElementsIndexName;
    }
}
