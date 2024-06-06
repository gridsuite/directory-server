/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.utils;

import org.gridsuite.directory.server.repository.DirectoryElementEntity;

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
        return new DirectoryElementEntity(UUID.randomUUID(), null, elementName, type, userId, null, Instant.now(), Instant.now(), userId, false, null);
    }

    public static DirectoryElementEntity createElement(UUID parentDirectoryUuid, String elementName, String type, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, elementName, type, userId, null, Instant.now(), Instant.now(), userId, false, null);
    }
}
