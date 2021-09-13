/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.directory.server.dto.filter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.directory.server.utils.FilterType;

import java.util.Date;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */

@Getter
@NoArgsConstructor
public class FilterAttributes implements IFilterAttributes {
    String name;
    UUID id;
    Date creationDate;
    Date modificationDate;
    String description;
    FilterType type;

    public FilterAttributes(FilterMetadata filterMetadata, FilterType type) {
        name = filterMetadata.getName();
        id = filterMetadata.getId();
        creationDate = filterMetadata.getCreationDate();
        modificationDate = filterMetadata.getModificationDate();
        description = filterMetadata.getDescription();
        this.type = type;
    }
}
