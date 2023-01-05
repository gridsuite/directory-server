/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Service
public class StudyService {

    private static final String STUDY_SERVER_API_VERSION = "v1";

    private static final String NOTIFICATION_TYPE_METADATA_UPDATED = "metadata_updated";

    private static final String HEADER_USER_ID = "userId";

    private static final String DELIMITER = "/";

    @Autowired
    private RestTemplate restTemplate;

    private final String studyServerBaseUri;

    @Autowired
    public StudyService(@Value("${gridsuite.services.study-server.base-uri:http://study-server/}") String studyServerBaseUri) {
        this.studyServerBaseUri = studyServerBaseUri;
    }

    public ResponseEntity<Void> notifyStudyUpdate(UUID studyUuid, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STUDY_SERVER_API_VERSION +
                "/studies/{studyUuid}/notification?type={metadata_updated}")
                .buildAndExpand(studyUuid, NOTIFICATION_TYPE_METADATA_UPDATED)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        return restTemplate.exchange(studyServerBaseUri + path, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
    }
}
