/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.services.SupervisionService;
// import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.google.common.collect.Iterables;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
// @DisableElasticsearch
class SupervisionTest {
    @Autowired
    SupervisionService supervisionService;

    @MockBean
    DirectoryElementRepository directoryElementRepository;

    @MockBean
    DirectoryElementInfosRepository directoryElementInfosRepository;

    List<DirectoryElementEntity> expectedElements = List.of(
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "dir1", "DIRECTORY", false, "user1", null, LocalDateTime.now(), LocalDateTime.now(), "user1", true, LocalDateTime.now()),
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "filter1", "FILTER", false, "user1", null, LocalDateTime.now(), LocalDateTime.now(), "user1", true, LocalDateTime.now()),
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "study", "STUDY", false, "user2", null, LocalDateTime.now(), LocalDateTime.now(), "user2", true, LocalDateTime.now())
    );

    @Test
    void testGetStashedElements() {
        when(directoryElementRepository.findAllByStashed(true)).thenReturn(expectedElements);
        assertEquals(3, supervisionService.getStashedElementsAttributes().size());

        verify(directoryElementRepository, times(1)).findAllByStashed(true);
    }

    @Test
    void testDeleteElements() {
        List<UUID> uuidsToDelete = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        supervisionService.deleteElementsByIds(uuidsToDelete);

        verify(directoryElementRepository, times(1)).deleteAllById(uuidsToDelete);
        verify(directoryElementInfosRepository, times(1)).deleteAllById(uuidsToDelete);
    }

    @Test
    void testGetElementInfosCount() {
        supervisionService.getIndexedDirectoryElementsCount();
        verify(directoryElementInfosRepository, times(1)).count();

        UUID parentId = UUID.randomUUID();
        supervisionService.getIndexedDirectoryElementsCount(parentId);
        verify(directoryElementInfosRepository, times(1)).countByParentId(parentId);
    }

    @Test
    void testDeleteElementInfos() {
        UUID parentId = UUID.randomUUID();
        supervisionService.deleteIndexedDirectoryElements(parentId);

        verify(directoryElementInfosRepository, times(1)).countByParentId(parentId);
        verify(directoryElementInfosRepository, times(1)).deleteAllByParentId(parentId);
    }

    @Test
    void testReindexElements() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        DirectoryElementEntity rootDir = new DirectoryElementEntity(UUID.randomUUID(), null, "name", "DIRECTORY", true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity dirEntity = new DirectoryElementEntity(UUID.randomUUID(), rootDir.getId(), "name", "DIRECTORY", true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity subdirEntity = new DirectoryElementEntity(UUID.randomUUID(), dirEntity.getId(), "name", "DIRECTORY", true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity studyEntity = new DirectoryElementEntity(UUID.randomUUID(), rootDir.getId(), "name", "STUDY", true, "userId", "description", now, now, "userId", false, null);

        directoryElementRepository.saveAll(List.of(rootDir, dirEntity, subdirEntity, studyEntity));

        assertNbElementsInRepositories(4, 0);

        supervisionService.reindexElements(subdirEntity.getId());

        assertNbElementsInRepositories(4, 0);

        supervisionService.reindexElements(dirEntity.getId());

        assertNbElementsInRepositories(4, 1);

        supervisionService.reindexElements(rootDir.getId());

        assertNbElementsInRepositories(4, 4);
    }

    @AfterEach
    public void verifyNoMoreInteractionsMocks() {
        verifyNoMoreInteractions(directoryElementRepository);
        verifyNoMoreInteractions(directoryElementInfosRepository);
    }

    private void assertNbElementsInRepositories(int nbEntities, int nbElementsInfos) {
        assertEquals(nbEntities, directoryElementRepository.findAll().size());
        assertEquals(nbElementsInfos, Iterables.size(directoryElementInfosRepository.findAll()));
    }
}
