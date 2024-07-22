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
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class SupervisionTest {

    @Autowired
    SupervisionService supervisionService;

    @MockBean
    DirectoryElementRepository directoryElementRepository;

    @MockBean
    DirectoryElementInfosRepository directoryElementInfosRepository;

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
    }

    @Test
    void testDeleteElementInfos() {
        supervisionService.deleteIndexedDirectoryElements();

        verify(directoryElementInfosRepository, times(1)).count();
        verify(directoryElementInfosRepository, times(1)).deleteAll();
    }

    @Test
    void testReindexElements() {
        DirectoryElementEntity rootDir = new DirectoryElementEntity(UUID.randomUUID(), null, "name", DIRECTORY, "userId", "description", Instant.now(), Instant.now(), "userId");
        DirectoryElementEntity dirEntity = new DirectoryElementEntity(UUID.randomUUID(), rootDir.getId(), "name", DIRECTORY, "userId", "description", Instant.now(), Instant.now(), "userId");
        DirectoryElementEntity subdirEntity = new DirectoryElementEntity(UUID.randomUUID(), dirEntity.getId(), "name", DIRECTORY, "userId", "description", Instant.now(), Instant.now(), "userId");
        DirectoryElementEntity elementEntity = new DirectoryElementEntity(UUID.randomUUID(), rootDir.getId(), "name", "ANOTHER_TYPE", "userId", "description", Instant.now(), Instant.now(), "userId");

        List<DirectoryElementEntity> allElements = List.of(rootDir, dirEntity, subdirEntity, elementEntity);
        when(directoryElementRepository.findAll()).thenReturn(allElements);

        supervisionService.reindexElements();

        List<DirectoryElementEntity> elementPath = List.of(); // No need path for tests
        verify(directoryElementRepository, times(1)).findAll();
        verify(directoryElementInfosRepository, times(1)).saveAll(allElements.stream().map(e -> e.toDirectoryElementInfos(elementPath)).toList());
        verify(directoryElementRepository, times(3)).findElementHierarchy(any(UUID.class));
    }

    @AfterEach
    public void verifyNoMoreInteractionsMocks() {
        verifyNoMoreInteractions(directoryElementRepository);
        verifyNoMoreInteractions(directoryElementInfosRepository);
    }
}
