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
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
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
    ObjectMapper objectMapper;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Test
    public void test() throws Exception {

        webTestClient.get()
                .uri("/v1/root-directories")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        // Insert a root directory
        EntityExchangeResult result = webTestClient.post()
                .uri("/v1/root-directories")
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new RootDirectoryAttributes("newDir", new AccessRightsAttributes(false), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewDirectory = jsonTree.get("elementUuid").asText();
        assertEquals("newDir", jsonTree.get("elementName").asText());
        assertFalse(jsonTree.get("accessRights").get("private").asBoolean());

        webTestClient.get()
                .uri("/v1/root-directories")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

        // Insert a sub-element of type DIRECTORY
        result = webTestClient.post()
                .uri("/v1/directories/" + UUID.fromString(uuidNewDirectory))
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(null, "newSubDir", ElementType.DIRECTORY, new AccessRightsAttributes(true), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewSubDirectory = jsonTree.get("elementUuid").asText();
        assertEquals("newSubDir", jsonTree.get("elementName").asText());
        assertTrue(jsonTree.get("accessRights").get("private").asBoolean());

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewDirectory + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"owner\"}]");

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewDirectory)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}");

        UUID uuidAddedStudy = UUID.randomUUID();

        // Insert a  sub-element of type STUDY
        result = webTestClient.post().uri("/v1/directories/" + UUID.fromString(uuidNewDirectory))
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(uuidAddedStudy, "newElement", ElementType.STUDY, new AccessRightsAttributes(false), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        assertEquals(uuidAddedStudy.toString(), jsonTree.get("elementUuid").asText());

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewDirectory + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"owner\"}" +
                        ",{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newElement\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

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
                .isEqualTo("[{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newElement\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

        webTestClient.delete()
                .uri("/v1/directories/" + uuidAddedStudy)
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

        webTestClient.put().uri("/v1/directories/" + uuidNewDirectory + "/rename/newName")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/root-directories")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"owner\"}]");

        webTestClient.put().uri("/v1/directories/" + uuidNewDirectory + "/rights")
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new AccessRightsAttributes(true)))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/root-directories")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"owner\"}]");

        /* add another directory*/
        result = webTestClient.post()
                .uri("/v1/directories/" + UUID.fromString(uuidNewDirectory))
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(null, "newSubDir", ElementType.DIRECTORY, new AccessRightsAttributes(true), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        uuidNewSubDirectory = jsonTree.get("elementUuid").asText();
        assertEquals("newSubDir", jsonTree.get("elementName").asText());
        assertTrue(jsonTree.get("accessRights").get("private").asBoolean());

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewDirectory + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"owner\"}]");

        /* add another directory*/
        result = webTestClient.post()
                .uri("/v1/directories/" + UUID.fromString(uuidNewSubDirectory))
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(null, "newSubSubDir", ElementType.DIRECTORY, new AccessRightsAttributes(true), "owner")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewSubSubDirectory = jsonTree.get("elementUuid").asText();
        assertEquals("newSubSubDir", jsonTree.get("elementName").asText());
        assertTrue(jsonTree.get("accessRights").get("private").asBoolean());

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewSubDirectory + "/content")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[{\"elementUuid\":\"" + uuidNewSubSubDirectory + "\",\"elementName\":\"newSubSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"owner\"}]");

        webTestClient.delete()
                .uri("/v1/directories/" + uuidNewDirectory)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/v1/root-directories")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewSubDirectory)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/v1/directories/" + uuidNewSubSubDirectory)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isNotFound();
    }

}
