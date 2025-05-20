/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.utils;

import okhttp3.mockwebserver.MockResponse;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public final class DirectoryTestUtils {
    private DirectoryTestUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static DirectoryElementEntity createRootElement(String elementName, String type, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), null, elementName, type, userId, null, Instant.now(), Instant.now(), userId);
    }

    public static DirectoryElementEntity createElement(UUID parentDirectoryUuid, String elementName, String type, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, elementName, type, userId, null, Instant.now(), Instant.now(), userId);
    }

    public static MockResponse jsonResponse(HttpStatus status, String body) {
        return new MockResponse()
                .setResponseCode(status.value())
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }
}
