/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.CreateDirectoryAttributes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
@ContextConfiguration(classes = {DirectoryApplication.class})
public class DirectoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryTest.class);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    ObjectMapper objectMapper;

    @Before
    public void initDatabaseSchema() {
        databaseClient.execute("CREATE TABLE ELEMENT (parentId uuid,id uuid,name varchar(80),type varchar(20),isPrivate boolean,owner varchar(30));").fetch().first().block();
    }

    @Test
    public void test() throws Exception {

        UUID rootDirectoryUuid = UUID.randomUUID();

        webTestClient.get()
                .uri("/v1/directories/" + rootDirectoryUuid + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        EntityExchangeResult result = webTestClient.post()
                .uri("/v1/directories/create")
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new CreateDirectoryAttributes(rootDirectoryUuid, "newDir", new AccessRightsAttributes(false), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuid = jsonTree.get("id").asText();
        assertTrue(jsonTree.get("name").asText().equals("newDir"));
        assertFalse(jsonTree.get("private").asBoolean());
        webTestClient.get()
                .uri("/v1/directories/" + rootDirectoryUuid + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuid + "\",\"elementName\":\"newDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

    }
}
