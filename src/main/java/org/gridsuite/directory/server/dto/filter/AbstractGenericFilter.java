/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto.filter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Borsenberger jacques <borsenberger.jacques at rte-france.com>
 */

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Schema(description = "filter contingency list attributes", allOf = AbstractFilter.class)
public abstract class AbstractGenericFilter extends AbstractFilter {

    @Schema(description = "Equipment ID")
    private String equipmentID;

    @Schema(description = "Equipment name")
    private String equipmentName;

    protected boolean isEmpty() {
        return equipmentID == null && equipmentName == null;
    }
}
