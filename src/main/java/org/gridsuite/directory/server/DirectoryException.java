/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import lombok.NonNull;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public class DirectoryException extends RuntimeException {

    private final Type type;

    public DirectoryException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    public DirectoryException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static DirectoryException createNotificationUnknown(@NonNull String action) {
        return new DirectoryException(Type.UNKNOWN_NOTIFICATION, String.format("The notification type '%s' is unknown", action));
    }

    public static DirectoryException createElementNotFound(@NonNull String type, @NonNull UUID uuid) {
        return new DirectoryException(Type.NOT_FOUND, String.format("%s '%s' not found !", type, uuid));
    }

    public static DirectoryException createElementNameAlreadyExists(@NonNull String name) {
        return new DirectoryException(Type.NAME_ALREADY_EXISTS, String.format("Element with the same name '%s' already exists the same directory !", name));
    }

    Type getType() {
        return type;
    }

    public enum Type {
        NOT_ALLOWED,
        NOT_FOUND,
        NOT_DIRECTORY,
        IS_DIRECTORY,
        UNKNOWN_NOTIFICATION,
        NAME_ALREADY_EXISTS,
        IS_DESCENDENT
    }
}
