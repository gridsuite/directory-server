/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.constants;

/**
 * @author Achour Berrahma <achour.berrahma at rte-france.com>
 * Constants class for application roles.
 * Centralizes all role definitions for consistency.
 */
public final class ApplicationRoles {

    // Private constructor to prevent instantiation
    private ApplicationRoles() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static final String USER = "UTILISATEURS";
    public static final String ADMIN_EXPLORE = "ADMIN_EXPLORE";

}
