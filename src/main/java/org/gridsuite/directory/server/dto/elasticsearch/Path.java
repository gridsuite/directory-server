/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.directory.server.dto.elasticsearch;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui_externe at rte-france.com>
 */

@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@ToString
@EqualsAndHashCode
public class Path {
    private List<String> pathName;

    private List<UUID> pathUuid;
}
