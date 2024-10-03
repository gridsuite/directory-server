/*
  Copyright (c) 2020, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.hamcrest.core.IsEqual;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Before
    public void setUp() {
        directoryElementRepository.deleteAll();
    }

    @Test
    public void testRootDirectories() throws Exception {
        checkRootDirectories("user1", List.of());
        checkRootDirectories("user2", List.of());
        checkRootDirectories("user3", List.of());

        // Insert a root directory for user1
        UUID rootUuid1 = insertRootDirectory("user1", "root1");

        // Insert a root directory for user2
        UUID rootUuid2 = insertRootDirectory("user2", "root2");

        // Test with empty list
        controlElementsAccess("user", List.of(), HttpStatus.OK);

        // Any user has access to root directories
        controlElementsAccess("user", List.of(rootUuid1, rootUuid2), HttpStatus.OK);
        controlElementsAccess("user1", List.of(rootUuid1, rootUuid2), HttpStatus.OK);
        controlElementsAccess("user2", List.of(rootUuid1, rootUuid2), HttpStatus.OK);
        controlElementsAccess("user3", List.of(rootUuid1, rootUuid2), HttpStatus.OK);

    }

    @Test
    public void testElements() throws Exception {
        checkRootDirectories("user1", List.of());
        checkRootDirectories("user2", List.of());
        // Create directory tree for user1 : root1 -> dir1 -> element of type TYPE_01
        UUID rootUuid1 = insertRootDirectory("user1", "root1");
        UUID dirUuid1 = insertSubElement(rootUuid1, toElementAttributes(null, "dir1", DIRECTORY, "user1"));
        UUID eltUuid1 = insertSubElement(dirUuid1, toElementAttributes(null, "elementName1", TYPE_01, "user1"));

        // Create directory tree for user2 : root2 -> dir2 -> element of type TYPE_01
        UUID rootUuid2 = insertRootDirectory("user2", "root2");
        UUID dirUuid2 = insertSubElement(rootUuid2, toElementAttributes(null, "dir2", DIRECTORY, "user2"));
        UUID eltUuid2 = insertSubElement(dirUuid2, toElementAttributes(null, "elementName2", TYPE_01, "user2"));

        // Dir2 is created by user2 and is accessible by user1 and user2
        controlElementsAccess("user1", List.of(rootUuid1, rootUuid2, dirUuid1, dirUuid2, eltUuid1, eltUuid2), HttpStatus.OK);
        controlElementsAccess("user2", List.of(rootUuid1, rootUuid2, dirUuid1, dirUuid2, eltUuid1, eltUuid2), HttpStatus.OK);

        // Dir2 is created by user2 and sub elements creation for user1
        UUID dirUuid = insertSubElement(dirUuid2, toElementAttributes(null, "dir", DIRECTORY, "user1"));
        UUID eltUuid = insertSubElement(dirUuid2, toElementAttributes(null, "elementName", TYPE_01, "user1"));

        // elementName2 is accessible by user1 and user2
        controlElementsAccess("user1", List.of(eltUuid1, eltUuid2), HttpStatus.OK);
        controlElementsAccess("user2", List.of(eltUuid1, eltUuid2), HttpStatus.OK);

        // Delete elements
        deleteSubElement(dirUuid2, "user1", HttpStatus.FORBIDDEN);
        deleteSubElement(eltUuid2, "user1", HttpStatus.FORBIDDEN);

        deleteSubElement(rootUuid1, "user1", HttpStatus.OK);
        deleteSubElement(dirUuid1, "user1", HttpStatus.NOT_FOUND);
        deleteSubElement(eltUuid1, "user1", HttpStatus.NOT_FOUND);

        deleteSubElement(eltUuid, "user1", HttpStatus.OK);
        deleteSubElement(dirUuid, "user1", HttpStatus.OK);

        deleteSubElement(dirUuid2, "user2", HttpStatus.OK);
        deleteSubElement(eltUuid2, "user2", HttpStatus.NOT_FOUND);
        deleteSubElement(rootUuid2, "user2", HttpStatus.OK);
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

    private void deleteSubElement(UUID elementUUid, String userId, HttpStatus expectedStatus) throws Exception {
        mockMvc.perform(delete("/v1/elements/" + elementUUid).header("userId", userId))
               .andExpect(status().is(new IsEqual<>(expectedStatus.value())));
    }

    private void controlElementsAccess(String userId, List<UUID> uuids, HttpStatus expectedStatus) throws Exception {
        var ids = uuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        mockMvc.perform(head("/v1/elements?ids=" + ids).header("userId", userId))
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
