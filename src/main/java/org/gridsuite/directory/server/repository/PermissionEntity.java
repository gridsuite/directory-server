/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "permission")
@IdClass(PermissionId.class)
@EqualsAndHashCode
public class PermissionEntity {

    @Column(name = "elementId")
    @Id
    private UUID elementId;

    @Column(name = "userId")
    @Id
    private String userId;

    @Column(name = "userGroupId")
    @Id
    private String userGroupId;

    @Column(name = "read")
    private Boolean read;

    @Column(name = "write")
    private Boolean write;

}
