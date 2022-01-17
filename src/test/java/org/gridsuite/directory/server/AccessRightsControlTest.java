/*
  Copyright (c) 2020, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.hamcrest.core.IsEqual;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.DirectoryService.STUDY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest
public class AccessRightsControlTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Before
    public void setUp() {
        directoryElementRepository.deleteAll();
    }

    @Test
    public void testRootDirectories() {
        checkRootDirectories("user1", List.of());
        checkRootDirectories("user2", List.of());
        checkRootDirectories("user3", List.of());

        // Insert a public root directory for user1
        UUID rootUuid1 = insertRootDirectory("user1", "root1", false);

        // Insert a public root directory for user2
        UUID rootUuid2 = insertRootDirectory("user2", "root2", false);

        // Insert a private root directory for user3
        UUID rootUuid3 = insertRootDirectory("user3", "root3", true);

        // Test with empty list
        controlDirectoriesAccess("user", List.of(), HttpStatus.OK);

        // Any user has access to public root directories
        controlDirectoriesAccess("user", List.of(rootUuid1, rootUuid2), HttpStatus.OK);
        controlDirectoriesAccess("user1", List.of(rootUuid1, rootUuid2), HttpStatus.OK);
        controlDirectoriesAccess("user2", List.of(rootUuid1, rootUuid2), HttpStatus.OK);
        controlDirectoriesAccess("user3", List.of(rootUuid1, rootUuid2), HttpStatus.OK);

        // Only owner has access to a private root directory
        controlDirectoriesAccess("user", List.of(rootUuid3), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user1", List.of(rootUuid3), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user2", List.of(rootUuid3), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user3", List.of(rootUuid3), HttpStatus.OK);
        controlDirectoriesAccess("user", List.of(rootUuid1, rootUuid2, rootUuid3), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user1", List.of(rootUuid1, rootUuid2, rootUuid3), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user2", List.of(rootUuid1, rootUuid2, rootUuid3), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user3", List.of(rootUuid1, rootUuid2, rootUuid3), HttpStatus.OK);
    }

    @Test
    public void testElements() {
        checkRootDirectories("user1", List.of());
        checkRootDirectories("user2", List.of());

        // Create directory tree for user1 : root1(public) -> dir1(public) -> study1
        UUID rootUuid1 = insertRootDirectory("user1", "root1", false);
        UUID dirUuid1 = insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, false, "user1"));
        UUID eltUuid1 = insertSubElement(dirUuid1, toElementAttributes(null, "study1", STUDY, true, "user1"));

        // Create directory tree for user2 : root2(public) -> dir2(private) -> study2
        UUID rootUuid2 = insertRootDirectory("user2", "root2", false);
        UUID dirUuid2 = insertSubElement(rootUuid2, toElementAttributes(null, "dir2", DIRECTORY, true, "user2"));
        UUID eltUuid2 = insertSubElement(dirUuid2, toElementAttributes(null, "study2", STUDY, true, "user2"));

        // Dir2 is private directory and only accessible by user2
        controlDirectoriesAccess("user1", List.of(rootUuid1, rootUuid2, dirUuid1), HttpStatus.OK);
        controlDirectoriesAccess("user1", List.of(dirUuid2), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user1", List.of(rootUuid1, rootUuid2, dirUuid1, dirUuid2), HttpStatus.FORBIDDEN);
        controlDirectoriesAccess("user2", List.of(rootUuid1, rootUuid2, dirUuid1, dirUuid2), HttpStatus.OK);

        // Dir2 is private and only sub elements creation for user2
        insertSubElement(dirUuid2, toElementAttributes(null, "dir", DIRECTORY, true, "user1"), HttpStatus.FORBIDDEN);
        insertSubElement(dirUuid2, toElementAttributes(null, "study", STUDY, true, "user1"), HttpStatus.FORBIDDEN);

        // Study2 is in a private directory and only accessible by user2
        controlElementsAccess("user1", List.of(eltUuid1), HttpStatus.OK);
        controlElementsAccess("user1", List.of(eltUuid2), HttpStatus.FORBIDDEN);
        controlElementsAccess("user2", List.of(eltUuid1, eltUuid2), HttpStatus.OK);

        // Delete elements
        deleteSubElement(dirUuid2, "user1", HttpStatus.FORBIDDEN);
        deleteSubElement(eltUuid2, "user1", HttpStatus.FORBIDDEN);

        deleteSubElement(rootUuid1, "user1", HttpStatus.OK);
        deleteSubElement(dirUuid1, "user1", HttpStatus.NOT_FOUND);
        deleteSubElement(eltUuid1, "user1", HttpStatus.NOT_FOUND);

        deleteSubElement(dirUuid2, "user2", HttpStatus.OK);
        deleteSubElement(eltUuid2, "user2", HttpStatus.NOT_FOUND);
        deleteSubElement(rootUuid2, "user2", HttpStatus.OK);
    }

    @Test
    public void testExistence() {
        // Insert root directory with same name not allowed
        UUID rootUuid1 = insertRootDirectory("user1", "root1", false);
        insertRootDirectory("user1", "root1", false, HttpStatus.FORBIDDEN);

        // Insert elements with same name in a directory not allowed
        UUID dirUuid1 = insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, false, "user1"));
        insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, false, "user1"), HttpStatus.FORBIDDEN);
        insertSubElement(dirUuid1, toElementAttributes(null, "study1", STUDY, true, "user1"));
        insertSubElement(dirUuid1, toElementAttributes(null, "study1", STUDY, true, "user1"), HttpStatus.FORBIDDEN);
    }

    private UUID insertSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) {
        return insertSubElement(parentDirectoryUUid, subElementAttributes, HttpStatus.OK)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(ElementAttributes.class)
            .returnResult()
            .getResponseBody()
            .getElementUuid();
    }

    private WebTestClient.ResponseSpec insertSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes, HttpStatus expectedStatus) {
        return webTestClient.post()
            .uri("/v1/directories/" + parentDirectoryUUid + "/elements")
            .header("userId", subElementAttributes.getOwner())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(subElementAttributes)
            .exchange()
            .expectStatus()
            .value(new IsEqual<>(expectedStatus.value()));
    }

    private void deleteSubElement(UUID elementUUid, String userId, HttpStatus expectedStatus) {
        webTestClient.delete()
            .uri("/v1/elements/" + elementUUid)
            .header("userId", userId)
            .exchange()
            .expectStatus()
            .value(new IsEqual<>(expectedStatus.value()));
    }

    private void controlElementsAccess(String userId, List<UUID> uuids, HttpStatus expectedStatus) {
        controlDirectoriesAccess(userId, uuids, true, expectedStatus);
    }

    private void controlDirectoriesAccess(String userId, List<UUID> uuids, HttpStatus expectedStatus) {
        controlDirectoriesAccess(userId, uuids, false, expectedStatus);
    }

    private void controlDirectoriesAccess(String userId, List<UUID> uuids, boolean isElementsControl, HttpStatus expectedStatus) {
        var ids = uuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        webTestClient.head()
            .uri((isElementsControl ? "/v1/elements?ids=" : "/v1/directories?ids=") + ids)
            .header("userId", userId)
            .exchange()
            .expectStatus()
            .value(new IsEqual<>(expectedStatus.value()));
    }

    private void checkRootDirectories(String userId, List<ElementAttributes> list) {
        webTestClient.get()
            .uri("/v1/root-directories")
            .header("userId", userId)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(ElementAttributes.class)
            .value(new MatcherJson<>(objectMapper, list));
    }

    private UUID insertRootDirectory(String userId, String rootDirectoryName, boolean isPrivate) {
        return insertRootDirectory(userId, rootDirectoryName, isPrivate,  HttpStatus.OK)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(ElementAttributes.class)
            .returnResult()
            .getResponseBody()
            .getElementUuid();
    }

    private WebTestClient.ResponseSpec insertRootDirectory(String userId, String rootDirectoryName, boolean isPrivate, HttpStatus expectedStatus) {
        return webTestClient.post()
            .uri("/v1/root-directories")
            .header("userId", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new RootDirectoryAttributes(rootDirectoryName, new AccessRightsAttributes(isPrivate), userId, null))
            .exchange()
            .expectStatus()
            .value(new IsEqual<>(expectedStatus.value()));
    }
}
