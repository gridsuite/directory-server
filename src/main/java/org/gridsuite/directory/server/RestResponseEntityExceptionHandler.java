/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.powsybl.ws.commons.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestResponseEntityExceptionHandler.class);
    private static final String SERVICE_NAME = "directory-server";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @ExceptionHandler(DirectoryException.class)
    protected ResponseEntity<ErrorResponse> handleDirectoryException(DirectoryException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.getType()) {
            case NOT_ALLOWED, NOT_DIRECTORY, MOVE_IN_DESCENDANT_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNKNOWN_NOTIFICATION -> HttpStatus.BAD_REQUEST;
            case NAME_ALREADY_EXISTS -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
            .body(buildErrorResponse(request, status, exception.getType().name(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleAllExceptions(Exception exception, HttpServletRequest request) {
        HttpStatus status = resolveStatus(exception);
        String message = exception.getMessage() != null ? exception.getMessage() : status.getReasonPhrase();
        return ResponseEntity.status(status)
            .body(buildErrorResponse(request, status, status.name(), message));
    }

    private HttpStatus resolveStatus(Exception exception) {
        if (exception instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        if (exception instanceof HttpStatusCodeException httpStatusCodeException) {
            return HttpStatus.valueOf(httpStatusCodeException.getStatusCode().value());
        }
        if (exception instanceof ServletRequestBindingException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof NoResourceFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ErrorResponse buildErrorResponse(HttpServletRequest request, HttpStatus status, String errorCode, String message) {
        return new ErrorResponse(
            SERVICE_NAME,
            errorCode,
            message,
            status.value(),
            Instant.now(),
            request.getRequestURI(),
            request.getHeader(CORRELATION_ID_HEADER)
        );
    }
}
