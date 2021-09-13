/*
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.directory.server.dto.filter;

import org.gridsuite.directory.server.utils.FilterType;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public interface IFilterAttributes {
    String getName();

    java.util.UUID getId();

    java.util.Date getCreationDate();

    java.util.Date getModificationDate();

    String getDescription();

    FilterType getType();
}
