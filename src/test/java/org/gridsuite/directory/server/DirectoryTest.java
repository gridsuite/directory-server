/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jparams.verifier.tostring.ToStringVerifier;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.io.IOException;
import java.util.*;

import static org.gridsuite.directory.server.DirectoryService.*;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
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
    private DirectoryService directoryService;

    @Autowired
    private FilterService filterService;

    @Autowired
    ContingencyListService contingencyListService;

    private MockWebServer server;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    private static final UUID STUDY_RENAME_UUID = UUID.randomUUID();
    private static final UUID STUDY_RENAME_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID STUDY_RENAME_NOT_FOUND_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_NOT_FOUND_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();

    private void cleanDB() {
        directoryElementRepository.deleteAll();
    }

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        directoryService.setStudyServerBaseUri(baseUrl);
        filterService.setFilterServerBaseUri(baseUrl);
        contingencyListService.setActionsServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String userId = request.getHeaders().get("userId");

                if (path.matches("/v1/studies/.*") && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/studies/" + STUDY_RENAME_UUID + "/rename") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/studies/" + STUDY_RENAME_FORBIDDEN_UUID + "/rename") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(403);
                } else if (path.matches("/v1/studies/" + STUDY_RENAME_NOT_FOUND_UUID + "/rename") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(404);
                } else if (path.matches("/v1/studies/" + STUDY_UPDATE_ACCESS_RIGHT_UUID + "/public") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/studies/" + STUDY_UPDATE_ACCESS_RIGHT_UUID + "/private") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/studies/" + STUDY_UPDATE_ACCESS_RIGHT_NOT_FOUND_UUID + "/private") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(404);
                } else if (path.matches("/v1/studies/" + STUDY_UPDATE_ACCESS_RIGHT_NOT_FOUND_UUID + "/public") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(404);
                } else if (path.matches("/v1/studies/" + STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID + "/private") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(403);
                } else if (path.matches("/v1/studies/" + STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID + "/public") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(403);
                } else if (path.matches("/v1/studies/.*") && "POST".equals(request.getMethod())) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("studyUuid", path.split("=")[3])
                        .setHeader("userId", userId)
                        .build());
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_UUID + "/rename") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/script-contingency-lists.*") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters-contingency-lists.*/new-script/.*") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters-contingency-lists.*/replace-with-script") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters-contingency-lists.*") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters/" + FILTER_UUID + "/rename") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters.*/new-script/.*") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters.*/replace-with-script") && "PUT".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/filters.*") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(500);
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    public void test() {
        checkRootDirectoriesList("userId", List.of());

        // Insert a root directory
        UUID uuidNewDirectory = insertAndCheckRootDirectory("newDir", false, "userId");

        // Insert a sub-element of type DIRECTORY
        UUID uuidNewSubDirectory = insertAndCheckSubElement(null, uuidNewDirectory, "newSubDir", DIRECTORY, true, "userId", false);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(toElementAttributes(uuidNewSubDirectory, "newSubDir", DIRECTORY, true, "userId")));

        // Insert a  sub-element of type STUDY
        UUID uuidAddedStudy = insertAndCheckSubElement(UUID.randomUUID(), uuidNewDirectory, "newStudy", STUDY, false, "userId", false);
        checkDirectoryContent(uuidNewDirectory, "userId",
            List.of(
                toElementAttributes(uuidNewSubDirectory, "newSubDir", DIRECTORY, true, "userId"),
                toElementAttributes(uuidAddedStudy, "newStudy", STUDY, false, "userId")
            )
        );

        // Delete the sub-directory newSubDir
        deleteElement(uuidNewSubDirectory, uuidNewDirectory, "userId", false, false);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(toElementAttributes(uuidAddedStudy, "newStudy", STUDY, false, "userId")));

        // Delete the sub-element newStudy
        deleteElement(uuidAddedStudy, uuidNewDirectory, "userId", false, false);
        assertDirectoryIsEmpty(uuidNewDirectory, "userId");

        // Rename the root directory
        renameElement(uuidNewDirectory, uuidNewDirectory, "userId", "newName", true, false);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, false, "userId")));

        // Change root directory access rights public => private
        // change access of a root directory from public to private => we should receive a notification with isPrivate= false to notify all clients
        updateAccessRights(uuidNewDirectory, uuidNewDirectory, "userId", true, true, false);

        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, true, "userId")));

        // Add another sub-directory
        uuidNewSubDirectory = insertAndCheckSubElement(null, uuidNewDirectory, "newSubDir", DIRECTORY, true, "userId", true);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(toElementAttributes(uuidNewSubDirectory, "newSubDir", DIRECTORY, true, "userId")));

        // Add another sub-directory
        UUID uuidNewSubSubDirectory = insertAndCheckSubElement(null, uuidNewSubDirectory, "newSubSubDir", DIRECTORY, true, "userId", true);
        checkDirectoryContent(uuidNewSubDirectory, "userId", List.of(toElementAttributes(uuidNewSubSubDirectory, "newSubSubDir", DIRECTORY, true, "userId")));

        // Test children number of root directory
        checkRootDirectoriesList("userId", List.of(toElementAttributes(uuidNewDirectory, "newName", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L)));

        deleteElement(uuidNewDirectory, uuidNewDirectory, "userId", true, true);
        checkRootDirectoriesList("userId", List.of());

        checkElementNotFound(uuidNewSubDirectory, "userId");
        checkElementNotFound(uuidNewSubSubDirectory, "userId");
    }

    @Test
    public void testTwoUsersTwoPublicDirectories() {
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
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, false);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false);
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateDirectories() {
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
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, false);
    }

    @Test
    public void testTwoUsersTwoPrivateDirectories() {
        checkRootDirectoriesList("user1", List.of());
        checkRootDirectoriesList("user2", List.of());
        // Insert a root directory user1
        UUID rootDir1Uuid = insertAndCheckRootDirectory("rootDir1", true, "user1");
        // Insert a root directory user2
        UUID rootDir2Uuid = insertAndCheckRootDirectory("rootDir2", true, "user2");

        checkRootDirectoriesList("user1", List.of(toElementAttributes(rootDir1Uuid, "rootDir1", DIRECTORY, true, "user1")));

        checkRootDirectoriesList("user2", List.of(toElementAttributes(rootDir2Uuid, "rootDir2", DIRECTORY, true, "user2")));

        //Cleaning Test
        deleteElement(rootDir1Uuid, rootDir1Uuid, "user1", true, true);
        deleteElement(rootDir2Uuid, rootDir2Uuid, "user2", true, true);
    }

    @Test
    public void testTwoUsersTwoPublicStudies() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory bu the user1
        UUID study1Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", STUDY, false, "user1", false);
        // Insert a public study in the root directory bu the user1
        UUID study2Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study2", STUDY, false, "user2", false);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1",
            List.of(
                toElementAttributes(study1Uuid, "study1", STUDY, false, "user1"),
                toElementAttributes(study2Uuid, "study2", STUDY, false, "user2")
            )
        );

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2",
            List.of(
                toElementAttributes(study1Uuid, "study1", STUDY, false, "user1"),
                toElementAttributes(study2Uuid, "study2", STUDY, false, "user2")
            )
        );
        deleteElement(study1Uuid, rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersOnePublicOnePrivateStudies() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory bu the user1
        UUID study1Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", STUDY, true, "user1", false);
        // Insert a public study in the root directory bu the user1
        UUID study2Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study2", STUDY, false, "user2", false);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1",
            List.of(
                toElementAttributes(study1Uuid, "study1", STUDY, true, "user1"),
                toElementAttributes(study2Uuid, "study2", STUDY, false, "user2")
            )
        );

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(toElementAttributes(study2Uuid, "study2", STUDY, false, "user2")));

        deleteElement(study1Uuid, rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersTwoPrivateStudies() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory bu the user1
        UUID study1Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", STUDY, true, "user1", false);
        // Insert a public study in the root directory bu the user1
        UUID study2Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study2", STUDY, true, "user2", false);

        // check user1 visible studies
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Uuid, "study1", STUDY, true, "user1")));

        // check user2 visible studies
        checkDirectoryContent(rootDirUuid, "user2", List.of(toElementAttributes(study2Uuid, "study2", STUDY, true, "user2")));

        deleteElement(study1Uuid, rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Uuid, "user1");

        deleteElement(study2Uuid, rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Uuid, "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testRecursiveDelete() {
        checkRootDirectoriesList("userId", List.of());
        // Insert a private root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", true, "userId");
        // Insert a public study in the root directory bu the userId
        UUID study1Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", STUDY, true, "userId", true);
        // Insert a public study in the root directory bu the userId
        UUID study2Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study2", STUDY, true, "userId", true);
        // Insert a subDirectory
        UUID subDirUuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "subDir", DIRECTORY, true, "userId", true);
        // Insert a public study in the root directory bu the userId
        UUID subDirStudyUuid = insertAndCheckSubElement(UUID.randomUUID(), subDirUuid, "study3", STUDY, true, "userId", true);

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, true);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(study1Uuid, "userId");
        checkElementNotFound(study2Uuid, "userId");
        checkElementNotFound(subDirUuid, "userId");
        checkElementNotFound(subDirStudyUuid, "userId");
    }

    @Test
    public void testRenameStudy() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(STUDY_RENAME_UUID, rootDirUuid, "study1", STUDY, false, "user1", false);

        renameElement(study1Uuid, rootDirUuid, "user1", "newName1", false, false);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Uuid, "newName1", STUDY, false, "user1")));
    }

    @Test
    public void testRenameStudyForbiddenFail() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(STUDY_RENAME_FORBIDDEN_UUID, rootDirUuid, "study1", STUDY, false, "user1", false);

        //the name should not change
        renameElementExpectFail(study1Uuid, "user1", "newName1", 403);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Uuid, "study1", STUDY, false, "user1")));
    }

    @Test
    public void testRenameStudyNotFoundFail() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(STUDY_RENAME_NOT_FOUND_UUID, rootDirUuid, "study1", STUDY, false, "user1", false);

        //the name should not change
        renameElementExpectFail(study1Uuid, "user1", "newName1", 404);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Uuid, "study1", STUDY, false, "user1")));
    }

    @Test
    public void testRenameDirectoryNotAllowed() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        //the name should not change
        renameElementExpectFail(rootDirUuid, "user1", "newName1", 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, false, "Doe")));
    }

    @Test
    public void testUpdateStudyAccessRight() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(STUDY_UPDATE_ACCESS_RIGHT_UUID, rootDirUuid, "study1", STUDY, false, "user1", false);

        //set study to private
        updateAccessRights(study1Uuid, rootDirUuid, "user1", true, false, false);
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Uuid, "study1", STUDY, true, "user1")));

        //set study back to public
        updateAccessRights(study1Uuid, rootDirUuid, "user1", false, false, false);
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Uuid, "study1", STUDY, false, "user1")));
    }

    @Test
    public void testUpdateStudyAccessRightForbiddenFail() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID, rootDirUuid, "study1", STUDY, false, "user1", false);

        //the access rights should not change
        updateStudyAccessRightFail(study1Uuid, "user2", true, 403);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Uuid, "study1", STUDY, false, "user1")));
    }

    @Test
    public void testUpdateStudyAccessRightWithWrongUser() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", STUDY, false, "user1", false);

        //try to update the study1 (of user1) with the user2 -> the access rights should not change because it's not allowed
        updateStudyAccessRightFail(study1Uuid, "user2", true, 403);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Uuid, "study1", STUDY, false, "user1")));
    }

    @Test
    public void testDeleteStudyWithWrongUser() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID study1Uuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", STUDY, false, "user1", false);

        //try to delete the study1 (of user1) with the user2 -> the should still be here
        deleteElementFail(study1Uuid, "user2", 403);
        checkDirectoryContent(rootDirUuid, "userId", List.of(toElementAttributes(study1Uuid, "study1", STUDY, false, "user1")));
    }

    @Test
    public void testEmitDirectoryChangedNotification() {
        checkRootDirectoriesList("Doe", List.of());
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");
        // Insert a public study in the root directory by the user1
        UUID contingencyListUuid = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "study1", CONTINGENCY_LIST, false, "Doe", false);

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
        assertEquals(rootDirUuid, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(true, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
    }

    @SneakyThrows
    @Test
    public void testGetElement() {
        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "user1");

        // Insert a public filter in the root directory by the user1
        UUID contingencyUUID = insertAndCheckSubElement(CONTINGENCY_LIST_UUID, rootDirUuid, "Contingency", CONTINGENCY_LIST, false, "user1", false);
        UUID filterUuid = insertAndCheckSubElement(FILTER_UUID, rootDirUuid, "Filter", FILTER, false, "user1", false);
        UUID scriptUUID = insertAndCheckSubElement(UUID.randomUUID(), rootDirUuid, "Script", FILTER, false, "user1", false);

        var res = getElements(List.of(contingencyUUID, filterUuid, scriptUUID, UUID.randomUUID()), "user1");
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper,
            List.of(
                toElementAttributes(contingencyUUID, "Contingency", CONTINGENCY_LIST, false, "user1"),
                toElementAttributes(filterUuid, "Filter", FILTER, false, "user1"),
                toElementAttributes(scriptUUID, "Script", FILTER, false, "user1")
            ))
        );

        renameElement(contingencyUUID, rootDirUuid, "user1", "newContingency", false, false);
        renameElement(filterUuid, rootDirUuid, "user1", "newFilter", false, false);
        renameElement(scriptUUID, rootDirUuid, "user1", "newScript", false, false);
        res = getElements(List.of(contingencyUUID, scriptUUID, filterUuid), "user1");
        assertEquals(3, res.size());

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper,
            List.of(
                toElementAttributes(contingencyUUID, "newContingency", CONTINGENCY_LIST, false, "user1"),
                toElementAttributes(filterUuid, "newFilter", FILTER, false, "user1"),
                toElementAttributes(scriptUUID, "newScript", FILTER, false, "user1")
            ))
        );
    }

    private void checkRootDirectoriesList(String userId, List<ElementAttributes> list) {
        webTestClient.get()
            .uri("/v1/root-directories")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, list));
    }

    private UUID insertAndCheckRootDirectory(String rootDirectoryName, boolean isPrivate, String userId) {
        WebTestClient.BodySpec<ElementAttributes, ?> result = webTestClient.post()
            .uri("/v1/root-directories")
            .header("userId", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(ElementAttributes.class);

        UUID uuidNewDirectory = Objects.requireNonNull(result.returnResult().getResponseBody()).getElementUuid();
        result.value(new MatcherJson<>(objectMapper, toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId)));

        assertElementIsProperlyInserted(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId);

        // assert that the broker message has been sent a root directory creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(uuidNewDirectory, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(true, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.ADD_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));

        return uuidNewDirectory;
    }

    private List<ElementAttributes> getElements(List<UUID> elementUuids, String userId) {
        var ids = new StringJoiner("&id=");
        elementUuids.stream().map(UUID::toString).forEach(ids::add);
        // Insert a sub-element of type DIRECTORY
        return webTestClient.get()
            .uri("/v1/directories/elements?id=" + ids)
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .returnResult()
            .getResponseBody();
    }

    private UUID insertAndCheckSubElement(UUID elementUuid, UUID parentDirectoryUUid, String subElementName, String type, boolean isPrivate, String userId, boolean isParentPrivate) {
        // Insert a sub-element of type DIRECTORY
        WebTestClient.BodySpec<ElementAttributes, ?> result = webTestClient.post()
            .uri("/v1/directories/" + parentDirectoryUUid)
            .header("userId", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(toElementAttributes(elementUuid, subElementName, type, isPrivate, userId))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(ElementAttributes.class);

        UUID uuidSubElement = Objects.requireNonNull(result.returnResult().getResponseBody()).getElementUuid();
        result.value(new MatcherJson<>(objectMapper, toElementAttributes(uuidSubElement, subElementName, type, isPrivate, userId)));

        // assert that the broker message has been sent an element creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(parentDirectoryUUid, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isParentPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));

        assertElementIsProperlyInserted(uuidSubElement, subElementName, type, isPrivate, userId);

        return uuidSubElement;
    }

    private void renameElement(UUID elementUuidToRename, UUID elementUuidHeader, String userId, String newName, boolean isRoot, boolean isPrivate) {
        webTestClient.put().uri("/v1/directories/" + elementUuidToRename + "/rename/" + newName)
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk();

        // assert that the broker message has been sent a notif for rename
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(isRoot, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
    }

    private void renameElementExpectFail(UUID elementUuidToRename, String userId, String newName, int httpCodeExpected) {
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

    private void updateAccessRights(UUID elementUuidToUpdate, UUID elementUuidHeader, String userId, boolean newIsPrivate, boolean isRoot, boolean isPrivate) {
        webTestClient.put().uri("/v1/directories/" + elementUuidToUpdate + "/rights")
            .header("userId", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(newIsPrivate)
            .exchange()
            .expectStatus().isOk();

        // assert that the broker message has been sent a notif for rename
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(elementUuidHeader, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(isRoot, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
    }

    private void updateStudyAccessRightFail(UUID elementUuidToUpdate, String userId, boolean newisPrivate, int httpCodeExpected) {
        if (httpCodeExpected == 403) {
            webTestClient.put().uri("/v1/directories/" + elementUuidToUpdate + "/rights")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(newisPrivate)
                .exchange()
                .expectStatus().isForbidden();
        } else if (httpCodeExpected == 404) {
            webTestClient.put().uri("/v1/directories/" + elementUuidToUpdate + "/rights")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(newisPrivate)
                .exchange()
                .expectStatus().isNotFound();
        } else {
            fail("unexpected case");
        }
    }

    private void assertDirectoryIsEmpty(UUID uuidDir, String userId) {
        webTestClient.get()
            .uri("/v1/directories/" + uuidDir + "/content")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, List.of()));
    }

    private void assertElementIsProperlyInserted(UUID elementUuid, String elementName, String type, boolean isPrivate, String userId) {
        webTestClient.get()
            .uri("/v1/directories/" + elementUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, List.of(toElementAttributes(elementUuid, elementName, type, isPrivate, userId))));
    }

    private void checkDirectoryContent(UUID parentDirectoryUuid, String userId, List<ElementAttributes> list) {
        webTestClient.get()
            .uri("/v1/directories/" + parentDirectoryUuid + "/content")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, list));
    }

    private void checkElementNotFound(UUID elementUuid, String userId) {
        webTestClient.get()
            .uri("/v1/directories/" + elementUuid)
            .header("userId", userId)
            .exchange()
            .expectStatus().isNotFound();
    }

    private void deleteElement(UUID elementUuidToBeDeleted, UUID elementUuidHeader, String userId, boolean isRoot, boolean isPrivate) {
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
        assertEquals(elementUuidHeader, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(isRoot, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));
        assertEquals(isRoot ? NotificationType.DELETE_DIRECTORY : NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
    }

    private void deleteElementFail(UUID elementUuidToBeDeleted, String userId, int httpCodeExpected) {
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
