/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.powsybl.ws.commons.error.AbstractBaseRestExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Optional;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler
    extends AbstractBaseRestExceptionHandler<DirectoryException, DirectoryBusinessErrorCode> {

    public RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected Optional<PowsyblWsProblemDetail> getRemoteError(DirectoryException ex) {
        return ex.getRemoteError();
    }

    @Override
    protected Optional<DirectoryBusinessErrorCode> getBusinessCode(DirectoryException ex) {
        return ex.getErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(DirectoryBusinessErrorCode errorCode) {
        return switch (errorCode) {
            case DIRECTORY_ELEMENT_NOT_FOUND, DIRECTORY_DIRECTORY_NOT_FOUND_IN_PATH -> HttpStatus.NOT_FOUND;
            case DIRECTORY_ELEMENT_NAME_CONFLICT -> HttpStatus.CONFLICT;
            case DIRECTORY_NOTIFICATION_UNKNOWN -> HttpStatus.BAD_REQUEST;
            case DIRECTORY_REMOTE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case DIRECTORY_PERMISSION_DENIED,
                 DIRECTORY_ELEMENT_NAME_BLANK,
                 DIRECTORY_ROOT_ALREADY_EXISTS,
                 DIRECTORY_NOT_DIRECTORY,
                 DIRECTORY_MOVE_IN_DESCENDANT_NOT_ALLOWED,
                 DIRECTORY_MOVE_SELECTION_EMPTY,
                 DIRECTORY_CANNOT_DELETE_ELEMENT -> HttpStatus.FORBIDDEN;
        };
    }

    @Override
    protected DirectoryBusinessErrorCode defaultRemoteErrorCode() {
        return DirectoryBusinessErrorCode.DIRECTORY_REMOTE_ERROR;
    }

    @Override
    protected DirectoryException wrapRemote(PowsyblWsProblemDetail remoteError) {
        return new DirectoryException(
            DirectoryBusinessErrorCode.DIRECTORY_REMOTE_ERROR,
            remoteError.getDetail(),
            remoteError
        );
    }
}
