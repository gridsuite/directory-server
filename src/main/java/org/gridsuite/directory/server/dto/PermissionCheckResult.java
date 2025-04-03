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
