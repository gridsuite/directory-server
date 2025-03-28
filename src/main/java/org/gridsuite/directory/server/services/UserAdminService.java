/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class UserAdminService {

    private static final String USER_ADMIN_API_VERSION = "v1";
    private static final String IS_USER_ADMIN_URI = "/users/{sub}/isAdmin";
    private static final String DELIMITER = "/";
    private final RestTemplate restTemplate;
    @Setter
    private String userAdminServerBaseUri;

    @Autowired
    public UserAdminService(RestTemplate restTemplate, RemoteServicesProperties remoteServicesProperties) {
        this.userAdminServerBaseUri = remoteServicesProperties.getServiceUri("user-admin-server");
        this.restTemplate = restTemplate;
    }

    public boolean isUserAdmin(String sub) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + USER_ADMIN_API_VERSION + IS_USER_ADMIN_URI)
                .buildAndExpand(sub).toUriString();
        try {
            var responseEntity = restTemplate.exchange(userAdminServerBaseUri + path, HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);
            return responseEntity.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException e) {
            return false;
        }
    }
}
