/*
  Copyright (c) 2020, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.PermissionType;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.repository.PermissionEntity;
import org.gridsuite.directory.server.repository.PermissionRepository;
import org.gridsuite.directory.server.services.UserAdminService;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.hamcrest.core.IsEqual;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.directory.server.DirectoryService.ALL_USERS;
import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.gridsuite.directory.server.dto.PermissionType.READ;
import static org.gridsuite.directory.server.dto.PermissionType.WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfiguration(classes = {DirectoryApplication.class, TestChannelBinderConfiguration.class})
public class AccessRightsControlTest {
    public static final String TYPE_01 = "TYPE_01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserAdminService userAdminService;

    MockWebServer server;

    @Before
    public void setup() {
        server = new MockWebServer();
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);

        userAdminService.setUserAdminServerBaseUri(baseUrl);

        // Ask the server for its URL. You'll need this to make HTTP requests.
        final Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if ("HEAD".equals(request.getMethod()) && path.matches("/v1/users/.*/isAdmin")) {
                        return new MockResponse().setResponseCode(200);
                    }

                return new MockResponse().setResponseCode(418);
            }
        };
        server.setDispatcher(dispatcher);
        directoryElementRepository.deleteAll();
        permissionRepository.deleteAll();
    }

    @Test
    public void testRootDirectories() throws Exception {
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        checkRootDirectories(user1, List.of());
        checkRootDirectories(user2, List.of());
        checkRootDirectories(user3, List.of());

        // Insert a root directory for user1
        UUID rootUuid1 = insertRootDirectory(user1, "root1");

        // Insert a root directory for user2
        UUID rootUuid2 = insertRootDirectory(user2, "root2");

        // Insert a root directory for user3
        UUID rootUuid3 = insertRootDirectory(user3, "root3");

        //For each element creation we should have 2 entries in DB, 1 for the creator and one for all users "ALL_USERS"
        //Check that permission are set to read for all and write only for the creator
        ArrayList<PermissionEntity> expectedPermissions = new ArrayList<>();
        expectedPermissions.add(new PermissionEntity(rootUuid1, user1, "", true, true));
        expectedPermissions.add(new PermissionEntity(rootUuid1, ALL_USERS, "", true, false));
        expectedPermissions.add(new PermissionEntity(rootUuid2, user2, "", true, true));
        expectedPermissions.add(new PermissionEntity(rootUuid2, ALL_USERS, "", true, false));
        expectedPermissions.add(new PermissionEntity(rootUuid3, user3, "", true, true));
        expectedPermissions.add(new PermissionEntity(rootUuid3, ALL_USERS, "", true, false));
        List<PermissionEntity> permissions = permissionRepository.findAll();
        assertEquals(6, permissions.size());
        assertTrue(permissions.containsAll(expectedPermissions));

        // Test with empty list
        controlElementsAccess("user", List.of(), null, READ, HttpStatus.OK);

        // All users should have read permission for all root directories
        // But they should only have the others permission (move, update, write...) for the root directory they created
        // Check read access
        controlElementsAccess("user", List.of(rootUuid1, rootUuid2, rootUuid3), null, READ, HttpStatus.OK);
        controlElementsAccess(user1, List.of(rootUuid1, rootUuid2, rootUuid3), null, READ, HttpStatus.OK);
        controlElementsAccess(user2, List.of(rootUuid1, rootUuid2, rootUuid3), null, READ, HttpStatus.OK);
        controlElementsAccess(user3, List.of(rootUuid1, rootUuid2, rootUuid3), null, READ, HttpStatus.OK);

        // Check WRITE access OK
        controlElementsAccess(user1, List.of(rootUuid1), null, WRITE, HttpStatus.OK);
        controlElementsAccess(user2, List.of(rootUuid2), null, WRITE, HttpStatus.OK);
        controlElementsAccess(user3, List.of(rootUuid3), null, WRITE, HttpStatus.OK);

        // Check WRITE access not OK
        controlElementsAccess(user1, List.of(rootUuid2), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess(user2, List.of(rootUuid1), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess(user3, List.of(rootUuid2), null, WRITE, HttpStatus.NO_CONTENT);

        // Check WRITE access on multiple element not OK
        controlElementsAccess(user1, List.of(rootUuid1, rootUuid2, rootUuid3), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess(user2, List.of(rootUuid1, rootUuid2, rootUuid3), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess(user3, List.of(rootUuid1, rootUuid2, rootUuid3), null, WRITE, HttpStatus.NO_CONTENT);
    }

    @Test
    public void testElements() throws Exception {
        checkRootDirectories("user1", List.of());
        checkRootDirectories("user2", List.of());
        // Create directory tree for user1 : root1 -> dir1 -> element of type TYPE_01
        UUID rootUuid1 = insertRootDirectory("user1", "root1");
        UUID dirUuid1 = insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, "user1"));
        UUID eltUuid1 = insertSubElement(dirUuid1, toElementAttributes(null, "elementName1", TYPE_01, "user1"));

        UUID rootUuid3 = insertRootDirectory("user1", "root3");
        UUID dirUuid3 = insertSubElement(rootUuid3, toElementAttributes(null, "dir3", DIRECTORY, "user1"));
        UUID eltUuid3 = insertSubElement(dirUuid3, toElementAttributes(null, "elementName3", TYPE_01, "user1"));

        // Create directory tree for user2 : root2 -> dir2 -> element of type TYPE_01
        UUID rootUuid2 = insertRootDirectory("user2", "root2");
        UUID dirUuid2 = insertSubElement(rootUuid2, toElementAttributes(null, "dir2", DIRECTORY, "user2"));
        UUID eltUuid2 = insertSubElement(dirUuid2, toElementAttributes(null, "elementName2", TYPE_01, "user2"));

        UUID rootUuid4 = insertRootDirectory("user2", "root4");
        UUID dirUuid4 = insertSubElement(rootUuid4, toElementAttributes(null, "dir4", DIRECTORY, "user2"));
        UUID eltUuid4 = insertSubElement(dirUuid4, toElementAttributes(null, "elementName4", TYPE_01, "user2"));

        // Dir2 is created by user2 and is accessible by user1 and user2 to read
        controlElementsAccess("user1", List.of(rootUuid1, rootUuid2, dirUuid1, dirUuid2, eltUuid1, eltUuid2), null, READ, HttpStatus.OK);
        controlElementsAccess("user2", List.of(rootUuid1, rootUuid2, dirUuid1, dirUuid2, eltUuid1, eltUuid2), null, READ, HttpStatus.OK);

        // - ROOT1 (USER1)
        //      - DIR1
        //          - ELEMENT1
        // - ROOT2 (USER2)
        //      - DIR2
        //          - ELEMENT2
        // - ROOT3 (USER1)
        //      - DIR3
        //          - ELEMENT3
        // - ROOT4 (USER2)
        //      - DIR4
        //          - ELEMENT4
        // Write access should be OK for each user on its own elements
        controlElementsAccess("user1", List.of(rootUuid1, dirUuid1, eltUuid1), null, WRITE, HttpStatus.OK);
        controlElementsAccess("user2", List.of(rootUuid2, dirUuid2, eltUuid2), null, WRITE, HttpStatus.OK);

        // Write access should NOT be OK for other user elements
        controlElementsAccess("user1", List.of(rootUuid2, dirUuid2, eltUuid2), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess("user2", List.of(rootUuid1, dirUuid1, eltUuid1), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess("user1", List.of(eltUuid2), null, WRITE, HttpStatus.NO_CONTENT);
        controlElementsAccess("user2", List.of(eltUuid1), null, WRITE, HttpStatus.NO_CONTENT);

        // Write access should be OK for something like move within folder the user has permissions
        // this is what should be called if user1 moves element3 to directory1 (should be OK)
        controlElementsAccess("user1", List.of(eltUuid3), dirUuid1, WRITE, HttpStatus.OK);
        // this is what should be called if user2 moves element4 to directory2 (should be OK)
        controlElementsAccess("user2", List.of(eltUuid4), dirUuid2, WRITE, HttpStatus.OK);

        // Write access should NOT be OK for something like move within folder the user doesn't have permissions
        // this is what should be called if user1 moves element3 to directory2 (should NOT be OK)
        controlElementsAccess("user1", List.of(eltUuid3), dirUuid2, WRITE, HttpStatus.NO_CONTENT);
        // this is what should be called if user2 moves element4 to directory3 (should NOT be OK)
        controlElementsAccess("user2", List.of(eltUuid4), dirUuid3, WRITE, HttpStatus.NO_CONTENT);
    }

    @Test
    public void testExistence() throws Exception {
        // Insert root directory with same name not allowed
        UUID rootUuid1 = insertRootDirectory("user1", "root1");
        insertRootDirectory("user1", "root1", HttpStatus.FORBIDDEN);

        // Insert elements with same name in a directory not allowed
        UUID dirUuid1 = insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, "user1"));
        insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, "user1"), HttpStatus.CONFLICT);
        insertSubElement(dirUuid1, toElementAttributes(null, "elementName1", TYPE_01, "user1"));
        insertSubElement(dirUuid1, toElementAttributes(null, "elementName1", TYPE_01, "user1"), HttpStatus.CONFLICT);
    }

    private UUID insertSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes) throws Exception {
        String response = insertSubElement(parentDirectoryUUid, subElementAttributes, HttpStatus.OK)
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, ElementAttributes.class).getElementUuid();
    }

    private MvcResult insertSubElement(UUID parentDirectoryUUid, ElementAttributes subElementAttributes, HttpStatus expectedStatus) throws Exception {
        return mockMvc.perform(post("/v1/directories/" + parentDirectoryUUid + "/elements")
                .header("userId", subElementAttributes.getOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subElementAttributes)))
                .andExpect(status().is(new IsEqual<>(expectedStatus.value())))
                .andReturn();
    }

    private void controlElementsAccess(String userId, List<UUID> uuids, UUID targetDirectoryUuid, PermissionType accessType, HttpStatus expectedStatus) throws Exception {
        var ids = uuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        mockMvc.perform(head("/v1/elements?accessType=" + accessType.name() + "&ids=" + ids + "&targetDirectoryUuid" + (targetDirectoryUuid != null ? "=" + targetDirectoryUuid : "")).header("userId", userId))
               .andExpect(status().is(new IsEqual<>(expectedStatus.value())));
    }

    private void checkRootDirectories(String userId, List<ElementAttributes> list) throws Exception {
        String response = mockMvc.perform(get("/v1/root-directories").header("userId", userId)
                                          .accept(MediaType.APPLICATION_JSON))
                                 .andExpectAll(status().isOk(), content().contentType(APPLICATION_JSON_VALUE))
                                 .andReturn()
                                 .getResponse()
                                 .getContentAsString();

        List<ElementAttributes> elementAttributes = objectMapper.readerForListOf(ElementAttributes.class).readValue(response);
        assertTrue(new MatcherJson<>(objectMapper, list).matchesSafely(elementAttributes));
    }

    private UUID insertRootDirectory(String userId, String rootDirectoryName) throws Exception {
        MockHttpServletResponse response = insertRootDirectory(userId, rootDirectoryName, HttpStatus.OK).getResponse();
        assertNotNull(response);
        assertEquals(APPLICATION_JSON_VALUE, response.getContentType());
        return objectMapper.readValue(response.getContentAsString(), ElementAttributes.class).getElementUuid();
    }

    private MvcResult insertRootDirectory(String userId, String rootDirectoryName, HttpStatus expectedStatus) throws Exception {
        return mockMvc.perform(post("/v1/root-directories")
                .header("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RootDirectoryAttributes(rootDirectoryName, userId, null, null, null, null))))
                .andExpect(status().is(new IsEqual<>(expectedStatus.value())))
                .andReturn();
    }
}
