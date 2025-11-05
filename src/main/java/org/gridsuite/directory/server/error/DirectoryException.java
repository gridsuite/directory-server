/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import lombok.NonNull;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class DirectoryException extends AbstractBusinessException {

    private final DirectoryBusinessErrorCode errorCode;

    public DirectoryException(DirectoryBusinessErrorCode errorCode, String message) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public static DirectoryException createElementNotFound(@NonNull String type, @NonNull UUID uuid) {
        return new DirectoryException(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NOT_FOUND,
                String.format("%s '%s' not found !", type, uuid));
    }

    public static DirectoryException createElementNameAlreadyExists(@NonNull String name) {
        return new DirectoryException(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_CONFLICT,
                String.format("Element with the same name '%s' already exists in the directory !", name));
    }

    public static DirectoryException of(DirectoryBusinessErrorCode errorCode, String message, Object... args) {
        return new DirectoryException(errorCode, args.length == 0 ? message : String.format(message, args));
    }

    @NonNull
    @Override
    public DirectoryBusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }

}
