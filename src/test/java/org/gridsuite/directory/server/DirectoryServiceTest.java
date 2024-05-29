package org.gridsuite.directory.server;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.directory.server.utils.DirectoryTestUtils.createElement;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.createRootElement;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisableElasticsearch
class DirectoryServiceTest {
    @Autowired
    DirectoryService directoryService;

    @MockBean
    DirectoryElementRepository directoryElementRepository;

    @MockBean
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @MockBean
    NotificationService notificationService;

    DirectoryElementEntity parentDirectory = createRootElement("root", "DIRECTORY", "user1");
    UUID parentDirectoryUuid = parentDirectory.getId();

    DirectoryElementEntity dir1 = createElement(parentDirectoryUuid, "dir1", "DIRECTORY", "user1");
    DirectoryElementEntity filter1 = createElement(parentDirectoryUuid, "filter1", "FILTER", "user1");
    DirectoryElementEntity study1 = createElement(parentDirectoryUuid, "study1", "STUDY", "user1");
    DirectoryElementEntity study2 = createElement(parentDirectoryUuid, "study2", "STUDY", "user1");
    DirectoryElementEntity studyFromOtherDir = createElement(UUID.randomUUID(), "studyFromOtherDir", "STUDY", "user1");

    DirectoryElementEntity study3 = createElement(parentDirectoryUuid, "study3", "STUDY", "user2");

    List<DirectoryElementEntity> elementsToDelete = List.of(
        dir1,
        filter1,
        study1,
        study2,
        studyFromOtherDir
    );

    @Test
    void testDeleteMultipleElementsFromOneDirectory() {
        List<UUID> elementToDeleteUuids = elementsToDelete.stream().map(e -> e.getId()).toList();
        // following elements should not be deleted with this call
        // - elements with type DIRECTORY
        // - elements having a parent directory different from the one passed as parameter

        List<DirectoryElementEntity> elementsExpectedToDelete = elementsToDelete.stream()
            .filter(e -> !"DIRECTORY".equals(e.getType()))
            .filter(e -> e.getParentId().equals(parentDirectoryUuid))
            .toList();

        List<UUID> elementExpectedToDeleteUuids = elementsExpectedToDelete.stream().map(e -> e.getId()).toList();

        when(directoryElementRepository.findById(parentDirectoryUuid)).thenReturn(Optional.of(parentDirectory));
        when(directoryElementRepository.findAllByIdInAndStashed(elementToDeleteUuids, false)).thenReturn(elementsToDelete);
        when(directoryElementRepository.findAllByIdInAndParentIdAndTypeNotAndStashed(elementToDeleteUuids, parentDirectoryUuid, "DIRECTORY", false))
            .thenReturn(elementsExpectedToDelete);

        // actual service call
        directoryService.deleteElements(elementToDeleteUuids, parentDirectoryUuid, "user1");

        // check elements are actually deleted
        verify(directoryElementRepository, times(1)).deleteAllById(elementExpectedToDeleteUuids);
        verify(directoryElementInfosRepository, times(1)).deleteAllById(elementExpectedToDeleteUuids);

        // notifications should be sent for each deleted study
        verify(notificationService, times(1)).emitDeletedStudy(study1.getId(), "user1");
        verify(notificationService, times(1)).emitDeletedStudy(study2.getId(), "user1");

        // notification for updated directory
        verify(notificationService, times(1)).emitDirectoryChanged(parentDirectoryUuid, null, "user1", null, false, NotificationType.UPDATE_DIRECTORY);

        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void testDeleteFromForbiddenDirectory() {
        List<UUID> elementToDeleteUuids = List.of(study3).stream().map(e -> e.getId()).toList();
        UUID study3Uuid = study3.getId();
        when(directoryElementRepository.findById(study3.getId())).thenReturn(Optional.of(study3));
        when(directoryElementRepository.findAllByIdInAndStashed(elementToDeleteUuids, false)).thenReturn(List.of(study3));

        // study3 was created by user2,so it can not be deleted by user1
        DirectoryException exception = assertThrows(DirectoryException.class, () -> directoryService.deleteElements(elementToDeleteUuids, study3Uuid, "user1"));
        assertEquals(DirectoryException.Type.NOT_ALLOWED.name(), exception.getMessage());
    }
}
