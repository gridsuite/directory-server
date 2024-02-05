/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.FILTER;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DirectoryElementInfosServiceTest {

    private static final UUID ELEMENT_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void addDirectoryElementsInfos() {
        LocalDateTime localCreationDate = LocalDateTime.now(ZoneOffset.UTC);
        DirectoryElementEntity elementEntity2 = new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", FILTER, true, "userId", "description", localCreationDate, localCreationDate, "userId");

    }

    @Test
    void findAllDirectoryElementInfos() {
    }
}
