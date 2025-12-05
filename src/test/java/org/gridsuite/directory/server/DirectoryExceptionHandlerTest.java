/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.directory.server.error.DirectoryBusinessErrorCode;
import org.gridsuite.directory.server.error.DirectoryException;
import org.gridsuite.directory.server.error.DirectoryExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class DirectoryExceptionHandlerTest {

    private TestDirectoryExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestDirectoryExceptionHandler();
    }

    @Test
    void mapsBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dir");
        DirectoryException exception = new DirectoryException(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NOT_FOUND,
            "Directory element missing");

        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleDomainException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertEquals("directory.elementNotFound", response.getBody().getBusinessErrorCode());
    }

    private static final class TestDirectoryExceptionHandler extends DirectoryExceptionHandler {

        private TestDirectoryExceptionHandler() {
            super(() -> "directory-server");
        }

        ResponseEntity<PowsyblWsProblemDetail> invokeHandleDomainException(DirectoryException exception, MockHttpServletRequest request) {
            return super.handleDomainException(exception, request);
        }
    }
}
