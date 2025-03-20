/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.directory.server.services;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import org.gridsuite.directory.server.DirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.gridsuite.directory.server.NotificationService.HEADER_ERROR;
import static org.gridsuite.directory.server.NotificationService.HEADER_USER_ID;

@Service
public class ConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerService.class);

    private static final String CATEGORY_BROKER_INPUT = ConsumerService.class.getName() + ".input-broker-messages";

    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";
    public static final String UPDATE_TYPE_STUDIES = "studies";
    public static final String HEADER_STUDY_UUID = "studyUuid";
    public static final String HEADER_MODIFIED_BY = "modifiedBy";
    public static final String HEADER_MODIFICATION_DATE = "modificationDate";

    DirectoryService directoryService;

    @Autowired
    public ConsumerService(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Bean
    public Consumer<Message<String>> consumeElementUpdate() {
        return message -> {
            String elementUpdatedUuidStr = message.getHeaders().get(HEADER_ELEMENT_UUID, String.class);
            String modifiedBy = message.getHeaders().get(HEADER_MODIFIED_BY, String.class);
            String modificationDateStr = message.getHeaders().get(HEADER_MODIFICATION_DATE, String.class);

            UUID elementUpdatedUuid = UUID.fromString(elementUpdatedUuidStr);
            Instant modificationDate = Instant.parse(modificationDateStr);

            directoryService.updateElementLastModifiedAttributes(elementUpdatedUuid, modificationDate, modifiedBy);
        };
    }

    //TODO: this consumer is the kept here at the moment, but it will be moved to explore server later on
    @Bean
    public Consumer<Message<String>> consumeStudyUpdate() {
        LOGGER.info(CATEGORY_BROKER_INPUT);
        return message -> {
            try {
                String studyUuidHeader = message.getHeaders().get(HEADER_STUDY_UUID, String.class);
                String error = message.getHeaders().get(HEADER_ERROR, String.class);
                String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
                String updateType = message.getHeaders().get(HEADER_UPDATE_TYPE, String.class);
                // UPDATE_TYPE_STUDIES is the update type used when inserting or duplicating studies, and when a study import fails
                if (UPDATE_TYPE_STUDIES.equals(updateType) && studyUuidHeader != null) {
                    directoryService.studyUpdated(UUID.fromString(studyUuidHeader), error, userId);
                }
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            }
        };
    }
}
