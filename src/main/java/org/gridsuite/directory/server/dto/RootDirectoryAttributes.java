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

import java.time.ZonedDateTime;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RootDirectoryAttributes {
    private String elementName;

    private AccessRightsAttributes accessRights;

    private String owner;

    private String description;

    private ZonedDateTime creationDate;
}
