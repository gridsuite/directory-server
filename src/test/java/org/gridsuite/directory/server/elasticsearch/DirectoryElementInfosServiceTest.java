/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import com.google.common.collect.Iterables;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.junit.jupiter.api.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Iterator;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DirectoryElementInfosServiceTest {

    @Autowired
    DirectoryElementInfosService directoryElementInfosService;

    private void cleanDB() {
        directoryElementInfosService.deleteAll();
    }

    @BeforeAll
    public void setup() {
        cleanDB();
    }

    @Test
    @Order(1)
    void addDirectoryElementsInfosForStudy() {
        // GIVEN
        DirectoryElementInfos studiesInfos = DirectoryElementInfos.builder().id("id").name("name").type("STUDY").parentId("root").build();
        // WHEN
        directoryElementInfosService.addDirectoryElementInfos(studiesInfos);
        // THEN
        assertEquals(1, Iterables.size(directoryElementInfosService.findAllElementInfos()));
        DirectoryElementInfos loadInfosDB = directoryElementInfosService.findAllElementInfos().iterator().next();
        assertEquals(studiesInfos, loadInfosDB);
    }

    @Test
    @Order(2)
    void addDirectoryElementsInfosForFilter() {
        // GIVEN
        DirectoryElementInfos filtersInfos = DirectoryElementInfos.builder().id("id2").name("name").type("FILTER").parentId("root").build();
        // WHEN
        directoryElementInfosService.addDirectoryElementInfos(filtersInfos);
        // THEN
        assertEquals(2, Iterables.size(directoryElementInfosService.findAllElementInfos()));
        Iterator<DirectoryElementInfos> iterator = directoryElementInfosService.findAllElementInfos().iterator();
        iterator.next();
        DirectoryElementInfos loadInfosDB2 = iterator.next();
        assertEquals(filtersInfos, loadInfosDB2);

    }

    @Test
    @Order(3)
    void updateDirectoryElementsInfosForStudy() {
        // GIVEN
        DirectoryElementInfos studyInfos = DirectoryElementInfos.builder().id("id").name("study name").type("STUDY").parentId("root").build();
        // WHEN
        directoryElementInfosService.updateElementsInfos(studyInfos);
        // THEN
        assertEquals(2, Iterables.size(directoryElementInfosService.findAllElementInfos()));
        Iterator<DirectoryElementInfos> iterator = directoryElementInfosService.findAllElementInfos().iterator();
        DirectoryElementInfos loadInfosDB = iterator.next();
        loadInfosDB = Objects.equals(loadInfosDB.getId(), "id") ? loadInfosDB : iterator.next();
        assertEquals("study name", loadInfosDB.getName());
    }

    @Test
    @Order(4)
    void updateDirectoryElementsInfosForFilter() {
        // GIVEN
        DirectoryElementInfos filtersInfos = DirectoryElementInfos.builder().id("id2").name("filter name").type("FILTER").parentId("root").build();
        // WHEN
        directoryElementInfosService.updateElementsInfos(filtersInfos);
        // THEN
        assertEquals(2, Iterables.size(directoryElementInfosService.findAllElementInfos()));
        Iterator<DirectoryElementInfos> iterator = directoryElementInfosService.findAllElementInfos().iterator();
        iterator.next();
        DirectoryElementInfos loadInfosDB2 = iterator.next();
        assertEquals("filter name", loadInfosDB2.getName());
    }

    @Test
    @Order(5)
    void deleteExistingElementsInfos() {
        // GIVEN
        Iterator<DirectoryElementInfos> iterator = directoryElementInfosService.findAllElementInfos().iterator();
        // WHEN
        directoryElementInfosService.deleteElementsInfos(iterator.next().getId());
        directoryElementInfosService.deleteElementsInfos(iterator.next().getId());
        // THEN
        assertEquals(0, Iterables.size(directoryElementInfosService.findAllElementInfos()));
    }
}
