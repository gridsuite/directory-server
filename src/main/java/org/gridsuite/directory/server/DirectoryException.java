/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.powsybl.ws.commons.error.AbstractPowsyblWsException;
import com.powsybl.ws.commons.error.BusinessErrorCode;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import lombok.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
public class DirectoryException extends AbstractPowsyblWsException {

    private final DirectoryBusinessErrorCode errorCode;
    private final PowsyblWsProblemDetail remoteError;

    public DirectoryException(DirectoryBusinessErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public DirectoryException(DirectoryBusinessErrorCode errorCode, String message, PowsyblWsProblemDetail remoteError) {
        super(Objects.requireNonNull(message, "message must not be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.remoteError = remoteError;
    }

    public static DirectoryException createNotificationUnknown(@NonNull String action) {
        return new DirectoryException(DirectoryBusinessErrorCode.DIRECTORY_NOTIFICATION_UNKNOWN,
                String.format("The notification type '%s' is unknown", action));
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

    public Optional<DirectoryBusinessErrorCode> getErrorCode() {
        return Optional.of(errorCode);
    }

    @Override
    public Optional<BusinessErrorCode> getBusinessErrorCode() {
        return Optional.ofNullable(errorCode);
    }

    public Optional<PowsyblWsProblemDetail> getRemoteError() {
        return Optional.ofNullable(remoteError);
    }
}
