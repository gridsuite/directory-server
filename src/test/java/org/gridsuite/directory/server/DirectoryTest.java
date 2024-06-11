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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.directory.server.DirectoryException.Type.UNKNOWN_NOTIFICATION;
import static org.gridsuite.directory.server.DirectoryService.*;
import static org.gridsuite.directory.server.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.directory.server.NotificationService.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private static final UUID FILTER_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_UUID = UUID.randomUUID();

    public static final String HEADER_MODIFIED_BY = "modifiedBy";
    public static final String HEADER_MODIFICATION_DATE = "modificationDate";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";
    public static final String USER_ID = "userId";
    public static final String USERID_1 = "userId1";
    public static final String USERID_2 = "userId2";
    public static final String USERID_3 = "userId3";
    public static final String RECOLLEMENT = "recollement";
    private final String elementUpdateDestination = "element.update";
    private final String directoryUpdateDestination = "directory.update";

    @Value("${spring.data.elasticsearch.embedded.port:}")
    private String expectedEsPort;

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

    @Autowired
    private DirectoryService directoryService;

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
        ElementAttributes newDirectory = retrieveInsertAndCheckRootDirectory("newDir", "userId");
        UUID uuidNewDirectory = newDirectory.getElementUuid();
        Instant creationDateNewDirectory = newDirectory.getCreationDate();
        Instant modificationDateNewDirectory = newDirectory.getLastModificationDate();

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, "userId");
        insertAndCheckSubElement(uuidNewDirectory, subDirAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subDirAttributes));

        // Insert a  sub-element of type STUDY
        ElementAttributes subEltAttributes = toElementAttributes(UUID.randomUUID(), "newStudy", STUDY, "userId", "descr study");
        insertAndCheckSubElement(uuidNewDirectory, subEltAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subDirAttributes, subEltAttributes));

        checkElementNameExistInDirectory(uuidNewDirectory, "newStudy", STUDY, HttpStatus.OK);
        checkElementNameExistInDirectory(uuidNewDirectory, "tutu", STUDY, HttpStatus.NO_CONTENT);

        // Delete the sub-directory newSubDir
        deleteElement(subDirAttributes.getElementUuid(), uuidNewDirectory, "userId", false, false, 0);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subEltAttributes));

        // Delete the sub-element newStudy
        deleteElement(subEltAttributes.getElementUuid(), uuidNewDirectory, "userId", false, true, 0);
        assertDirectoryIsEmpty(uuidNewDirectory, "userId");

        // Rename the root directory
        renameElement(uuidNewDirectory, uuidNewDirectory, "userId", "newName", true);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, "userId", null, creationDateNewDirectory, modificationDateNewDirectory, "userId")));

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, "userId", null, creationDateNewDirectory, creationDateNewDirectory, "userId")));
        // Add another sub-directory
        ElementAttributes newSubDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, "userId", "descr newSubDir");
        insertAndCheckSubElement(uuidNewDirectory, newSubDirAttributes);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(newSubDirAttributes));

        // Add another sub-directory
        ElementAttributes newSubSubDirAttributes = toElementAttributes(null, "newSubSubDir", DIRECTORY, "userId");
        insertAndCheckSubElement(newSubDirAttributes.getElementUuid(), newSubSubDirAttributes);
        checkDirectoryContent(newSubDirAttributes.getElementUuid(), "userId", List.of(newSubSubDirAttributes));

        // Test children number of root directory
        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, "userId", 1L, null, creationDateNewDirectory, creationDateNewDirectory, "userId")));

        deleteElement(uuidNewDirectory, uuidNewDirectory, "userId", true, false, 0);
        checkRootDirectoriesList("userId", List.of());

        checkElementNotFound(newSubDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(newSubSubDirAttributes.getElementUuid(), "userId");
    }

    @Test
    public void testGetPathOfStudy() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDirUuid, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, "Doe");
        insertAndCheckSubElement(directory1UUID, directory2Attributes);

        // Insert a study in the directory2
        UUID study1UUID = UUID.randomUUID();
        ElementAttributes study1Attributes = toElementAttributes(study1UUID, "study1", STUDY, "Doe");
        insertAndCheckSubElement(directory2UUID, study1Attributes);
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(study1UUID, "Doe");
        assertRequestsCount(1, 0, 0, 0);

        //Check if all element's parents are retrieved in the right order
        assertEquals(
                path.stream()
                    .map(ElementAttributes::getElementUuid)
                    .collect(Collectors.toList()),
                Arrays.asList(rootDirUuid, directory1UUID, directory2UUID, study1UUID)
        );
    }

    @Test
    public void testGetPathOfFilter() throws Exception {
       // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDirUuid, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, "Doe");
        insertAndCheckSubElement(directory1UUID, directory2Attributes);

        // Insert a filter in the directory2
        UUID filter1UUID = UUID.randomUUID();
        ElementAttributes study1Attributes = toElementAttributes(filter1UUID, "filter1", FILTER, "Doe");
        insertAndCheckSubElement(directory2UUID, study1Attributes);
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(filter1UUID, "Doe");
        assertRequestsCount(1, 0, 0, 0);

        //Check if all element's parents are retrieved in the right order
        assertEquals(
                path.stream()
                    .map(ElementAttributes::getElementUuid)
                    .collect(Collectors.toList()),
                Arrays.asList(rootDirUuid, directory1UUID, directory2UUID, filter1UUID)
        );
    }

    @Test
    public void testGetPathOfOtherUserElements() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDirUuid, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, "Doe");
        insertAndCheckSubElement(directory1UUID, directory2Attributes);

        // Insert a filter in the directory2
        UUID filter1UUID = UUID.randomUUID();
        ElementAttributes filter1Attributes = toElementAttributes(filter1UUID, "filter1", FILTER, "Doe");
        insertAndCheckSubElement(directory2UUID, filter1Attributes);

        mockMvc.perform(get("/v1/elements/" + filter1UUID + "/path")
                   .header("userId", "User"))
            .andExpect(status().isOk());

        // Trying to get path of forbidden element
        mockMvc.perform(get("/v1/elements/" + directory2UUID + "/path")
                   .header("userId", "User"))
            .andExpect(status().isOk());
    }

    @Test
    public void testGetPathOfRootDir() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(rootDirUuid, "Doe");
        assertRequestsCount(1, 0, 0, 0);

        assertEquals(
                path.stream()
                    .map(ElementAttributes::getElementUuid)
                    .collect(Collectors.toList()),
                Arrays.asList(rootDirUuid)
        );
    }

    @Test
    public void testGetPathOfNotFound() throws Exception {
        UUID unknownElementUuid = UUID.randomUUID();

        mockMvc.perform(get("/v1/elements/" + unknownElementUuid + "/path")
                .header("userId", "user1")).andExpect(status().isNotFound());
    }

    @Test
    public void testTwoUsersTwoPublicDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());

        // Insert a root directory user1
        ElementAttributes rootDir1 = retrieveInsertAndCheckRootDirectory("rootDir1", "user1");
        // Insert a root directory user2
        ElementAttributes rootDir2 = retrieveInsertAndCheckRootDirectory("rootDir2", "user2");

        checkRootDirectoriesList("user1",
            List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
            )
        );

        checkRootDirectoriesList("user2",
            List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
            )
        );

        //Cleaning Test
        deleteElement(rootDir1.getElementUuid(), rootDir1.getElementUuid(), "user1", true, false, 0);
        deleteElement(rootDir2.getElementUuid(), rootDir2.getElementUuid(), "user2", true, false, 0);
    }

    @Test
    public void testMoveElement() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        // Insert another root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, directory20Attributes);

        // Insert a filter in the last subdirectory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, "Doe");
        insertAndCheckSubElement(directory21UUID, filterAttributes);

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
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        // Insert another root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21PrivateUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21PrivateUUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, directory20Attributes);

        // Insert a filter in the last subdirectory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, "Doe");
        insertAndCheckSubElement(directory21PrivateUUID, filterAttributes);

        assertNbElementsInRepositories(4);

        // Move from one folder to another folder is forbidden if the issuer of the operation isn't the element's owner
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Roger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(filterUuid))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(4);

        // Move from one folder to another folder is allowed if the issuer of the operation is the element's owner
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
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        checkElementNameExistInDirectory(rootDir10Uuid, "filter", FILTER, HttpStatus.OK);
    }

    @Test
    public void testMoveUnallowedElement() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Unallowed User");

        // Insert another public root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21PrivateUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21PrivateUUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, directory20Attributes);

        // Insert a filter in the last subdirectory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, "Doe");
        insertAndCheckSubElement(directory21PrivateUUID, filterAttributes);

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
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, filterAttributes);

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
        // Insert root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        // Insert a filter in this directory
        UUID filterUuid = UUID.randomUUID();
        ElementAttributes filterAttributes = toElementAttributes(filterUuid, "filter", FILTER, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, filterAttributes);

        // Insert a subDirectory20 in the root20 directory
        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, directory20Attributes);

        // Insert a filter in the last subdirectory with the same name and type as the 1st one
        UUID filterwithSameNameAndTypeUuid = UUID.randomUUID();
        ElementAttributes filterwithSameNameAndTypeAttributes = toElementAttributes(filterwithSameNameAndTypeUuid, "filter", FILTER, "Doe");
        insertAndCheckSubElement(directory21UUID, filterwithSameNameAndTypeAttributes);

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
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID filter1Uuid = UUID.randomUUID();
        ElementAttributes filter1Attributes = toElementAttributes(filter1Uuid, "filter1", FILTER, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, filter1Attributes);

        UUID filter2Uuid = UUID.randomUUID();
        ElementAttributes filter2Attributes = toElementAttributes(filter2Uuid, "filter2", FILTER, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, filter2Attributes);

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
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, directory20Attributes);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(directory21UUID))))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(3);
    }

    @Test
    public void testMoveStudy() throws Exception {
        when(studyService.notifyStudyUpdate(any(), any())).thenReturn(ResponseEntity.of(Optional.empty()));
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID study21UUID = UUID.randomUUID();
        ElementAttributes study21Attributes = toElementAttributes(study21UUID, "Study21", STUDY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, study21Attributes);

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
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDir20Uuid, directory20Attributes);

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

        ElementAttributes rootDir1 = retrieveInsertAndCheckRootDirectory("rootDir1", "user1");
        // Insert a root directory user2
        ElementAttributes rootDir2 = retrieveInsertAndCheckRootDirectory("rootDir2", "user2");

        checkRootDirectoriesList("user1",
            List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
            )
        );

        checkRootDirectoriesList("user2", List.of(
                toElementAttributes(rootDir1.getElementUuid(), "rootDir1", DIRECTORY, "user1", null, rootDir1.getCreationDate(), rootDir1.getLastModificationDate(), "user1"),
                toElementAttributes(rootDir2.getElementUuid(), "rootDir2", DIRECTORY, "user2", null, rootDir2.getCreationDate(), rootDir2.getLastModificationDate(), "user2")
        ));
        //Cleaning Test
        deleteElement(rootDir1.getElementUuid(), rootDir1.getElementUuid(), "user1", true, false, 0);
        deleteElement(rootDir2.getElementUuid(), rootDir2.getElementUuid(), "user2", true, false, 0);
    }

    @Test
    public void testTwoUsersTwoDirectories() throws Exception {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());
        // Insert a root directory user1
        ElementAttributes rootDir1 = retrieveInsertAndCheckRootDirectory("rootDir1", "user1");
        UUID rootDir1Uuid = rootDir1.getElementUuid();
        Instant rootDir1CreationDate = rootDir1.getCreationDate();
        // Insert a root directory user2
        ElementAttributes rootDir2 = retrieveInsertAndCheckRootDirectory("rootDir2", "user2");
        UUID rootDir2Uuid = rootDir2.getElementUuid();
        Instant rootDir2CreationDate = rootDir2.getCreationDate();

        checkRootDirectoriesList("user1", List.of(toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, "user1", null, rootDir1CreationDate, rootDir1CreationDate, "user1"),
                toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, "user2", null, rootDir2CreationDate, rootDir2CreationDate, "user2")));

        checkRootDirectoriesList("user2", List.of(toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, "user1", null, rootDir1CreationDate, rootDir1CreationDate, "user1"), toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, "user2", null, rootDir2CreationDate, rootDir2CreationDate, "user2")));

        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, false, 0);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false, 0);
    }

    @Test
    public void testTwoUsersTwoStudiesInPublicDirectory() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        // Insert a study in the root directory by the user1
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, "user2", "descr study2");
        insertAndCheckSubElement(rootDirUuid, study2Attributes);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1", List.of(study1Attributes, study2Attributes));

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(study1Attributes, study2Attributes));
        deleteElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", false, true, 0);
        checkElementNotFound(study1Attributes.getElementUuid(), "user1");

        deleteElement(study2Attributes.getElementUuid(), rootDirUuid, "user2", false, true, 0);
        checkElementNotFound(study2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false, 0);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersElementsWithSameName() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        // Insert a study with the same name in the root directory by the user1 and expect a 403
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, "user1");
        insertExpectFail(rootDirUuid, study2Attributes);

        // Insert a study in the root directory by the user1
        ElementAttributes study3Attributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study3Attributes);

        // Insert a study with the same name in the root directory by the user1 and expect a 403
        ElementAttributes study4Attributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, "user1");
        insertExpectFail(rootDirUuid, study4Attributes);

        // Insert a filter with the same name in the root directory by the user1 and expect ok since it's not the same type
        ElementAttributes filterAttributes = toElementAttributes(UUID.randomUUID(), "study3", FILTER, "user1");
        insertAndCheckSubElement(rootDirUuid, filterAttributes);

        // Insert a study with the same name in the root directory by the user2 should not work even if the study by user 1 is private
        ElementAttributes study5Attributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, "user3");
        insertExpectFail(rootDirUuid, study5Attributes);
    }

    @Test
    public void testTwoUsersTwoStudies() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory by Doe
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        // Insert a study in the root directory by the user2
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, "user2");
        insertAndCheckSubElement(rootDirUuid, study2Attributes);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1", List.of(study1Attributes, study2Attributes));

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(study1Attributes, study2Attributes));

        deleteElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", false, true, 0);
        checkElementNotFound(study1Attributes.getElementUuid(), "user1");

        deleteElement(study2Attributes.getElementUuid(), rootDirUuid, "user2", false, true, 0);
        checkElementNotFound(study2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false, 0);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testRecursiveDelete() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "userId");

        // Insert a study in the root directory by the userId
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, "userId", "descr study1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        // Insert a study in the root directory by the userId;
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, "userId");
        insertAndCheckSubElement(rootDirUuid, study2Attributes);

        // Insert a subDirectory
        ElementAttributes subDirAttributes = toElementAttributes(UUID.randomUUID(), "subDir", DIRECTORY, "userId");
        insertAndCheckSubElement(rootDirUuid, subDirAttributes);

        // Insert a study in the root directory by the userId
        ElementAttributes subDirStudyAttributes = toElementAttributes(UUID.randomUUID(), "study3", STUDY, "userId", "descr study3");

        insertAndCheckSubElement(subDirAttributes.getElementUuid(), subDirStudyAttributes);

        assertNbElementsInRepositories(5);

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, false, 3);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(study1Attributes.getElementUuid(), "userId");
        checkElementNotFound(study2Attributes.getElementUuid(), "userId");
        checkElementNotFound(subDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(subDirStudyAttributes.getElementUuid(), "userId");

        assertNbElementsInRepositories(0);
    }

    @Test
    public void testRenameStudy() throws Exception {
        when(studyService.notifyStudyUpdate(any(), any())).thenReturn(ResponseEntity.of(Optional.empty()));
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory by user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_UUID, "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        assertNbElementsInRepositories(2);

        renameElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", "newName1", false);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Attributes.getElementUuid(), "newName1", STUDY, "user1", null, study1Attributes.getCreationDate(), study1Attributes.getLastModificationDate(), "user1")));

        assertNbElementsInRepositories(2);
    }

    @Test
    public void testRenameStudyToSameName() throws Exception {
        when(studyService.notifyStudyUpdate(any(), any())).thenReturn(ResponseEntity.of(Optional.empty()));
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_UUID, "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        // Updating to same name should not send error
        renameElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", "study1", false);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, "user1", null, study1Attributes.getCreationDate(), study1Attributes.getLastModificationDate(), "user1")));
    }

    @Test
    public void testRenameStudyForbiddenFail() throws Exception {
        checkRootDirectoriesList("user1", List.of());

        // Insert a root directory by user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "user1");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_RENAME_FORBIDDEN_UUID, "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        //the name should not change
        renameElementExpectFail(study1Attributes.getElementUuid(), "user2", "newName1", 403);
        checkDirectoryContent(rootDirUuid, "user2", List.of(study1Attributes));
    }

    @Test
    public void testRenameElementWithSameNameAndTypeInSameDirectory() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study1 in the root directory by Doe
        ElementAttributes study1Attributes = toElementAttributes(UUID.randomUUID(), "study1", STUDY, "Doe");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);

        // Insert a study2 in the root directory by Doe;
        ElementAttributes study2Attributes = toElementAttributes(UUID.randomUUID(), "study2", STUDY, "Doe");
        insertAndCheckSubElement(rootDirUuid, study2Attributes);

        // Renaming file to an already existing name should fail
        renameElementExpectFail(study1Attributes.getElementUuid(), "Doe", "study2", 403);
    }

    @Test
    public void testRenameDirectoryNotAllowed() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        ElementAttributes rootDir = retrieveInsertAndCheckRootDirectory("rootDir1", "Doe");
        UUID rootDirUuid = rootDir.getElementUuid();
        Instant rootDirCreationDate = rootDir.getCreationDate();

        assertNbElementsInRepositories(1);

        //the name should not change
        renameElementExpectFail(rootDirUuid, "user1", "newName1", 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, "Doe", null, rootDirCreationDate, rootDirCreationDate, "Doe")));

        assertNbElementsInRepositories(1);
    }

    @Test
    public void testDirectoryContentAfterInsertStudy() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_UPDATE_ACCESS_RIGHT_UUID, "study1", STUDY, "user1");
        insertAndCheckSubElement(rootDirUuid, study1Attributes);
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, "user1", null, study1Attributes.getCreationDate(), study1Attributes.getLastModificationDate(), "user1")));
    }

    @Test
    public void testDirectory() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory by user1
        ElementAttributes rootDir = retrieveInsertAndCheckRootDirectory("rootDir1", "Doe");
        UUID rootDirUuid = rootDir.getElementUuid();
        Instant rootDirCreationDate = rootDir.getCreationDate();

        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, "Doe", null, rootDirCreationDate, rootDirCreationDate, "Doe")));
    }

    @SneakyThrows
    @Test
    public void testEmitDirectoryChangedNotification() {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory by the user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a contingency list in the root directory by the user1
        ElementAttributes contingencyListAttributes = toElementAttributes(UUID.randomUUID(), "CL1", CONTINGENCY_LIST, "Doe");
        insertAndCheckSubElement(rootDirUuid, contingencyListAttributes);

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
        // Insert a root directory by the user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "user1");

        // Insert a contingency list in the root directory by the user1
        ElementAttributes contingencyAttributes = toElementAttributes(CONTINGENCY_LIST_UUID, "Contingency", CONTINGENCY_LIST, "user1");
        insertAndCheckSubElement(rootDirUuid, contingencyAttributes);

        // Insert a filter in the root directory by the user1
        ElementAttributes filterAttributes = toElementAttributes(FILTER_UUID, "Filter", FILTER, "user1");
        insertAndCheckSubElement(rootDirUuid, filterAttributes);

        // Insert a script in the root directory by the user1
        ElementAttributes scriptAttributes = toElementAttributes(UUID.randomUUID(), "Script", FILTER, "user1");
        insertAndCheckSubElement(rootDirUuid, scriptAttributes);

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

        renameElement(contingencyAttributes.getElementUuid(), rootDirUuid, "user1", "newContingency", false);
        renameElement(filterAttributes.getElementUuid(), rootDirUuid, "user1", "newFilter", false);
        renameElement(scriptAttributes.getElementUuid(), rootDirUuid, "user1", "newScript", false);
        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        ElementAttributes newContingency = toElementAttributes(contingencyAttributes.getElementUuid(), "newContingency", CONTINGENCY_LIST, "user1", null, contingencyAttributes.getCreationDate(), contingencyAttributes.getLastModificationDate(), "user1");
        ElementAttributes newFilter = toElementAttributes(filterAttributes.getElementUuid(), "newFilter", FILTER, "user1", null, filterAttributes.getCreationDate(), filterAttributes.getLastModificationDate(), "user1");
        ElementAttributes newScript = toElementAttributes(scriptAttributes.getElementUuid(), "newScript", FILTER, "user1", null, scriptAttributes.getCreationDate(), scriptAttributes.getLastModificationDate(), "user1");

        assertThat(res).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(List.of(newContingency, newFilter, newScript));

        ElementAttributes directory = retrieveInsertAndCheckRootDirectory("testDir", "user1");
        List<ElementAttributes> result = getElements(List.of(FILTER_UUID, UUID.randomUUID(), directory.getElementUuid()), "user1", false, List.of(FILTER), 200);
        assertEquals(2, result.size());
        result.sort(Comparator.comparing(ElementAttributes::getElementName));

        result.sort(Comparator.comparing(ElementAttributes::getElementName));
        assertThat(result).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(List.of(
                toElementAttributes(FILTER_UUID, "newFilter", FILTER, "user1", 0, null, filterAttributes.getCreationDate(), filterAttributes.getLastModificationDate(), "user1"),
                directory
        ));

        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, "user1");
        insertAndCheckSubElement(rootDirUuid, subDirAttributes);
        insertAndCheckSubElement(subDirAttributes.getElementUuid(), toElementAttributes(null, "subDirContingency", CONTINGENCY_LIST, "user1"));
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
        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "user1");

        // Insert a contingency list in the root directory by the user1
        ElementAttributes contingencyAttributes = toElementAttributes(CONTINGENCY_LIST_UUID, "Contingency", CONTINGENCY_LIST, "user1");
        insertAndCheckSubElement(rootDirUuid, contingencyAttributes);

        // Insert a filter in the root directory by the user1
        ElementAttributes filterAttributes = toElementAttributes(FILTER_UUID, "Filter", FILTER, "user1");
        insertAndCheckSubElement(rootDirUuid, filterAttributes);

        // Insert a script in the root directory by the user1
        ElementAttributes scriptAttributes = toElementAttributes(UUID.randomUUID(), "Script", FILTER, "user1");
        insertAndCheckSubElement(rootDirUuid, scriptAttributes);

        var res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(contingencyAttributes, filterAttributes, scriptAttributes)));

        renameElement(contingencyAttributes.getElementUuid(), rootDirUuid, "user1", "newContingency", false);
        renameElement(filterAttributes.getElementUuid(), rootDirUuid, "user1", "newFilter", false);
        renameElement(scriptAttributes.getElementUuid(), rootDirUuid, "user1", "newScript", false);
        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        assertThat(res).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(List.of(
                toElementAttributes(contingencyAttributes.getElementUuid(), "newContingency", CONTINGENCY_LIST, "user1", null, contingencyAttributes.getCreationDate(), contingencyAttributes.getLastModificationDate(), "user1"),
                toElementAttributes(filterAttributes.getElementUuid(), "newFilter", FILTER, "user1", null, filterAttributes.getCreationDate(), filterAttributes.getLastModificationDate(), "user1"),
                toElementAttributes(scriptAttributes.getElementUuid(), "newScript", FILTER, "user1", null, scriptAttributes.getCreationDate(), scriptAttributes.getLastModificationDate(), "user1")
        ));
    }

    @Test
    public void testRootDirectoryExists() throws Exception {
        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", "user1");

        UUID directoryUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directoryUUID, "directoryToFind", DIRECTORY, "Doe");
        insertAndCheckSubElement(rootDirUuid, directory20Attributes);

        checkRootDirectoryExists("rootDirToFind");
        checkRootDirectoryNotExists("directoryToFind");
        checkRootDirectoryNotExists("notExistingRootDir");

    }

    @Test
    public void testCreateDirectoryWithEmptyName() throws Exception {
        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", "user1");

        // Insert a directory with empty name in the root directory and expect a 403
        ElementAttributes directoryWithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", DIRECTORY, "user1");
        insertExpectFail(rootDirUuid, directoryWithoutNameAttributes);
        String requestBody = objectMapper.writeValueAsString(new RootDirectoryAttributes("", "userId", null, null, null, "userId"));

        // Insert a root directory by user1 with empty name and expect 403
        mockMvc.perform(post(String.format("/v1/root-directories"))
                        .header("userId", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(1);
    }

    @Test
    public void testCreateElementWithEmptyName() throws Exception {
        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", "user1");

        // Insert a study with empty name in the root directory and expect a 403
        ElementAttributes studyWithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", STUDY, "user1");
        insertExpectFail(rootDirUuid, studyWithoutNameAttributes);

       // Insert a filter with empty name in the root directory and expect a 403
        ElementAttributes filterWithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", FILTER, "user1");
        insertExpectFail(rootDirUuid, filterWithoutNameAttributes);

        assertNbElementsInRepositories(1);
    }

    @Test
    public void testElementUpdateNotification() throws Exception {
        // Insert a root directory
        ElementAttributes newDirectory = retrieveInsertAndCheckRootDirectory("newDir", "userId");
        UUID uuidNewDirectory = newDirectory.getElementUuid();

        // Insert a  sub-element of type STUDY
        ElementAttributes subEltAttributes = toElementAttributes(UUID.randomUUID(), "newStudy", STUDY, "userId", "descr study");
        insertAndCheckSubElement(uuidNewDirectory, subEltAttributes);

        Instant newModificationDate = Instant.now().truncatedTo(ChronoUnit.MICROS);

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

        assertEquals(newModificationDate, updatedElement.getLastModificationDate());
        assertEquals(userMakingModification, updatedElement.getLastModifiedBy());
    }

    @Test
    public void testSupervision() throws Exception {
        MvcResult mvcResult;
        // Test get elasticsearch host
        mvcResult = mockMvc.perform(get("/v1/supervision/elasticsearch-host"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("localhost:" + expectedEsPort, mvcResult.getResponse().getContentAsString());

        // Test get indexed elements index name
        mvcResult = mockMvc.perform(get("/v1/supervision/elements/index-name"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals("directory-elements", mvcResult.getResponse().getContentAsString());

        // create some elements here
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir", "userId");

        UUID dirUuid = UUID.randomUUID();
        ElementAttributes directoryAttributes = toElementAttributes(dirUuid, "directory", DIRECTORY, "userId");
        insertAndCheckSubElement(rootDirUuid, directoryAttributes);
        ElementAttributes subdirectoryAttributes = toElementAttributes(UUID.randomUUID(), "subdirectory", DIRECTORY, "userId");
        insertAndCheckSubElement(dirUuid, subdirectoryAttributes);
        ElementAttributes studyAttributes = toElementAttributes(UUID.randomUUID(), "study", STUDY, "userId");
        insertAndCheckSubElement(rootDirUuid, studyAttributes);

        // Test get indexed elements counts
        mvcResult = mockMvc.perform(get("/v1/supervision/elements/indexation-count"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(4, Long.parseLong(mvcResult.getResponse().getContentAsString()));

        // Test indexed elements deletion
        mvcResult = mockMvc.perform(delete("/v1/supervision/elements/indexation"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(4, Long.parseLong(mvcResult.getResponse().getContentAsString()));

        // reindex
        mockMvc.perform(post("/v1/supervision/elements/reindex"))
            .andExpect(status().isOk())
            .andReturn();

        mvcResult = mockMvc.perform(get("/v1/supervision/elements/indexation-count"))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(4, Long.parseLong(mvcResult.getResponse().getContentAsString()));
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
        assertThat(list).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(elementAttributes);
    }

    private ElementAttributes retrieveInsertAndCheckRootDirectory(String rootDirectoryName, String userId) throws Exception {
        String response = mockMvc.perform(post("/v1/root-directories")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, userId, null, null, null, userId))))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();
        Instant creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getCreationDate();
        Instant modificationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getLastModificationDate();

        ElementAttributes newDirectoryAttributes = toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, userId, null, creationDateNewDirectory, modificationDateNewDirectory, userId);
        assertElementIsProperlyInserted(newDirectoryAttributes);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        return newDirectoryAttributes;
    }

    private UUID insertAndCheckRootDirectory(String rootDirectoryName, String userId) throws Exception {
        String response = mockMvc.perform(post("/v1/root-directories")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, userId, null, null, null, null))))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();
        Instant creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getCreationDate();

        assertElementIsProperlyInserted(toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, userId, null, creationDateNewDirectory, creationDateNewDirectory, userId));

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        return uuidNewDirectory;
    }

    private List<ElementAttributes> getElements(List<UUID> elementUuids, String userId, boolean strictMode, int httpCodeExpected) throws Exception {
        return getElements(elementUuids, userId, strictMode, null, httpCodeExpected);
    }

    private List<ElementAttributes> getElements(List<UUID> elementUuids, String userId, boolean strictMode, List<String> elementTypes, int httpCodeExpected) throws Exception {
        var ids = elementUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        var typesPath = elementTypes != null ? "&elementTypes=" + String.join(",", elementTypes) : "";

        // get sub-elements list
        if (httpCodeExpected == 200) {
            MvcResult result = mockMvc.perform(get("/v1/elements?strictMode=" + (strictMode ? "true" : "false") + "&ids=" + ids + typesPath)
                    .header("userId", userId))
                    .andExpectAll(status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
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

    private void insertSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes, boolean allowNewName) throws Exception {
        // Insert a sub-element in a directory
        MvcResult response = mockMvc.perform(post("/v1/directories/" + parentDirectoryUUid + "/elements" + (allowNewName ? "?allowNewName=true" : ""))
                        .header("userId", subElementAttributes.getOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subElementAttributes)))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        UUID uuidNewDirectory = objectMapper.readValue(Objects.requireNonNull(response).getResponse().getContentAsString(), ElementAttributes.class)
                .getElementUuid();
        Instant creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response).getResponse().getContentAsString(), ElementAttributes.class).getCreationDate();
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
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    private void insertAndCheckSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) throws Exception {
        insertSubElement(parentDirectoryUUid, subElementAttributes, false);
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

    private void renameElement(UUID elementUuidToRename, UUID elementUuidHeader, String userId, String newName, boolean isRoot) throws Exception {
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
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
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
        String elementTypes = !CollectionUtils.isEmpty(types) ? "?elementTypes=" + String.join(",", types) : "";
        String response = mockMvc.perform(get("/v1/directories/" + parentDirectoryUuid + "/elements" + elementTypes)
                .header("userId", userId))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<ElementAttributes> result = objectMapper.readValue(response, new TypeReference<>() {
        });
        assertThat(list).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(result);
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

    private void deleteElement(UUID elementUuidToBeDeleted, UUID elementUuidHeader, String userId, boolean isRoot, boolean isStudy, int numberOfChildStudies) throws Exception {
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
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(isRoot ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
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

        var directoryId = insertAndCheckRootDirectory("newDir", "userId");

        mockMvc.perform(get("/v1/directories/" + directoryId + "/" + "pouet" + "/newNameCandidate?type=" + STUDY)
                .header("userId", "youplaboum"))
            .andExpect(status().isOk());

        var name = "newStudy";
        // check when no elements is corresponding (empty folder
        assertEquals("newStudy", candidateName(directoryId, name, STUDY));
        var element = toElementAttributes(UUID.randomUUID(), name, STUDY, "userId");
        insertAndCheckSubElement(directoryId, element);
        var newCandidateName = candidateName(directoryId, name, STUDY);
        assertEquals("newStudy(1)", newCandidateName);
        element.setElementName(newCandidateName);
        element.setElementUuid(UUID.randomUUID());
        insertAndCheckSubElement(directoryId, element);
        assertEquals("newStudy(2)", candidateName(directoryId, name, STUDY));
        assertEquals("newStudy", candidateName(directoryId, name, CONTINGENCY_LIST));
    }

    @Test
    @SneakyThrows
    public void testCreateElementInDirectory() {
        String userId = "user";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ElementAttributes caseElement = ElementAttributes.toElementAttributes(UUID.randomUUID(), "caseName", "CASE", "user", null, now, now, userId);
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
    public void duplicateElementTest() throws Exception {
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir", "user1");
        ElementAttributes caseElement = toElementAttributes(UUID.randomUUID(), "caseName", "CASE", "user1");
        insertAndCheckSubElement(rootDirUuid, caseElement);
        UUID elementUUID = caseElement.getElementUuid();
        UUID newElementUuid = UUID.randomUUID();
        // duplicate the element
        ElementAttributes duplicatedElement = directoryService.duplicateElement(elementUUID, newElementUuid, null, "user1");
        assertEquals("caseName(1)", duplicatedElement.getElementName());
        assertEquals(newElementUuid, duplicatedElement.getElementUuid());

        List<DirectoryElementEntity> directoryElementList = directoryElementRepository.findAll();
        //there should be 3 elements, the 2 directories created and the one element inside
        assertEquals(3, directoryElementList.size());
        DirectoryElementEntity sourceDirectoryElementEntity = directoryElementList.stream().filter(directoryElementEntity -> directoryElementEntity.getId().equals(elementUUID)).findFirst().orElseThrow();

        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("user1", headers.get(HEADER_USER_ID));
        assertEquals(sourceDirectoryElementEntity.getParentId(), headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    @Test
    @SneakyThrows
    public void testCreateModificationElementWithAutomaticNewName() {
        final String userId = "Doe";
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirModif", userId);

        // insert a new element
        final String modifElementName = "modif";
        ElementAttributes modifAttributes = toElementAttributes(UUID.randomUUID(), modifElementName, MODIFICATION, userId);
        insertAndCheckSubElement(rootDirUuid, modifAttributes);

        // insert another new element having the same existing name, allowing duplication with a new name
        ElementAttributes anotherModifAttributes = toElementAttributes(UUID.randomUUID(), modifElementName, MODIFICATION, userId);
        insertSubElement(rootDirUuid, anotherModifAttributes, true);
        // expecting "incremental name"
        anotherModifAttributes.setElementName(modifElementName + "(1)");
        assertElementIsProperlyInserted(anotherModifAttributes);

        checkDirectoryContent(rootDirUuid, userId, List.of(MODIFICATION), List.of(modifAttributes, anotherModifAttributes));

        assertNbElementsInRepositories(3);
    }

    @Test
    public void testSearch() throws Exception {

        //                          root (userId2)
        //         /                          |               \
        //       dir1 (userId1)      dir2 (userId2)          dir3 (userId3)
        //        |                                                    |
        //       dir4 (userId1)                                       dir5 (userId3)

        ElementAttributes rootDirectory = retrieveInsertAndCheckRootDirectory("directory", USERID_2);
        UUID rootDirectoryUuid = rootDirectory.getElementUuid();

        UUID subDirUuid1 = UUID.randomUUID();
        ElementAttributes subDirAttributes1 = toElementAttributes(subDirUuid1, "newSubDir1", DIRECTORY, USERID_1);

        UUID subDirUuid2 = UUID.randomUUID();
        ElementAttributes subDirAttributes2 = toElementAttributes(subDirUuid2, "newSubDir2", DIRECTORY, USERID_2);

        UUID subDirUuid3 = UUID.randomUUID();
        ElementAttributes subDirAttributes3 = toElementAttributes(subDirUuid3, "newSubDir3", DIRECTORY, USERID_3);

        UUID subDirUuid4 = UUID.randomUUID();
        ElementAttributes subDirAttributes4 = toElementAttributes(subDirUuid4, "newSubDir4", DIRECTORY, USERID_1);

        UUID subDirUuid5 = UUID.randomUUID();
        ElementAttributes subDirAttributes5 = toElementAttributes(subDirUuid5, "newSubDir5", DIRECTORY, USERID_3);

        insertAndCheckSubElement(rootDirectoryUuid, subDirAttributes1);
        insertAndCheckSubElement(rootDirectoryUuid, subDirAttributes2);
        insertAndCheckSubElement(rootDirectoryUuid, subDirAttributes3);
        insertAndCheckSubElement(subDirUuid1, subDirAttributes4);
        insertAndCheckSubElement(subDirUuid3, subDirAttributes5);

        insertAndCheckSubElement(subDirUuid1, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, STUDY, USERID_1, ""));

        insertAndCheckSubElement(subDirUuid2, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, STUDY, USERID_2, ""));

        insertAndCheckSubElement(subDirUuid3, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, STUDY, USERID_3, ""));

        insertAndCheckSubElement(subDirUuid4, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, STUDY, USERID_1, ""));

        insertAndCheckSubElement(subDirUuid5, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, STUDY, USERID_3, ""));

        MvcResult mvcResult;

        mvcResult = mockMvc
                .perform(get("/v1/elements/indexation-infos?userInput={request}", "r").header(USER_ID, USERID_1))
                .andExpectAll(status().isOk()).andReturn();
        List<Object> result = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(5, result.size());
        output.clear();

        mvcResult = mockMvc
                .perform(get("/v1/elements/indexation-infos?userInput={request}", "r").header(USER_ID, USERID_2))
                .andExpectAll(status().isOk()).andReturn();
        result = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(5, result.size());
        output.clear();

        mvcResult = mockMvc
                .perform(get("/v1/elements/indexation-infos?userInput={request}", "r").header(USER_ID, USERID_3))
                .andExpectAll(status().isOk()).andReturn();
        result = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(5, result.size());
        output.clear();
    }

    @Test
    public void testElementsAccessibilityOk() throws Exception {
        checkRootDirectoriesList("userId", List.of());
        // Insert a root directory
        ElementAttributes newDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewDirectory = newDirectory.getElementUuid();

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, USER_ID);
        insertAndCheckSubElement(uuidNewDirectory, subDirAttributes);
        checkDirectoryContent(uuidNewDirectory, USER_ID, List.of(subDirAttributes));
        // The subDirAttributes is created by the userId,so it is deletable
        mockMvc
                .perform(head("/v1/elements?forDeletion=true&ids={ids}", subDirAttributes.getElementUuid()).header(USER_ID, USER_ID))
                .andExpectAll(status().isOk()).andReturn();
        deleteElement(subDirAttributes.getElementUuid(), uuidNewDirectory, "userId", false, false, 0);
    }

    @Test
    public void testElementsAccessibilityNotOk() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory
        ElementAttributes newDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewDirectory = newDirectory.getElementUuid();

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, USER_ID);
        insertAndCheckSubElement(uuidNewDirectory, subDirAttributes);
        checkDirectoryContent(uuidNewDirectory, USER_ID, List.of(subDirAttributes));
        //The subDirAttributes is created by the userId,so the userId1 is not allowed to delete it.
        mockMvc
                .perform(head("/v1/elements?forDeletion=true&ids={ids}", subDirAttributes.getElementUuid()).header(USER_ID, USERID_1))
                .andExpectAll(status().isNoContent()).andReturn();
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
