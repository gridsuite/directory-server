/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Set;

/**
 * @author Achour Berrahma <achour.berrahma at rte-france.com>
 * Service for checking user roles.
 */
@Getter
@Service
public class RoleService {
    public static final String ROLES_HEADER = "roles";
    public static final String ROLE_DELIMITER = "\\|";

    @Value("${gridsuite.user-roles.admin-explore:ADMIN_EXPLORE}")
    private String adminExploreRole;

    /**
     * Gets the current user's roles from the request headers.
     *
     * @return A set of the user's roles
     */
    public Set<String> getCurrentUserRoles() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Collections.emptySet();
        }

        HttpServletRequest request = attributes.getRequest();
        String rolesHeader = request.getHeader(ROLES_HEADER);

        if (rolesHeader == null || rolesHeader.trim().isEmpty()) {
            return Collections.emptySet();
        }

        return Set.of(rolesHeader.split(ROLE_DELIMITER));
    }

   /**
     * Checks if the user has the required roles for admin access.
     *
     * @return True if the user has admin access, false otherwise
     */
    public boolean isUserExploreAdmin() {
        Set<String> userRoles = getCurrentUserRoles();
        return !userRoles.isEmpty() && userRoles.contains(adminExploreRole);
    }

}
