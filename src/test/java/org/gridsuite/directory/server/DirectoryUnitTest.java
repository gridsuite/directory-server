package org.gridsuite.directory.server;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

@SpringBootTest
@DisableElasticsearch
public class DirectoryUnitTest {
    @Autowired
    DirectoryService directoryService;

    @MockBean
    DirectoryElementRepository directoryElementRepository;

    @MockBean
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @MockBean
    NotificationService notificationService;

    UUID parentDirectoryUuid = UUID.randomUUID();

    DirectoryElementEntity parentDirectory = new DirectoryElementEntity(parentDirectoryUuid, null, "root", "DIRECTORY", false, "user1", null, LocalDateTime.now(), LocalDateTime.now(), "user1", false, null);

    DirectoryElementEntity dir1 = new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, "dir1", "DIRECTORY", false, "user1", null, LocalDateTime.now(), LocalDateTime.now(), "user1", false, null);
    DirectoryElementEntity filter1 = new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, "filter1", "FILTER", false, "user1", null, LocalDateTime.now(), LocalDateTime.now(), "user1", false, null);
    DirectoryElementEntity study1 = new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, "study1", "STUDY", false, "user2", null, LocalDateTime.now(), LocalDateTime.now(), "user2", false, null);
    DirectoryElementEntity study2 = new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, "study2", "STUDY", false, "user2", null, LocalDateTime.now(), LocalDateTime.now(), "user2", false, null);

    DirectoryElementEntity privateDir = new DirectoryElementEntity(UUID.randomUUID(), parentDirectoryUuid, "dir2", "DIRECTORY", true, "user2", null, LocalDateTime.now(), LocalDateTime.now(), "user1", false, null);

    List<DirectoryElementEntity> elementsToDelete = List.of(
        dir1,
        filter1,
        study1,
        study2
    );

    @Test
    public void testDeleteMultipleElements() {
        List<UUID> elementToDeleteUuids = elementsToDelete.stream().map(e -> e.getId()).toList();
        // directory elements should not be delete with this method
        List<UUID> elementExpectedToDeleteUuids = elementsToDelete.stream().filter(e -> !"DIRECTORY".equals(e.getType())).map(e -> e.getId()).toList();

        when(directoryElementRepository.findById(parentDirectoryUuid)).thenReturn(Optional.of(parentDirectory));
        when(directoryElementRepository.findAllByIdIn(elementToDeleteUuids)).thenReturn(elementsToDelete);

        directoryService.deleteElements(elementToDeleteUuids, parentDirectoryUuid, "user1");

        verify(directoryElementRepository, times(1)).deleteAllById(elementExpectedToDeleteUuids);
        verify(directoryElementInfosRepository, times(1)).deleteAllById(elementExpectedToDeleteUuids);

        // notifications should be sent for each deleted study
        verify(notificationService, times(1)).emitDeletedStudy(study1.getId(), "user1");
        verify(notificationService, times(1)).emitDeletedStudy(study2.getId(), "user1");

        // notification for updated directory
        verify(notificationService, times(1)).emitDirectoryChanged(parentDirectoryUuid, null, "user1", null, false, false, NotificationType.UPDATE_DIRECTORY);

        verifyNoMoreInteractions(notificationService);
    }

    @Test
    public void testDeleteFromForbiddenDirectory() {
        when(directoryElementRepository.findById(privateDir.getId())).thenReturn(Optional.of(privateDir));

        DirectoryException exception = assertThrows(DirectoryException.class, () -> directoryService.deleteElements(List.of(), privateDir.getId(), "user1"));
        assertEquals(DirectoryException.Type.NOT_ALLOWED.name(), exception.getMessage());
    }
}
