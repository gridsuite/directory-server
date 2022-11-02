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
    public static final String HEADER_STUDY_UUID = "studyUuid";
    public static final String UPDATE_TYPE_STUDY_DELETE = "deleteStudy";
    private static final String CATEGORY_BROKER_OUTPUT = DirectoryService.class.getName() + ".output-broker-messages";
    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge studyUpdatePublisher;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        studyUpdatePublisher.send("publishDirectoryUpdate-out-0", message);
    }

    public void emitDirectoryChanged(UUID directoryUuid, String elementName, String userId, String error, Boolean isPrivate, boolean isRoot, NotificationType notificationType) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_DIRECTORY_UUID, directoryUuid)
                .setHeader(HEADER_ELEMENT_NAME, elementName)
                .setHeader(HEADER_IS_ROOT_DIRECTORY, isRoot)
                .setHeader(HEADER_IS_PUBLIC_DIRECTORY, isPrivate == null || !isPrivate) // null may only come from borked REST request
                .setHeader(HEADER_NOTIFICATION_TYPE, notificationType)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES)
                .setHeader(HEADER_ERROR, error);
        sendUpdateMessage(messageBuilder.build());
    }

    public void emitDeletedStudy(UUID studyUuid, String userId) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDY_DELETE);
        sendUpdateMessage(messageBuilder.build());
    }
}

enum NotificationType {
    DELETE_DIRECTORY,
    ADD_DIRECTORY,
    UPDATE_DIRECTORY
}
