/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Getter
@AllArgsConstructor
public class ReferenceAttributes {
    public enum ReferenceType {
        STUDY_NODE,
        STUDY_NODE_NETWORK_MODIFICATION,
        DIRECTORY_NETWORK_MODIFICATION,
    }

    @NonNull private UUID referenceId;
    @NonNull private ReferenceContainer referenceContainer;
    @NonNull private ReferenceType referenceType;
}
