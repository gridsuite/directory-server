/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.elasticsearch;

import com.google.common.collect.Iterables;
import org.gridsuite.directory.server.DirectoryApplication;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextConfiguration(classes = {DirectoryApplication.class, TestChannelBinderConfiguration.class})
class DirectoryElementInfosServiceTest {

    @Autowired
    DirectoryElementInfosService directoryElementInfosService;

    @Autowired
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @Autowired
    private MockMvc mockMvc;

    private void cleanDB() {
        directoryElementInfosRepository.deleteAll();
    }

    @BeforeEach
    public void setup() {
        cleanDB();
    }

    @Test
    void addDeleteDirectoryElementsInfosForStudy() {
        // GIVEN
        DirectoryElementInfos studiesInfos = createAndSaveDirectoryElement("id", "name", "STUDY", "root");

        // THEN
        assertSingleElementInRepository(studiesInfos);

        // GIVEN
        DirectoryElementInfos filtersInfos = createAndSaveDirectoryElement("id2", "name", "FILTER", "root");

        // THEN
        assertTwoElementsInRepository(filtersInfos);

        // GIVEN
        DirectoryElementInfos studyInfos = createAndSaveDirectoryElement("id", "study name", "STUDY", "root");

        // THEN
        assertTwoElementsInRepository(studyInfos);

        // GIVEN
        DirectoryElementInfos filtersInfos3 = createAndSaveDirectoryElement("id2", "filter name", "FILTER", "root");

        // THEN
        assertTwoElementsInRepository(filtersInfos3);

        // WHEN
        deleteTwoElements();

        // THEN
        assertNoElementsInRepository();
    }

    @Test
    void testReindexAllElementsEndpoint() throws Exception {
        // GIVEN
        DirectoryElementInfos studyInfos = createAndSaveDirectoryElement("id1", "Study 1", "STUDY", "root");
        DirectoryElementInfos filterInfos = createAndSaveDirectoryElement("id2", "Filter 1", "FILTER", "root");

        // WHEN
        mockMvc.perform(post("/v1/elements/reindex-all"))
                .andExpect(status().isOk());

        // THEN
        assertReindexedElement("id1", "Study 1");
        assertReindexedElement("id2", "Filter 1");
    }

    // Helper methods

    private DirectoryElementInfos createAndSaveDirectoryElement(String id, String name, String type, String parentId) {
        DirectoryElementInfos element = DirectoryElementInfos.builder().id(id).name(name).type(type).parentId(parentId).build();
        directoryElementInfosRepository.save(element);
        return element;
    }

    private void assertSingleElementInRepository(DirectoryElementInfos element) {
        assertEquals(1, Iterables.size(directoryElementInfosRepository.findAll()));
        assertEquals(element, directoryElementInfosRepository.findAll().iterator().next());
    }

    private void assertTwoElementsInRepository(DirectoryElementInfos element) {
        assertEquals(2, Iterables.size(directoryElementInfosRepository.findAll()));
        Iterator<DirectoryElementInfos> iterator = directoryElementInfosRepository.findAll().iterator();
        iterator.next(); // Skip the first element
        assertEquals(element, iterator.next());
    }

    private void deleteTwoElements() {
        Iterator<DirectoryElementInfos> iterator = directoryElementInfosRepository.findAll().iterator();
        directoryElementInfosRepository.deleteById(iterator.next().getId());
        directoryElementInfosRepository.deleteById(iterator.next().getId());
    }

    private void assertNoElementsInRepository() {
        assertEquals(0, Iterables.size(directoryElementInfosRepository.findAll()));
    }

    private void assertReindexedElement(String id, String expectedName) {
        DirectoryElementInfos reindexedElement = directoryElementInfosRepository.findById(id).orElse(null);
        assertNotNull(reindexedElement);
        assertEquals(expectedName, reindexedElement.getName());
    }
}
