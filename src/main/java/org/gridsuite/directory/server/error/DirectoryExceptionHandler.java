/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Mohamed Ben-rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@ControllerAdvice
public class DirectoryExceptionHandler
    extends AbstractBusinessExceptionHandler<DirectoryException, DirectoryBusinessErrorCode> {

    public DirectoryExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @NonNull
    @Override
    protected DirectoryBusinessErrorCode getBusinessCode(DirectoryException ex) {
        return ex.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(DirectoryBusinessErrorCode errorCode) {
        return switch (errorCode) {
            case DIRECTORY_ELEMENT_NOT_FOUND, DIRECTORY_SOME_ELEMENTS_ARE_MISSING -> HttpStatus.NOT_FOUND;
            case DIRECTORY_ELEMENT_NAME_CONFLICT -> HttpStatus.CONFLICT;
            case DIRECTORY_PERMISSION_DENIED,
                 DIRECTORY_PARENT_PERMISSION_DENIED,
                 DIRECTORY_TARGET_PERMISSION_DENIED,
                 DIRECTORY_CHILD_PERMISSION_DENIED,
                 DIRECTORY_ELEMENT_NAME_BLANK,
                 DIRECTORY_NOT_DIRECTORY,
                 DIRECTORY_MOVE_IN_DESCENDANT_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
        };
    }

    @ExceptionHandler(DirectoryException.class)
    protected ResponseEntity<PowsyblWsProblemDetail> handleDirectoryException(
        DirectoryException exception, HttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }
}
