/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aStudy").type("STUDY").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var filterInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aFilter").type("FILTER").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type("DIRECTORY").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aContingencyList").type("CONTINGENCY_LIST").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();

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

    /*private BoolQuery buildBoolQueryElementName(String elementName) {
        return new BoolQuery.Builder().filter(Queries.termQuery(EQUIPMENT_TYPE_FIELD, elementName)._toQuery()).build();
    }

    @Test
    void searchElementInfos() {
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aStudy").type("STUDY").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var filterInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aFilter").type("FILTER").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type("DIRECTORY").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aContingencyList").type("CONTINGENCY_LIST").parentId(UUID.randomUUID()).isPrivate(true).subdirectoriesCount(0L).lastModificationDate(LocalDateTime.now().withNano(0)).build();

        List<DirectoryElementInfos> infos = List.of(studyInfos, filterInfos, directoryInfos, contingencyListInfos);
        repositoryService.saveElementsInfos(infos);

        Set<DirectoryElementInfos> hits = new HashSet<>(directoryElementInfosService.searchElements("aFilter", "admin"));
        Assert.assertEquals(0, hits.size());

        hits = new HashSet<>(directoryElementInfosService.searchElements(buildBoolQueryElementName("aStudy")));
        Assert.assertEquals(1, hits.size());
        assertTrue(hits.contains(filterInfos));

        hits = new HashSet<>(directoryElementInfosService.searchElements(buildBoolQueryElementName("aContingencyList")));
        Assert.assertEquals(3, hits.size());
        assertTrue(hits.contains(studyInfos));
        assertTrue(hits.contains(filterInfos));
        assertTrue(hits.contains(contingencyListInfos));

        hits = new HashSet<>(directoryElementInfosService.searchElements(buildBoolQueryElementName("TWO_WINDINGS_TRANSFORMER")));

        Assert.assertEquals(2, hits.size());
        assertTrue(hits.contains(filterInfos));
        assertTrue(hits.contains(filterInfos));

        hits = new HashSet<>(directoryElementInfosService.searchElements(buildBoolQueryElementName("CONFIGURED_BUS")));

        Assert.assertEquals(1, hits.size());
        assertTrue(hits.contains(filterInfos));
    }*/
}
