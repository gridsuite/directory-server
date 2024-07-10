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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    public static final String TYPE_01 = "TYPE_01";
    public static final String TYPE_02 = "TYPE_02";
    public static final String TYPE_03 = "TYPE_03";
    public static final String TYPE_04 = "TYPE_04";
    public static final String DIRECTORY = "DIRECTORY";
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
        var element1Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName1").type(TYPE_01).parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var element2Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName2").type(TYPE_02).parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type(DIRECTORY).parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var element3Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName3").type(TYPE_03).parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();

        // Add
        List<DirectoryElementInfos> infos = List.of(element1Infos, element2Infos, directoryInfos, element3Infos);
        repositoryService.saveElementsInfos(infos);
        List<DirectoryElementInfos> infosDB = IterableUtils.toList(directoryElementInfosRepository.findAll());
        assertEquals(4, infosDB.size());
        assertEquals(infos, infosDB);

        // Modify
        element1Infos.setName("newName");
        directoryElementInfosRepository.save(element1Infos);
        assertEquals(element1Infos, directoryElementInfosRepository.findById(element1Infos.getId()).orElseThrow());

        // Delete
        directoryElementInfosRepository.deleteAll();
        assertEquals(0, Iterables.size(directoryElementInfosRepository.findAll()));
    }

    @Test
    void searchElementInfos() {
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type(DIRECTORY).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var element1Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName1").type(TYPE_01).owner("admin1").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var element4Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName4").type(TYPE_04).owner("admin1").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var element2Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName2").type(TYPE_02).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var element3Infos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("elementName3").type(TYPE_03).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();

        List<DirectoryElementInfos> infos = List.of(directoryInfos, element2Infos, element1Infos, element4Infos, element3Infos);
        repositoryService.saveElementsInfos(infos);

        Set<DirectoryElementInfos> hits = new HashSet<>(directoryElementInfosService.searchElements("a"));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(element1Infos));
        assertTrue(hits.contains(element4Infos));
        assertTrue(hits.contains(element2Infos));
        assertTrue(hits.contains(element3Infos));

        hits = new HashSet<>(directoryElementInfosService.searchElements("aDirectory"));
        assertEquals(0, hits.size());
    }

    @Test
    void searchSpecialChars() {
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("s+Ss+ss'sp&pn(n n)ne{e e}et<t t>te|eh-ht.th/hl\\lk[k k]k")
                .type(TYPE_01).owner("admin1").parentId(UUID.randomUUID())
                .subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
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
