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
import org.gridsuite.directory.server.utils.MatcherJson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

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
        databaseClient.sql(Files.readString(Path.of(schemaFile.toURI()))).fetch().first().block();
    }

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

        EntityExchangeResult<String> result = webTestClient.post()
            .uri("/v1/directories")
            .header("userId", "userId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(DirectoryAttributes.builder()
                .id(null).parentId(null).name("newDir").accessRights(new AccessRightsAttributes(false)).owner("owner").build()))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody());
        String uuidNewDirectory = jsonTree.get("id").asText();
        assertEquals("newDir", jsonTree.get("name").asText());
        assertFalse(jsonTree.get("private").asBoolean());
        webTestClient.get()
            .uri("/v1/root-directories")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper,
                List.of(ElementAttributes.builder().id(UUID.fromString(uuidNewDirectory)).name("newDir").type(ElementType.DIRECTORY).accessRights(new AccessRightsAttributes(false)).owner("owner").build())));

        result = webTestClient.post()
            .uri("/v1/directories")
            .header("userId", "userId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(DirectoryAttributes.builder()
                .id(null).parentId(UUID.fromString(uuidNewDirectory)).name("newSubDir").accessRights(new AccessRightsAttributes(true)).owner("owner").build()))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody());
        String uuidNewSubDirectory = jsonTree.get("id").asText();
        assertEquals("newSubDir", jsonTree.get("name").asText());
        assertTrue(jsonTree.get("private").asBoolean());

        webTestClient.get()
            .uri("/v1/directories/" + uuidNewDirectory + "/content")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper,
                List.of(ElementAttributes.builder().id(UUID.fromString(uuidNewSubDirectory)).name("newSubDir").type(ElementType.DIRECTORY).accessRights(new AccessRightsAttributes(true)).owner("owner").build())));

        UUID uuidAddedStudy = UUID.randomUUID();

        result = webTestClient.put().uri("/v1/directories/" + uuidNewDirectory)
            .header("userId", "userId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(objectMapper.writeValueAsString(ElementAttributes.builder()
                .id(uuidAddedStudy).name("newElement").type(ElementType.STUDY).accessRights(new AccessRightsAttributes(false)).owner("owner").build()))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult();

        jsonTree = objectMapper.readTree(result.getResponseBody());
        assertEquals(uuidAddedStudy.toString(), jsonTree.get("id").asText());

        webTestClient.get()
            .uri("/v1/directories/" + uuidNewDirectory + "/content")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper,
                List.of(
                    ElementAttributes.builder().id(UUID.fromString(uuidNewSubDirectory)).name("newSubDir").type(ElementType.DIRECTORY).accessRights(new AccessRightsAttributes(true)).owner("owner").build(),
                    ElementAttributes.builder().id(uuidAddedStudy).name("newElement").type(ElementType.STUDY).accessRights(new AccessRightsAttributes(false)).owner("owner").build()
                )
            ));

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
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper,
                List.of(ElementAttributes.builder().id(uuidAddedStudy).name("newElement").type(ElementType.STUDY).accessRights(new AccessRightsAttributes(false)).owner("owner").build())));

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
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, List.of()));

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
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper,
                List.of(ElementAttributes.builder().id(UUID.fromString(uuidNewDirectory)).name("newName").type(ElementType.DIRECTORY).accessRights(new AccessRightsAttributes(false)).owner("owner").build())));

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
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper,
                List.of(ElementAttributes.builder().id(UUID.fromString(uuidNewDirectory)).name("newName").type(ElementType.DIRECTORY).accessRights(new AccessRightsAttributes(true)).owner("owner").build())));

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

    }

}
