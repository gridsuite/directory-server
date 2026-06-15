/**
 * Copyright (c) 2022, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.DirectoryInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.List;
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
    public static final String HEADER_DIRECTORIES_INFOS = "directoriesInfos";
    public static final String HEADER_IS_PUBLIC_DIRECTORY = "isPublicDirectory";
    public static final String HEADER_ERROR = "error";
    public static final String HEADER_NOTIFICATION_TYPE = "notificationType";
    public static final String HEADER_ELEMENT_NAMES = "elementNames";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";
    public static final String HEADER_IS_DIRECTORY_MOVING = "isDirectoryMoving";
    public static final String UPDATE_TYPE_ELEMENT_DELETE = "deleteElement";
    public static final String HEADER_EXPORT_UUID = "exportUuid";
    public static final String CASE_EXPORT_FINISHED = "caseExportFinished";
    private static final String CATEGORY_BROKER_OUTPUT = DirectoryService.class.getName() + ".output-broker-messages";
    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge directoryUpdatePublisher;

    @Autowired
    protected ObjectMapper mapper;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        directoryUpdatePublisher.send("publishDirectoryUpdate-out-0", message);
    }

    public void emitDirectoryChanged(UUID directoryUuid, String elementName, String userId, String error, boolean isRoot, NotificationType notificationType) {
        emitDirectoryChanged(List.of(new DirectoryInfos(directoryUuid, isRoot)), List.of(elementName), userId, error, false, notificationType);
    }

    public void emitDirectoryChanged(List<DirectoryInfos> directoryrInfos, List<String> elementNames, String userId, String error, boolean isDirectoryMoving, NotificationType notificationType) {

        //TODO basseche : see what to do in case of error
        String directoriesInfosJson = null;
        try {
            directoriesInfosJson = mapper.writeValueAsString(directoryrInfos);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_ELEMENT_NAMES, elementNames)
                .setHeader(HEADER_DIRECTORIES_INFOS, directoriesInfosJson)
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

    public void emitCaseExportFinished(String userId, UUID exportUuid, @Nullable String error) {
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_EXPORT_UUID, exportUuid)
                .setHeader(HEADER_ERROR, error)
                .setHeader(HEADER_NOTIFICATION_TYPE, CASE_EXPORT_FINISHED)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_DIRECTORIES);
        sendUpdateMessage(messageBuilder.build());
    }
}

enum NotificationType {
    DELETE_DIRECTORY,
    ADD_DIRECTORY,
    UPDATE_DIRECTORY
}
