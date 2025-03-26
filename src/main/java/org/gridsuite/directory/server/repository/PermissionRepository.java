/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.repository;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, PermissionId> {

    @NonNull
    Optional<PermissionEntity> findById(@NonNull PermissionId permissionId);

    void deleteAllByElementId(UUID elementId);

    List<PermissionEntity> findAllByElementId(UUID elementId);

    void deleteAllByElementIdAndUserIdNot(UUID elementId, String userId);
}
