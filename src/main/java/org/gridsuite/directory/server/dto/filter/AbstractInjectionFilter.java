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
import lombok.experimental.SuperBuilder;
import org.springframework.util.CollectionUtils;

import java.util.Set;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@NoArgsConstructor
@SuperBuilder
@Schema(description = "Injection Filters", allOf = AbstractGenericFilter.class)
public abstract class AbstractInjectionFilter extends AbstractGenericFilter {
    @Schema(description = "SubstationName")
    String substationName;

    @Schema(description = "Countries")
    private Set<String> countries;

    @Schema(description = "Nominal voltage")
    private NumericalFilter nominalVoltage;

    @Override
    public boolean isEmpty() {
        return super.isEmpty()
            && substationName == null
            && CollectionUtils.isEmpty(countries)
            && nominalVoltage == null;
    }
}
