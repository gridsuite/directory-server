/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@RunWith(SpringRunner.class)
@WebMvcTest(DirectoryController.class)
public class DirectoryControllerTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DirectoryService directoryService;

    @MockBean
    private DirectoryRepositoryService directoryRepositoryService;

    @Test
    public void testCreateElementWithEncodedPath() throws Exception {
        String encodedPath = "%26~%23%7B%5B%5Eimport%C3%A9%22";
        String decodedPath = "&~#{[^importé\"";
        String userId = "testUser";
        String requestBody = new ObjectMapper().writeValueAsString(ElementAttributes.builder().build());
        mockMvc.perform(post("/v1/directories/paths/elements")
                        .param("directoryPath", encodedPath)
                        .content(requestBody)
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
        verify(directoryService).createElementInDirectoryPath(eq(decodedPath), any(), eq(userId));
    }
}
