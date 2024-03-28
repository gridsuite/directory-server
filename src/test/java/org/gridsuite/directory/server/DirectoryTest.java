/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.jparams.verifier.tostring.ToStringVerifier;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import lombok.SneakyThrows;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
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
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static org.gridsuite.directory.server.DirectoryException.Type.UNKNOWN_NOTIFICATION;
import static org.gridsuite.directory.server.DirectoryService.*;
import static org.gridsuite.directory.server.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.directory.server.NotificationService.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    private static final long TIMEOUT = 1000;
    private static final UUID STUDY_RENAME_UUID = UUID.randomUUID();
    private static final UUID STUDY_RENAME_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_UUID = UUID.randomUUID();

    public static final String HEADER_MODIFIED_BY = "modifiedBy";
    public static final String HEADER_MODIFICATION_DATE = "modificationDate";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";

    private final String elementUpdateDestination = "element.update";
    private final String directoryUpdateDestination = "directory.update";

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudyService studyService;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    private void cleanDB() {
        directoryElementRepository.deleteAll();
        directoryElementInfosRepository.deleteAll();
        SQLStatementCountValidator.reset();
    }

    @Before
    public void setup() {
        cleanDB();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(elementUpdateDestination, directoryUpdateDestination);
        assertQueuesEmptyThenClear(destinations);
    }

    @Test
    public void test() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory
        ElementAttributes newDirectory = retrieveInsertAndCheckRootDirectory("newDir", false, "userId");
        UUID uuidNewDirectory = newDirectory.getElementUuid();
        ZonedDateTime creationDateNewDirectory = newDirectory.getCreationDate();

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

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, false, "userId", null, creationDateNewDirectory, creationDateNewDirectory, "userId")));

        // Change root directory access rights public => private
        // change access of a root directory from public to private => we should receive a notification with isPrivate= false to notify all clients
        updateAccessRights(uuidNewDirectory, uuidNewDirectory, "userId", true, true, false);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, true, "userId", null, creationDateNewDirectory, creationDateNewDirectory, "userId")));

        // Add another sub-directory
        ElementAttributes newSubDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, true, "userId", "descr newSubDir");
        insertAndCheckSubElement(uuidNewDirectory, true, newSubDirAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(newSubDirAttributes));

        // Add another sub-directory
        ElementAttributes newSubSubDirAttributes = toElementAttributes(null, "newSubSubDir", DIRECTORY, true, "userId");
        insertAndCheckSubElement(newSubDirAttributes.getElementUuid(), true, newSubSubDirAttributes);
        checkDirectoryContent(newSubDirAttributes.getElementUuid(), "userId", List.of(newSubSubDirAttributes));

        // Test children number of root directory
        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, null, creationDateNewDirectory, creationDateNewDirectory, "userId")));

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
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(study1UUID, "Doe");
        assertRequestsCount(2, 0, 0, 0);

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
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(filter1UUID, "Doe");
        assertRequestsCount(2, 0, 0, 0);

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
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(rootDirUuid, "Doe");
        assertRequestsCount(1, 0, 0, 0);

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
        ElementAttributes rootDir1 = retrieveInsertAndCheckRootDirectory("rootDir1", false, "user1");
        // Insert a root directory user2
        ElementAttributes rootDir2 = retrieveInsertAndCheckRootDirectory("rootDir2", false, "user2");

        checkRootDirectoriesList("user1",
            List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, false, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, false, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
            )
        );

        checkRootDirectoriesList("user2",
            List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, false, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, false, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
            )
        );

        //Cleaning Test
        deleteElement(rootDir1.getElementUuid(), rootDir1.getElementUuid(), "user1", true, false, false, 0);
        deleteElement(rootDir2.getElementUuid(), rootDir2.getElementUuid(), "user2", true, false, false, 0);
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

        assertNbElementsInRepositories(4);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filterUuid))))
                .andExpect(status().isOk());

        assertNbElementsInRepositories(4);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a root directory creation request message
        message = output.receive(TIMEOUT, directoryUpdateDestination);
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

        assertNbElementsInRepositories(4);

        // Move from public folder to private folder is forbidden if the issuer of the operation isn't the element's owner
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Roger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filterUuid))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(4);

        // Move from public folder to private folder is allowed if the issuer of the operation is the element's owner
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filterUuid))))
                .andExpect(status().isOk());

        assertNbElementsInRepositories(4);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(directory21PrivateUUID, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(false, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        checkElementNameExistInDirectory(rootDir10Uuid, "filter", FILTER, HttpStatus.OK);
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

        assertNbElementsInRepositories(4);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Unallowed User")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(filterUuid))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(4);
    }

    @Test
    public void testMoveElementNotFound() throws Exception {
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, filterAttributes);

        assertNbElementsInRepositories(2);

        UUID unknownUuid = UUID.randomUUID();

        mockMvc.perform(put("/v1/elements/?targetDirectoryUuid=" + rootDir20Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(unknownUuid))))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/v1/elements/?targetDirectoryUuid=" + unknownUuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filterUuid))))
                .andExpect(status().isNotFound());

        assertNbElementsInRepositories(2);
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

        assertNbElementsInRepositories(4);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir20Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filterwithSameNameAndTypeUuid))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(4);
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

        assertNbElementsInRepositories(3);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + filter2Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filter1Uuid)))
                )
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(3);
    }

    @Test
    public void testMoveDirectory() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, false, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, directory20Attributes);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(directory21UUID))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(3);
    }

    @Test
    public void testMoveStudy() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", false, "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", false, "Doe");

        UUID study21UUID = UUID.randomUUID();
        ElementAttributes study21Attributes = toElementAttributes(study21UUID, "Study21", STUDY, null, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, false, study21Attributes);

        assertNbElementsInRepositories(3);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(study21UUID)))
                )
                .andExpect(status().isOk());

        assertNbElementsInRepositories(3);

        // assert that the broker message has been sent a update notification on directory
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, directoryUpdateDestination);
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

        assertNbElementsInRepositories(3);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir20Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(rootDir10Uuid))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(3);
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());
        // Insert a root directory user1

        ElementAttributes rootDir1 = retrieveInsertAndCheckRootDirectory("rootDir1", true, "user1");
        // Insert a root directory user2
        ElementAttributes rootDir2 = retrieveInsertAndCheckRootDirectory("rootDir2", false, "user2");

        checkRootDirectoriesList("user1",
            List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, true, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, false, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
            )
        );

        checkRootDirectoriesList("user2", List.of(toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, false, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")));

        //Cleaning Test
        deleteElement(rootDir1.getElementUuid(), rootDir1.getElementUuid(), "user1", true, true, false, 0);
        deleteElement(rootDir2.getElementUuid(), rootDir2.getElementUuid(), "user2", true, false, false, 0);
    }

    @Test
    public void testTwoUsersTwoPrivateDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());
        // Insert a root directory user1
        ElementAttributes rootDir1 = retrieveInsertAndCheckRootDirectory("rootDir1", true, "user1");
        UUID rootDir1Uuid = rootDir1.getElementUuid();
        ZonedDateTime rootDir1CreationDate = rootDir1.getCreationDate();
        // Insert a root directory user2
        ElementAttributes rootDir2 = retrieveInsertAndCheckRootDirectory("rootDir2", true, "user2");
        UUID rootDir2Uuid = rootDir2.getElementUuid();
        ZonedDateTime rootDir2CreationDate = rootDir2.getCreationDate();

        checkRootDirectoriesList("user1", List.of(toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, true, "user1", null, rootDir1CreationDate, rootDir1CreationDate, "user1")));

        checkRootDirectoriesList("user2", List.of(toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, true, "user2", null, rootDir2CreationDate, rootDir2CreationDate, "user2")));

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

        assertNbElementsInRepositories(5);

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, true, false, 3);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(study1Attributes.getElementUuid(), "userId");
        checkElementNotFound(study2Attributes.getElementUuid(), "userId");
        checkElementNotFound(subDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(subDirStudyAttributes.getElementUuid(), "userId");

        assertNbElementsInRepositories(0);
    }

    @Test
    public void testRenameStudy() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_UUID, "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        assertNbElementsInRepositories(2);

        renameElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", "newName1", false, false);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Attributes.getElementUuid(), "newName1", STUDY, null, "user1", null, study1Attributes.getCreationDate(), study1Attributes.getLastModificationDate(), "user1")));

        assertNbElementsInRepositories(2);
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
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, null, "user1", null, study1Attributes.getCreationDate(), study1Attributes.getLastModificationDate(), "user1")));
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
        ElementAttributes rootDir = retrieveInsertAndCheckRootDirectory("rootDir1", true, "Doe");
        UUID rootDirUuid = rootDir.getElementUuid();
        ZonedDateTime rootDirCreationDate = rootDir.getCreationDate();

        assertNbElementsInRepositories(1);

        //the name should not change
        renameElementExpectFail(rootDirUuid, "user1", "newName1", 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe", null, rootDirCreationDate, rootDirCreationDate, "Doe")));

        assertNbElementsInRepositories(1);
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
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, null, "user1", null, study1Attributes.getCreationDate(), study1Attributes.getLastModificationDate(), "user1")));
    }

    @Test
    public void testUpdateDirectoryAccessRight() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        ElementAttributes rootDir = retrieveInsertAndCheckRootDirectory("rootDir1", false, "Doe");
        UUID rootDirUuid = rootDir.getElementUuid();
        ZonedDateTime rootDirCreationDate = rootDir.getCreationDate();

        //set directory to private
        updateAccessRights(rootDirUuid, rootDirUuid, "Doe", true, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe", null, rootDirCreationDate, rootDirCreationDate, "Doe")));

        //reset it to public
        updateAccessRights(rootDirUuid, rootDirUuid, "Doe", false, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, false, "Doe", null, rootDirCreationDate, rootDirCreationDate, "Doe")));

        updateAccessRightFail(rootDirUuid, "User1", true, 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, false, "Doe", null, rootDirCreationDate, rootDirCreationDate, "Doe")));
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
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
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
        ElementAttributes newContingency = toElementAttributes(contingencyAttributes.getElementUuid(), "newContingency", CONTINGENCY_LIST, null, "user1", null, contingencyAttributes.getCreationDate(), contingencyAttributes.getLastModificationDate(), "user1");
        ElementAttributes newFilter = toElementAttributes(filterAttributes.getElementUuid(), "newFilter", FILTER, null, "user1", null, filterAttributes.getCreationDate(), filterAttributes.getLastModificationDate(), "user1");
        ElementAttributes newScript = toElementAttributes(scriptAttributes.getElementUuid(), "newScript", FILTER, null, "user1", null, scriptAttributes.getCreationDate(), scriptAttributes.getLastModificationDate(), "user1");
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(newContingency, newFilter, newScript)));

        ElementAttributes directory = retrieveInsertAndCheckRootDirectory("testDir", false, "user1");
        List<ElementAttributes> result = getElements(List.of(FILTER_UUID, UUID.randomUUID(), directory.getElementUuid()), "user1", false, List.of(FILTER), 200);
        assertEquals(2, result.size());
        result.sort(Comparator.comparing(ElementAttributes::getElementName));

        result.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(result, new MatcherJson<>(objectMapper, List.of(
                toElementAttributes(FILTER_UUID, "newFilter", FILTER, new AccessRightsAttributes(null), "user1", 0, null, filterAttributes.getCreationDate(), filterAttributes.getLastModificationDate(), "user1"),
                directory
        )));

        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, true, "user1");
        insertAndCheckSubElement(rootDirUuid, false, subDirAttributes);
        insertAndCheckSubElement(subDirAttributes.getElementUuid(), true, toElementAttributes(null, "subDirContingency", CONTINGENCY_LIST, null, "user1"));
        checkDirectoryContent(rootDirUuid, "user1", List.of(newContingency, newFilter, newScript, subDirAttributes));
        subDirAttributes.setSubdirectoriesCount(1L);
        checkDirectoryContent(rootDirUuid, "user1", List.of(CONTINGENCY_LIST), List.of(newContingency, subDirAttributes));

        ElementAttributes rootDirectory = getElements(List.of(rootDirUuid), "user1", false, 200).get(0);

        checkRootDirectoriesList("user1", List.of(rootDirectory, directory));

        rootDirectory.setSubdirectoriesCount(3L);
        checkRootDirectoriesList("user1", List.of(FILTER), List.of(rootDirectory, directory));

        assertNbElementsInRepositories(7);
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
                toElementAttributes(contingencyAttributes.getElementUuid(), "newContingency", CONTINGENCY_LIST, null, "user1", null, contingencyAttributes.getCreationDate(), contingencyAttributes.getLastModificationDate(), "user1"),
                toElementAttributes(filterAttributes.getElementUuid(), "newFilter", FILTER, null, "user1", null, filterAttributes.getCreationDate(), filterAttributes.getLastModificationDate(), "user1"),
                toElementAttributes(scriptAttributes.getElementUuid(), "newScript", FILTER, null, "user1", null, scriptAttributes.getCreationDate(), scriptAttributes.getLastModificationDate(), "user1")
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
        String requestBody = objectMapper.writeValueAsString(new RootDirectoryAttributes("", new AccessRightsAttributes(false), "userId", null, null, null, "userId"));

        // Insert a public root directory user1 with empty name and expect 403
        mockMvc.perform(post(String.format("/v1/root-directories"))
                        .header("userId", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(1);
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

        assertNbElementsInRepositories(1);
    }

    @Test
    public void testElementUpdateNotification() throws Exception {
        // Insert a root directory
        ElementAttributes newDirectory = retrieveInsertAndCheckRootDirectory("newDir", false, "userId");
        UUID uuidNewDirectory = newDirectory.getElementUuid();

        // Insert a  sub-element of type STUDY
        ElementAttributes subEltAttributes = toElementAttributes(UUID.randomUUID(), "newStudy", STUDY, null, "userId", "descr study");
        insertAndCheckSubElement(uuidNewDirectory, false, subEltAttributes);

        LocalDateTime newModificationDate = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        String userMakingModification = "newUser";

        input.send(MessageBuilder.withPayload("")
            .setHeader(HEADER_MODIFIED_BY, userMakingModification)
            .setHeader(HEADER_MODIFICATION_DATE, newModificationDate.toString())
            .setHeader(HEADER_ELEMENT_UUID, subEltAttributes.getElementUuid().toString())
            .build(), elementUpdateDestination);

        MvcResult result = mockMvc.perform(get("/v1/elements/" + subEltAttributes.getElementUuid()))
            .andExpectAll(status().isOk(),
                    content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        ElementAttributes updatedElement = objectMapper.readValue(result.getResponse().getContentAsString(), ElementAttributes.class);

        assertEquals(newModificationDate, updatedElement.getLastModificationDate().toLocalDateTime());
        assertEquals(userMakingModification, updatedElement.getLastModifiedBy());
    }

    @SneakyThrows
    @Test
    public void testStashAndRestoreElements() {
        // Insert a root directory
        ElementAttributes rootDirectory = retrieveInsertAndCheckRootDirectory("directory", false, "userId");
        UUID rootDirectoryUuid = rootDirectory.getElementUuid();

        // Insert a sub-elements of type DIRECTORY
        UUID subDirUuid1 = UUID.randomUUID();
        ElementAttributes subDirAttributes1 = toElementAttributes(subDirUuid1, "newSubDir1", DIRECTORY, true, "userId");

        UUID subDirUuid2 = UUID.randomUUID();
        ElementAttributes subDirAttributes2 = toElementAttributes(subDirUuid2, "newSubDir2", DIRECTORY, false, "userId");

        UUID subDirUuid3 = UUID.randomUUID();
        ElementAttributes subDirAttributes3 = toElementAttributes(subDirUuid3, "newSubDir3", DIRECTORY, true, "userId2");

        UUID subDirUuid4 = UUID.randomUUID();
        ElementAttributes subDirAttributes4 = toElementAttributes(subDirUuid4, "newSubDir4", DIRECTORY, false, "userId2");

        //          root
        //         /    \
        //       dir1    dir4
        //        |       |
        //       dir2    dir3
        insertAndCheckSubElement(rootDirectoryUuid, false, subDirAttributes1);
        insertAndCheckSubElement(subDirUuid1, true, subDirAttributes2);
        insertAndCheckSubElement(rootDirectoryUuid, false, subDirAttributes4);
        insertAndCheckSubElement(subDirUuid4, false, subDirAttributes3);
        subDirAttributes1.setSubdirectoriesCount(1L);
        checkDirectoryContent(rootDirectoryUuid, "userId", List.of(subDirAttributes1, subDirAttributes4));

        // Insert a sub-element of type STUDY
        ElementAttributes subEltAttributes = toElementAttributes(UUID.randomUUID(), "newStudy", STUDY, null, "userId", "descr study");
        insertAndCheckSubElement(rootDirectoryUuid, false, subEltAttributes);
        checkDirectoryContent(rootDirectoryUuid, "userId", List.of(subDirAttributes1, subDirAttributes4, subEltAttributes));
        checkElementNameExistInDirectory(rootDirectoryUuid, "newStudy", STUDY, HttpStatus.OK);

        assertNbElementsInRepositories(6, 6);

        mockMvc.perform(post("/v1/elements/stash?ids=" + subDirUuid4)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/elements/stash?ids=" + subDirUuid1)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        output.clear();

        assertNbElementsInRepositories(6, 4);

        subDirAttributes1.setSubdirectoriesCount(0L);
        checkStashedElements(List.of(Pair.of(subDirAttributes1, 1L)));

        mockMvc.perform(post("/v1/elements/" + rootDirectoryUuid + "/restore")
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(subDirUuid1))))
                .andExpect(status().isOk());
        output.clear();
        checkStashedElements(List.of());

        assertNbElementsInRepositories(6, 6);

        mockMvc.perform(post("/v1/elements/stash?ids=" + rootDirectoryUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        output.clear();

        rootDirectory.setSubdirectoriesCount(0L);
        checkStashedElements(List.of(Pair.of(rootDirectory, 4L)));

        assertNbElementsInRepositories(6, 1); // 'newSubDir3' is private with a different owner 'userId2'

        ElementAttributes rootDirectory2 = retrieveInsertAndCheckRootDirectory("directory", false, "userId");
        UUID rootDirectoryUuid2 = rootDirectory2.getElementUuid();

        mockMvc.perform(post("/v1/elements/" + rootDirectoryUuid2 + "/restore")
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(subDirUuid1))))
                .andExpect(status().isOk());
        output.clear();

        checkStashedElements(List.of());

        assertNbElementsInRepositories(7, 7);

        mockMvc.perform(post("/v1/elements/stash?ids=" + subDirUuid1)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        output.clear();

        assertNbElementsInRepositories(7, 5);

        mockMvc.perform(post("/v1/elements/stash?ids=" + subDirUuid4)
                        .header("userId", "userId2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        output.clear();

        assertNbElementsInRepositories(7, 3);

        subDirAttributes1.setSubdirectoriesCount(0L);
        subDirAttributes4.setSubdirectoriesCount(0L);
        checkStashedElements(List.of(Pair.of(subDirAttributes1, 1L), Pair.of(subDirAttributes4, 0L)));

        mockMvc.perform(delete("/v1/elements?ids=" + subDirUuid1 + "," + subDirUuid4)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        output.clear();
        checkStashedElements(List.of(Pair.of(subDirAttributes4, 0L)));

        assertNbElementsInRepositories(5, 3);
    }

    private void checkStashedElements(List<Pair<ElementAttributes, Long>> expectedStashed) throws Exception {
        String response = mockMvc.perform(get("/v1/elements/stash")
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(objectMapper.writeValueAsString(expectedStashed), response);
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
        checkRootDirectoriesList(userId, List.of(), list);
    }

    private void checkRootDirectoriesList(String userId, List<String> elementTypes, List<ElementAttributes> list) throws Exception {
        var types = !CollectionUtils.isEmpty(elementTypes) ? "?elementTypes=" + elementTypes.stream().collect(Collectors.joining(",")) : "";
        String response = mockMvc.perform(get("/v1/root-directories" + types).header("userId", userId))
                             .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                             .andReturn()
                            .getResponse()
                            .getContentAsString();

        List<ElementAttributes> elementAttributes = objectMapper.readValue(response, new TypeReference<>() {
        });
        assertTrue(new MatcherJson<>(objectMapper, list).matchesSafely(elementAttributes));
    }

    private ElementAttributes retrieveInsertAndCheckRootDirectory(String rootDirectoryName, boolean isPrivate, String userId) throws Exception {
        String response = mockMvc.perform(post("/v1/root-directories")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId, null, null, null, userId))))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();
        ZonedDateTime creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getCreationDate();

        ElementAttributes newDirectoryAttributes = toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId, null, creationDateNewDirectory, creationDateNewDirectory, userId);
        assertElementIsProperlyInserted(newDirectoryAttributes);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        return newDirectoryAttributes;
    }

    private UUID insertAndCheckRootDirectory(String rootDirectoryName, boolean isPrivate, String userId) throws Exception {
        String response = mockMvc.perform(post("/v1/root-directories")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId, null, null, null, null))))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();
        ZonedDateTime creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getCreationDate();

        assertElementIsProperlyInserted(toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId, null, creationDateNewDirectory, creationDateNewDirectory, userId));

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
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
        return getElements(elementUuids, userId, strictMode, null, httpCodeExpected);
    }

    private List<ElementAttributes> getElements(List<UUID> elementUuids, String userId, boolean strictMode, List<String> elementTypes, int httpCodeExpected) throws Exception {
        var ids = elementUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        var typesPath = elementTypes != null ? "&elementTypes=" + elementTypes.stream().collect(Collectors.joining(",")) : "";

        // get sub-elements list
        if (httpCodeExpected == 200) {
            MvcResult result = mockMvc.perform(get("/v1/elements?strictMode=" + (strictMode ? "true" : "false") + "&ids=" + ids + typesPath)
                    .header("userId", userId))
                    .andExpectAll(status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<ElementAttributes>>() {
            });
        } else if (httpCodeExpected == 404) {
            mockMvc.perform(get("/v1/elements?strictMode=" + (strictMode ? "true" : "false") + "&ids=" + ids + typesPath)
                            .header("userId", userId))
                    .andExpectAll(status().isNotFound());
        } else {
            fail("unexpected case");
        }
        return Collections.emptyList();
    }

    private void insertSubElement(UUID parentDirectoryUUid, boolean isParentPrivate, ElementAttributes subElementAttributes, boolean allowNewName) throws Exception {
        // Insert a sub-element in a directory
        MvcResult response = mockMvc.perform(post("/v1/directories/" + parentDirectoryUUid + "/elements" + (allowNewName ? "?allowNewName=true" : ""))
                        .header("userId", subElementAttributes.getOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subElementAttributes)))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response).getResponse().getContentAsString(), ElementAttributes.class)
                .getElementUuid();
        ZonedDateTime creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response).getResponse().getContentAsString(), ElementAttributes.class).getCreationDate();
        String lastModifiedBy = objectMapper.readValue(Objects.requireNonNull(response).getResponse().getContentAsString(), ElementAttributes.class).getLastModifiedBy();

        subElementAttributes.setElementUuid(uuidNewDirectory);
        subElementAttributes.setCreationDate(creationDateNewDirectory);
        subElementAttributes.setLastModificationDate(creationDateNewDirectory);
        subElementAttributes.setLastModifiedBy(lastModifiedBy);

        // assert that the broker message has been sent an element creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(subElementAttributes.getOwner(), headers.get(HEADER_USER_ID));
        assertEquals(parentDirectoryUUid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isParentPrivate, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    private void insertAndCheckSubElement(UUID parentDirectoryUUid, boolean isParentPrivate, ElementAttributes subElementAttributes) throws Exception {
        insertSubElement(parentDirectoryUUid, isParentPrivate, subElementAttributes, false);
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
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
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
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
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

    private void assertNbElementsInRepositories(int nbElements) {
        assertNbElementsInRepositories(nbElements, nbElements);
    }

    private void assertNbElementsInRepositories(int nbEntities, int nbElementsInfos) {
        assertEquals(nbEntities, directoryElementRepository.findAll().size());
        assertEquals(nbElementsInfos, Iterables.size(directoryElementInfosRepository.findAll()));
    }

    public void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
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
        checkDirectoryContent(parentDirectoryUuid, userId, List.of(), list);
    }

    private void checkDirectoryContent(UUID parentDirectoryUuid, String userId, List<String> types, List<ElementAttributes> list) throws Exception {
        String elementTypes = !CollectionUtils.isEmpty(types) ? "?elementTypes=" + types.stream().collect(Collectors.joining(",")) : "";
        String response = mockMvc.perform(get("/v1/directories/" + parentDirectoryUuid + "/elements" + elementTypes)
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
            message = output.receive(TIMEOUT, directoryUpdateDestination);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals(userId, headers.get(HEADER_USER_ID));
            assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));
            assertEquals(elementUuidToBeDeleted, headers.get(HEADER_STUDY_UUID));
        } else {
            //empty the queue of all delete study notif
            for (int i = 0; i < numberOfChildStudies; i++) {
                message = output.receive(TIMEOUT, directoryUpdateDestination);
                headers = message.getHeaders();
                assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));
                assertEquals(userId, headers.get(HEADER_USER_ID));
            }
        }
        // assert that the broker message has been sent a delete
        message = output.receive(TIMEOUT, directoryUpdateDestination);
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

    @Test
    @SneakyThrows
    public void testCreateElementInDirectory() {
        String userId = "user";
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        ElementAttributes caseElement = ElementAttributes.toElementAttributes(UUID.randomUUID(), "caseName", "CASE",
                false, "user", null, now, now, userId);
        String requestBody = objectMapper.writeValueAsString(caseElement);
        mockMvc.perform(post("/v1/directories/paths/elements?directoryPath=" + "dir1/dir2")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        checkRootDirectoryExists("dir1");
        List<DirectoryElementEntity> directoryElementList = directoryElementRepository.findAll();
        //there should be 3 elements, the 2 directories created and the one element inside
        assertEquals(3, directoryElementList.size());
        DirectoryElementEntity dir1 = directoryElementList.stream().filter(directoryElementEntity -> directoryElementEntity.getName().equals("dir1")).findFirst().orElseThrow();
        assertEquals(DIRECTORY, dir1.getType());
        //because it should be a root directory
        assertNull(dir1.getParentId());
        UUID dir1Uuid = dir1.getId();

        DirectoryElementEntity dir2 = directoryElementList.stream().filter(directoryElementEntity -> directoryElementEntity.getName().equals("dir2")).findFirst().orElseThrow();
        assertEquals(DIRECTORY, dir2.getType());
        //dir2 is a child of dir1
        assertEquals(dir1Uuid, dir2.getParentId());
        UUID dir2Uuid = dir2.getId();

        DirectoryElementEntity insertedCaseElement = directoryElementList.stream().filter(directoryElementEntity -> directoryElementEntity.getName().equals("caseName")).findFirst().orElseThrow();
        assertEquals("CASE", insertedCaseElement.getType());
        //the element is in dir2
        assertEquals(dir2Uuid, insertedCaseElement.getParentId());

        assertNbElementsInRepositories(3);

        //we don't care about message
        output.clear();
    }

    @Test
    @SneakyThrows
    public void testCreateModificationElementWithAutomaticNewName() {
        final String userId = "Doe";
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirModif", false, userId);

        // insert a new element
        final String modifElementName = "modif";
        ElementAttributes modifAttributes = toElementAttributes(UUID.randomUUID(), modifElementName, MODIFICATION, null, userId);
        insertAndCheckSubElement(rootDirUuid, false, modifAttributes);

        // insert another new element having the same existing name, allowing duplication with a new name
        ElementAttributes anotherModifAttributes = toElementAttributes(UUID.randomUUID(), modifElementName, MODIFICATION, null, userId);
        insertSubElement(rootDirUuid, false, anotherModifAttributes, true);
        // expecting "incremental name"
        anotherModifAttributes.setElementName(modifElementName + "(1)");
        assertElementIsProperlyInserted(anotherModifAttributes);

        checkDirectoryContent(rootDirUuid, userId, List.of(MODIFICATION), List.of(modifAttributes, anotherModifAttributes));

        assertNbElementsInRepositories(3);
    }

    @Test
    @SneakyThrows
    public void testReindexAll() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        DirectoryElementEntity dirEntity = new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "name", DIRECTORY, true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity studyEntity = new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "name", STUDY, true, "userId", "description", now, now, "userId", false, null);

        directoryElementRepository.saveAll(List.of(dirEntity, studyEntity));

        assertNbElementsInRepositories(2, 0);

        mockMvc.perform(post("/v1/elements/reindex-all")).andExpect(status().isOk());

        assertNbElementsInRepositories(2, 2);

        output.clear();
    }

    @Test
    public void testSearch() throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        ElementAttributes caseElement = ElementAttributes.toElementAttributes(UUID.randomUUID(), "recollement", "STUDY",
                false, "user", null, now, now, "user");
        String requestBody = objectMapper.writeValueAsString(caseElement);
        mockMvc.perform(post("/v1/directories/paths/elements?directoryPath=" + "dir1/dir2")
                        .header("userId", "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        MvcResult mvcResult;
        String resultAsString;

        mvcResult = mockMvc
                .perform(get("/v1/elements/search?userInput={request}", "r").header("userId", "user"))
                .andExpectAll(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<Object> result = objectMapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(1, result.size());
        output.clear();
    }

    private void assertQueuesEmptyThenClear(List<String> destinations) {
        try {
            destinations.forEach(destination -> assertNull("Should not be any messages in queue " + destination + " : ", output.receive(100, destination)));
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }
}
