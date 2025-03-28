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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
        directoryElementInfosRepository.saveAll(infos);
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
        directoryElementInfosRepository.saveAll(infos);

        Set<DirectoryElementInfos> hits = new HashSet<>(directoryElementInfosService.searchElements("a", "", PageRequest.of(0, 10)).stream().toList());
        assertEquals(4, hits.size());
        assertTrue(hits.contains(element1Infos));
        assertTrue(hits.contains(element4Infos));
        assertTrue(hits.contains(element2Infos));
        assertTrue(hits.contains(element3Infos));
        Page<DirectoryElementInfos> pagedHits = directoryElementInfosService.searchElements("a", "", PageRequest.of(0, 10));
        assertEquals(4, pagedHits.getTotalElements());
        assertTrue(pagedHits.getContent().contains(element1Infos));
        assertTrue(pagedHits.getContent().contains(element4Infos));
        assertTrue(pagedHits.getContent().contains(element2Infos));
        assertTrue(pagedHits.getContent().contains(element3Infos));

        pagedHits = directoryElementInfosService.searchElements("aDirectory", "", PageRequest.of(0, 10));
        assertEquals(0, pagedHits.getTotalElements());
    }

    @Test
    void searchPagedElementInfos() {
        List<DirectoryElementInfos> elements = new ArrayList<>(20);
        for (int i = 0; i < 20; i++) {
            elements.add(createElements("filter" + i));
        }
        directoryElementInfosRepository.saveAll(elements);
        Page<DirectoryElementInfos> pagedHits = directoryElementInfosService.searchElements("filter", "", PageRequest.of(0, 10));
        assertEquals(20, pagedHits.getTotalElements());
        assertEquals(10, pagedHits.getContent().size());
    }

    @Test
    void searchSpecialChars() {
        var studyInfos = DirectoryElementInfos.builder().id(UUID.randomUUID()).name("s+Ss+ss'sp&pn(n n)ne{e e}et<t t>te|eh-ht.th/hl\\lk[k k]k")
                .type(TYPE_01).owner("admin1").parentId(UUID.randomUUID())
                .subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
        directoryElementInfosRepository.saveAll(List.of(studyInfos));

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
        assertEquals(1, directoryElementInfosService.searchElements(pat, "", PageRequest.of(0, 10)).getTotalElements());
    }

    private DirectoryElementInfos createElements(String name) {
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type("TYPE_01").owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
    }

    private DirectoryElementInfos makeElementDir(String name) {
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type(DIRECTORY).owner("admin").parentId(UUID.randomUUID()).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
    }

    private DirectoryElementInfos makeElementFile(String name, UUID parentId) {
        return DirectoryElementInfos.builder().id(UUID.randomUUID()).name(name).type(TYPE_01).owner("admin").parentId(parentId).subdirectoriesCount(0L).lastModificationDate(Instant.now().truncatedTo(ChronoUnit.SECONDS)).build();
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

        directoryElementInfosRepository.saveAll(allDirs.values().stream().toList());
        List<DirectoryElementInfos> infos = List.of(
                file1, file2, file3,
                commonFile1, commonFile2, commonFile3, commonFile4, commonFile5, commonFile6);

        directoryElementInfosRepository.saveAll(infos);

        return allDirs;
    }

    @Test
    void testExactMatchFromSubDirectory() {
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        List<DirectoryElementInfos> hitsCommunFile = directoryElementInfosService.searchElements("common_file", currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
        assertEquals(6, hitsCommunFile.size());
        assertEquals(currentDirUuid, hitsCommunFile.get(0).getParentId()); // we get first the element in the current directory
        assertEquals("common_file", hitsCommunFile.get(0).getName());

        //now using another current dir , we expect similar results
        currentDirUuid = allDirs.get("sub_sub_directory2_2").getId();
        hitsCommunFile = directoryElementInfosService.searchElements("common_file", currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
        assertEquals(6, hitsCommunFile.size());
        assertEquals(currentDirUuid, hitsCommunFile.get(0).getParentId()); // we get first the element in the current directory
        assertEquals("common_file", hitsCommunFile.get(0).getName());
    }

    @Test
    void testExactMatchFromOtherDirectory() {
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        List<DirectoryElementInfos> hits = directoryElementInfosService.searchElements("file3", currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
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
        directoryElementInfosRepository.saveAll(List.of(newFile1, newFile2, newFile3));

        //we want to have the files in the current directory if any
        // then the files in the path of the current directory (sub directories and parent directories)
        // then the files in the other directories
        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements("new-file", currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
        assertEquals(3, hitsFile.size());
        assertEquals(newFile1, hitsFile.get(0));
        assertEquals(newFile2, hitsFile.get(1));
        assertEquals(newFile3, hitsFile.get(2));
    }

    @Test
    void testPartialMatchFromSubDirectory() {
        HashMap<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements("file", currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
        assertEquals(9, hitsFile.size());
        assertEquals(currentDirUuid, hitsFile.get(0).getParentId()); // we get first the elements in the current directory
        assertEquals("common_file", hitsFile.get(0).getName());
        assertEquals("file1", hitsFile.get(1).getName()); // we get second the elements in the path
    }

    @Test
    void testExactMatchInCurrentDir() {
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_sub_directory1_2").getId();
        String fileName = "new-file";
        var newFile = makeElementFile(fileName, allDirs.get("sub_sub_directory1_2").getId());
        var newFile1 = makeElementFile(fileName + "1", allDirs.get("sub_sub_directory1_2").getId());
        var newFile2 = makeElementFile("1" + fileName + "2", allDirs.get("sub_sub_directory1_2").getId());
        directoryElementInfosRepository.saveAll(List.of(newFile, newFile2, newFile1));
        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements(fileName, currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
        assertEquals(3, hitsFile.size());
        assertEquals(fileName, hitsFile.get(0).getName());
        assertEquals(fileName + "1", hitsFile.get(1).getName());
        assertEquals("1" + fileName + "2", hitsFile.get(2).getName());
    }

    /*
      root_directory
      ├── sub_directory1
      ....
      ├── sub_directory2
      │   ├── bnew-filebbbb
      │   ├── anew-file
      │   ├── new-file
      │   ├── test-new-file
      ...
   */
    @Test
    void testTermStartByUserInput() { // when a file start with search term
        Map<String, DirectoryElementInfos> allDirs = createFilesElements();
        UUID currentDirUuid = allDirs.get("sub_directory2").getId();
        var anewFile1 = makeElementFile("anew-file", allDirs.get("sub_directory2").getId());
        var newFile2 = makeElementFile("new-file-Ok", allDirs.get("sub_directory2").getId());
        var bNewFile = makeElementFile("bnew-filebbbb", allDirs.get("sub_directory2").getId());
        var testNewFile = makeElementFile("test-new-file", allDirs.get("sub_directory2").getId());
        directoryElementInfosRepository.saveAll(List.of(bNewFile, newFile2, anewFile1, testNewFile));

        //we want to have the files in the current directory if any
        // then the files in the path of the current directory (sub directories and parent directories)
        // then the files in the other directories
        List<DirectoryElementInfos> hitsFile = directoryElementInfosService.searchElements("new-file", currentDirUuid.toString(), PageRequest.of(0, 10)).stream().toList();
        assertEquals(4, hitsFile.size());
        assertEquals(newFile2, hitsFile.get(0));
        assertEquals(bNewFile, hitsFile.get(1));
        assertEquals(anewFile1, hitsFile.get(2));
        assertEquals(testNewFile, hitsFile.get(3));
    }
}
