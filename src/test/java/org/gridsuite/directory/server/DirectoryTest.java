/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.CreateDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@RunWith(SpringRunner.class)
@WebMvcTest(DirectoryController.class)
@ContextConfiguration(classes = {DirectoryApplication.class})
public class DirectoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryTest.class);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    private DirectoryService directoryService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    public void test() throws Exception {

        UUID rootDirectoryUuid = UUID.randomUUID();
        UUID directoryUuid = UUID.randomUUID();

        mvc.perform(post("/v1/directories/create")
                .header("userId", "userId")
                .content(objectMapper.writeValueAsString(new CreateDirectoryAttributes(rootDirectoryUuid, "newDir", new AccessRightsAttributes(false), "owner")))
                .contentType(APPLICATION_JSON));

        mvc.perform(get("/v1/directories/" + directoryUuid + "/content")
                .header("userId", "userId")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        MvcResult result = mvc.perform(post("/v1/directories/create")
                .header("userId", "userId")
                .content(objectMapper.writeValueAsString(new CreateDirectoryAttributes(directoryUuid, "subDir", new AccessRightsAttributes(false), "owner")))
                .contentType(APPLICATION_JSON))
                .andReturn();

        JsonNode jsonTree = objectMapper.readTree(result.getResponse().getContentAsString());
        String uuid = jsonTree.get("directoryUuid").asText();
        assertTrue(jsonTree.get("directoryName").asText().equals("subDir"));
        assertFalse(jsonTree.get("directoryAccessRights").get("private").asBoolean());
        mvc.perform(get("/v1/directories/" + directoryUuid + "/content")
                .header("userId", "userId")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[{\"elementUuid\":" + uuid + ",\"elementName\":\"subDir\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\": false}}]"));

        String newSubDirName = "newSubDir";
        mvc.perform(put("/v1/directories/" + directoryUuid + "/rename/" + uuid + "/" + newSubDirName)
                .header("userId", "userId")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk());

        mvc.perform(get("/v1/directories/" + directoryUuid + "/content")
                .header("userId", "userId")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[{\"elementUuid\":" + uuid + ",\"elementName\":\"" + newSubDirName + "\",\"type\":\"DIRECTORY\",\"accessRights\":{\"private\": false}}]"));
    }

}
