/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jparams.verifier.tostring.ToStringVerifier;
import lombok.SneakyThrows;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.services.StudyService;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.directory.server.DirectoryException.Type.UNKNOWN_NOTIFICATION;
import static org.gridsuite.directory.server.DirectoryService.CONTINGENCY_LIST;
import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.DirectoryService.FILTER;
import static org.gridsuite.directory.server.DirectoryService.STUDY;
import static org.gridsuite.directory.server.NotificationService.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextConfiguration(classes = {DirectoryApplication.class, TestChannelBinderConfiguration.class})
public class DirectoryTest {

    private static final UUID STUDY_RENAME_UUID = UUID.randomUUID();
    private static final UUID STUDY_RENAME_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_UUID = UUID.randomUUID();

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudyService studyService;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    private OutputDestination output;

    private void cleanDB() {
        directoryElementRepository.deleteAll();
    }

    @Before
    public void setup() {
        cleanDB();
    }

    @Test
    public void test() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory
        UUID uuidNewDirectory = insertAndCheckRootDirectory("newDir", false, "userId");

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, true, "userId");
        insertAndCheckSubElement(uuidNewDirectory, false, subDirAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subDirAttributes));

        // Insert a  sub-element of type STUDY
        ElementAttributes subEltAttributes = toElementAttributes(UUID.randomUUID(), "newStudy", STUDY, null, "userId", "descr study");
        insertAndCheckSubElement(uuidNewDirectory, false, subEltAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subDirAttributes, subEltAttributes));

        checkElementNameExistInDirectory(uuidNewDirectory, "newStudy", STUDY, HttpStatus.OK);
        checkElementNameExistInDirectory(uuidNewDirectory, "tutu", STUDY, HttpStatus.NO_CONTENT);

        // Delete the sub-directory newSubDir
        deleteElement(subDirAttributes.getElementUuid(), uuidNewDirectory, "userId", false, false, false, 0);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subEltAttributes));

        // Delete the sub-element newStudy
        deleteElement(subEltAttributes.getElementUuid(), uuidNewDirectory, "userId", false, false, true, 0);
        assertDirectoryIsEmpty(uuidNewDirectory, "userId");

        // Rename the root directory
        renameElement(uuidNewDirectory, uuidNewDirectory, "userId", "newName", true, false);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, false, "userId")));

        // Change root directory access rights public => private
        // change access of a root directory from public to private => we should receive a notification with isPrivate= false to notify all clients
        updateAccessRights(uuidNewDirectory, uuidNewDirectory, "userId", true, true, false);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, true, "userId")));

        // Add another sub-directory
        ElementAttributes newSubDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, true, "userId", "descr newSubDir");
        insertAndCheckSubElement(uuidNewDirectory, true, newSubDirAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(newSubDirAttributes));

        // Add another sub-directory
        ElementAttributes newSubSubDirAttributes = toElementAttributes(null, "newSubSubDir", DIRECTORY, true, "userId");
        insertAndCheckSubElement(newSubDirAttributes.getElementUuid(), true, newSubSubDirAttributes);
        checkDirectoryContent(newSubDirAttributes.getElementUuid(), "userId", List.of(newSubSubDirAttributes));

        // Test children number of root directory
        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, null)));

        deleteElement(uuidNewDirectory, uuidNewDirectory, "userId", true, true, false, 0);
        checkRootDirectoriesList("userId", List.of());

        checkElementNotFound(newSubDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(newSubSubDirAttributes.getElementUuid(), "userId");
    }

    @Test
    public void testGetPathOfStudy() throws Exception {
     // Insert a public root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, null, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, null, "Doe");
        insertAndCheckSubElement(directory1UUID, false, directory2Attributes);

        // Insert a study in the directory2
        UUID study1UUID = UUID.randomUUID();
        ElementAttributes study1Attributes = toElementAttributes(study1UUID, "study1", STUDY, null, "Doe");
        insertAndCheckSubElement(directory2UUID, false, study1Attributes);

        List<ElementAttributes> path = getPath(study1UUID, "Doe");

        //Check if all element's parents are retrieved in the right order
        assertEquals(
                path.stream()
                    .map(parent -> parent.getElementUuid())
                    .collect(Collectors.toList()),
                Arrays.asList(study1UUID, directory2UUID, directory1UUID, rootDirUuid)
        );
    }

    @Test
    public void testGetPathOfFilter() throws Exception {
     // Insert a public root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, null, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, null, "Doe");
        insertAndCheckSubElement(directory1UUID, false, directory2Attributes);

        // Insert a filter in the directory2
        UUID filter1UUID = UUID.randomUUID();
        ElementAttributes study1Attributes = toElementAttributes(filter1UUID, "filter1", FILTER, null, "Doe");
        insertAndCheckSubElement(directory2UUID, false, study1Attributes);

        List<ElementAttributes> path = getPath(filter1UUID, "Doe");

        //Check if all element's parents are retrieved in the right order
        assertEquals(
                path.stream()
                    .map(parent -> parent.getElementUuid())
                    .collect(Collectors.toList()),
                Arrays.asList(filter1UUID, directory2UUID, directory1UUID, rootDirUuid)
        );
    }

    @Test
    public void testGetPathOfNotAllowed() throws Exception {
     // Insert a public root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, directory1Attributes);

        // Insert a private subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, true, "Doe");
        insertAndCheckSubElement(directory1UUID, false, directory2Attributes);

        // Insert a filter in the directory2
        UUID filter1UUID = UUID.randomUUID();
        ElementAttributes study1Attributes = toElementAttributes(filter1UUID, "filter1", FILTER, null, "Doe");
        insertAndCheckSubElement(directory2UUID, true, study1Attributes);

        // Trying to get path of forbidden element
        mockMvc.perform(get("/v1/elements/" + filter1UUID + "/path")
                   .header("userId", "Unallowed User"))
            .andExpect(status().isForbidden());

        // Trying to get path of forbidden element
        mockMvc.perform(get("/v1/elements/" + directory2UUID + "/path")
                   .header("userId", "Unallowed User"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testGetPathOfRootDir() throws Exception {
     // Insert a public root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        List<ElementAttributes> path = getPath(rootDirUuid, "Doe");

        assertEquals(
                path.stream()
                    .map(parent -> parent.getElementUuid())
                    .collect(Collectors.toList()),
                Arrays.asList(rootDirUuid)
        );
    }

    @Test
    public void testGetPathOfNotFound() throws Exception {
        UUID unknownElementUuid = UUID.randomUUID();

        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        mockMvc.perform(get("/v1/elements/" + unknownElementUuid + "/path")
                .header("userId", "user1")).andExpect(status().isNotFound());
    }

    @Test
    public void testTwoUsersTwoPublicDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());

        // Insert a root directory user1
        UUID rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", false, "user1");
        // Insert a root directory user2
        UUID rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", false, "user2");

        checkRootDirectoriesList("user1",
            List.of(
                toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, false, "user1"),
                toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, false, "user2")
            )
        );

        checkRootDirectoriesList("user2",
            List.of(
                toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, false, "user1"),
                toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, false, "user2")
            )
        );

        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, false, false, 0);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false, false, 0);
    }

    @Test
    public void testMoveElement() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        // Insert another public root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, directory20Attributes);

        // Insert a filter in the last subdirectory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(directory21UUID, false, filterAttributes);

        mockMvc.perform(put("/v1/elements/" + filterUuid + "?newDirectory=" + rootDir10Uuid)
                .header("userId", "Doe"))
                .andExpect(status().isOk());

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a root directory creation request message
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(directory21UUID, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        checkElementNameExistInDirectory(rootDir10Uuid, "filter", FILTER, HttpStatus.OK);
    }

    @Test
    public void testMoveElementFromDifferentAccessRightsFolder() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        // Insert another public root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21PrivateUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21PrivateUUID, "directory20", DIRECTORY, true, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, directory20Attributes);

        // Insert a filter in the last subdirectory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(directory21PrivateUUID, true, filterAttributes);

        mockMvc.perform(put("/v1/elements/" + filterUuid + "?newDirectory=" + rootDir10Uuid)
                .header("userId", "Doe"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testMoveUnallowedElement() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", true, "Unallowed User");

        // Insert another public root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", true, "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21PrivateUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21PrivateUUID, "directory20", DIRECTORY, true, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, true, directory20Attributes);

        // Insert a filter in the last subdirectory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(directory21PrivateUUID, true, filterAttributes);

        mockMvc.perform(put("/v1/elements/" + filterUuid + "?newDirectory=" + rootDir10Uuid)
                        .header("userId", "Unallowed User"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testMoveElementNotFound() throws Exception {
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, filterAttributes);

        UUID unknownUuid = UUID.randomUUID();

        mockMvc.perform(put("/v1/elements/" + unknownUuid + "?newDirectory=" + rootDir20Uuid)
                        .header("userId", "Doe"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/v1/elements/" + filterUuid + "?newDirectory=" + unknownUuid)
                        .header("userId", "Doe"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testMoveElementWithAlreadyExistingNameAndTypeInDestination() throws Exception {
        // Insert public root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        // Insert a filter in this directory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, filterAttributes);

        // Insert a subDirectory20 in the root20 directory
        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, directory20Attributes);

        // Insert a filter in the last subdirectory with the same name and type as the 1st one
        UUID filterwithSameNameAndTypeUuid = UUID.randomUUID();
        ElementAttributes filterwithSameNameAndTypeAttributes = toElementAttributes(filterwithSameNameAndTypeUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(directory21UUID, false, filterwithSameNameAndTypeAttributes);

        mockMvc.perform(put("/v1/elements/" + filterwithSameNameAndTypeUuid + "?newDirectory=" + rootDir20Uuid)
                        .header("userId", "Doe"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testMoveElementToNotDirectory() throws Exception {
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID filter1Uuid = UUID.randomUUID();
        ElementAttributes filter1Attributes = toElementAttributes(filter1Uuid, "filter1", FILTER, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, filter1Attributes);

        UUID filter2Uuid = UUID.randomUUID();
        ElementAttributes filter2Attributes = toElementAttributes(filter2Uuid, "filter2", FILTER, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, filter2Attributes);

        mockMvc.perform(put("/v1/elements/" + filter1Uuid + "?newDirectory=" + filter2Uuid)
                        .header("userId", "Doe"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testMoveDirectory() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, directory20Attributes);

        mockMvc.perform(put("/v1/elements/" + directory21UUID + "?newDirectory=" + rootDir10Uuid)
                        .header("userId", "Doe"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testMoveStudy() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID study21UUID = UUID.randomUUID();
        ElementAttributes study21Attributes = toElementAttributes(study21UUID, "Study21", STUDY, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, study21Attributes);

        mockMvc.perform(put("/v1/elements/" + study21UUID + "?newDirectory=" + rootDir10Uuid)
                        .header("userId", "Doe"))
                .andExpect(status().isOk());

        // assert that the broker message has been sent a update notification on directory
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir20Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    @Test
    public void testMoveRootDirectory() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, directory20Attributes);

        mockMvc.perform(put("/v1/elements/" + rootDir10Uuid + "?newDirectory=" + rootDir20Uuid)
                        .header("userId", "Doe"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());
        // Insert a root directory user1
        UUID rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1");
        // Insert a root directory user2
        UUID rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", false, "user2");

        checkRootDirectoriesList("user1",
            List.of(
                toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, true, "user1"),
                toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, false, "user2")
            )
        );

        checkRootDirectoriesList("user2", List.of(toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, false, "user2")));

        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true, false, 0);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false, false, 0);
    }

    @Test
    public void testTwoUsersTwoPrivateDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());
        // Insert a root directory user1
        UUID rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1");
        // Insert a root directory user2
        UUID rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", true, "user2");

        checkRootDirectoriesList("user1", List.of(toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, true, "user1")));

        checkRootDirectoriesList("user2", List.of(toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, true, "user2")));

        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true, false, 0);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, true, false, 0);
    }

    @Test
    public void testTwoUsersTwoStudiesInPublicDirectory() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        // Insert a study in the root directory by the user1
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, null, "user2", "descr study2");
        insertAndCheckSubElement(rootDirUuid, false, study2Attributes);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1", List.of(study1Attributes, study2Attributes));

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(study1Attributes, study2Attributes));
        deleteElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", false, false, true, 0);
        checkElementNotFound(study1Attributes.getElementUuid(), "user1");

        deleteElement(study2Attributes.getElementUuid(), rootDirUuid, "user2", false, false, true, 0);
        checkElementNotFound(study2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false, false, 0);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersElementsWithSameName() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        // Insert a study with the same name in the root directory by the user1 and expect a 403
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, null, "user1");
        insertExpectFail(rootDirUuid, study2Attributes);

        // Insert a study in the root directory by the user1
        ElementAttributes study3Attributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study3Attributes);

        // Insert a study with the same name in the root directory by the user1 and expect a 403
        ElementAttributes study4Attributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, null, "user1");
        insertExpectFail(rootDirUuid, study4Attributes);

        // Insert a filter with the same name in the root directory by the user1 and expect ok since it's not the same type
        ElementAttributes filterAttributes = toElementAttributes(UUID.randomUUID(), "study3", FILTER, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, filterAttributes);

        // Insert a study with the same name in the root directory by the user2 should not work even if the study by user 1 is private
        ElementAttributes study5Attributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, null, "user3");
        insertExpectFail(rootDirUuid, study5Attributes);
    }

    @Test
    public void testTwoUsersTwoStudies() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a public study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        // Insert a public study in the root directory by the user1
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, null, "user2");
        insertAndCheckSubElement(rootDirUuid, false, study2Attributes);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1", List.of(study1Attributes, study2Attributes));

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(study1Attributes, study2Attributes));

        deleteElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", false, false, true, 0);
        checkElementNotFound(study1Attributes.getElementUuid(), "user1");

        deleteElement(study2Attributes.getElementUuid(), rootDirUuid, "user2", false, false, true, 0);
        checkElementNotFound(study2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false, false, 0);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testRecursiveDelete() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a private root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", true, "userId");

        // Insert a public study in the root directory by the userId
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, null, "userId", "descr study1");
        insertAndCheckSubElement(rootDirUuid, true, study1Attributes);

        // Insert a public study in the root directory by the userId;
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, null, "userId");
        insertAndCheckSubElement(rootDirUuid, true, study2Attributes);

        // Insert a subDirectory
        ElementAttributes subDirAttributes = toElementAttributes(UUID.randomUUID(), "subDir", DIRECTORY, true, "userId");
        insertAndCheckSubElement(rootDirUuid, true, subDirAttributes);

        // Insert a study in the root directory by the userId
        ElementAttributes subDirStudyAttributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, null, "userId", "descr study3");

        insertAndCheckSubElement(subDirAttributes.getElementUuid(), true, subDirStudyAttributes);

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, true, false, 3);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(study1Attributes.getElementUuid(), "userId");
        checkElementNotFound(study2Attributes.getElementUuid(), "userId");
        checkElementNotFound(subDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(subDirStudyAttributes.getElementUuid(), "userId");
    }

    @Test
    public void testRenameStudy() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_UUID, "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        renameElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", "newName1", false, false);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Attributes.getElementUuid(), "newName1", STUDY, null, "user1")));
    }

    @Test
    public void testRenameStudyToSameName() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_UUID, "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        // Updating to same name should not send error
        renameElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", "study1", false, false);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, null, "user1")));
    }

    @Test
    public void testRenameStudyForbiddenFail() throws Exception {
        checkRootDirectoriesList("user1", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", true, "user1");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_FORBIDDEN_UUID, "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, true, study1Attributes);

        //the name should not change
        renameElementExpectFail(study1Attributes.getElementUuid(), "user2", "newName1", 403);
        checkDirectoryContent(rootDirUuid, "user2", List.of());
    }

    @Test
    public void testRenameElementWithSameNameAndTypeInSameDirectory() throws Exception {
        // Insert a public root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a public study1 in the root directory by Doe
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, null, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        // Insert a public study2 in the root directory by Doe;
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, null, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, study2Attributes);

        // Renaming file to an already existing name should fail
        renameElementExpectFail(study1Attributes.getElementUuid(), "Doe", "study2", 403);
    }

    @Test
    public void testRenameDirectoryNotAllowed() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", true, "Doe");

        //the name should not change
        renameElementExpectFail(rootDirUuid, "user1", "newName1", 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe")));
    }

    @Test
    public void testUpdateStudyAccessRight() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_UPDATE_ACCESS_RIGHT_UUID, "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        //set study to private -> not updatable
        updateAccessRightFail(study1Attributes.getElementUuid(), "user1", true, 403);
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, null, "user1")));
    }

    @Test
    public void testUpdateDirectoryAccessRight() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        //set directory to private
        updateAccessRights(rootDirUuid, rootDirUuid, "Doe", true, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe")));

        //reset it to public
        updateAccessRights(rootDirUuid, rootDirUuid, "Doe", false, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, false, "Doe")));

        updateAccessRightFail(rootDirUuid, "User1", true, 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, false, "Doe")));
    }

    @SneakyThrows
    @Test
    public void testEmitDirectoryChangedNotification() {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a contingency list in the root directory by the user1
        ElementAttributes contingencyListAttributes = toElementAttributes(UUID.randomUUID(), "CL1", CONTINGENCY_LIST, null, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, contingencyListAttributes);

        mockMvc.perform(post(String.format("/v1/elements/%s/notification?type=update_directory", contingencyListAttributes.getElementUuid()))
                        .header("userId", "Doe"))
                .andExpect(status().isOk());

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDirUuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        // Test unknown type notification
        mockMvc.perform(post(String.format("/v1/elements/%s/notification?type=bad_type", contingencyListAttributes.getElementUuid()))
                        .header("userId", "Doe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new IsEqual<>(objectMapper.writeValueAsString(UNKNOWN_NOTIFICATION))));
    }

    @SneakyThrows
    @Test
    public void testGetElement() {
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "user1");

        // Insert a contingency list in the root directory by the user1
        ElementAttributes contingencyAttributes = toElementAttributes(CONTINGENCY_LIST_UUID, "Contingency", CONTINGENCY_LIST, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, contingencyAttributes);

        // Insert a filter in the root directory by the user1
        ElementAttributes filterAttributes = toElementAttributes(FILTER_UUID, "Filter", FILTER, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, filterAttributes);

        // Insert a script in the root directory by the user1
        ElementAttributes scriptAttributes = toElementAttributes(UUID.randomUUID(), "Script", FILTER, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, scriptAttributes);

        var res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), UUID.randomUUID()), "user1", true, 404);
        assertTrue(res.isEmpty());

        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), UUID.randomUUID()), "user1", false, 200);
        assertEquals(2, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(contingencyAttributes, filterAttributes, scriptAttributes)));

        renameElement(contingencyAttributes.getElementUuid(), rootDirUuid, "user1", "newContingency", false, false);
        renameElement(filterAttributes.getElementUuid(), rootDirUuid, "user1", "newFilter", false, false);
        renameElement(scriptAttributes.getElementUuid(), rootDirUuid, "user1", "newScript", false, false);
        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper,
            List.of(
                toElementAttributes(contingencyAttributes.getElementUuid(), "newContingency", CONTINGENCY_LIST, null, "user1"),
                toElementAttributes(filterAttributes.getElementUuid(), "newFilter", FILTER, null, "user1"),
                toElementAttributes(scriptAttributes.getElementUuid(), "newScript", FILTER, null, "user1")
            ))
        );
    }

    @SneakyThrows
    @Test
    public void testElementAccessControl() {
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "user1");

        // Insert a contingency list in the root directory by the user1
        ElementAttributes contingencyAttributes = toElementAttributes(CONTINGENCY_LIST_UUID, "Contingency", CONTINGENCY_LIST, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, contingencyAttributes);

        // Insert a filter in the root directory by the user1
        ElementAttributes filterAttributes = toElementAttributes(FILTER_UUID, "Filter", FILTER, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, filterAttributes);

        // Insert a script in the root directory by the user1
        ElementAttributes scriptAttributes = toElementAttributes(UUID.randomUUID(), "Script", FILTER, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, scriptAttributes);

        var res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(contingencyAttributes, filterAttributes, scriptAttributes)));

        renameElement(contingencyAttributes.getElementUuid(), rootDirUuid, "user1", "newContingency", false, false);
        renameElement(filterAttributes.getElementUuid(), rootDirUuid, "user1", "newFilter", false, false);
        renameElement(scriptAttributes.getElementUuid(), rootDirUuid, "user1", "newScript", false, false);
        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper,
            List.of(
                toElementAttributes(contingencyAttributes.getElementUuid(), "newContingency", CONTINGENCY_LIST, null, "user1"),
                toElementAttributes(filterAttributes.getElementUuid(), "newFilter", FILTER, null, "user1"),
                toElementAttributes(scriptAttributes.getElementUuid(), "newScript", FILTER, null, "user1")
            ))
        );
    }

    @Test
    public void testRootDirectoryExists() throws Exception {
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", false, "user1");

        UUID directoryUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directoryUUID, "directoryToFind", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDirUuid, false, directory20Attributes);

        checkRootDirectoryExists("rootDirToFind");
        checkRootDirectoryNotExists("directoryToFind");
        checkRootDirectoryNotExists("notExistingRootDir");

    }

    @Test
    public void testCreateDirectoryWithEmptyName() throws Exception {
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", false, "user1");

        // Insert a directory with empty name in the root directory and expect a 403
        ElementAttributes directoryWithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", DIRECTORY, null, "user1");
        insertExpectFail(rootDirUuid, directoryWithoutNameAttributes);
        String requestBody = objectMapper.writeValueAsString(new RootDirectoryAttributes("", new AccessRightsAttributes(false), "userId", null));

        // Insert a public root directory user1 with empty name and expect 403
        mockMvc.perform(post(String.format("/v1/root-directories"))
                        .header("userId", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testCreateElementWithEmptyName() throws Exception {
     // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", false, "user1");

        // Insert a study with empty name in the root directory and expect a 403
        ElementAttributes studyWithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", STUDY, null, "user1");
        insertExpectFail(rootDirUuid, studyWithoutNameAttributes);

     // Insert a filter with empty name in the root directory and expect a 403
        ElementAttributes filterWithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", FILTER, null, "user1");
        insertExpectFail(rootDirUuid, filterWithoutNameAttributes);
    }

    private List<ElementAttributes> getPath(UUID elementUuid, String userId) throws Exception {
        String response = mockMvc.perform(get("/v1/elements/" + elementUuid + "/path")
                        .header("userId", userId))
                .andExpect(status().isOk())
                .andExpectAll(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, new TypeReference<>() {
        });
    }

    private void checkRootDirectoriesList(String userId, List<ElementAttributes> list) throws Exception {
        String response = mockMvc.perform(get("/v1/root-directories").header("userId", userId))
                             .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                             .andReturn()
                            .getResponse()
                            .getContentAsString();

        List<ElementAttributes> elementAttributes = objectMapper.readValue(response, new TypeReference<>() {
        });
        assertTrue(new MatcherJson<>(objectMapper, list).matchesSafely(elementAttributes));
    }

    private UUID insertAndCheckRootDirectory(String rootDirectoryName, boolean isPrivate, String userId) throws Exception {
        String response = mockMvc.perform(post("/v1/root-directories")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId, null))))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();

        assertElementIsProperlyInserted(toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId));

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        return uuidNewDirectory;
    }

    private List<ElementAttributes> getElements(List<UUID> elementUuids, String userId, boolean strictMode, int httpCodeExpected) throws Exception {
        var ids = elementUuids.stream().map(UUID::toString).collect(Collectors.joining(","));

        // Insert a sub-element of type DIRECTORY
        if (httpCodeExpected == 200) {
            MvcResult result = mockMvc.perform(get("/v1/elements?strictMode=" + (strictMode ? "true" : "false") + "&ids=" + ids)
                    .header("userId", userId))
                    .andExpectAll(status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<ElementAttributes>>() {
            });
        } else if (httpCodeExpected == 404) {
            mockMvc.perform(get("/v1/elements?strictMode=" + (strictMode ? "true" : "false") + "&ids=" + ids)
                            .header("userId", userId))
                    .andExpectAll(status().isNotFound());
        } else {
            fail("unexpected case");
        }
        return Collections.emptyList();
    }

    private void insertAndCheckSubElement(UUID parentDirectoryUUid, boolean isParentPrivate, ElementAttributes subElementAttributes) throws Exception {
        // Insert a sub-element of type DIRECTORY
        MvcResult response = mockMvc.perform(post("/v1/directories/" + parentDirectoryUUid + "/elements")
                        .header("userId", subElementAttributes.getOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subElementAttributes)))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response).getResponse().getContentAsString(), ElementAttributes.class)
                .getElementUuid();

        subElementAttributes.setElementUuid(uuidNewDirectory);

        // assert that the broker message has been sent an element creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(subElementAttributes.getOwner(), headers.get(HEADER_USER_ID));
        assertEquals(parentDirectoryUUid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isParentPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        assertElementIsProperlyInserted(subElementAttributes);
    }

    private void insertExpectFail(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) throws Exception {
        // Insert a sub-element of type DIRECTORY and expect 403 forbidden
        mockMvc.perform(post("/v1/directories/" + parentDirectoryUUid + "/elements")
                .header("userId", subElementAttributes.getOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subElementAttributes)))
            .andExpect(status().isForbidden());
    }

    private void renameElement(UUID elementUuidToRename, UUID elementUuidHeader, String userId, String newName, boolean isRoot, boolean isPrivate) throws Exception {
        mockMvc.perform(put(String.format("/v1/elements/%s", elementUuidToRename))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ElementAttributes.builder().elementName(newName).build())))
            .andExpect(status().isOk());

        // assert that the broker message has been sent a notif for rename
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(isRoot, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    private void renameElementExpectFail(UUID elementUuidToRename, String userId, String newName, int httpCodeExpected) throws Exception {
        if (httpCodeExpected == 403) {
            mockMvc.perform(put(String.format("/v1/elements/%s", elementUuidToRename))
                    .header("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ElementAttributes.builder().elementName(newName).build())))
                .andExpect(status().isForbidden());
        } else if (httpCodeExpected == 404) {
            mockMvc.perform(put(String.format("/v1/elements/%s", elementUuidToRename))
                            .header("userId", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ElementAttributes.builder().elementName(newName).build())))
                    .andExpect(status().isNotFound());
        } else {
            fail("unexpected case");
        }
    }

    private void updateAccessRights(UUID elementUuidToUpdate, UUID elementUuidHeader, String userId, boolean newIsPrivate, boolean isRoot, boolean isPrivate) throws Exception {
        mockMvc.perform(put(String.format("/v1/elements/%s", elementUuidToUpdate))
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ElementAttributes.builder().accessRights(new AccessRightsAttributes(newIsPrivate)).build())))
                .andExpect(status().isOk());

        // assert that the broker message has been sent a notif for rename
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(isRoot, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    private void updateAccessRightFail(UUID elementUuidToUpdate, String userId, boolean newIsPrivate, int httpCodeExpected) throws Exception {
        if (httpCodeExpected == 403) {
            mockMvc.perform(put(String.format("/v1/elements/%s", elementUuidToUpdate))
                            .header("userId", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ElementAttributes.builder().accessRights(new AccessRightsAttributes(newIsPrivate)).build())))
                    .andExpect(status().isForbidden());
        } else if (httpCodeExpected == 404) {
            mockMvc.perform(put(String.format("/v1/elements/%s", elementUuidToUpdate))
                            .header("userId", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ElementAttributes.builder().accessRights(new AccessRightsAttributes(newIsPrivate)).build())))
                    .andExpect(status().isNotFound());
        } else {
            fail("unexpected case");
        }
    }

    private void assertDirectoryIsEmpty(UUID uuidDir, String userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/directories/" + uuidDir + "/elements")
                                      .header("userId", userId))
                              .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                              .andReturn();
        objectMapper.readerForListOf(ElementAttributes.class).readValue(result.getResponse().getContentAsString());
    }

    private void assertElementIsProperlyInserted(ElementAttributes elementAttributes) throws Exception {
        String response = mockMvc.perform(get("/v1/elements/" + elementAttributes.getElementUuid())
                        .header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        ElementAttributes result = objectMapper.readValue(response, new TypeReference<>() {
        });
        assertTrue(new MatcherJson<>(objectMapper, elementAttributes).matchesSafely(result));
    }

    private void checkDirectoryContent(UUID parentDirectoryUuid, String userId, List<ElementAttributes> list) throws Exception {
        String response = mockMvc.perform(get("/v1/directories/" + parentDirectoryUuid + "/elements")
                .header("userId", userId))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<ElementAttributes> result = objectMapper.readValue(response, new TypeReference<>() {
        });
        assertTrue(new MatcherJson<>(objectMapper, list).matchesSafely(result));
    }

    private void checkElementNotFound(UUID elementUuid, String userId) throws Exception {
        mockMvc.perform(get("/v1/elements/" + elementUuid)
                .header("userId", userId))
                        .andExpect(status().isNotFound());
    }

    private void checkElementNameExistInDirectory(UUID parentDirectoryUuid, String elementName, String type, HttpStatus expectedStatus) throws Exception {
        mockMvc.perform(head(String.format("/v1/directories/%s/elements/%s/types/%s", parentDirectoryUuid, elementName, type)))
                        .andExpect(status().is(expectedStatus.value()));
    }

    private void deleteElement(UUID elementUuidToBeDeleted, UUID elementUuidHeader, String userId, boolean isRoot, boolean isPrivate, boolean isStudy, int numberOfChildStudies) throws Exception {
        mockMvc.perform(delete("/v1/elements/" + elementUuidToBeDeleted)
                .header("userId", userId))
                        .andExpect(status().isOk());

        Message<byte[]> message;
        MessageHeaders headers;
        // assert that the broker message has been sent a delete study
        if (isStudy) {
            message = output.receive(1000);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals(userId, headers.get(HEADER_USER_ID));
            assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));
            assertEquals(elementUuidToBeDeleted, headers.get(HEADER_STUDY_UUID));
        } else {
            //empty the queue of all delete study notif
            for (int i = 0; i < numberOfChildStudies; i++) {
                message = output.receive(1000);
                headers = message.getHeaders();
                assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));
                assertEquals(userId, headers.get(HEADER_USER_ID));
            }
        }
        // assert that the broker message has been sent a delete
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(isRoot, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(isRoot ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
    }

    private void deleteElementFail(UUID elementUuidToBeDeleted, String userId, int httpCodeExpected) throws Exception {
        if (httpCodeExpected == 403) {
            mockMvc.perform(delete("/v1/elements/" + elementUuidToBeDeleted)
                    .header("userId", userId))
                            .andExpect(status().isForbidden());
        } else {
            fail("unexpected case");
        }
    }

    private void checkRootDirectoryExists(String rootDirectoryName) throws Exception {
        mockMvc.perform(head("/v1/root-directories?directoryName=" + rootDirectoryName))
                        .andExpect(status().isOk());
    }

    private void checkRootDirectoryNotExists(String rootDirectoryName) throws Exception {
        mockMvc.perform(head("/v1/root-directories?directoryName=" + rootDirectoryName))
                        .andExpect(status().isNoContent());
    }

    private String candidateName(UUID directoryUUid, String originalName, String type) throws Exception {
        return mockMvc.perform(get("/v1/directories/" + directoryUUid + "/" + originalName + "/newNameCandidate?type=" + type)
                               .header("userId", "userId"))
                      .andReturn().getResponse().getContentAsString();
    }

    @SneakyThrows
    @Test
    public void testNameCandidate() {

        var directoryId = insertAndCheckRootDirectory("newDir", true, "userId");

        mockMvc.perform(get("/v1/directories/" + directoryId + "/" + "pouet" + "/newNameCandidate?type=" + STUDY)
                .header("userId", "youplaboum"))
            .andExpect(status().isForbidden());

        var name = "newStudy";
        // check when no elements is corresponding (empty folder
        assertEquals("newStudy", candidateName(directoryId, name, STUDY));
        var element = toElementAttributes(UUID.randomUUID(), name, STUDY, null, "userId");
        insertAndCheckSubElement(directoryId, true, element);
        var newCandidateName = candidateName(directoryId, name, STUDY);
        assertEquals("newStudy(1)", newCandidateName);
        element.setElementName(newCandidateName);
        element.setElementUuid(UUID.randomUUID());
        insertAndCheckSubElement(directoryId, true, element);
        assertEquals("newStudy(2)", candidateName(directoryId, name, STUDY));
        assertEquals("newStudy", candidateName(directoryId, name, CONTINGENCY_LIST));
    }

    @After
    public void tearDown() {
        assertNull("Should not be any messages", output.receive(1000));
    }
}
