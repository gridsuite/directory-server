/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.gridsuite.directory.server.services.SupervisionService;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
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

    @SpyBean
    DirectoryRepositoryService repositoryService;

    @MockBean(name = "elasticsearchOperations")
    ElasticsearchTemplate elasticsearchTemplate;

    List<DirectoryElementEntity> expectedElements = List.of(
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "dir1", "DIRECTORY", "user1", null, Instant.now(), Instant.now(), "user1", true, Instant.now()),
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "filter1", "FILTER", "user1", null, Instant.now(), Instant.now(), "user1", true, Instant.now()),
        new DirectoryElementEntity(UUID.randomUUID(), UUID.randomUUID(), "study", "STUDY", "user2", null, Instant.now(), Instant.now(), "user2", true, Instant.now())
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
        verify(repositoryService).deleteElements(uuidsToDelete);
        verify(directoryElementRepository, times(1)).deleteAllById(uuidsToDelete);
        verify(directoryElementInfosRepository, times(1)).deleteAllById(uuidsToDelete);
    }

    @ParameterizedTest
    @CsvSource(nullValues = { "null" }, value = {
        "false, null, true, true", //not existant, juste recreate
        "false, null, false, false", //not existant, error while creating
        "true, true, true, true", //delete + create + reindex OK
        "true, true, false, false", //delete, but error while creating
        "true, false, null, false" //exist, but error while deleting
    })
    void testReindexElements(final boolean exists, final Boolean delete, final Boolean create, final boolean result) {
        final IndexOperations idxOps = mock(IndexOperations.class);
        when(elasticsearchTemplate.indexOps(any(Class.class))).thenReturn(idxOps);
        when(idxOps.getIndexCoordinates()).thenReturn(IndexCoordinates.of("test-mock-index")); //for logs
        when(idxOps.exists()).thenReturn(exists);
        if (delete != null) {
            when(idxOps.delete()).thenReturn(delete);
        }
        if (create != null) {
            when(idxOps.createWithMapping()).thenReturn(create);
        }
        doNothing().when(repositoryService).reindexAllElements(); //intercept call
        assertThat(supervisionService.recreateIndexDirectoryElementInfos()).as("service call result").isEqualTo(result);
        verify(elasticsearchTemplate).indexOps(DirectoryElementInfos.class);
        verify(idxOps, atLeastOnce()).getIndexCoordinates();
        verify(idxOps).exists();
        if (delete != null) {
            verify(idxOps).delete();
        }
        if (create != null) {
            verify(idxOps).createWithMapping();
        }
        if (create == Boolean.TRUE) {
            verify(repositoryService).reindexAllElements();
        }
        verifyNoMoreInteractions(idxOps);
    }

    @AfterEach
    public void verifyNoMoreInteractionsMocks() {
        verifyNoMoreInteractions(directoryElementRepository);
        verifyNoMoreInteractions(directoryElementInfosRepository);
        verifyNoMoreInteractions(repositoryService);
        verifyNoMoreInteractions(elasticsearchTemplate);
    }
}
