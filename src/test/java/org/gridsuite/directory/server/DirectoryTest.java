/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
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

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
@ContextConfiguration(classes = {DirectoryApplication.class})
public class DirectoryTest {
    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Test
    public void test() throws Exception {
        checkRootDirectoriesList("userId", "[]");

        // Insert a root directory
        String uuidNewDirectory = insertAndCheckRootDirectory("newDir", false, "userId");

        // Insert a sub-element of type DIRECTORY
        String uuidNewSubDirectory = insertAndCheckSubElement(null, uuidNewDirectory, "newSubDir", ElementType.DIRECTORY, true, "userId");
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\"}" + "]", "userId");

        // Insert a  sub-element of type STUDY
        String uuidAddedStudy = insertAndCheckSubElement(UUID.randomUUID(), uuidNewDirectory, "newStudy", ElementType.STUDY, false, "userId");
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\"}" +
                ",{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newStudy\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"userId\"}]", "userId");

        // Delete the sub-directory newSubDir
        deleteElement(uuidNewSubDirectory, "userId");
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newStudy\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"userId\"}]", "userId");

        // Delete the sub-element newStudy
        deleteElement(uuidAddedStudy, "userId");
        assertDirectoryIsEmpty(uuidNewDirectory, "userId");

        // Rename the root directory
        webTestClient.put().uri("/v1/directories/" + uuidNewDirectory + "/rename/newName")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk();

        checkRootDirectoriesList("userId", "[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"userId\"}]");

        // Change root directory access rights
        webTestClient.put().uri("/v1/directories/" + uuidNewDirectory + "/rights")
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new AccessRightsAttributes(true)))
                .exchange()
                .expectStatus().isOk();

        checkRootDirectoriesList("userId", "[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\"}]");

        // Add another sub-directory
        uuidNewSubDirectory = insertAndCheckSubElement(null, uuidNewDirectory, "newSubDir", ElementType.DIRECTORY, true, "userId");
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\"}" + "]", "userId");

        // Add another sub-directory
        String uuidNewSubSubDirectory = insertAndCheckSubElement(null, uuidNewSubDirectory, "newSubSubDir", ElementType.DIRECTORY, true, "userId");
        checkDirectoryContent(uuidNewSubDirectory, "[{\"elementUuid\":\"" + uuidNewSubSubDirectory + "\",\"elementName\":\"newSubSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\"}" + "]", "userId");

        deleteElement(uuidNewDirectory, "userId");
        checkRootDirectoriesList("userId", "[]");

        checkElementNotFound(uuidNewSubDirectory, "userId");
        checkElementNotFound(uuidNewSubSubDirectory, "userId");
    }

    @Test
    public void testTwoUsersTwoPublicDirectories() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a root directory user1
        String rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", false, "user1");
        // Insert a root directory user2
        String rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", false, "user2");
        checkRootDirectoriesList("user1", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\"}," +
                "{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]");
        checkRootDirectoriesList("user2", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\"}," +
                "{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]");
        //Cleaning Test
        deleteElement(rootDir1Uuid, "user1");
        deleteElement(rootDir2Uuid, "user2");
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateDirectories() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a root directory user1
        String rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1");
        // Insert a root directory user2
        String rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", false, "user2");
        checkRootDirectoriesList("user1", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\"}," +
                "{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]");
        checkRootDirectoriesList("user2", "[{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]");
        //Cleaning Test
        deleteElement(rootDir1Uuid, "user1");
        deleteElement(rootDir2Uuid, "user2");
    }

    @Test
    public void testTwoUsersTwoPrivateDirectories() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a root directory user1
        String rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1");
        // Insert a root directory user2
        String rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", true, "user2");
        checkRootDirectoriesList("user1", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\"}]");
        checkRootDirectoriesList("user2", "[{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"user2\"}]");
        //Cleaning Test
        deleteElement(rootDir1Uuid, "user1");
        deleteElement(rootDir2Uuid, "user2");
    }

    @Test
    public void testTwoUsersTwoPublicStudies() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory bu the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  ElementType.STUDY, false, "user1");
        // Insert a public study in the root directory bu the user1
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  ElementType.STUDY, false, "user2");

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\"}," +
                "{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]", "user1");

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\"}," +
                "{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]", "user2");

        deleteElement(rootDirUuid, "Doe");
        checkElementNotFound(rootDirUuid, "Doe");
        checkElementNotFound(study1Uuid, "user1");
        checkElementNotFound(study1Uuid, "user2");
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateStudies() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory bu the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  ElementType.STUDY, true, "user1");
        // Insert a public study in the root directory bu the user1
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  ElementType.STUDY, false, "user2");

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\"}," +
                "{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]", "user1");

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\"}]", "user2");

        deleteElement(study1Uuid, "user1");
        checkElementNotFound(study1Uuid, "user1");
        deleteElement(rootDirUuid, "Doe");
        checkElementNotFound(rootDirUuid, "Doe");
        checkElementNotFound(study1Uuid, "user2");
    }

    @Test
    public void testTwoUsersTwoPrivateStudies() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory bu the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  ElementType.STUDY, true, "user1");
        // Insert a public study in the root directory bu the user1
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  ElementType.STUDY, true, "user2");

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\"}]", "user1");

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user2\"}]", "user2");

        deleteElement(study1Uuid, "user1");
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, "user2");
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, "Doe");
        checkElementNotFound(rootDirUuid, "Doe");
    }

    private void checkRootDirectoriesList(String userId, String expected) {
        webTestClient.get()
                .uri("/v1/root-directories")
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo(expected);
    }

    private String insertAndCheckRootDirectory(String rootDirectoryName, boolean isPrivate, String userId) throws JsonProcessingException {
        EntityExchangeResult result = webTestClient.post()
                .uri("/v1/root-directories")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewDirectory = jsonTree.get("elementUuid").asText();
        assertEquals(rootDirectoryName, jsonTree.get("elementName").asText());
        assertEquals(isPrivate, jsonTree.get("accessRights").get("private").asBoolean());

        assertElementIsProperlyInserted(uuidNewDirectory, rootDirectoryName, ElementType.DIRECTORY, isPrivate, userId);

        return uuidNewDirectory;
    }

    private String insertAndCheckSubElement(UUID elementUuid, String parentDirectoryUUid, String subElementName, ElementType type, boolean isPrivate, String userId) throws JsonProcessingException {
        // Insert a sub-element of type DIRECTORY
        EntityExchangeResult result = webTestClient.post()
                .uri("/v1/directories/" + UUID.fromString(parentDirectoryUUid))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(elementUuid, subElementName, type, new AccessRightsAttributes(isPrivate), userId)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidSubElement = jsonTree.get("elementUuid").asText();
        assertEquals(subElementName, jsonTree.get("elementName").asText());
        assertEquals(isPrivate, jsonTree.get("accessRights").get("private").asBoolean());

        assertElementIsProperlyInserted(uuidSubElement, subElementName, type, isPrivate, userId);
        return uuidSubElement;
    }

    private void assertDirectoryIsEmpty(String uuidDir, String userId) {
        webTestClient.get()
                .uri("/v1/directories/" + uuidDir + "/content")
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("[]");
    }

    private void assertElementIsProperlyInserted(String elementUuid, String elementName, ElementType type, boolean isPrivate, String userId) {
        webTestClient.get()
                .uri("/v1/directories/" + elementUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"elementUuid\":\"" + elementUuid + "\",\"elementName\":\"" + elementName + "\",\"type\":\"" + type.toString() + "\",\"accessRights\":{\"private\":" + isPrivate + "},\"owner\":\"" + userId + "\"}");
    }

    private void checkDirectoryContent(String parentDirectoryUuid, String expected, String userId) {
        webTestClient.get()
                .uri("/v1/directories/" + parentDirectoryUuid + "/content")
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo(expected);
    }

    private void checkElementNotFound(String elementUuid, String userId) {
        webTestClient.get()
                .uri("/v1/directories/" + elementUuid)
                .header("userId", userId)
                .exchange()
                .expectStatus().isNotFound();
    }

    private void deleteElement(String elementUuid, String userId) {
        webTestClient.delete()
                .uri("/v1/directories/" + elementUuid)
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk();
    }
}
