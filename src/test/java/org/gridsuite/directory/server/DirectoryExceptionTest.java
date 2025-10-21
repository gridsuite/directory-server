/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.error.DirectoryBusinessErrorCode;
import org.gridsuite.directory.server.error.DirectoryException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class DirectoryExceptionTest {

    @Test
    void staticFactoriesProduceExpectedMessages() {
        DirectoryException notFound = DirectoryException.createElementNotFound("Folder", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(notFound.getMessage()).contains("Folder");
        assertThat(notFound.getBusinessErrorCode()).isEqualTo(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NOT_FOUND);

        DirectoryException conflict = DirectoryException.createElementNameAlreadyExists("report");
        assertThat(conflict.getMessage()).contains("report");
        assertThat(conflict.getBusinessErrorCode()).isEqualTo(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_CONFLICT);

        DirectoryException formatted = DirectoryException.of(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_BLANK,
            "Element '%s' invalid", "x");
        assertThat(formatted.getMessage()).isEqualTo("Element 'x' invalid");
        assertThat(formatted.getBusinessErrorCode()).isEqualTo(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_BLANK);
    }

}
