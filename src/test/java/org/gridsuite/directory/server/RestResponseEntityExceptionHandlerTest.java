/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.directory.server.error.DirectoryBusinessErrorCode;
import org.gridsuite.directory.server.error.DirectoryException;
import org.gridsuite.directory.server.error.RestResponseEntityExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
class RestResponseEntityExceptionHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private TestRestResponseEntityExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestRestResponseEntityExceptionHandler();
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

    @Test
    void propagatesRemoteErrorDetails() throws JsonProcessingException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dir/resource");
        PowsyblWsProblemDetail remote = PowsyblWsProblemDetail.builder(HttpStatus.FORBIDDEN)
            .server("downstream")
            .detail("Denied")
            .path("/remote").build();

        HttpClientErrorException exception = HttpClientErrorException.create(
            HttpStatus.FORBIDDEN,
            "coucou",
            HttpHeaders.EMPTY,
            OBJECT_MAPPER.writeValueAsBytes(remote),
            null
        );

        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleRemoteException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        PowsyblWsProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).isEqualTo("Denied");
        assertThat(body.getChain()).hasSize(1);
    }

    @Test
    void wrapsRemoteExceptionWhenPayloadInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/dir/remote");
        HttpClientErrorException exception = HttpClientErrorException.create(
            HttpStatus.BAD_GATEWAY,
            "Bad gateway",
            null,
            "oops".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );

        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleRemoteException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getChain()).hasSize(1);
    }

    @Test
    void reusesRemoteStatusWhenPayloadValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dir/remote");
        PowsyblWsProblemDetail remote = PowsyblWsProblemDetail.builder(HttpStatus.NOT_FOUND)
            .server("downstream")
            .businessErrorCode("directory.downstreamNotFound")
            .detail("missing")
            .path("/remote/missing")
            .build();

        byte[] payload = OBJECT_MAPPER.writeValueAsBytes(remote);
        HttpClientErrorException exception = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not found",
            null, payload, StandardCharsets.UTF_8);

        ResponseEntity<PowsyblWsProblemDetail> response = handler.invokeHandleRemoteException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertEquals("directory.downstreamNotFound", response.getBody().getBusinessErrorCode());
    }

    private static final class TestRestResponseEntityExceptionHandler extends RestResponseEntityExceptionHandler {

        private TestRestResponseEntityExceptionHandler() {
            super(() -> "directory-server");
        }

        ResponseEntity<PowsyblWsProblemDetail> invokeHandleDomainException(DirectoryException exception, MockHttpServletRequest request) {
            return super.handleDomainException(exception, request);
        }

        ResponseEntity<PowsyblWsProblemDetail> invokeHandleRemoteException(HttpClientErrorException exception, MockHttpServletRequest request) {
            return super.handleRemoteException(exception, request);
        }
    }
}
