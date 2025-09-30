/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.gridsuite.directory.server.DirectoryException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestResponseEntityExceptionHandler.class);

    @ExceptionHandler(value = {DirectoryException.class})
    protected ResponseEntity<Object> handleException(RuntimeException exception) {
        DirectoryException directoryException = (DirectoryException) exception;
        LOGGER.debug(exception.getMessage(), exception);
        return switch (directoryException.getType()) {
            case NOT_ALLOWED -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(NOT_ALLOWED);
            case IS_DIRECTORY -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(IS_DIRECTORY);
            case NOT_DIRECTORY -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(NOT_DIRECTORY);
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(NOT_FOUND);
            case UNKNOWN_NOTIFICATION -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(UNKNOWN_NOTIFICATION);
            case NAME_ALREADY_EXISTS ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(directoryException.getMessage());
            case MOVE_IN_DESCENDANT_NOT_ALLOWED ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(MOVE_IN_DESCENDANT_NOT_ALLOWED);
        };
    }
}
