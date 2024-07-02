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
import java.util.*;

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
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aStudy").type("STUDY").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var filterInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aFilter").type("FILTER").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type("DIRECTORY").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aContingencyList").type("CONTINGENCY_LIST").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();

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
        var directoryInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aDirectory").type(DIRECTORY).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aStudy").type(STUDY).owner("admin1").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var caseInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aCase").type(CASE).owner("admin1").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var filterInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aFilter").type(FILTER).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        var contingencyListInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("aContingencyList").type(CONTINGENCY_LIST).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();

        List<DirectoryElementInfos> infos = List.of(directoryInfos, filterInfos, studyInfos, caseInfos, contingencyListInfos);
        repositoryService.saveElementsInfos(infos);

        Set<DirectoryElementInfos> hits = new HashSet<>(directoryElementInfosService.searchElements("a", ""));
        assertEquals(4, hits.size());
        assertTrue(hits.contains(studyInfos));
        assertTrue(hits.contains(caseInfos));
        assertTrue(hits.contains(filterInfos));
        assertTrue(hits.contains(contingencyListInfos));

        hits = new HashSet<>(directoryElementInfosService.searchElements("aDirectory", ""));
        assertEquals(0, hits.size());
    }

    @Test
    void searchSpecialChars() {
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("s+Ss+ss'sp&pn(n n)ne{e e}et<t t>te|eh-ht.th/hl\\lk[k k]k")
                .type(STUDY).owner("admin1").parentId(UUID.randomUUID())
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
        Assert.assertEquals(1, directoryElementInfosService.searchElements(pat, "").size());
    }

    private DirectoryElementInfos makeDir(String name){
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type(DIRECTORY).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
    }

    private DirectoryElementInfos makeFile(String name, String type, String owner, UUID parentId){
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type(type).owner(owner).parentId(parentId).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
    }
    /*
        Directory Structure:

        root_directory
        ├── sub_directory1
        │   ├── sub_sub_directory1_1
        │   │   └── common_file
        │   ├── sub_sub_directory1_2
        │   │   └── common_file
        │   └── file1
        ├── sub_directory2
        │   ├── sub_sub_directory2_1
        │   │   └── common_file
        │   ├── sub_sub_directory2_2
        │   │   └── common_file
        │   └── file2
        └── sub_directory3
            ├── sub_sub_directory3_1
            │   └── common_file
            ├── sub_sub_directory3_2
            │   └── common_file
            └── file3
     */
    HashMap<String, DirectoryElementInfos> createFiles() {
        HashMap<String, DirectoryElementInfos> allDirs = new HashMap<>();
        allDirs.put("root_directory", makeDir("root_directory"));
        allDirs.put("sub_directory1", makeDir("sub_directory1"));
        allDirs.put("sub_sub_directory1_1", makeDir("sub_sub_directory1_1"));
        allDirs.put("sub_sub_directory1_2", makeDir("sub_sub_directory1_2"));
        allDirs.put("sub_directory2", makeDir("sub_directory2"));
        allDirs.put("sub_sub_directory2_1", makeDir("sub_sub_directory2_1"));
        allDirs.put("sub_sub_directory2_2", makeDir("sub_sub_directory2_2"));
        allDirs.put("sub_directory3", makeDir("sub_directory3"));
        allDirs.put("sub_sub_directory3_1", makeDir("sub_sub_directory3_1"));
        allDirs.put("sub_sub_directory3_2", makeDir("sub_sub_directory3_2"));

        var file1 = makeFile("file1", STUDY, "admin", allDirs.get("sub_directory1").getId());
        var file2 = makeFile("file2", FILTER, "admin", allDirs.get("sub_directory2").getId());
        var file3 = makeFile("file2", STUDY, "admin", allDirs.get("sub_directory3").getId());

        var common_file1 = makeFile("common_file", STUDY, "admin", allDirs.get("sub_sub_directory1_1").getId());
        var common_file2 = makeFile("common_file", STUDY, "admin", allDirs.get("sub_sub_directory1_2").getId());
        var common_file3 = makeFile("common_file", STUDY, "admin", allDirs.get("sub_sub_directory2_1").getId());
        var common_file4 = makeFile("common_file", STUDY, "admin", allDirs.get("sub_sub_directory2_2").getId());
        var common_file5 = makeFile("common_file", STUDY, "admin", allDirs.get("sub_sub_directory3_1").getId());
        var common_file6 = makeFile("common_file", STUDY, "admin", allDirs.get("sub_sub_directory3_2").getId());

        repositoryService.saveElementsInfos(allDirs.values().stream().toList());
        List<DirectoryElementInfos> infos = List.of(
                file1, file2, file3,
                common_file1, common_file2, common_file3, common_file4, common_file5, common_file6);

        repositoryService.saveElementsInfos(infos);

        return allDirs;
    }

    @Test
    void testGetExactMatchFromSubDirectory(){
        createFiles();
        Set<DirectoryElementInfos> hits = new HashSet<>(directoryElementInfosService.searchElements("common_file", ""));
        assertEquals(6, hits.size());
    }

    @Test
    void testGetExactMatchFromOtherDirectory(){

    }

    @Test
    void testGetExactMatchinParentDirectory(){

    }
}
