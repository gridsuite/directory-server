package org.gridsuite.directory.server;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.createElement;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.createRootElement;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisableElasticsearch
class DirectoryServiceTest {
    public static final String TYPE_01 = "TYPE_01";
    public static final String TYPE_02 = "TYPE_02";
    @Autowired
    DirectoryService directoryService;

    @MockBean
    DirectoryElementRepository directoryElementRepository;

    @MockBean
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @MockBean
    NotificationService notificationService;

    DirectoryElementEntity parentDirectory = createRootElement("root", DIRECTORY, "user1");
    UUID parentDirectoryUuid = parentDirectory.getId();

    DirectoryElementEntity dir1 = createElement(parentDirectoryUuid, "dir1", DIRECTORY, "user1");
    DirectoryElementEntity element0 = createElement(parentDirectoryUuid, "elementName0", TYPE_02, "user1");
    DirectoryElementEntity element1 = createElement(parentDirectoryUuid, "elementName1", TYPE_01, "user1");
    DirectoryElementEntity element2 = createElement(parentDirectoryUuid, "elementName2", TYPE_01, "user1");
    DirectoryElementEntity elementFromOtherDir = createElement(UUID.randomUUID(), "elementName3", TYPE_01, "user1");

    DirectoryElementEntity element3 = createElement(parentDirectoryUuid, "elementName4", TYPE_01, "user2");

    List<DirectoryElementEntity> elementsToDelete = List.of(
        dir1,
            element0,
            element1,
            element2,
            elementFromOtherDir
    );

    @Test
    void testDeleteMultipleElementsFromOneDirectory() {
        List<UUID> elementToDeleteUuids = elementsToDelete.stream().map(e -> e.getId()).toList();
        // following elements should not be deleted with this call
        // - elements with type DIRECTORY
        // - elements having a parent directory different from the one passed as parameter

        List<DirectoryElementEntity> elementsExpectedToDelete = elementsToDelete.stream()
            .filter(e -> !DIRECTORY.equals(e.getType()))
            .filter(e -> e.getParentId().equals(parentDirectoryUuid))
            .toList();

        List<UUID> elementExpectedToDeleteUuids = elementsExpectedToDelete.stream().map(e -> e.getId()).toList();

        when(directoryElementRepository.findById(parentDirectoryUuid)).thenReturn(Optional.of(parentDirectory));
        when(directoryElementRepository.findAllByIdIn(elementToDeleteUuids)).thenReturn(elementsToDelete);
        when(directoryElementRepository.findAllByIdInAndParentIdAndTypeNot(elementToDeleteUuids, parentDirectoryUuid, DIRECTORY))
            .thenReturn(elementsExpectedToDelete);

        // actual service call
        directoryService.deleteElements(elementToDeleteUuids, parentDirectoryUuid, "user1");

        // check elements are actually deleted
        verify(directoryElementRepository, times(1)).deleteAllById(elementExpectedToDeleteUuids);
        verify(directoryElementInfosRepository, times(1)).deleteAllById(elementExpectedToDeleteUuids);

        // notifications should be sent for each deleted element
        verify(notificationService, times(1)).emitDeletedElement(element1.getId(), "user1");
        verify(notificationService, times(1)).emitDeletedElement(element2.getId(), "user1");
        verify(notificationService, times(1)).emitDeletedElement(element0.getId(), "user1");
        // notification for updated directory
        verify(notificationService, times(1)).emitDirectoryChanged(parentDirectoryUuid, null, "user1", null, false, NotificationType.UPDATE_DIRECTORY);

        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void testDeleteFromForbiddenDirectory() {
        List<UUID> elementToDeleteUuids = List.of(element3).stream().map(e -> e.getId()).toList();
        UUID element3Uuid = element3.getId();
        when(directoryElementRepository.findById(element3.getId())).thenReturn(Optional.of(element3));
        when(directoryElementRepository.findAllByIdIn(elementToDeleteUuids)).thenReturn(List.of(element3));

        // element3 was created by user2,so it can not be deleted by user1
        DirectoryException exception = assertThrows(DirectoryException.class, () -> directoryService.deleteElements(elementToDeleteUuids, element3Uuid, "user1"));
        assertEquals(DirectoryException.Type.NOT_ALLOWED.name(), exception.getMessage());
    }

    @Test
    void testFailDuplicateElement() {
        when(directoryElementRepository.findById(parentDirectoryUuid)).thenReturn(Optional.of(parentDirectory));
        when(directoryElementRepository.findById(element1.getId())).thenReturn(Optional.of(element1));
        when(directoryElementRepository.save(any(DirectoryElementEntity.class))).thenThrow(new DataIntegrityViolationException("Name already exists"));
        when(directoryElementRepository.existsByIdAndOwnerOrId(any(), any(), any())).thenReturn(true);
        UUID element1Uuid = element1.getId();
        UUID newElementUuid = UUID.randomUUID();
        DirectoryException exception = assertThrows(DirectoryException.class, () -> directoryService.duplicateElement(element1Uuid, newElementUuid, parentDirectoryUuid, "user1"));
        assertEquals(DirectoryException.Type.NAME_ALREADY_EXISTS.name(), exception.getType().name());
    }
}
