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
        assertEquals(1, directoryElementInfosService.searchElements(pat, "").size());
    }

    private DirectoryElementInfos makeElementDir(String name) {
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type(DIRECTORY).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
    }

    private DirectoryElementInfos makeElementFile(String name, UUID parentId) {
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type(STUDY).owner("admin").parentId(parentId).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
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
    HashMap<String, DirectoryElementInfos> createFilesElements() {
        HashMap<String, DirectoryElementInfos> allDirs = new HashMap<>();
        allDirs.put("root_directory", makeElementDir("root_directory"));
        allDirs.put("sub_directory1", makeElementDir("sub_directory1"));
        allDirs.put("sub_sub_directory1_1", makeElementDir("sub_sub_directory1_1"));
        allDirs.put("sub_sub_directory1_2", makeElementDir("sub_sub_directory1_2"));
        allDirs.put("sub_directory2", makeElementDir("sub_directory2"));
        allDirs.put("sub_sub_directory2_1", makeElementDir("sub_sub_directory2_1"));
        allDirs.put("sub_sub_directory2_2", makeElementDir("sub_sub_directory2_2"));
        allDirs.put("sub_directory3", makeElementDir("sub_directory3"));
        allDirs.put("sub_sub_directory3_1", makeElementDir("sub_sub_directory3_1"));
        allDirs.put("sub_sub_directory3_2", makeElementDir("sub_sub_directory3_2"));

        var file1 = makeElementFile("file1", allDirs.get("sub_directory1").getId());
        var file2 = makeElementFile("file2", allDirs.get("sub_directory2").getId());
        var file3 = makeElementFile("file3", allDirs.get("sub_directory3").getId());

        var commonFile1 = makeElementFile("common_file", allDirs.get("sub_sub_directory1_1").getId());
        var commonFile2 = makeElementFile("common_file", allDirs.get("sub_sub_directory1_2").getId());
        var commonFile3 = makeElementFile("common_file", allDirs.get("sub_sub_directory2_1").getId());
        var commonFile4 = makeElementFile("common_file", allDirs.get("sub_sub_directory2_2").getId());
        var commonFile5 = makeElementFile("common_file", allDirs.get("sub_sub_directory3_1").getId());
        var commonFile6 = makeElementFile("common_file", allDirs.get("sub_sub_directory3_2").getId());

        repositoryService.saveElementsInfos(allDirs.values().stream().toList());
        List<DirectoryElementInfos> infos = List.of(
                file1, file2, file3,
                commonFile1, commonFile2, commonFile3, commonFile4, commonFile5, commonFile6);

        repositoryService.saveElementsInfos(infos);

        return allDirs;
    }

    @Test
    void testExactMatchFromSubDirectory() {
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        List<DirectoryElementInfos> hitsCommunFile = directoryElementInfosService.searchElements("common_file", currentDirUuid.toString());
        assertEquals(6, hitsCommunFile.size());
        assertEquals(currentDirUuid, hitsCommunFile.get(0).getParentId()); // we get first the element in the current directory
        assertEquals("common_file", hitsCommunFile.get(0).getName());

        //now using another current dir , we expect similar results
        currentDirUuid = allDirs.get("sub_sub_directory2_2").getId();
        hitsCommunFile = directoryElementInfosService.searchElements("common_file", currentDirUuid.toString());
        assertEquals(6, hitsCommunFile.size());
        assertEquals(currentDirUuid, hitsCommunFile.get(0).getParentId()); // we get first the element in the current directory
        assertEquals("common_file", hitsCommunFile.get(0).getName());
    }

    @Test
    void testExactMatchFromOtherDirectory() {
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        List<DirectoryElementInfos> hits = directoryElementInfosService.searchElements("file3", currentDirUuid.toString());
        assertEquals(1, hits.size());
        assertEquals(allDirs.get("sub_directory3").getId(), hits.get(0).getParentId());
        assertEquals("file3", hits.get(0).getName());
    }

    /*
        root_directory
        ├── sub_directory1
        ....
        ├── sub_directory2
        │   ├── new-file ** file to find
        │   .....
        │   ├── sub_sub_directory2_2
        │   │   ├── new-file ** file to find
        └── sub_directory3
            ├── sub_sub_directory3_1
            ├── new-file ** file to find
        ...
     */
    @Test
    void testExactMatchingParentDirectory() { // when a file is in a sub directory of the current directory
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_directory2").getId();
        var newFile1 = makeElementFile("new-file", allDirs.get("sub_directory2").getId());
        var newFile2 = makeElementFile("new-file", allDirs.get("sub_sub_directory2_2").getId());
        var newFile3 = makeElementFile("new-file", allDirs.get("sub_sub_directory3_1").getId());
        repositoryService.saveElementsInfos(List.of(newFile1, newFile2, newFile3));

        //we want to have the files in the current directory if any
        // then the files in the path of the current directory (sub directories and parent directories)
        // then the files in the other directories
        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements("new-file", currentDirUuid.toString());
        assertEquals(3, hitsFile.size());
        assertEquals(newFile1, hitsFile.get(0));
        assertEquals(newFile2, hitsFile.get(1));
        assertEquals(newFile3, hitsFile.get(2));
    }

    @Test
    void testPartialMatchFromSubDirectory() {
        HashMap<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements("file", currentDirUuid.toString());
        assertEquals(9, hitsFile.size());
        assertEquals(currentDirUuid, hitsFile.get(0).getParentId()); // we get first the elements in the current directory
        assertEquals("common_file", hitsFile.get(0).getName());
        assertEquals("file1", hitsFile.get(1).getName()); // we get second the elements in the path
    }

    @Test
    void testExactMatchInCurrentDir() {
        HashMap<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        String fileName = "new-file";
        var newFile = makeElementFile(fileName, allDirs.get("sub_sub_directory1_2").getId());
        var newFile1 = makeElementFile(fileName + "1", allDirs.get("sub_sub_directory1_2").getId());
        var newFile2 = makeElementFile("1" + fileName + "2", allDirs.get("sub_sub_directory1_2").getId());
        repositoryService.saveElementsInfos(List.of(newFile1, newFile, newFile2));

        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements(fileName, currentDirUuid.toString());
        assertEquals(3, hitsFile.size());
        assertEquals(fileName, hitsFile.get(0).getName());
        assertEquals(fileName + "1", hitsFile.get(1).getName());
        assertEquals("1" + fileName + "2", hitsFile.get(2).getName());
    }

}
