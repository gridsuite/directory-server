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
import org.gridsuite.directory.server.dto.DirectoryAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public void initDatabase() throws IOException {
        // Init schema
        File schemaFile = new File(getClass().getClassLoader().getResource("schema.sql").getFile());
        databaseClient.execute(Files.readString(Path.of(schemaFile.toURI()))).fetch().first().block();
    }

    @Test
    public void test() throws Exception {

        webTestClient.get()
                .uri("/v1/directories/root/content")
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
                .bodyValue(objectMapper.writeValueAsString(new DirectoryAttributes(null, "newDir", new AccessRightsAttributes(false), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewDirectory = jsonTree.get("id").asText();
        assertTrue(jsonTree.get("name").asText().equals("newDir"));
        assertFalse(jsonTree.get("private").asBoolean());
        webTestClient.get()
                .uri("/v1/directories/root/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

        result = webTestClient.post()
                .uri("/v1/directories/create")
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new DirectoryAttributes(UUID.fromString(uuidNewDirectory), "newSubDir", new AccessRightsAttributes(true), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewSubDirectory = jsonTree.get("id").asText();
        assertTrue(jsonTree.get("name").asText().equals("newSubDir"));
        assertTrue(jsonTree.get("private").asBoolean());

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewDirectory + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"owner\"}]");

        webTestClient.delete()
                .uri("/v1/directories/" + uuidNewSubDirectory)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewDirectory + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        UUID uuidAddedStudy = UUID.randomUUID();

        result = webTestClient.put().uri("/v1/directories/root/add")
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(uuidAddedStudy, "newElement", ElementType.STUDY, new AccessRightsAttributes(false), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        assertTrue(uuidAddedStudy.toString().equals(jsonTree.get("id").asText()));

        webTestClient.put().uri("/v1/directories/" + uuidNewDirectory + "/rename/newName")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/directories/root/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}" +
                        ",{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newElement\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

        webTestClient.delete()
                .uri("/v1/directories/" + uuidNewDirectory)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/directories/root/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newElement\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

    }

}
