/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BasicAttributes {

    private UUID id;

    private String name;

    private AccessRightsAttributes accessRights;

    private String owner;

    public boolean hasAccessRights(@NonNull String userId) {
        return owner.equals(userId) || !accessRights.isPrivate();
    }

}
