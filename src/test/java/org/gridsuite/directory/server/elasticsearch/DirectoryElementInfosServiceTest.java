/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DirectoryElementInfosServiceTest {

    @Autowired
    DirectoryElementInfosService directoryElementInfosService;

    @Autowired
    DirectoryElementInfosRepository directoryElementInfosRepository;

    private void cleanDB() {
        directoryElementInfosRepository.deleteAll();
    }

    @BeforeEach
    public void setup() {
        cleanDB();
    }

    @Test
    void testAddDeleteElementInfos() {
        var studyInfos = DirectoryElementInfos.builder().id("idStudy").name("aStudy").type("STUDY").parentId("parentId").isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var filterInfos = DirectoryElementInfos.builder().id("idFilter").name("aFilter").type("FILTER").parentId("parentId").isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var directoryInfos = DirectoryElementInfos.builder().id("idDirectory").name("aDirectory").type("DIRECTORY").parentId("parentId").isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id("idContingencyList").name("aContingencyList").type("CONTINGENCY_LIST").parentId("parentId").isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();

        // Add
        List<DirectoryElementInfos> infos = List.of(studyInfos, filterInfos, directoryInfos, contingencyListInfos);
        directoryElementInfosService.addAll(infos);
        List<DirectoryElementInfos> infosDB = directoryElementInfosRepository.findAll();
        assertEquals(4, infosDB.size());
        assertEquals(infos, infosDB);

        // Modify
        studyInfos.setName("newName");
        directoryElementInfosRepository.save(studyInfos);
        assertEquals(studyInfos, directoryElementInfosRepository.findById("idStudy").orElseThrow());

        // Delete
        directoryElementInfosRepository.deleteAll();
        assertEquals(0, directoryElementInfosRepository.findAll().size());
    }
}
