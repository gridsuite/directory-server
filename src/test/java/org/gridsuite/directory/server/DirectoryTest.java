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
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.hamcrest.core.IsEqual;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.directory.server.DirectoryException.Type.UNKNOWN_NOTIFICATION;
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

    private static final UUID STUDY_RENAME_UUID = UUID.randomUUID();
    private static final UUID STUDY_RENAME_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_UUID = UUID.randomUUID();
    private static final UUID STUDY_UPDATE_ACCESS_RIGHT_FORBIDDEN_UUID = UUID.randomUUID();
    private static final UUID FILTER_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_UUID = UUID.randomUUID();

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private WebTestClient webTestClient;

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
    public void test() {
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
        checkElementNameExistInDirectory(uuidNewDirectory, "tutu", STUDY, HttpStatus.NOT_FOUND);

        // Delete the sub-directory newSubDir
        deleteElement(subDirAttributes.getElementUuid(), uuidNewDirectory, "userId", false, false);
        checkDirectoryContent(uuidNewDirectory, "userId", List.of(subEltAttributes));

        // Delete the sub-element newStudy
        deleteElement(subEltAttributes.getElementUuid(), uuidNewDirectory, "userId", false, false);
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

        deleteElement(uuidNewDirectory, uuidNewDirectory, "userId", true, true);
        checkRootDirectoriesList("userId", List.of());

        checkElementNotFound(newSubDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(newSubSubDirAttributes.getElementUuid(), "userId");
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
    public void testTwoUsersTwoStudiesInPublicDirectory() {
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
        deleteElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Attributes.getElementUuid(), "user1");

        deleteElement(study2Attributes.getElementUuid(), rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testTwoUsersElementsWithSameName() {
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
    public void testTwoUsersTwoStudies() {
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

        deleteElement(study1Attributes.getElementUuid(), rootDirUuid, "user1", false, false);
        checkElementNotFound(study1Attributes.getElementUuid(), "user1");

        deleteElement(study2Attributes.getElementUuid(), rootDirUuid, "user2", false, false);
        checkElementNotFound(study2Attributes.getElementUuid(), "user2");

        deleteElement(rootDirUuid, rootDirUuid, "Doe", true, false);
        checkElementNotFound(rootDirUuid, "Doe");
    }

    @Test
    public void testRecursiveDelete() {
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

        deleteElement(rootDirUuid, rootDirUuid, "userId", true, true);

        checkElementNotFound(rootDirUuid, "userId");
        checkElementNotFound(study1Attributes.getElementUuid(), "userId");
        checkElementNotFound(study2Attributes.getElementUuid(), "userId");
        checkElementNotFound(subDirAttributes.getElementUuid(), "userId");
        checkElementNotFound(subDirStudyAttributes.getElementUuid(), "userId");
    }

    @Test
    public void testRenameStudy() {
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
    public void testRenameStudyForbiddenFail() {
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
    public void testRenameDirectoryNotAllowed() {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", true, "Doe");

        //the name should not change
        renameElementExpectFail(rootDirUuid, "user1", "newName1", 403);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe")));
    }

    @Test
    public void testUpdateStudyAccessRight() {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        // Insert a study in the root directory by the user1
        ElementAttributes study1Attributes = toElementAttributes(STUDY_UPDATE_ACCESS_RIGHT_UUID, "study1", STUDY, null, "user1");
        insertAndCheckSubElement(rootDirUuid, false, study1Attributes);

        //set study to private -> not updatable
        updateStudyAccessRightFail(study1Attributes.getElementUuid(), "user1", true, 403);
        checkDirectoryContent(rootDirUuid, "user1", List.of(toElementAttributes(study1Attributes.getElementUuid(), "study1", STUDY, null, "user1")));
    }

    @Test
    public void testUpdateDirectoryAccessRight() {
        checkRootDirectoriesList("Doe", List.of());

        // Insert a public root directory user1
        UUID rootDirUuid = insertAndCheckRootDirectory("rootDir1", false, "Doe");

        //set directory to private
        updateAccessRights(rootDirUuid, rootDirUuid, "Doe", true, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe")));

        //reset it to public
        updateAccessRights(rootDirUuid, rootDirUuid, "Doe", false, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, false, "Doe")));

        //FIXME : This works now but it SHOULDNT remind me if i make the PR with that (Making a directory that you don't own private thus loosing view of it...)
        updateAccessRights(rootDirUuid, rootDirUuid, "User1", true, true, false);
        checkRootDirectoriesList("Doe", List.of(toElementAttributes(rootDirUuid, "rootDir1", DIRECTORY, true, "Doe")));
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

        webTestClient.post()
            .uri(String.format("/v1/elements/%s/notification?type=update_directory", contingencyListAttributes.getElementUuid()))
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

        // Test unknown type notification
        webTestClient.post()
            .uri(String.format("/v1/elements/%s/notification?type=bad_type", contingencyListAttributes.getElementUuid()))
            .header("userId", "Doe")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody(String.class)
            .value(new IsEqual<>(objectMapper.writeValueAsString(UNKNOWN_NOTIFICATION)));
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

        var res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1");
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(contingencyAttributes, filterAttributes, scriptAttributes)));

        renameElement(contingencyAttributes.getElementUuid(), rootDirUuid, "user1", "newContingency", false, false);
        renameElement(filterAttributes.getElementUuid(), rootDirUuid, "user1", "newFilter", false, false);
        renameElement(scriptAttributes.getElementUuid(), rootDirUuid, "user1", "newScript", false, false);
        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1");
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

        var res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1");
        assertEquals(3, res.size());
        ToStringVerifier.forClass(ElementAttributes.class).verify();

        res.sort(Comparator.comparing(ElementAttributes::getElementName));
        org.hamcrest.MatcherAssert.assertThat(res, new MatcherJson<>(objectMapper, List.of(contingencyAttributes, filterAttributes, scriptAttributes)));

        renameElement(contingencyAttributes.getElementUuid(), rootDirUuid, "user1", "newContingency", false, false);
        renameElement(filterAttributes.getElementUuid(), rootDirUuid, "user1", "newFilter", false, false);
        renameElement(scriptAttributes.getElementUuid(), rootDirUuid, "user1", "newScript", false, false);
        res = getElements(List.of(contingencyAttributes.getElementUuid(), filterAttributes.getElementUuid(), scriptAttributes.getElementUuid()), "user1");
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
            .bodyValue(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId, null))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(ElementAttributes.class);

        UUID uuidNewDirectory = Objects.requireNonNull(result.returnResult().getResponseBody()).getElementUuid();
        result.value(new MatcherJson<>(objectMapper, toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId)));

        assertElementIsProperlyInserted(toElementAttributes(uuidNewDirectory, rootDirectoryName, DIRECTORY, isPrivate, userId));

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
        var ids = elementUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        return webTestClient.get()
            .uri("/v1/elements?ids=" + ids)
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .returnResult()
            .getResponseBody();
    }

    private void insertAndCheckSubElement(UUID parentDirectoryUUid, boolean isParentPrivate, ElementAttributes subElementAttributes) {
        // Insert a sub-element of type DIRECTORY
        WebTestClient.BodySpec<ElementAttributes, ?> result = webTestClient.post()
            .uri("/v1/directories/" + parentDirectoryUUid + "/elements")
            .header("userId", subElementAttributes.getOwner())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(subElementAttributes)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(ElementAttributes.class);

        subElementAttributes.setElementUuid(Objects.requireNonNull(result.returnResult().getResponseBody()).getElementUuid());
        result.value(new MatcherJson<>(objectMapper, subElementAttributes));

        // assert that the broker message has been sent an element creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(subElementAttributes.getOwner(), headers.get(DirectoryService.HEADER_USER_ID));
        assertEquals(parentDirectoryUUid, headers.get(DirectoryService.HEADER_DIRECTORY_UUID));
        assertEquals(false, headers.get(DirectoryService.HEADER_IS_ROOT_DIRECTORY));
        assertEquals(!isParentPrivate, headers.get(DirectoryService.HEADER_IS_PUBLIC_DIRECTORY));
        assertEquals(NotificationType.UPDATE_DIRECTORY, headers.get(DirectoryService.HEADER_NOTIFICATION_TYPE));
        assertEquals(DirectoryService.UPDATE_TYPE_DIRECTORIES, headers.get(DirectoryService.HEADER_UPDATE_TYPE));

        assertElementIsProperlyInserted(subElementAttributes);
    }

    private void insertExpectFail(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) {
        // Insert a sub-element of type DIRECTORY and expect 403 forbidden
        webTestClient.post()
                .uri("/v1/directories/" + parentDirectoryUUid + "/elements")
                .header("userId", subElementAttributes.getOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(subElementAttributes)
                .exchange()
                .expectStatus().isForbidden();
    }

    private void renameElement(UUID elementUuidToRename, UUID elementUuidHeader, String userId, String newName, boolean isRoot, boolean isPrivate) {
        webTestClient.put()
            .uri(String.format("/v1/elements/%s", elementUuidToRename))
            .header("userId", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ElementAttributes.builder().elementName(newName).build())
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
            webTestClient.put()
                .uri(String.format("/v1/elements/%s", elementUuidToRename))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ElementAttributes.builder().elementName(newName).build())
                .exchange()
                .expectStatus().isForbidden();
        } else if (httpCodeExpected == 404) {
            webTestClient.put()
                .uri(String.format("/v1/elements/%s", elementUuidToRename))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ElementAttributes.builder().elementName(newName).build())
                .exchange()
                .expectStatus().isNotFound();
        } else {
            fail("unexpected case");
        }
    }

    private void updateAccessRights(UUID elementUuidToUpdate, UUID elementUuidHeader, String userId, boolean newIsPrivate, boolean isRoot, boolean isPrivate) {
        webTestClient.put()
            .uri(String.format("/v1/elements/%s", elementUuidToUpdate))
            .header("userId", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ElementAttributes.builder().accessRights(new AccessRightsAttributes(newIsPrivate)).build())
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

    private void updateStudyAccessRightFail(UUID elementUuidToUpdate, String userId, boolean newIsPrivate, int httpCodeExpected) {
        if (httpCodeExpected == 403) {
            webTestClient.put()
                .uri(String.format("/v1/elements/%s", elementUuidToUpdate))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ElementAttributes.builder().accessRights(new AccessRightsAttributes(newIsPrivate)).build())
                .exchange()
                .expectStatus().isForbidden();
        } else if (httpCodeExpected == 404) {
            webTestClient.put()
                .uri(String.format("/v1/elements/%s", elementUuidToUpdate))
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ElementAttributes.builder().accessRights(new AccessRightsAttributes(newIsPrivate)).build())
                .exchange()
                .expectStatus().isNotFound();
        } else {
            fail("unexpected case");
        }
    }

    private void assertDirectoryIsEmpty(UUID uuidDir, String userId) {
        webTestClient.get()
            .uri("/v1/directories/" + uuidDir + "/elements")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, List.of()));
    }

    private void assertElementIsProperlyInserted(ElementAttributes elementAttributes) {
        webTestClient.get()
            .uri("/v1/elements/" + elementAttributes.getElementUuid())
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, List.of(elementAttributes)));
    }

    private void checkDirectoryContent(UUID parentDirectoryUuid, String userId, List<ElementAttributes> list) {
        webTestClient.get()
            .uri("/v1/directories/" + parentDirectoryUuid + "/elements")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, list));
    }

    private void checkElementNotFound(UUID elementUuid, String userId) {
        webTestClient.get()
            .uri("/v1/elements/" + elementUuid)
            .header("userId", userId)
            .exchange()
            .expectStatus().isNotFound();
    }

    private void checkElementNameExistInDirectory(UUID parentDirectoryUuid, String elementName, String type, HttpStatus expectedStatus) {
        webTestClient.head()
            .uri(String.format("/v1/directories/%s/elements/%s/types/%s", parentDirectoryUuid, elementName, type))
            .exchange()
            .expectStatus().isEqualTo(expectedStatus);
    }

    private void deleteElement(UUID elementUuidToBeDeleted, UUID elementUuidHeader, String userId, boolean isRoot, boolean isPrivate) {
        webTestClient.delete()
            .uri("/v1/elements/" + elementUuidToBeDeleted)
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
                .uri("/v1/elements/" + elementUuidToBeDeleted)
                .header("userId", userId)
                .exchange()
                .expectStatus().isForbidden();
        } else {
            fail("unexpected case");
        }
    }

    @After
    public void tearDown() {
        assertNull("Should not be any messages", output.receive(1000));
    }
}
