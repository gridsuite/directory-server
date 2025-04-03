/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto;

/**
 * @author Achour BERRAHMA <achour.berrahma at rte-france.com>
 */
public enum PermissionCheckResult {
    ALLOWED,
    PARENT_PERMISSION_DENIED,
    TARGET_PERMISSION_DENIED,
    CHILD_PERMISSION_DENIED
}
