/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient(timeout = "20000")
@EnableWebFlux
@SpringBootTest
@ContextConfiguration(classes = {DirectoryApplication.class, TestChannelBinderConfiguration.class})
public class DirectoryTest {
    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryTest.class);

    private static final UUID STUDY_RENAME_UUID = UUID.randomUUID();
    private static final UUID STUDY_RENAME_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();
    static final String STUDY = "STUDY";
    static final String FILTER = "FILTER";
    static final String DIRECTORY = "DIRECTORY";

    private void cleanDB() {
        directoryElementRepository.deleteAll();
    }

    @Before
    public void setup() {
    }

    @Test
    public void test() throws Exception {
        checkRootDirectoriesList("userId", "[]");

        // Insert a root directory
        String uuidNewDirectory = insertAndCheckRootDirectory("newDir", false, "userId", null);

        // Insert a sub-element of type DIRECTORY
        String uuidNewSubDirectory = insertAndCheckSubElement(null, uuidNewDirectory, "newSubDir", DIRECTORY, true, "userId", false, null);
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "userId");

        // Insert a  sub-element of type STUDY
        String uuidAddedStudy = insertAndCheckSubElement(UUID.randomUUID(), uuidNewDirectory, "newStudy", STUDY, false, "userId", false, "descr study");
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":null}" +
                ",{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newStudy\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":\"descr study\"}]", "userId");

        checkElementNameExistInDirectory(uuidNewDirectory, "newStudy", "userId", Boolean.TRUE);
        checkElementNameExistInDirectory(uuidNewDirectory, "tutu", "userId", Boolean.FALSE);

        // Delete the sub-directory newSubDir
        deleteElement(uuidNewSubDirectory, uuidNewDirectory, "userId", false, false);
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidAddedStudy + "\",\"elementName\":\"newStudy\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":\"descr study\"}]", "userId");

        // Delete the sub-element newStudy
        deleteElement(uuidAddedStudy, uuidNewDirectory, "userId", false, false);
        assertDirectoryIsEmpty(uuidNewDirectory, "userId");

        // Rename the root directory
        renameElement(uuidNewDirectory, uuidNewDirectory, "userId", "newName", true, false);

        checkRootDirectoriesList("userId", "[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":null}]");

        // Change root directory access rights public => private
        // change access of a root directory from public to private => we should receive a notification with isPrivate= false to notify all clients
        updateAccessRights(uuidNewDirectory, uuidNewDirectory, "userId", true, true, false);

        checkRootDirectoriesList("userId", "[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":null}]");

        // Add another sub-directory
        uuidNewSubDirectory = insertAndCheckSubElement(null, uuidNewDirectory, "newSubDir", DIRECTORY, true, "userId", true, "descr newSubDir");
        checkDirectoryContent(uuidNewDirectory, "[{\"elementUuid\":\"" + uuidNewSubDirectory + "\",\"elementName\":\"newSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":\"descr newSubDir\"}" + "]", "userId");

        // Add another sub-directory
        String uuidNewSubSubDirectory = insertAndCheckSubElement(null, uuidNewSubDirectory, "newSubSubDir", DIRECTORY, true, "userId", true, null);
        checkDirectoryContent(uuidNewSubDirectory, "[{\"elementUuid\":\"" + uuidNewSubSubDirectory + "\",\"elementName\":\"newSubSubDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "userId");

        // Test children number of root directory
        checkRootDirectoriesList("userId", "[{\"elementUuid\":\"" + uuidNewDirectory + "\",\"elementName\":\"newName\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"userId\",\"subdirectoriesCount\":1,\"description\":null}" + "]");

        deleteElement(uuidNewDirectory, uuidNewDirectory, "userId", true, true);
        checkRootDirectoriesList("userId", "[]");

        checkElementNotFound(uuidNewSubDirectory, "userId");
        checkElementNotFound(uuidNewSubSubDirectory, "userId");
    }

    @Test
    public void testTwoUsersTwoPublicDirectories() throws Exception {
        checkRootDirectoriesList("user1", "[]");
        checkRootDirectoriesList("user2", "[]");
        // Insert a root directory user1
        String rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", false, "user1", null);
        // Insert a root directory user2
        String rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", false, "user2", null);
        checkRootDirectoriesList("user1", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}," +
                "{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":null}]");
        checkRootDirectoriesList("user2", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}," +
                "{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":null}]");
        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, false);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false);
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateDirectories() throws Exception {
        checkRootDirectoriesList("user1", "[]");
        checkRootDirectoriesList("user2", "[]");
        // Insert a root directory user1
        String rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1", null);
        // Insert a root directory user2
        String rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", false, "user2", null);
        checkRootDirectoriesList("user1", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}," +
                "{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":null}]");
        checkRootDirectoriesList("user2", "[{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":null}]");
        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false);
    }

    @Test
    public void testTwoUsersTwoPrivateDirectories() throws Exception {
        checkRootDirectoriesList("user1", "[]");
        checkRootDirectoriesList("user2", "[]");
        // Insert a root directory user1
        String rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1", null);
        // Insert a root directory user2
        String rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", true, "user2", null);
        checkRootDirectoriesList("user1", "[{\"elementUuid\":\"" + rootDir1Uuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}]");
        checkRootDirectoriesList("user2", "[{\"elementUuid\":\"" + rootDir2Uuid + "\",\"elementName\":\"rootDir2\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":true},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":null}]");
        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, true);
    }

    @Test
    public void testTwoUsersTwoPublicStudies() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory bu the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  STUDY, false, "user1", false, null);
        // Insert a public study in the root directory bu the user1
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  STUDY, false, "user2", false, "descr study2");

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}," +
                "{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":\"descr study2\"}]", "user1");

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}," +
                "{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":\"descr study2\"}]", "user2");
        deleteElement(study1Uuid, rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateStudies() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory bu the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  STUDY, true, "user1", false, "descr study1");
        // Insert a public study in the root directory bu the user1
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  STUDY, false, "user2", false, "descr study2");

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":\"descr study1\"}," +
                "{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":\"descr study2\"}]", "user1");

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":\"descr study2\"}]", "user2");

        deleteElement(study1Uuid, rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersTwoPrivateStudies() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory bu the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  STUDY, true, "user1", false, null);
        // Insert a public study in the root directory bu the user1
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  STUDY, true, "user2", false, null);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}]", "user1");

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study2Uuid + "\",\"elementName\":\"study2\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user2\",\"subdirectoriesCount\":0,\"description\":null}]", "user2");

        deleteElement(study1Uuid, rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testRecursiveDelete() throws Exception {
        checkRootDirectoriesList("userId", "[]");
        // Insert a private root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", true, "userId", null);
        // Insert a public study in the root directory bu the userId
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  STUDY, true, "userId", true, "descr study1");
        // Insert a public study in the root directory bu the userId
        String study2Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study2",  STUDY, true, "userId", true, null);
        // Insert a subDirectory
        String subDirUuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "subDir",  DIRECTORY, true, "userId", true, null);
        // Insert a public study in the root directory bu the userId
        String subDirStudyUuid = insertAndCheckSubElement(UUID.randomUUID(),  subDirUuid, "study3",  STUDY, true, "userId", true, "descr study3");

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, true);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(study1Uuid, "userId");
        checkElementNotFound(study2Uuid, "userId");
        checkElementNotFound(subDirUuid, "userId");
        checkElementNotFound(subDirStudyUuid, "userId");
    }

    @Test
    public void testRenameStudy() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory by the user1
        String study1Uuid = insertAndCheckSubElement(STUDY_RENAME_UUID,  rootDirUuid, "study1",  STUDY, false, "user1", false, null);

        renameElement(study1Uuid, rootDirUuid, "user1", "newName1", false, false);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"newName1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "userId");
    }

    @Test
    public void testRenameStudyForbiddenFail() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory by the user1
        String study1Uuid = insertAndCheckSubElement(STUDY_RENAME_FORBIDDEN_UUID,  rootDirUuid, "study1",  STUDY, false, "user1", false, null);

        //the name should not change
        renameElementExpectFail(study1Uuid, "user2", "newName1", 403);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "userId");
    }

    @Test
    public void testRenameDirectoryNotAllowed() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);

        //the name should not change
        renameElementExpectFail(rootDirUuid, "user1", "newName1", 403);
        checkRootDirectoriesList("Doe", "[{\"elementUuid\":\"" + rootDirUuid + "\",\"elementName\":\"rootDir1\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\":false},\"owner\":\"Doe\",\"subdirectoriesCount\":0,\"description\":null}" + "]");
    }

    @Test
    public void testUpdateStudyAccessRight() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory by the user1
        String study1Uuid = insertAndCheckSubElement(STUDY_UPDATE_ACCESS_RIGHT_UUID,  rootDirUuid, "study1",  STUDY, false, "user1", false, null);

        //set study to private
        updateAccessRights(study1Uuid, rootDirUuid, "user1", true, false, false);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":true},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "user1");

        //set study back to public
        updateAccessRights(study1Uuid, rootDirUuid, "user1", false, false, false);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "user1");
    }

    @Test
    public void testUpdateStudyAccessRightForbiddenFail() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory by the user1
        String study1Uuid = insertAndCheckSubElement(STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID,  rootDirUuid, "study1",  STUDY, false, "user1", false, "descr study1");

        //the access rights should not change
        updateStudyAccessRightFail(study1Uuid, "user2", true, 403);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":\"descr study1\"}" + "]", "userId");
    }

    @Test
    public void testUpdateStudyAccessRightWithWrongUser() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory by the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  STUDY, false, "user1", false, null);

        //try to update the study1 (of user1) with the user2 -> the access rights should not change because it's not allowed
        updateStudyAccessRightFail(study1Uuid, "user2", true, 403);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "userId");
    }

    @Test
    public void testDeleteStudyWithWrongUser() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe", null);
        // Insert a public study in the root directory by the user1
        String study1Uuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  STUDY, false, "user1", false, null);

        //try to delete the study1 (of user1) with the user2 -> the should still be here
        deleteElementFail(study1Uuid, "user2", 403);
        checkDirectoryContent(rootDirUuid, "[{\"elementUuid\":\"" + study1Uuid + "\",\"elementName\":\"study1\",\"type\":\"STUDY\",\"accessRights\":{\"private\":false},\"owner\":\"user1\",\"subdirectoriesCount\":0,\"description\":null}" + "]", "userId");
    }

    @Test
    public void testEmitDirectoryChangedNotification() throws Exception {
        checkRootDirectoriesList("Doe", "[]");
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        String contingencyListUuid = insertAndCheckSubElement(UUID.randomUUID(),  rootDirUuid, "study1",  CONTINGENCY_LIST, false, "Doe", false);

        webTestClient.put()
                .uri("/v1/directories/" + contingencyListUuid + "/notify-parent")
                .header("userId", "Doe")
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(rootDirUuid, headers.get(DirectoryService.HEADER_DIRECTORY_UUID).toString());
        assertEquals(false, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
    }

    @SneakyThrows
    @Test
    public void testGetElement() {
        // Insert a public root directory user1
        String rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "user1", null);

        // Insert a public filter in the root directory by the user1
        String filterUuid = insertAndCheckSubElement(FILTER_UUID, rootDirUuid, "Filter", FILTER, false, "user1", false, null);
        String scriptUUID = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "Script", FILTER, false, "user1", false, null);
        var res = getElements(List.of(scriptUUID, filterUuid, UUID.randomUUID().toString()), "user1");
        assertEquals(2, res.size());
        var filter1 = res.get(0).getElementName().equals("Filter") ? res.get(0) : res.get(1);
        assertEquals(filterUuid, filter1.getElementUuid().toString());
        assertEquals("Filter", filter1.getElementName());
        assertEquals(filterUuid, filter1.getElementUuid().toString());
        assertEquals(FILTER, filter1.getType());

        var script = res.get(0).getElementName().equals("Filter") ? res.get(1) : res.get(0);
        assertEquals(scriptUUID, script.getElementUuid().toString());
        assertEquals("Script", script.getElementName());
        assertEquals(FILTER, script.getType());
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

    private String insertAndCheckRootDirectory(String rootDirectoryName, boolean isPrivate, String userId, String description) throws JsonProcessingException {
        EntityExchangeResult result = webTestClient.post()
                .uri("/v1/root-directories")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId, description)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidNewDirectory = jsonTree.get("elementUuid").asText();
        assertEquals(rootDirectoryName, jsonTree.get("elementName").asText());
        if (description != null) {
            assertEquals(description, jsonTree.get("description").asText());
        }
        assertEquals(isPrivate, jsonTree.get("accessRights").get("private").asBoolean());

        assertElementIsProperlyInserted(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId, description);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(uuidNewDirectory, headers.get(DirectoryService.HEADER_DIRECTORY_UUID).toString());
        assertEquals(true, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));

        return uuidNewDirectory;
    }

    private List<ElementAttributes> getElements(List<String> elementUuids, String userId) throws JsonProcessingException {
        var ids = new StringJoiner("&id=");
        elementUuids.forEach(ids::add);
        // Insert a sub-element of type DIRECTORY
        EntityExchangeResult result = webTestClient.get()
            .uri("/v1/directories/elements?id=" + ids)
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .returnResult();
        return objectMapper.readValue(result.getResponseBody().toString(), new TypeReference<>() {
        });
    }

    private String insertAndCheckSubElement(UUID elementUuid, String parentDirectoryUUid, String subElementName, String type, boolean isPrivate, String userId, boolean isParentPrivate, String subElementDescription) throws JsonProcessingException {
        // Insert a sub-element of type DIRECTORY
        EntityExchangeResult result = webTestClient.post()
                .uri("/v1/directories/" + UUID.fromString(parentDirectoryUUid))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(new ElementAttributes(elementUuid, subElementName, type, new AccessRightsAttributes(isPrivate), userId, 0L, subElementDescription)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .returnResult();

        JsonNode jsonTree = objectMapper.readTree(result.getResponseBody().toString());
        String uuidSubElement = jsonTree.get("elementUuid").asText();
        assertEquals(subElementName, jsonTree.get("elementName").asText());
        if (subElementDescription != null) {
            assertEquals(subElementDescription, jsonTree.get("description").asText());
        }
        assertEquals(isPrivate, jsonTree.get("accessRights").get("private").asBoolean());

        // assert that the broker message has been sent an element creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(parentDirectoryUUid, headers.get(DirectoryService.HEADER_DIRECTORY_UUID).toString());
        assertEquals(false, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isParentPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));

        assertElementIsProperlyInserted(uuidSubElement, subElementName, type, isPrivate, userId, subElementDescription);
        return uuidSubElement;
    }

    private void renameElement(String elementUuidToRename, String elementUuidHeader, String userId, String newName, boolean isRoot, boolean isPrivate) {
        webTestClient.put().uri("/v1/directories/" + elementUuidToRename + "/rename/" + newName)
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent a notif for rename
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(DirectoryService.HEADER_DIRECTORY_UUID).toString());
        assertEquals(isRoot, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
    }

    private void renameElementExpectFail(String elementUuidToRename, String userId, String newName, int httpCodeExpected) {
        if (httpCodeExpected == 403) {
            webTestClient.put().uri("/v1/directories/" + elementUuidToRename + "/rename/" + newName)
                    .header("userId", userId)
                    .exchange()
                    .expectStatus().isForbidden();
        } else if (httpCodeExpected == 404) {
            webTestClient.put().uri("/v1/directories/" + elementUuidToRename + "/rename/" + newName)
                    .header("userId", userId)
                    .exchange()
                    .expectStatus().isNotFound();
        } else {
            fail("unexpected case");
        }
    }

    private void updateAccessRights(String elementUuidToUpdate, String elementUuidHeader, String userId, boolean newIsPrivate, boolean isRoot, boolean isPrivate) throws JsonProcessingException {
        webTestClient.put().uri("/v1/directories/" + elementUuidToUpdate + "/rights")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(newIsPrivate))
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent a notif for rename
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(DirectoryService.HEADER_DIRECTORY_UUID).toString());
        assertEquals(isRoot, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
    }

    private void updateStudyAccessRightFail(String elementUuidToUpdate, String userId, boolean newisPrivate, int httpCodeExpected) throws JsonProcessingException {
        if (httpCodeExpected == 403) {
            webTestClient.put().uri("/v1/directories/" + elementUuidToUpdate + "/rights")
                    .header("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(newisPrivate))
                    .exchange()
                    .expectStatus().isForbidden();
        } else if (httpCodeExpected == 404) {
            webTestClient.put().uri("/v1/directories/" + elementUuidToUpdate + "/rights")
                    .header("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(newisPrivate))
                    .exchange()
                    .expectStatus().isNotFound();
        } else {
            fail("unexpected case");
        }
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

    private void assertElementIsProperlyInserted(String elementUuid, String elementName, String type, boolean isPrivate, String userId, String description) {
        webTestClient.get()
                .uri("/v1/directories/" + elementUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .isEqualTo("{\"elementUuid\":\"" + elementUuid + "\",\"elementName\":\"" + elementName + "\",\"type\":\"" + type.toString() + "\",\"accessRights\":{\"private\":" + isPrivate + "},\"owner\":\"" + userId + "\",\"subdirectoriesCount\":0,\"description\":" + (description != null ? "\"" + description + "\"" : "null") + "}");
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

    private void checkElementNameExistInDirectory(String parentDirectoryUuid, String elementName, String userId, Boolean expected) {
        webTestClient.get()
            .uri("/v1/directories/" + parentDirectoryUuid + "/" + elementName + "/exists")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(Boolean.class)
            .isEqualTo(expected);
    }

    private void deleteElement(String elementUuidToBeDeleted, String elementUuidHeader, String userId, boolean isRoot, boolean isPrivate) {
        webTestClient.delete()
                .uri("/v1/directories/" + elementUuidToBeDeleted)
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk();

        // assert that the broker message has been sent a delete
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(DirectoryService.HEADER_DIRECTORY_UUID).toString());
        assertEquals(isRoot, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
        assertEquals(isRoot ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
    }

    private void deleteElementFail(String elementUuidToBeDeleted, String userId, int httpCodeExpected) {
        if (httpCodeExpected == 403) {
            webTestClient.delete()
                    .uri("/v1/directories/" + elementUuidToBeDeleted)
                    .header("userId", userId)
                    .exchange()
                    .expectStatus().isForbidden();
        } else {
            fail("unexpected case");
        }
    }

    @After
    public void tearDown() {
        cleanDB();
        assertNull("Should not be any messages", output.receive(1000));
    }
}
