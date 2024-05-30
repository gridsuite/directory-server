/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.google.common.collect.Iterables;
import org.apache.commons.collections4.IterableUtils;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.services.DirectoryElementInfosService;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DirectoryElementInfosServiceTest {
    @Autowired
    DirectoryRepositoryService repositoryService;

    @Autowired
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @Autowired
    DirectoryElementInfosService directoryElementInfosService;

    private void cleanDB() {
        directoryElementInfosRepository.deleteAll();
    }

    @BeforeEach
    public void setup() {
        cleanDB();
    }

    @Test
    void testAddDeleteElementInfos() {
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aStudy").type("STUDY").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var filterInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aFilter").type("FILTER").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type(DIRECTORY).parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aContingencyList").type("CONTINGENCY_LIST").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();

        // Add
        List<DirectoryElementInfos> infos = List.of(studyInfos, filterInfos, directoryInfos, contingencyListInfos);
        repositoryService.saveElementsInfos(infos);
        List<DirectoryElementInfos> infosDB = IterableUtils.toList(directoryElementInfosRepository.findAll());
        assertEquals(4, infosDB.size());
        assertEquals(infos, infosDB);

        // Modify
        studyInfos.setName("newName");
        directoryElementInfosRepository.save(studyInfos);
        assertEquals(studyInfos, directoryElementInfosRepository.findById(studyInfos.getId()).orElseThrow());

        // Delete
        directoryElementInfosRepository.deleteAll();
        assertEquals(0, Iterables.size(directoryElementInfosRepository.findAll()));
    }

    @Test
    void searchElementInfos() {
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type(DIRECTORY).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aStudy").type(STUDY).owner("admin1").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var caseInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aCase").type(CASE).owner("admin1").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var filterInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aFilter").type(FILTER).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aContingencyList").type(CONTINGENCY_LIST).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();

        List<DirectoryElementInfos> infos = List.of(directoryInfos, filterInfos, studyInfos, caseInfos, contingencyListInfos);
        repositoryService.saveElementsInfos(infos);

        Set<DirectoryElementInfos> hits = new HashSet<>(directoryElementInfosService.searchElements("a"));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(studyInfos));
        assertTrue(hits.contains(caseInfos));
        assertTrue(hits.contains(filterInfos));
        assertTrue(hits.contains(contingencyListInfos));

        hits = new HashSet<>(directoryElementInfosService.searchElements("aDirectory"));
        assertEquals(0, hits.size());
    }

    @Test
    void searchSpecialChars() {
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("s+Ss+ss'sp&pn(n n)ne{e e}et<t t>te|eh-ht.th/hl\\lk[k k]k")
                .type(STUDY).owner("admin1").parentId(UUID.randomUUID())
                .subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        repositoryService.saveElementsInfos(List.of(studyInfos));

        testNameFullAscii("s+S");
        testNameFullAscii("s+s");
        testNameFullAscii("h-h");
        testNameFullAscii("t.t");
        testNameFullAscii("h/h");
        testNameFullAscii("l\\l");
        testNameFullAscii("p&p");
        testNameFullAscii("n(n");
        testNameFullAscii("n)n");
        testNameFullAscii("k[k");
        testNameFullAscii("k]k");
        testNameFullAscii("e{e");
        testNameFullAscii("e}e");
        testNameFullAscii("t<t");
        testNameFullAscii("t>t");
        testNameFullAscii("s's");
        testNameFullAscii("e|e");
    }

    private void testNameFullAscii(String pat) {
        Assert.assertEquals(1, directoryElementInfosService.searchElements(pat).size());
    }
}
