/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.utils;

import lombok.NonNull;
import okhttp3.mockwebserver.MockResponse;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public final class DirectoryTestUtils {
    private DirectoryTestUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static DirectoryElementEntity createRootElement(String elementName, String type, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), null, elementName, type, userId, null, Instant.now(), Instant.now(), userId, List.of());
    }

    public static DirectoryElementEntity createElement(UUID parentDirectoryUuid, String elementName, String type, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, elementName, type, userId, null, Instant.now(), Instant.now(), userId, List.of());
    }

    public static MockResponse jsonResponse(HttpStatus status, String body) {
        return new MockResponse()
                .setResponseCode(status.value())
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType, @NonNull String userId) {
        return ElementAttributes.toElementAttributes(elementUuid, elementName, elementType, userId, 0L, null, null, null, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull String userId, @NonNull String elementDescription) {
        return ElementAttributes.toElementAttributes(elementUuid, elementName, elementType, userId, 0L, elementDescription, null, null, null);
    }

    public static ElementAttributes toElementAttributes(UUID elementUuid, @NonNull String elementName, @NonNull String elementType,
                                                        @NonNull String userId, String elementDescription, Instant creationDate, Instant lastModificationDate, String lastModifiedBy) {
        return ElementAttributes.toElementAttributes(elementUuid, elementName, elementType, userId, 0L, elementDescription, creationDate, lastModificationDate, lastModifiedBy);
    }
}
