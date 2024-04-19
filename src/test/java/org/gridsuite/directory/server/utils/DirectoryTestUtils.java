package org.gridsuite.directory.server.utils;

import org.gridsuite.directory.server.repository.DirectoryElementEntity;

import java.time.LocalDateTime;
import java.util.UUID;

public class DirectoryTestUtils {
    public static DirectoryElementEntity createRootElement(String elementName, String type, boolean isPrivate, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), null, elementName, type, isPrivate, userId, null, LocalDateTime.now(), LocalDateTime.now(), userId, false, null);
    }

    public static DirectoryElementEntity createElement(UUID parentDirectoryUuid, String elementName, String type, boolean isPrivate, String userId) {
        return new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, elementName, type, isPrivate, userId, null, LocalDateTime.now(), LocalDateTime.now(), userId, false, null);
    }
}
