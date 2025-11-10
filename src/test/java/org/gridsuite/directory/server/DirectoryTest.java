/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Iterables;
import com.jparams.verifier.tostring.ToStringVerifier;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.repository.PermissionId;
import org.gridsuite.directory.server.repository.PermissionRepository;
import org.gridsuite.directory.server.services.ConsumerService;
import org.gridsuite.directory.server.services.UserAdminService;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.directory.server.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.directory.server.NotificationService.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.gridsuite.directory.server.services.ConsumerService.HEADER_STUDY_UUID;
import static org.gridsuite.directory.server.services.ConsumerService.UPDATE_TYPE_STUDIES;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.jsonResponse;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    public static final String TYPE_01 = "TYPE_01";
    public static final String TYPE_02 = "TYPE_02";
    public static final String TYPE_03 = "TYPE_03";
    public static final String TYPE_04 = "TYPE_04";
    public static final String TYPE_05 = "TYPE_05";
    public static final String DIRECTORY = "DIRECTORY";
    public static final String CASE = "CASE";
    public static final String MODIFICATION = "MODIFICATION";

    private static final long TIMEOUT = 1000;
    private static final UUID TYPE_01_RENAME_UUID = UUID.randomUUID();
    private static final UUID TYPE_01_UPDATE_ACCESS_RIGHT_UUID = UUID.randomUUID();
    private static final UUID TYPE_03_UUID = UUID.randomUUID();
    private static final UUID TYPE_02_UUID = UUID.randomUUID();

    public static final String HEADER_MODIFIED_BY = "modifiedBy";
    public static final String HEADER_MODIFICATION_DATE = "modificationDate";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";
    private static final String HEADER_USER_ROLES = "roles";
    public static final String USER_ID = "userId";
    public static final String USERID_1 = "userId1";
    public static final String USERID_2 = "userId2";
    public static final String USERID_3 = "userId3";
    public static final String RECOLLEMENT = "recollement";
    public static final String ALL_USERS = "ALL_USERS";
    private static final String NOT_ADMIN_USER = "notAdmin";
    private static final String ADMIN_USER = "adminUser";
    public static final String NO_ADMIN_ROLE = "NO_ADMIN_ROLE";
    public static final String ADMIN_ROLE = "ADMIN_EXPLORE";
    private final String elementUpdateDestination = "element.update";
    private final String directoryUpdateDestination = "directory.update";
    private final String studyUpdateDestination = "study.update";

    private static final String GROUPS_SUFFIX = "/groups";
    private static final String EMPTY_GROUPS_JSON = "[]";

    @Autowired
    RestClient restClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private UserAdminService userAdminService;

    MockWebServer server;

    @Autowired
    private PermissionRepository permissionRepository;

    @MockitoSpyBean
    ConsumerService consumeService;

    @Before
    public void setup() {
        setupMockWebServer();

        cleanDB();
    }

    private void setupMockWebServer() {
        server = new MockWebServer();
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);

        userAdminService.setUserAdminServerBaseUri(baseUrl);

        // Set up the dispatcher to handle all requests
        final Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = request.getMethod();

                if ("GET".equals(method) && path.endsWith(GROUPS_SUFFIX)) {
                    return jsonResponse(HttpStatus.OK, EMPTY_GROUPS_JSON);
                }

                return new MockResponse().setResponseCode(HttpStatus.I_AM_A_TEAPOT.value());
            }
        };
        server.setDispatcher(dispatcher);
    }

    private void cleanDB() {
        directoryElementRepository.deleteAll();
        directoryElementInfosRepository.deleteAll();
        permissionRepository.deleteAll();
        SQLStatementCountValidator.reset();
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
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", "userId");
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();
        Instant creationDateNewDirectory = newRootDirectory.getCreationDate();
        Instant modificationDateNewDirectory = newRootDirectory.getLastModificationDate();

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, "userId");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, subDirAttributes);
        checkDirectoryContent(uuidNewRootDirectory, "userId", List.of(subDirAttributes));

        // Insert a  sub-element of type TYPE_01
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), "elementName", TYPE_01, "userId", "descr element");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, elementAttributes);
        checkDirectoryContent(uuidNewRootDirectory, "userId", List.of(subDirAttributes, elementAttributes));

        checkElementNameExistInDirectory(uuidNewRootDirectory, "elementName", TYPE_01, HttpStatus.OK);
        checkElementNameExistInDirectory(uuidNewRootDirectory, "tutu", TYPE_01, HttpStatus.NO_CONTENT);

        // Delete the sub-directory newSubDir
        deleteElement(subDirAttributes.getElementUuid(), uuidNewRootDirectory, "userId", false, true, 0);
        checkDirectoryContent(uuidNewRootDirectory, "userId", List.of(elementAttributes));

        // Delete the sub-element elementName
        deleteElement(elementAttributes.getElementUuid(), uuidNewRootDirectory, "userId", false, true, 0);
        assertDirectoryIsEmpty(uuidNewRootDirectory, "userId");

        // Rename the root directory
        renameElement(uuidNewRootDirectory, uuidNewRootDirectory, "userId", "newName", true);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewRootDirectory, "newName", DIRECTORY, "userId", null, creationDateNewDirectory, modificationDateNewDirectory, "userId")));

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewRootDirectory, "newName", DIRECTORY, "userId", null, creationDateNewDirectory, creationDateNewDirectory, "userId")));
        // Add another sub-directory
        ElementAttributes newSubDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, "userId", "descr newSubDir");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, newSubDirAttributes);
        checkDirectoryContent(uuidNewRootDirectory, "userId", List.of(newSubDirAttributes));

        // Add another sub-directory
        ElementAttributes newSubSubDirAttributes = toElementAttributes(null, "newSubSubDir", DIRECTORY, "userId");
        insertAndCheckSubElement(newSubDirAttributes.getElementUuid(), newSubSubDirAttributes);
        checkDirectoryContent(newSubDirAttributes.getElementUuid(), "userId", List.of(newSubSubDirAttributes));

        // Test children number of root directory
        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewRootDirectory, "newName", DIRECTORY, "userId", 1L, null, creationDateNewDirectory, creationDateNewDirectory, "userId")));

        deleteElement(uuidNewRootDirectory, uuidNewRootDirectory, "userId", true, true, 3);
        checkRootDirectoriesList("userId", List.of());

        checkElementNotFound(newSubDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(newSubSubDirAttributes.getElementUuid(), "userId");
    }

    @Test
    public void testGetPathOfElementType01() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, "Doe");
        insertAndCheckSubElement(directory1UUID, directory2Attributes);

        // Insert an element of type TYPE_01 in the directory2
        UUID elementUUID = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUUID, "type01", TYPE_01, "Doe");
        insertAndCheckSubElement(directory2UUID, elementAttributes);
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(elementUUID, "Doe");

        //There is only recursive query and SQLStatementCountValidator ignore them
        assertRequestsCount(0, 0, 0, 0);

        //Check if all element's parents are retrieved in the right order
        assertEquals(
                path.stream()
                    .map(ElementAttributes::getElementUuid)
                    .collect(Collectors.toList()),
                Arrays.asList(rootDirUuid, directory1UUID, directory2UUID, elementUUID)
        );
    }

    @Test
    public void testGetPathOfElementType03() throws Exception {
       // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, "Doe");
        insertAndCheckSubElement(directory1UUID, directory2Attributes);

        // Insert an element of type TYPE_03 in the directory2
        UUID elementUUID = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUUID, "elementName", TYPE_03, "Doe");
        insertAndCheckSubElement(directory2UUID, elementAttributes);
        SQLStatementCountValidator.reset();
        List<ElementAttributes> path = getPath(elementUUID, "Doe");

        //There is only recursive query and SQLStatementCountValidator ignore them
        assertRequestsCount(0, 0, 0, 0);

        //Check if all element's parents are retrieved in the right order
        assertEquals(
                path.stream()
                    .map(ElementAttributes::getElementUuid)
                    .collect(Collectors.toList()),
                Arrays.asList(rootDirUuid, directory1UUID, directory2UUID, elementUUID)
        );
    }

    @Test
    public void testGetPathOfOtherUserElements() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert a subDirectory1 in the root directory
        UUID directory1UUID = UUID.randomUUID();
        ElementAttributes directory1Attributes = toElementAttributes(directory1UUID, "directory1", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, directory1Attributes);

        // Insert a subDirectory2 in the subDirectory1 directory
        UUID directory2UUID = UUID.randomUUID();
        ElementAttributes directory2Attributes = toElementAttributes(directory2UUID, "directory2", DIRECTORY, "Doe");
        insertAndCheckSubElement(directory1UUID, directory2Attributes);

        // Insert an element of type TYPE_03 in the directory2
        UUID elementUUID = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUUID, "elementName", TYPE_03, "Doe");
        insertAndCheckSubElement(directory2UUID, elementAttributes);

        mockMvc.perform(get("/v1/elements/" + elementUUID + "/path")
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

        //There is only recursive query and SQLStatementCountValidator ignore them
        assertRequestsCount(0, 0, 0, 0);

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
        deleteElement(rootDir1.getElementUuid(), rootDir1.getElementUuid(), "user1", true, true, 0);
        deleteElement(rootDir2.getElementUuid(), rootDir2.getElementUuid(), "user2", true, true, 0);
    }

    @Test
    public void testMoveElement() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        // Insert another root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        // Insert a subDirectory20 in the root20 directory
        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, directory20Attributes);

        // Insert an element of type TYPE_03 in the last subdirectory
        UUID elementUuid = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUuid, "type03", TYPE_03, "Doe");
        insertAndCheckSubElement(directory21UUID, elementAttributes);

        assertNbElementsInRepositories(4);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUuid))))
                .andExpect(status().isOk());

        assertNbElementsInRepositories(4);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(directory21UUID, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a root directory creation request message
        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        checkElementNameExistInDirectory(rootDir10Uuid, "type03", TYPE_03, HttpStatus.OK);
    }

    @Test
    public void testMoveElementNotFound() throws Exception {
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID elementUuid = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUuid, "type03", TYPE_03, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, elementAttributes);

        assertNbElementsInRepositories(2);

        UUID unknownUuid = UUID.randomUUID();

        mockMvc.perform(put("/v1/elements/?targetDirectoryUuid=" + rootDir20Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(unknownUuid))))
                .andExpect(status().isInternalServerError());

        mockMvc.perform(put("/v1/elements/?targetDirectoryUuid=" + unknownUuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUuid))))
                .andExpect(status().isInternalServerError());

        assertNbElementsInRepositories(2);
    }

    @Test
    public void testMoveElementWithAlreadyExistingNameAndTypeInDestination() throws Exception {
        // Insert root20 directory
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        // Insert an element of type TYPE_03 in this directory
        UUID elementUuid = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUuid, "elementName", TYPE_03, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, elementAttributes);

        // Insert a subDirectory20 in the root20 directory
        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, directory20Attributes);

        // Insert an element of type TYPE_03 in the last subdirectory with the same name and type as the 1st one
        UUID elementWithSameNameAndTypeUuid = UUID.randomUUID();
        ElementAttributes elementwithSameNameAndTypeAttributes = toElementAttributes(elementWithSameNameAndTypeUuid, "elementName", TYPE_03, "Doe");
        insertAndCheckSubElement(directory21UUID, elementwithSameNameAndTypeAttributes);

        assertNbElementsInRepositories(4);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir20Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementWithSameNameAndTypeUuid))))
                .andExpect(status().isConflict());

        assertNbElementsInRepositories(4);
    }

    @Test
    public void testMoveElementToNotDirectory() throws Exception {
        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID element1Uuid = UUID.randomUUID();
        ElementAttributes element1Attributes = toElementAttributes(element1Uuid, "elementName1", TYPE_03, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, element1Attributes);

        UUID element2Uuid = UUID.randomUUID();
        ElementAttributes element2Attributes = toElementAttributes(element2Uuid, "elementName2", TYPE_03, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, element2Attributes);

        assertNbElementsInRepositories(3);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + element2Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(element1Uuid)))
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
        insertAndCheckSubElementInRootDir(rootDir20Uuid, directory20Attributes);
        ElementAttributes elementAttributes1 = toElementAttributes(UUID.randomUUID(), "element1", TYPE_01, "Doe");
        ElementAttributes elementAttributes2 = toElementAttributes(UUID.randomUUID(), "element2", TYPE_02, "Doe");
        insertAndCheckSubElement(directory21UUID, elementAttributes1);
        insertAndCheckSubElement(directory21UUID, elementAttributes2);

        // test move directory
        moveDirectoryAndCheck(directory21UUID, rootDir20Uuid, rootDir10Uuid, false);

        // test move root directory
        moveDirectoryAndCheck(rootDir20Uuid, null, rootDir10Uuid, true);

        assertNbElementsInRepositories(5);
    }

    private void moveDirectoryAndCheck(UUID directoryUuid,
                                       UUID parentDirectoryUuid,
                                       UUID targetDirectoryUuid,
                                       boolean isMovingDirectoryRoot) throws Exception {
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + targetDirectoryUuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(directoryUuid))))
                .andExpect(status().isOk());

        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(isMovingDirectoryRoot ? directoryUuid : parentDirectoryUuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_DIRECTORY_MOVING));
        assertEquals(isMovingDirectoryRoot ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(targetDirectoryUuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_DIRECTORY_MOVING));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    @Test
    public void testElementMove() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID elementUUID = UUID.randomUUID();
        ElementAttributes elementAttributes = toElementAttributes(elementUUID, "type01", TYPE_01, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, elementAttributes);

        assertNbElementsInRepositories(3);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUUID)))
                )
                .andExpect(status().isOk());

        assertNbElementsInRepositories(3);

        // assert that the broker message has been sent a update notification on directory
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir20Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(false, headers.get(HEADER_IS_DIRECTORY_MOVING));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(false, headers.get(HEADER_IS_DIRECTORY_MOVING));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        // Test move element to its parent => keep the same parent
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir10Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUUID)))
                )
                .andExpect(status().isOk()); // Response status is 200 but nothing changed and no notification should be sent
    }

    @Test
    public void testDirectoryMoveError() throws Exception {
        UUID rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", USER_ID);

        UUID elementUuid1 = UUID.randomUUID();
        ElementAttributes elementAttributes1 = toElementAttributes(elementUuid1, "dir1", DIRECTORY, USER_ID);
        insertAndCheckSubElementInRootDir(rootDir1Uuid, elementAttributes1);

        UUID elementUuid2 = UUID.randomUUID();
        ElementAttributes elementAttributes2 = toElementAttributes(elementUuid2, "dir2", DIRECTORY, USER_ID);
        insertAndCheckSubElement(elementUuid1, elementAttributes2);

        // test move element to be root directory: targetDirectoryUuid = null
        mockMvc.perform(put("/v1/elements")
                        .header("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUuid1)))
                )
                .andExpect(status().isInternalServerError());

        // test move element to one of its descendents
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + elementUuid2)
                        .header("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUuid1)))
                )
                .andExpect(status().isForbidden());

        // test move element to itself
        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + elementUuid1)
                        .header("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(elementUuid1)))
                )
                .andExpect(status().isForbidden());

        assertNbElementsInRepositories(3);
    }

    @Test
    public void testMoveRootDirectory() throws Exception {
        UUID rootDir10Uuid = insertAndCheckRootDirectory("rootDir10", "Doe");

        UUID rootDir20Uuid = insertAndCheckRootDirectory("rootDir20", "Doe");

        UUID directory21UUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directory21UUID, "directory20", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDir20Uuid, directory20Attributes);

        assertNbElementsInRepositories(3);

        mockMvc.perform(put("/v1/elements?targetDirectoryUuid=" + rootDir20Uuid)
                        .header("userId", "Doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(rootDir10Uuid))))
                .andExpect(status().isOk());

        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir10Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(NotificationType.DELETE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));

        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDir20Uuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

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
        deleteElement(rootDir1.getElementUuid(), rootDir1.getElementUuid(), "user1", true, true, 0);
        deleteElement(rootDir2.getElementUuid(), rootDir2.getElementUuid(), "user2", true, true, 0);
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
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true, 0);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, true, 0);
    }

    @Test
    public void testTwoUsersTwoElementsInPublicDirectory() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(UUID.randomUUID(), "nameType01", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element2Attributes = toElementAttributes(UUID.randomUUID(), "nameType02", TYPE_01, "user2", "descr element2");
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // check user1 visible elements
        checkDirectoryContent(rootDirUuid, "user1", List.of(element1Attributes, element2Attributes));

        // check user2 visible elements
        checkDirectoryContent(rootDirUuid, "user2", List.of(element1Attributes, element2Attributes));
        deleteElement(element1Attributes.getElementUuid(), rootDirUuid, "user1", false, true, 0);
        checkElementNotFound(element1Attributes.getElementUuid(), "user1");

        deleteElement(element2Attributes.getElementUuid(), rootDirUuid, "user2", false, true, 0);
        checkElementNotFound(element2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, true, 0);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersElementsWithSameName() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(UUID.randomUUID(), "elementName1", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_01 with the same name in the root directory by the user1 and expect a 403
        ElementAttributes element2Attributes = toElementAttributes(UUID.randomUUID(), "elementName1", TYPE_01, "user1");
        insertExpectFail(rootDirUuid, element2Attributes, status().isConflict());

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element3Attributes = toElementAttributes(UUID.randomUUID(), "elementName2", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element3Attributes);

        // Insert an element of type TYPE_01 with the same name in the root directory by the user1 and expect a 403
        ElementAttributes element4Attributes = toElementAttributes(UUID.randomUUID(), "elementName2", TYPE_01, "user1");
        insertExpectFail(rootDirUuid, element4Attributes, status().isConflict());

        // Insert an element of type TYPE_03 with the same name in the root directory by the user1 and expect ok since it's not the same type
        ElementAttributes element5Attributes = toElementAttributes(UUID.randomUUID(), "elementName3", TYPE_03, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element5Attributes);

        // Insert an element of type TYPE_01 with the same name in the root directory by the user2 should not work.
        ElementAttributes element6Attributes = toElementAttributes(UUID.randomUUID(), "elementName2", TYPE_01, "user3");
        insertExpectFail(rootDirUuid, element6Attributes, status().isConflict());
    }

    @Test
    public void testTwoUsersTwoElements() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory by Doe
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(UUID.randomUUID(), "elementName1", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_01 in the root directory by the user2
        ElementAttributes element2Attributes = toElementAttributes(UUID.randomUUID(), "elementName2", TYPE_01, "user2");
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1", List.of(element1Attributes, element2Attributes));

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(element1Attributes, element2Attributes));

        deleteElement(element1Attributes.getElementUuid(), rootDirUuid, "user1", false, true, 0);
        checkElementNotFound(element1Attributes.getElementUuid(), "user1");

        deleteElement(element2Attributes.getElementUuid(), rootDirUuid, "user2", false, true, 0);
        checkElementNotFound(element2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, true, 0);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testRecursiveDelete() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "userId");

        // Insert an element of type TYPE_01 in the root directory by the userId
        ElementAttributes element1Attributes = toElementAttributes(UUID.randomUUID(), "elementName1", TYPE_01, "userId", "descr elementName1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_01 in the root directory by the userId;
        ElementAttributes element2Attributes = toElementAttributes(UUID.randomUUID(), "elementName2", TYPE_01, "userId");
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // Insert a subDirectory
        ElementAttributes subDirAttributes = toElementAttributes(UUID.randomUUID(), "subDir", DIRECTORY, "userId");
        insertAndCheckSubElementInRootDir(rootDirUuid, subDirAttributes);

        // Insert an element of type TYPE_01 in the root directory by the userId
        ElementAttributes subDirElementAttributes = toElementAttributes(UUID.randomUUID(), "elementName3", TYPE_01, "userId", "descr elementName3");

        insertAndCheckSubElement(subDirAttributes.getElementUuid(), subDirElementAttributes);

        assertNbElementsInRepositories(5);

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, true, 5);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(element1Attributes.getElementUuid(), "userId");
        checkElementNotFound(element2Attributes.getElementUuid(), "userId");
        checkElementNotFound(subDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(subDirElementAttributes.getElementUuid(), "userId");

        assertNbElementsInRepositories(0);
    }

    @Test
    public void testRenameElement() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory by user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(TYPE_01_RENAME_UUID, "elementName1", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        assertNbElementsInRepositories(2);

        renameElement(element1Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName1", true);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(element1Attributes.getElementUuid(), "newElementName1", TYPE_01, "user1", null, element1Attributes.getCreationDate(), element1Attributes.getLastModificationDate(), "user1")));

        assertNbElementsInRepositories(2);
    }

    @Test
    public void testRenameElementToSameName() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(TYPE_01_RENAME_UUID, "elementName1", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Updating to same name should not send error
        renameElement(element1Attributes.getElementUuid(), rootDirUuid, "user1", "elementName1", true);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(element1Attributes.getElementUuid(), "elementName1", TYPE_01, "user1", null, element1Attributes.getCreationDate(), element1Attributes.getLastModificationDate(), "user1")));
    }

    @Test
    public void testRenameElementWithSameNameAndTypeInSameDirectory() throws Exception {
        // Insert a root directory
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by Doe
        ElementAttributes element1Attributes = toElementAttributes(UUID.randomUUID(), "elementName1", TYPE_01, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_01 in the root directory by Doe;
        ElementAttributes element2Attributes = toElementAttributes(UUID.randomUUID(), "elementName2", TYPE_01, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // Renaming file to an already existing name should fail
        renameElementExpectFail(element1Attributes.getElementUuid(), "Doe", "elementName2", 403);
    }

    @Test
    public void testDirectoryContentAfterInsertElement() throws Exception {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "Doe");

        // Insert an element of type TYPE_01 in the root directory by the user1
        ElementAttributes elementAttributes = toElementAttributes(TYPE_01_UPDATE_ACCESS_RIGHT_UUID, "elementName1", TYPE_01, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, elementAttributes);
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(elementAttributes.getElementUuid(), "elementName1", TYPE_01, "user1", null, elementAttributes.getCreationDate(), elementAttributes.getLastModificationDate(), "user1")));
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

        // Insert an element of type TYPE_02 in the root directory by the user1
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), "elementName", TYPE_02, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, elementAttributes);

        mockMvc.perform(post(String.format("/v1/elements/%s/notification?type=update_directory", elementAttributes.getElementUuid()))
                        .header("userId", "Doe"))
                .andExpect(status().isOk());

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("Doe", headers.get(HEADER_USER_ID));
        assertEquals(rootDirUuid, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        // Test unknown type notification
        mockMvc.perform(post(String.format("/v1/elements/%s/notification?type=bad_type", elementAttributes.getElementUuid()))
                        .header("userId", "Doe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.server").value("directory-server"))
                .andExpect(jsonPath("$.status").value(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                .andExpect(jsonPath("$.path").value(String.format("/v1/elements/%s/notification", elementAttributes.getElementUuid())));
    }

    @SneakyThrows
    @Test
    public void testGetElementName() {
        // Insert an element named "elementName1"
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "user1");
        ElementAttributes element1Attributes = toElementAttributes(TYPE_02_UUID, "elementName1", TYPE_02, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        MvcResult result = mockMvc.perform(get("/v1/elements/" + element1Attributes.getElementUuid() + "/name")
                        .header("userId", USER_ID))
                .andExpectAll(status().isOk(),
                        content().contentType(MediaType.TEXT_PLAIN))
                .andReturn();
        String returnedName = result.getResponse().getContentAsString();
        assertEquals(element1Attributes.getElementName(), returnedName);

        // 404 case
        mockMvc.perform(get("/v1/elements/" + UUID.randomUUID() + "/name")
                .header("userId", USER_ID)).andExpectAll(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void testGetElement() {
        // Insert a root directory by the user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "user1");

        // Insert an element of type TYPE_02 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(TYPE_02_UUID, "elementName1", TYPE_02, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_03 in the root directory by the user1
        ElementAttributes element2Attributes = toElementAttributes(TYPE_03_UUID, "elementName2", TYPE_03, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // Insert an element of type TYPE_03 in the root directory by the user1
        ElementAttributes element3Attributes = toElementAttributes(UUID.randomUUID(), "elementName3", TYPE_03, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element3Attributes);

        var res = getElements(List.of(element1Attributes.getElementUuid(), element2Attributes.getElementUuid(), UUID.randomUUID()), "user1", true, 404);
        assertTrue(res.isEmpty());

        res = getElements(List.of(element1Attributes.getElementUuid(), element2Attributes.getElementUuid(), UUID.randomUUID()), "user1", false, 200);
        assertEquals(2, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res = getElements(List.of(element1Attributes.getElementUuid(), element2Attributes.getElementUuid(), element3Attributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(element1Attributes, element2Attributes, element3Attributes)));

        renameElement(element1Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName1", true);
        renameElement(element2Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName2", true);
        renameElement(element3Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName3", true);
        res = getElements(List.of(element1Attributes.getElementUuid(), element2Attributes.getElementUuid(), element3Attributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        ElementAttributes newElement1Attributes = toElementAttributes(element1Attributes.getElementUuid(), "newElementName1", TYPE_02, "user1", null, element1Attributes.getCreationDate(), element1Attributes.getLastModificationDate(), "user1");
        ElementAttributes newElement2Attributes = toElementAttributes(element2Attributes.getElementUuid(), "newElementName2", TYPE_03, "user1", null, element2Attributes.getCreationDate(), element2Attributes.getLastModificationDate(), "user1");
        ElementAttributes newElement3Attributes = toElementAttributes(element3Attributes.getElementUuid(), "newElementName3", TYPE_03, "user1", null, element3Attributes.getCreationDate(), element3Attributes.getLastModificationDate(), "user1");

        assertThat(res).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(List.of(newElement1Attributes, newElement2Attributes, newElement3Attributes));

        ElementAttributes directory = retrieveInsertAndCheckRootDirectory("testDir", "user1");
        List<ElementAttributes> result = getElements(List.of(TYPE_03_UUID, UUID.randomUUID(), directory.getElementUuid()), "user1", false, List.of(TYPE_03), 200);
        assertEquals(2, result.size());
        result.sort(Comparator.comparing(ElementAttributes::getElementName));

        result.sort(Comparator.comparing(ElementAttributes::getElementName));
        assertThat(result).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(List.of(
                toElementAttributes(TYPE_03_UUID, "newElementName2", TYPE_03, "user1", 0, null, element2Attributes.getCreationDate(), element2Attributes.getLastModificationDate(), "user1"),
                directory
        ));

        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, subDirAttributes);
        insertAndCheckSubElement(subDirAttributes.getElementUuid(), toElementAttributes(null, "subDirElementType02", TYPE_02, "user1"));
        checkDirectoryContent(rootDirUuid, "user1", List.of(newElement1Attributes, newElement2Attributes, newElement3Attributes, subDirAttributes));
        subDirAttributes.setSubdirectoriesCount(1L);
        checkDirectoryContent(rootDirUuid, "user1", List.of(TYPE_02), false, List.of(newElement1Attributes, subDirAttributes));

        ElementAttributes rootDirectory = getElements(List.of(rootDirUuid), "user1", false, 200).get(0);

        checkRootDirectoriesList("user1", List.of(rootDirectory, directory));

        rootDirectory.setSubdirectoriesCount(3L);
        checkRootDirectoriesList("user1", List.of(TYPE_03), List.of(rootDirectory, directory));

        assertNbElementsInRepositories(7);
    }

    @SneakyThrows
    @Test
    public void testGetElementWithNonAdminUser() {
        // Insert a root directory by the user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", ADMIN_USER);

        // Insert an element of type TYPE_02 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(TYPE_02_UUID, "elementName1", TYPE_02, ADMIN_USER);
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_03 in the root directory by the user1
        ElementAttributes element2Attributes = toElementAttributes(TYPE_03_UUID, "elementName2", TYPE_03, ADMIN_USER);
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // Insert an element of type TYPE_03 in the root directory by the user1
        ElementAttributes element3Attributes = toElementAttributes(UUID.randomUUID(), "elementName3", TYPE_03, ADMIN_USER);
        insertAndCheckSubElementInRootDir(rootDirUuid, element3Attributes);

        ElementAttributes rootDirectory = getElements(List.of(rootDirUuid), "user1", false, 200).get(0);

        //user should be able to retrieve the root directory because of the global permission
        checkRootDirectoriesList(NOT_ADMIN_USER, List.of(), List.of(rootDirectory));

        //delete the global read permission
        permissionRepository.deleteById(new PermissionId(rootDirUuid, ALL_USERS, ""));

        //admin user should still be able to retrieve the root directory
        checkRootDirectoriesList(ADMIN_USER, List.of(), List.of(rootDirectory));

        //and not_admin user shouldn't be able to retrieve the root directory anymore
        checkRootDirectoriesList(NOT_ADMIN_USER, List.of(), List.of());

        //retrieve directory content with admin or a user that has permission should work
        checkDirectoryContent(rootDirUuid, ADMIN_USER, List.of(element1Attributes, element2Attributes, element3Attributes));

        //retrieve directory content with non admin user should be empty (no permissions)
        checkDirectoryContent(rootDirUuid, NOT_ADMIN_USER, List.of());
    }

    @SneakyThrows
    @Test
    public void testElementAccessControl() {
        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", "user1");

        // Insert an element of type TYPE_02 in the root directory by the user1
        ElementAttributes element1Attributes = toElementAttributes(TYPE_02_UUID, "elementName1", TYPE_02, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element1Attributes);

        // Insert an element of type TYPE_03 in the root directory by the user1
        ElementAttributes element2Attributes = toElementAttributes(TYPE_03_UUID, "elementName2", TYPE_03, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element2Attributes);

        // Insert an element of type TYPE_03 in the root directory by the user1
        ElementAttributes element3Attributes = toElementAttributes(UUID.randomUUID(), "elementName3", TYPE_03, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, element3Attributes);

        var res = getElements(List.of(element1Attributes.getElementUuid(), element2Attributes.getElementUuid(), element3Attributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(element1Attributes, element2Attributes, element3Attributes)));

        renameElement(element1Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName1", true);
        renameElement(element2Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName2", true);
        renameElement(element3Attributes.getElementUuid(), rootDirUuid, "user1", "newElementName3", true);
        res = getElements(List.of(element1Attributes.getElementUuid(), element2Attributes.getElementUuid(), element3Attributes.getElementUuid()), "user1", true, 200);
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        assertThat(res).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(List.of(
                toElementAttributes(element1Attributes.getElementUuid(), "newElementName1", TYPE_02, "user1", null, element1Attributes.getCreationDate(), element1Attributes.getLastModificationDate(), "user1"),
                toElementAttributes(element2Attributes.getElementUuid(), "newElementName2", TYPE_03, "user1", null, element2Attributes.getCreationDate(), element2Attributes.getLastModificationDate(), "user1"),
                toElementAttributes(element3Attributes.getElementUuid(), "newElementName3", TYPE_03, "user1", null, element3Attributes.getCreationDate(), element3Attributes.getLastModificationDate(), "user1")
        ));
    }

    @Test
    public void testRootDirectoryExists() throws Exception {
        // Insert a root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDirToFind", "user1");

        UUID directoryUUID = UUID.randomUUID();
        ElementAttributes directory20Attributes = toElementAttributes(directoryUUID, "directoryToFind", DIRECTORY, "Doe");
        insertAndCheckSubElementInRootDir(rootDirUuid, directory20Attributes);

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
        insertExpectFail(rootDirUuid, directoryWithoutNameAttributes, status().isForbidden());
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

        // Insert an element of type TYPE_01 with empty name in the root directory and expect a 403
        ElementAttributes element1WithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", TYPE_01, "user1");
        insertExpectFail(rootDirUuid, element1WithoutNameAttributes, status().isForbidden());

       // Insert an element of type TYPE_03 with empty name in the root directory and expect a 403
        ElementAttributes element2WithoutNameAttributes = toElementAttributes(UUID.randomUUID(), "", TYPE_03, "user1");
        insertExpectFail(rootDirUuid, element2WithoutNameAttributes, status().isForbidden());

        assertNbElementsInRepositories(1);
    }

    @Test
    public void testElementUpdateNotification() throws Exception {
        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", "userId");
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a  sub-element of type TYPE_01
        ElementAttributes subEltAttributes = toElementAttributes(UUID.randomUUID(), "subElementName", TYPE_01, "userId", "descr subElementName");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, subEltAttributes);

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
    public void testStudyUpdateNotification() throws Exception {
        String userId = "userId";

        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", userId);
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a study
        UUID studyUuid = UUID.randomUUID();
        String studyName = "studyName";
        ElementAttributes subEltAttributes = toElementAttributes(studyUuid, studyName, TYPE_01, userId, "descr");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, subEltAttributes);

        input.send(MessageBuilder.withPayload("")
            .setHeader(HEADER_STUDY_UUID, studyUuid.toString())
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
            .setHeader(HEADER_ERROR, "error")
            .build(), studyUpdateDestination);

        // Assert that the broker message has been sent an element delete (study) request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(UPDATE_TYPE_ELEMENT_DELETE, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(studyUuid, headers.get(HEADER_ELEMENT_UUID));

        // Assert that the broker message has been sent a directory update request message
        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewRootDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(studyName, headers.get(HEADER_ELEMENT_NAME));

        // Assert that the broker message has been sent a directory update request message
        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewRootDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(studyName, headers.get(HEADER_ELEMENT_NAME));
    }

    @Test
    public void testSupervision() throws Exception {
        MvcResult mvcResult;
        // Test get elasticsearch host
        mvcResult = mockMvc.perform(get("/v1/supervision/elasticsearch-host"))
            .andExpect(status().isOk())
            .andReturn();

        HttpHost elasticSearchHost = restClient.getNodes().get(0).getHost();
        assertEquals(elasticSearchHost.getHostName() + ":" + elasticSearchHost.getPort(), mvcResult.getResponse().getContentAsString());

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
        insertAndCheckSubElementInRootDir(rootDirUuid, directoryAttributes);
        ElementAttributes subdirectoryAttributes = toElementAttributes(UUID.randomUUID(), "subdirectory", DIRECTORY, "userId");
        insertAndCheckSubElement(dirUuid, subdirectoryAttributes);
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), "elementName", TYPE_01, "userId");
        insertAndCheckSubElementInRootDir(rootDirUuid, elementAttributes);

        // Test get elements by a given type
        String res = mockMvc.perform(get("/v1/supervision/elements?elementType=" + TYPE_01))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<ElementAttributes> elementAttributesReceived = objectMapper.readValue(res, new TypeReference<>() {
        });
        assertThat(elementAttributesReceived.get(0)).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(elementAttributes);

        // Test get indexed elements counts
        mvcResult = mockMvc.perform(get("/v1/supervision/elements/indexation-count"))
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(4, Long.parseLong(mvcResult.getResponse().getContentAsString()));

        // Recreate the index
        mockMvc.perform(post("/v1/supervision/elements/index"))
                .andExpect(status().isOk());

        mvcResult = mockMvc.perform(get("/v1/supervision/elements/indexation-count"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, Long.parseLong(mvcResult.getResponse().getContentAsString()));

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
        String response = mockMvc.perform(get("/v1/root-directories" + types)
                                            .header("userId", userId)
                                            .header(HEADER_USER_ROLES, userId.equals(ADMIN_USER) ? ADMIN_ROLE : NO_ADMIN_ROLE))
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

        UUID uuidNewRootDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();
        Instant creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getCreationDate();
        Instant modificationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getLastModificationDate();

        ElementAttributes newDirectoryAttributes = toElementAttributes(uuidNewRootDirectory, rootDirectoryName, DIRECTORY, userId, null, creationDateNewDirectory, modificationDateNewDirectory, userId);
        assertElementIsProperlyInserted(newDirectoryAttributes);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewRootDirectory, headers.get(HEADER_DIRECTORY_UUID));
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

        UUID uuidNewRootDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getElementUuid();
        Instant creationDateNewDirectory = objectMapper.readValue(Objects.requireNonNull(response), ElementAttributes.class).getCreationDate();

        assertElementIsProperlyInserted(toElementAttributes(uuidNewRootDirectory, rootDirectoryName, DIRECTORY, userId, null, creationDateNewDirectory, creationDateNewDirectory, userId));

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(uuidNewRootDirectory, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));

        return uuidNewRootDirectory;
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

    private void insertSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes, boolean allowNewName, boolean parentIsRoot) throws Exception {
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
        assertEquals(parentIsRoot, headers.get(HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(HEADER_NOTIFICATION_TYPE));
        assertEquals(UPDATE_TYPE_DIRECTORIES, headers.get(HEADER_UPDATE_TYPE));
    }

    private void insertAndCheckSubElementInRootDir(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) throws Exception {
        insertSubElement(parentDirectoryUUid, subElementAttributes, false, true);
        assertElementIsProperlyInserted(subElementAttributes);
    }

    private void insertAndCheckSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) throws Exception {
        insertSubElement(parentDirectoryUUid, subElementAttributes, false, false);
        assertElementIsProperlyInserted(subElementAttributes);
    }

    private void insertExpectFail(UUID parentDirectoryUUid, ElementAttributes subElementAttributes, ResultMatcher resultMatcher) throws Exception {
        // Insert a sub-element of type DIRECTORY and expect 403 forbidden
        mockMvc.perform(post("/v1/directories/" + parentDirectoryUUid + "/elements")
                .header("userId", subElementAttributes.getOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subElementAttributes)))
            .andExpect(resultMatcher);
    }

    private void renameElement(UUID elementUuidToRename, UUID elementUuidHeader, String userId, String newName, boolean notifiedDirectoryIsRoot) throws Exception {
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
        assertEquals(notifiedDirectoryIsRoot, headers.get(HEADER_IS_ROOT_DIRECTORY));
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

    private void checkDirectoryContent(UUID parentDirectoryUuid, String userId, List<ElementAttributes> expectedList) throws Exception {
        checkDirectoryContent(parentDirectoryUuid, userId, List.of(), false, expectedList);
    }

    private void checkDirectoryContent(UUID parentDirectoryUuid, String userId, List<String> types, boolean recursive, List<ElementAttributes> expectedList) throws Exception {
        String recurs = "?recursive=" + (recursive ? "true" : "false");
        String elementTypes = !CollectionUtils.isEmpty(types) ? "&elementTypes=" + String.join(",", types) : "";
        String response = mockMvc.perform(get("/v1/directories/" + parentDirectoryUuid + "/elements" + recurs + elementTypes)
                .header("userId", userId)
                .header(HEADER_USER_ROLES, userId.equals(ADMIN_USER) ? ADMIN_ROLE : NO_ADMIN_ROLE))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<ElementAttributes> result = objectMapper.readValue(response, new TypeReference<>() {
        });
        if (recursive) {
            assertThat(expectedList).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).ignoringCollectionOrder().isEqualTo(result);
        } else {
            assertThat(expectedList).usingRecursiveComparison().ignoringFieldsOfTypes(Instant.class).isEqualTo(result);
        }
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

    private void deleteElement(UUID elementUuidToBeDeleted, UUID elementUuidHeader, String userId, boolean isRoot, boolean notifiedDirectoryIsRoot, int numberOfElements) throws Exception {
        mockMvc.perform(delete("/v1/elements/" + elementUuidToBeDeleted)
                .header("userId", userId))
                        .andExpect(status().isOk());

        Message<byte[]> message;
        MessageHeaders headers;
        // assert that the broker message has been sent a delete element
        if (numberOfElements == 0) {
            message = output.receive(TIMEOUT, directoryUpdateDestination);
            assertEquals("", new String(message.getPayload()));
            headers = message.getHeaders();
            assertEquals(userId, headers.get(HEADER_USER_ID));
            assertEquals(UPDATE_TYPE_ELEMENT_DELETE, headers.get(HEADER_UPDATE_TYPE));
            assertEquals(elementUuidToBeDeleted, headers.get(HEADER_ELEMENT_UUID));
        } else {
            //empty the queue of all delete element notif
            for (int i = 0; i < numberOfElements; i++) {
                message = output.receive(TIMEOUT, directoryUpdateDestination);
                headers = message.getHeaders();
                assertEquals(UPDATE_TYPE_ELEMENT_DELETE, headers.get(HEADER_UPDATE_TYPE));
                assertEquals(userId, headers.get(HEADER_USER_ID));
            }
        }
        // assert that the broker message has been sent a delete
        message = output.receive(TIMEOUT, directoryUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(HEADER_DIRECTORY_UUID));
        assertEquals(notifiedDirectoryIsRoot, headers.get(HEADER_IS_ROOT_DIRECTORY));
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

        mockMvc.perform(get("/v1/directories/" + directoryId + "/" + "pouet" + "/newNameCandidate?type=" + TYPE_01)
                .header("userId", "youplaboum"))
            .andExpect(status().isOk());

        var name = "elementName";
        // check when no elements is corresponding (empty folder
        assertEquals("elementName", candidateName(directoryId, name, TYPE_01));
        var element = toElementAttributes(UUID.randomUUID(), name, TYPE_01, "userId");
        insertAndCheckSubElementInRootDir(directoryId, element);
        var newCandidateName = candidateName(directoryId, name, TYPE_01);
        assertEquals("elementName(1)", newCandidateName);
        element.setElementName(newCandidateName);
        element.setElementUuid(UUID.randomUUID());
        insertAndCheckSubElementInRootDir(directoryId, element);
        assertEquals("elementName(2)", candidateName(directoryId, name, TYPE_01));
        assertEquals("elementName", candidateName(directoryId, name, TYPE_02));
    }

    @Test
    @SneakyThrows
    public void testCreateElementInDirectory() {
        String userId = "user";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ElementAttributes elementAttributes = ElementAttributes.toElementAttributes(UUID.randomUUID(), "elementName", TYPE_05, "user", null, now, now, userId);
        String requestBody = objectMapper.writeValueAsString(elementAttributes);
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

        DirectoryElementEntity insertedElement = directoryElementList.stream().filter(directoryElementEntity -> directoryElementEntity.getName().equals("elementName")).findFirst().orElseThrow();
        assertEquals(TYPE_05, insertedElement.getType());
        //the element is in dir2
        assertEquals(dir2Uuid, insertedElement.getParentId());

        assertNbElementsInRepositories(3);

        //we don't care about message
        output.clear();
    }

    @Test
    public void duplicateElementTest() throws Exception {
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir", "user1");
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), "elementName", TYPE_05, "user1");
        insertAndCheckSubElementInRootDir(rootDirUuid, elementAttributes);
        UUID elementUUID = elementAttributes.getElementUuid();
        UUID newElementUuid = UUID.randomUUID();
        // duplicate the element
        ElementAttributes duplicatedElement = directoryService.duplicateElement(elementUUID, newElementUuid, null, "user1");
        assertEquals("elementName(1)", duplicatedElement.getElementName());
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
        final String elementName = "elementName";
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), elementName, TYPE_04, userId);
        insertAndCheckSubElementInRootDir(rootDirUuid, elementAttributes);

        // insert another new element having the same existing name, allowing duplication with a new name
        ElementAttributes element2Attributes = toElementAttributes(UUID.randomUUID(), elementName, TYPE_04, userId);
        insertSubElement(rootDirUuid, element2Attributes, true, true);
        // expecting "incremental name"
        element2Attributes.setElementName(elementName + "(1)");
        assertElementIsProperlyInserted(element2Attributes);

        checkDirectoryContent(rootDirUuid, userId, List.of(TYPE_04), false, List.of(elementAttributes, element2Attributes));

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

        insertAndCheckSubElementInRootDir(rootDirectoryUuid, subDirAttributes1);
        insertAndCheckSubElementInRootDir(rootDirectoryUuid, subDirAttributes2);
        insertAndCheckSubElementInRootDir(rootDirectoryUuid, subDirAttributes3);
        insertAndCheckSubElement(subDirUuid1, subDirAttributes4);
        insertAndCheckSubElement(subDirUuid3, subDirAttributes5);

        insertAndCheckSubElement(subDirUuid1, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_1, ""));

        insertAndCheckSubElement(subDirUuid2, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_2, ""));

        insertAndCheckSubElement(subDirUuid3, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_3, ""));

        insertAndCheckSubElement(subDirUuid4, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_1, ""));

        insertAndCheckSubElement(subDirUuid5, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_3, ""));

        MvcResult mvcResult;

        mvcResult = mockMvc
                .perform(get("/v1/elements/indexation-infos?userInput={request}", "r").header(USER_ID, USERID_1))
                .andExpectAll(status().isOk()).andReturn();
        List<DirectoryElementInfos> result = mvcResultToList(mvcResult);
        assertEquals(5, result.size());
        output.clear();

        mvcResult = mockMvc
                .perform(get("/v1/elements/indexation-infos?userInput={request}", "r").header(USER_ID, USERID_2))
                .andExpectAll(status().isOk()).andReturn();
        result = mvcResultToList(mvcResult);
        assertEquals(5, result.size());
        output.clear();

        mvcResult = mockMvc
                .perform(get("/v1/elements/indexation-infos?userInput={request}", "r").header(USER_ID, USERID_3))
                .andExpectAll(status().isOk()).andReturn();
        result = mvcResultToList(mvcResult);
        assertEquals(5, result.size());
        output.clear();
    }

    @Test
    public void testElementsAccessibilityOk() throws Exception {
        checkRootDirectoriesList("userId", List.of());
        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, subDirAttributes);
        checkDirectoryContent(uuidNewRootDirectory, USER_ID, List.of(subDirAttributes));
        // The subDirAttributes is created by the userId,so it is deletable
        mockMvc
                .perform(head("/v1/elements?accessType=WRITE&ids={ids}&targetDirectoryUuid", subDirAttributes.getElementUuid()).header(USER_ID, USER_ID))
                .andExpectAll(status().isOk()).andReturn();
        deleteElement(subDirAttributes.getElementUuid(), uuidNewRootDirectory, "userId", false, true, 0);
    }

    @Test
    public void testElementsAccessibilityNotOk() throws Exception {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a sub-element of type DIRECTORY
        ElementAttributes subDirAttributes = toElementAttributes(null, "newSubDir", DIRECTORY, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, subDirAttributes);
        checkDirectoryContent(uuidNewRootDirectory, USER_ID, List.of(subDirAttributes));
        //The subDirAttributes is created by the userId,so the userId1 is not allowed to delete it.
        mockMvc
                .perform(head("/v1/elements?accessType=WRITE&ids={ids}&targetDirectoryUuid", subDirAttributes.getElementUuid()).header(USER_ID, USERID_1))
                .andExpectAll(status().isForbidden()).andReturn();
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

    @Test
    public void testCountUserCases() throws Exception {
        //TODO: the specific types such as study and filter... are kept on purpose for this test
        // It's will be removed later
        checkRootDirectoriesList("userId", List.of());
        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a sub-elements of type cases
        ElementAttributes caseAttributes1 = toElementAttributes(null, "case1", CASE, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, caseAttributes1);
        ElementAttributes caseAttributes2 = toElementAttributes(null, "case2", CASE, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, caseAttributes2);
        ElementAttributes caseAttributes3 = toElementAttributes(null, "case3", CASE, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, caseAttributes3);
        ElementAttributes caseAttribute4 = toElementAttributes(null, "case4", CASE, "NOT_SAME_USER");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, caseAttribute4);
        checkDirectoryContent(uuidNewRootDirectory, USER_ID, List.of(caseAttributes1, caseAttributes2, caseAttributes3, caseAttribute4));

        //get the number of cases for user "userId" and expect 3
        MvcResult result = mockMvc
                .perform(get("/v1/users/{userId}/cases/count", USER_ID))
                .andExpectAll(status().isOk()).andReturn();
        assertEquals("3", result.getResponse().getContentAsString());

        //get the number of cases for user "NOT_SAME_USER" and expect 1
        result = mockMvc
                .perform(get("/v1/users/{userId}/cases/count", "NOT_SAME_USER"))
                .andExpectAll(status().isOk()).andReturn();
        assertEquals("1", result.getResponse().getContentAsString());
    }

    @Test
    public void testGetDirectoryFromPath() throws Exception {

        //                          root (userId2)
        //         /                          |               \
        //       dir1 (userId1)      dir2 (userId2)          dir3 (userId3)
        //        |                                                    |
        //       dir4 (userId1)                                   dir5 (userId3)
        //                                                   /            |             \
        //                                              'a,b,c'    'dir6/dir7/dir8'   '&~#{[^repert'

        ElementAttributes rootDirectory = retrieveInsertAndCheckRootDirectory("root", USERID_2);
        UUID rootDirectoryUuid = rootDirectory.getElementUuid();
        UUID subDirUuid1 = UUID.randomUUID();
        ElementAttributes subDirAttributes1 = toElementAttributes(subDirUuid1, "dir1", DIRECTORY, USERID_1);
        UUID subDirUuid2 = UUID.randomUUID();
        ElementAttributes subDirAttributes2 = toElementAttributes(subDirUuid2, "dir2", DIRECTORY, USERID_2);
        UUID subDirUuid3 = UUID.randomUUID();
        ElementAttributes subDirAttributes3 = toElementAttributes(subDirUuid3, "dir3", DIRECTORY, USERID_3);
        UUID subDirUuid4 = UUID.randomUUID();
        ElementAttributes subDirAttributes4 = toElementAttributes(subDirUuid4, "dir4", DIRECTORY, USERID_1);
        UUID subDirUuid5 = UUID.randomUUID();
        ElementAttributes subDirAttributes5 = toElementAttributes(subDirUuid5, "dir5", DIRECTORY, USERID_3);
        UUID subDirUuid6 = UUID.randomUUID();
        String encodedPath = "%26~%23%7B%5B%5Erepert";
        String decodedPath = "&~#{[^repert";
        ElementAttributes subDirAttributes6 = toElementAttributes(subDirUuid6, decodedPath, DIRECTORY, USERID_3);
        UUID subDirUuid7 = UUID.randomUUID();
        ElementAttributes subDirAttributes7 = toElementAttributes(subDirUuid7, "dir6/dir7/dir8", DIRECTORY, USERID_1);
        UUID subDirUuid8 = UUID.randomUUID();
        ElementAttributes subDirAttributes8 = toElementAttributes(subDirUuid8, "a,b,c", DIRECTORY, USERID_1);

        insertAndCheckSubElementInRootDir(rootDirectoryUuid, subDirAttributes1);
        insertAndCheckSubElementInRootDir(rootDirectoryUuid, subDirAttributes2);
        insertAndCheckSubElementInRootDir(rootDirectoryUuid, subDirAttributes3);
        insertAndCheckSubElement(subDirUuid1, subDirAttributes4);
        insertAndCheckSubElement(subDirUuid3, subDirAttributes5);
        insertAndCheckSubElement(subDirUuid5, subDirAttributes6);
        insertAndCheckSubElement(subDirUuid5, subDirAttributes7);
        insertAndCheckSubElement(subDirUuid5, subDirAttributes8);

        insertAndCheckSubElement(subDirUuid1, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_1, ""));
        insertAndCheckSubElement(subDirUuid2, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_2, ""));
        insertAndCheckSubElement(subDirUuid3, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_3, ""));
        insertAndCheckSubElement(subDirUuid4, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_1, ""));
        insertAndCheckSubElement(subDirUuid5, toElementAttributes(UUID.randomUUID(), RECOLLEMENT, TYPE_01, USERID_3, ""));

        MvcResult mvcResult;

        // existing root directory
        mvcResult = mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=" + "root")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isOk()).andReturn();
        UUID resultUuid = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(rootDirectory.getElementUuid(), resultUuid);
        output.clear();

        // existing non root directory
        mvcResult = mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=" + "root,dir1,dir4")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isOk()).andReturn();
        resultUuid = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(subDirAttributes4.getElementUuid(), resultUuid);
        output.clear();

        // unexisting directory
        mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=" + "root,dir1,dir5")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isNotFound()).andReturn();
        output.clear();

        // path to element (not a directory)
        mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=" + "root,dir1,dir4," + RECOLLEMENT)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isNotFound()).andReturn();
        output.clear();

        // existing directory with special characters in path
        mvcResult = mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=root,dir3,dir5," + encodedPath)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isOk()).andReturn();
        resultUuid = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(subDirAttributes6.getElementUuid(), resultUuid);
        output.clear();

        // existing directory with '/' character in name
        mvcResult = mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=root,dir3,dir5,dir6/dir7/dir8")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isOk()).andReturn();
        resultUuid = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(subDirAttributes7.getElementUuid(), resultUuid);
        output.clear();

        // existing directory with ',' character in name
        mvcResult = mockMvc
            .perform(get("/v1/directories/uuid?directoryPath=root,dir3,dir5,a%2Cb%2Cc")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isOk()).andReturn();
        resultUuid = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(subDirAttributes8.getElementUuid(), resultUuid);
        output.clear();
    }

    private <T> List<T> mvcResultToList(MvcResult mvcResult) throws Exception {
        JsonNode resultJson = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        ObjectReader resultReader = objectMapper.readerFor(new TypeReference<>() { });
        return resultReader.readValue(resultJson.get("content"));
    }

    @Test
    public void testDirectoryContentRecursive() throws Exception {
        checkRootDirectoriesList("userId", List.of());
        //    rootDir  (contains modifRoot)
        //      |
        //    subDir   (contains modifSub, modifSub2)
        //      |
        //    lastDir   (contains modifLeaf)

        // create rootDir
        UUID uuidNewRootDirectory = retrieveInsertAndCheckRootDirectory("rootDir", USER_ID).getElementUuid();
        // create modifRoot
        ElementAttributes rootModifAttributes = toElementAttributes(null, "modifRoot", MODIFICATION, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, rootModifAttributes);
        // create subDir
        ElementAttributes subDirAttributes = toElementAttributes(null, "subDir", DIRECTORY, USER_ID);
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, subDirAttributes);
        // create modifSub, modifSub2
        ElementAttributes subModifAttributes = toElementAttributes(null, "modifSub", MODIFICATION, USER_ID);
        insertAndCheckSubElement(subDirAttributes.getElementUuid(), subModifAttributes);
        ElementAttributes subModifAttributes2 = toElementAttributes(null, "modifSub2", MODIFICATION, USER_ID);
        insertAndCheckSubElement(subDirAttributes.getElementUuid(), subModifAttributes2);
        // create lastDir
        ElementAttributes lastDirAttributes = toElementAttributes(null, "lastDir", DIRECTORY, USER_ID);
        insertAndCheckSubElement(subDirAttributes.getElementUuid(), lastDirAttributes);
        // create modifLeaf
        ElementAttributes leafModifAttributes = toElementAttributes(null, "modifLeaf", MODIFICATION, USER_ID);
        insertAndCheckSubElement(lastDirAttributes.getElementUuid(), leafModifAttributes);

        // 4 modifications expected starting recursively from rootDir (in random order)
        checkDirectoryContent(uuidNewRootDirectory, USER_ID, List.of(MODIFICATION), true, List.of(leafModifAttributes, rootModifAttributes, subModifAttributes, subModifAttributes2));
    }

    @Test
    public void testElementsUpdateOk() throws Exception {
        checkRootDirectoriesList(USER_ID, List.of());
        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a  sub-element of type TYPE_01
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), "elementName", TYPE_01, "userId", "descr element");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, elementAttributes);
        checkDirectoryContent(uuidNewRootDirectory, USER_ID, List.of(elementAttributes));

        // The elementAttributes is created by the userId,so it is updated
        mockMvc
                .perform(head("/v1/elements?accessType=WRITE&ids={ids}&targetDirectoryUuid", elementAttributes.getElementUuid()).header(USER_ID, USER_ID))
                .andExpectAll(status().isOk()).andReturn();
    }

    @Test
    public void testElementsUpdateNotOk() throws Exception {
        checkRootDirectoriesList(USER_ID, List.of());
        // Insert a root directory
        ElementAttributes newRootDirectory = retrieveInsertAndCheckRootDirectory("newDir", USER_ID);
        UUID uuidNewRootDirectory = newRootDirectory.getElementUuid();

        // Insert a  sub-element of type TYPE_01
        ElementAttributes elementAttributes = toElementAttributes(UUID.randomUUID(), "elementName", TYPE_01, "userId", "descr element");
        insertAndCheckSubElementInRootDir(uuidNewRootDirectory, elementAttributes);
        checkDirectoryContent(uuidNewRootDirectory, USER_ID, List.of(elementAttributes));

        // The elementAttributes is created by the userId,so it is not updated by USERID_1
        mockMvc
                .perform(head("/v1/elements?accessType=WRITE&ids={ids}&targetDirectoryUuid", elementAttributes.getElementUuid()).header(USER_ID, USERID_1))
                .andExpectAll(status().isForbidden()).andReturn();
    }

    @Test
    public void testConsumeCaseExportFinished() {
        UUID exportUuid = UUID.randomUUID();
        String userId = "user1";
        String errorMessage = "test error";
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_USER_ID, userId);
        headers.put(HEADER_EXPORT_UUID, exportUuid.toString());
        headers.put(HEADER_ERROR, errorMessage);
        Message<String> message = new GenericMessage<>("", headers);
        consumeService.consumeCaseExportFinished(message);
        var mess = output.receive(TIMEOUT, directoryUpdateDestination);
        assertNotNull(mess);
        assertEquals(exportUuid, mess.getHeaders().get(HEADER_EXPORT_UUID));
        output.clear();
    }
}
