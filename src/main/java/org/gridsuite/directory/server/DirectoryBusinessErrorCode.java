/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 *
 * Business error codes emitted by the directory service.
 */
public enum DirectoryBusinessErrorCode implements BusinessErrorCode {
    DIRECTORY_PERMISSION_DENIED("directory.permissionDenied"),
    DIRECTORY_ELEMENT_NAME_BLANK("directory.elementNameBlank"),
    DIRECTORY_NOT_DIRECTORY("directory.notDirectory"),
    DIRECTORY_ELEMENT_NAME_CONFLICT("directory.elementNameConflict"),
    DIRECTORY_MOVE_IN_DESCENDANT_NOT_ALLOWED("directory.moveInDescendantNotAllowed"),
    DIRECTORY_MOVE_SELECTION_EMPTY("directory.moveSelectionEmpty"),
    DIRECTORY_ELEMENT_NOT_FOUND("directory.elementNotFound");
    private final String code;

    DirectoryBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}
