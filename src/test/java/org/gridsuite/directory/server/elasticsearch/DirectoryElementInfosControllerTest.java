/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
class DirectoryElementInfosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private DirectoryElementRepository directoryElementRepository;

    @Autowired
    DirectoryElementInfosRepository directoryElementInfosRepository;

    private void cleanDB() {
        directoryElementRepository.deleteAll();
        directoryElementInfosRepository.deleteAll();
    }

    @BeforeEach
    public void setup() {
        cleanDB();
    }

    @Test
    void testReindexAll() throws Exception {
        String userId = "user";
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).withNano(0);
        ElementAttributes caseElement = ElementAttributes.toElementAttributes(UUID.randomUUID(), "caseName", "CASE",
                false, "user", null, now, now, userId);
        String requestBody = objectMapper.writeValueAsString(caseElement);
        mockMvc.perform(post("/v1/directories/paths/elements?directoryPath=dir")
                        .header("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        List<DirectoryElementEntity> directoryElementList = directoryElementRepository.findAll();
        assertEquals(2, directoryElementList.size());

        mockMvc.perform(post("/v1/elements/reindex-all"))
                .andExpect(status().isOk());

        assertEquals(2, directoryElementInfosRepository.findAll().size());
    }
}
