/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
class DirectoryException extends RuntimeException {

    private final Type type;

    DirectoryException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    DirectoryException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public static DirectoryException createNotificationUnknown(String action) {
        Objects.requireNonNull(action);
        return new DirectoryException(Type.UNKNOWN_NOTIFICATION, String.format("The notification type '%s' is unknown", action));
    }

    Type getType() {
        return type;
    }

    public enum Type {
        NOT_ALLOWED,
        NOT_FOUND,
        UNKNOWN_NOTIFICATION
    }
}
