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
import org.gridsuite.directory.server.utils.FilterType;
import org.springframework.util.CollectionUtils;

import java.util.Set;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Schema(description = "Line Filters", allOf = AbstractGenericFilter.class)
public class LineFilter extends AbstractGenericFilter {
    @Override
    public FilterType getType() {
        return FilterType.LINE;
    }

    @Schema(description = "SubstationName1")
    String substationName1;

    @Schema(description = "SubstationName2")
    String substationName2;

    @Schema(description = "Countries1")
    private Set<String> countries1;

    @Schema(description = "Countries2")
    private Set<String> countries2;

    @Schema(description = "Nominal voltage 1")
    private NumericalFilter nominalVoltage1;

    @Schema(description = "Nominal voltage 2")
    private NumericalFilter nominalVoltage2;

    @Override
    public boolean isEmpty() {
        return super.isEmpty()
            && substationName1 == null
            && substationName2 == null
            && CollectionUtils.isEmpty(countries1)
            && CollectionUtils.isEmpty(countries2)
            && nominalVoltage1 == null
            && nominalVoltage2 == null;
    }
}
