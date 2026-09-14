/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.gridsuite.directory.server.dto.ReferenceContainer;

import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Embeddable
public class ReferenceContainerEmbeddable {
    @Column(name = "rootContainerId")
    private UUID rootContainerId;

    @Column(name = "containerId")
    @NonNull private UUID containerId;

    public ReferenceContainer toReferenceAttributes() {
        return ReferenceContainer.builder()
            .rootContainerId(rootContainerId)
            .containerId(containerId)
            .build();
    }
}
