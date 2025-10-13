/**
 * Copyright (c) 2022, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 *
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

// Today we don't send notification inside @Transactional block. If this behavior change, we must make sure
// that notification is sent only when all the work inside @Transactional method is done
@Service
public class NotificationService {
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String UPDATE_TYPE_DIRECTORIES = "directories";
    public static final String HEADER_DIRECTORY_UUID = "directoryUuid";
    public static final String HEADER_IS_PUBLIC_DIRECTORY = "isPublicDirectory";
    public static final String HEADER_IS_ROOT_DIRECTORY = "isRootDirectory";
    public static final String HEADER_ERROR = "error";
    public static final String HEADER_NOTIFICATION_TYPE = "notificationType";
    public static final String HEADER_ELEMENT_NAME = "elementName";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";
    public static final String HEADER_IS_DIRECTORY_MOVING = "isDirectoryMoving";
    public static final String UPDATE_TYPE_ELEMENT_DELETE = "deleteElement";
    public static final String HEADER_CASE_UUID = "caseUuid";
    public static final String HEADER_EXPORT_UUID = "exportUuid";
    public static final String CASE_EXPORT_SUCCEEDED = "caseExportSucceeded";
    private static final String CATEGORY_BROKER_OUTPUT = DirectoryService.class.getName() + ".output-broker-messages";
    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge directoryUpdatePublisher;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        directoryUpdatePublisher.send("publishDirectoryUpdate-out-0", message);
    }

    public void emitDirectoryChanged(UUID directoryUuid, String elementName, String userId, String error, boolean isRoot, NotificationType notificationType) {
        emitDirectoryChanged(directoryUuid, elementName, userId, error, isRoot, false, notificationType);
    }

    public void emitDirectoryChanged(UUID directoryUuid, String elementName, String userId, String error, boolean isRoot, boolean isDirectoryMoving, NotificationType notificationType) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_DIRECTORY_UUID, directoryUuid)
                .setHeader(HEADER_ELEMENT_NAME, elementName)
                .setHeader(HEADER_IS_ROOT_DIRECTORY, isRoot)
                .setHeader(HEADER_IS_PUBLIC_DIRECTORY, true) // null may only come from borked REST request
                .setHeader(HEADER_NOTIFICATION_TYPE, notificationType)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES)
                .setHeader(HEADER_IS_DIRECTORY_MOVING, isDirectoryMoving)
                .setHeader(HEADER_ERROR, error);
        sendUpdateMessage(messageBuilder.build());
    }

    public void emitDeletedElement(UUID elementUuid, String userId) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_ELEMENT_UUID, elementUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_ELEMENT_DELETE);
        sendUpdateMessage(messageBuilder.build());
    }

    public void emitCaseExportSucceeded(UUID caseUuid, String userId, UUID exportUuid, @Nullable String error) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_CASE_UUID, caseUuid)
                .setHeader(HEADER_EXPORT_UUID, exportUuid)
                .setHeader(HEADER_ERROR, error)
                .setHeader(HEADER_NOTIFICATION_TYPE, CASE_EXPORT_SUCCEEDED)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES);
        sendUpdateMessage(messageBuilder.build());
    }
}

enum NotificationType {
    DELETE_DIRECTORY,
    ADD_DIRECTORY,
    UPDATE_DIRECTORY
}
