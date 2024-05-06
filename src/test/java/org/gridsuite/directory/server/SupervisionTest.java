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
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryException.Type.NOT_FOUND;
import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    List<DirectoryElementEntity> expectedElements = List.of(
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "dir1", DIRECTORY, false, "user1", null, LocalDateTime.now(), LocalDateTime.now(), "user1", true, LocalDateTime.now()),
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

        assertException(new NullPointerException("directoryUuid is marked non-null but is null"), () -> supervisionService.deleteIndexedDirectoryElements(null));

        verify(directoryElementInfosRepository, times(0)).countByParentId(null);
        verify(directoryElementInfosRepository, times(0)).deleteAllByParentId(null);
    }

    @Test
    void testReindexElements() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        DirectoryElementEntity rootDir = new DirectoryElementEntity(UUID.randomUUID(), null, "name", DIRECTORY, true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity dirEntity = new DirectoryElementEntity(UUID.randomUUID(), rootDir.getId(), "name", DIRECTORY, true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity subdirEntity = new DirectoryElementEntity(UUID.randomUUID(), dirEntity.getId(), "name", DIRECTORY, true, "userId", "description", now, now, "userId", false, null);
        DirectoryElementEntity studyEntity = new DirectoryElementEntity(UUID.randomUUID(), rootDir.getId(), "name", "ANOTHER_TYPE", true, "userId", "description", now, now, "userId", false, null);

        when(directoryElementRepository.findByIdAndType(rootDir.getId(), DIRECTORY)).thenReturn(Optional.of(rootDir));
        when(directoryElementRepository.findByIdAndType(dirEntity.getId(), DIRECTORY)).thenReturn(Optional.of(dirEntity));
        when(directoryElementRepository.findByIdAndType(subdirEntity.getId(), DIRECTORY)).thenReturn(Optional.of(subdirEntity));
        when(directoryElementRepository.findByIdAndType(studyEntity.getId(), DIRECTORY)).thenReturn(Optional.empty());

        when(directoryElementRepository.findAllByParentId(rootDir.getId())).thenReturn(List.of(dirEntity, studyEntity));
        when(directoryElementRepository.findAllByParentId(dirEntity.getId())).thenReturn(List.of(subdirEntity));
        when(directoryElementRepository.findAllByParentId(subdirEntity.getId())).thenReturn(List.of());

        assertException(new DirectoryException(NOT_FOUND), () -> supervisionService.reindexElements(studyEntity.getId()));

        verify(directoryElementRepository, times(1)).findByIdAndType(studyEntity.getId(), DIRECTORY);
        verify(directoryElementRepository, times(0)).findAllByParentId(studyEntity.getId());
        verify(directoryElementInfosRepository, times(0)).saveAll(List.of(studyEntity.toDirectoryElementInfos()));

        supervisionService.getDirectories();
        verify(directoryElementRepository, times(1)).findAllByType(DIRECTORY);

        supervisionService.reindexElements(subdirEntity.getId());

        verify(directoryElementRepository, times(1)).findByIdAndType(subdirEntity.getId(), DIRECTORY);
        verify(directoryElementRepository, times(1)).findAllByParentId(subdirEntity.getId());
        verify(directoryElementInfosRepository, times(0)).saveAll(List.of(subdirEntity.toDirectoryElementInfos()));

        supervisionService.reindexElements(dirEntity.getId());

        verify(directoryElementRepository, times(1)).findByIdAndType(dirEntity.getId(), DIRECTORY);
        verify(directoryElementRepository, times(1)).findAllByParentId(dirEntity.getId());
        verify(directoryElementInfosRepository, times(1)).saveAll(List.of(subdirEntity.toDirectoryElementInfos()));

        supervisionService.reindexElements(rootDir.getId());

        verify(directoryElementRepository, times(1)).findByIdAndType(rootDir.getId(), DIRECTORY);
        verify(directoryElementRepository, times(1)).findAllByParentId(rootDir.getId());
        verify(directoryElementInfosRepository, times(1)).saveAll(List.of(rootDir.toDirectoryElementInfos()));
        verify(directoryElementInfosRepository, times(1)).saveAll(List.of(dirEntity.toDirectoryElementInfos(), studyEntity.toDirectoryElementInfos()));
    }

    void assertException(Exception expectedException, Executable executable) {
        Exception exception = assertThrows(expectedException.getClass(), executable);
        assertEquals(expectedException.getMessage(), exception.getMessage());
    }

    @AfterEach
    public void verifyNoMoreInteractionsMocks() {
        verifyNoMoreInteractions(directoryElementRepository);
        verifyNoMoreInteractions(directoryElementInfosRepository);
    }
}
