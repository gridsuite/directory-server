/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.directory.server.ElementType;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ElementAttributes {
    private UUID elementUuid;

    private UUID parentUuid;

    private String elementName;

    private ElementType type;

    private AccessRightsAttributes accessRights;

    private String owner;
}
