/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.directory.server.dto.ReferenceAttributes;

import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "reference", indexes = @Index(name = "element_idx", columnList = "element_id"))
public class ReferenceEntity {

    @Id
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_name")
    private String referenceName;

    public ReferenceAttributes toReferenceAttributes() {
        return ReferenceAttributes.builder()
            .referenceId(referenceId)
            .referenceType(referenceType)
            .referenceName(referenceName)
            .build();
    }
}
