/**
 * Copyright (c) 2024 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.DirectoryService.MAX_RETRY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.createElement;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.createRootElement;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
class DirectoryServiceTest {
    public static final String TYPE_01 = "TYPE_01";
    public static final String TYPE_02 = "TYPE_02";

    @SpyBean
    DirectoryService directoryService;

    @SpyBean
    DirectoryElementRepository directoryElementRepository;

    @MockBean
    DirectoryElementInfosRepository directoryElementInfosRepository;

    @MockBean
    NotificationService notificationService;

    @BeforeEach
    public void setup() {
        directoryElementRepository.deleteAll();
    }

    @Test
    void testDeleteMultipleElementsFromOneDirectory() {
        DirectoryElementEntity parentDirectory = createRootElement("root", DIRECTORY, "user1");
        UUID parentDirectoryUuid = parentDirectory.getId();

        DirectoryElementEntity dir1 = createElement(parentDirectoryUuid, "dir1", DIRECTORY, "user1");
        DirectoryElementEntity element0 = createElement(parentDirectoryUuid, "elementName0", TYPE_02, "user1");
        DirectoryElementEntity element1 = createElement(parentDirectoryUuid, "elementName1", TYPE_01, "user1");
        DirectoryElementEntity element2 = createElement(parentDirectoryUuid, "elementName2", TYPE_01, "user1");
        DirectoryElementEntity elementFromOtherDir = createElement(UUID.randomUUID(), "elementName3", TYPE_01, "user1");

        List<DirectoryElementEntity> elementsToDelete = List.of(
                dir1,
                element0,
                element1,
                element2,
                elementFromOtherDir
        );

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
        verify(notificationService, times(1)).emitDirectoryChanged(parentDirectoryUuid, null, "user1", null, true, false, NotificationType.UPDATE_DIRECTORY);

        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void testDeleteFromForbiddenDirectory() {
        UUID parentDirectoryUuid = createRootElement("root", DIRECTORY, "user1").getId();
        DirectoryElementEntity element = createElement(parentDirectoryUuid, "elementName", TYPE_01, "user2");
        List<UUID> elementToDeleteUuids = List.of(element).stream().map(e -> e.getId()).toList();
        UUID elementUuid = element.getId();
        when(directoryElementRepository.findById(element.getId())).thenReturn(Optional.of(element));
        when(directoryElementRepository.findAllByIdIn(elementToDeleteUuids)).thenReturn(List.of(element));

        // element was created by user2,so it can not be deleted by user1
        DirectoryException exception = assertThrows(DirectoryException.class, () -> directoryService.deleteElements(elementToDeleteUuids, elementUuid, "user1"));
        assertEquals(DirectoryException.Type.NOT_ALLOWED.name(), exception.getMessage());
    }

    @Test
    void testDirectoryElementUniqueness() {
        ElementAttributes rootAttributes = directoryService.createRootDirectory(new RootDirectoryAttributes("root", "user1", null, null, null, null), "user1");
        UUID rootUuid = rootAttributes.getElementUuid();

        // Insert a new element
        ElementAttributes elementAttributes = toElementAttributes(null, "element1", "TYPE", "user1");
        UUID elementUuid = directoryService.createElement(elementAttributes, rootUuid, "User1", false).getElementUuid();

        // Insert the same element in the same directory throws an exception
        DirectoryException directoryException = assertThrows(DirectoryException.class, () -> directoryService.createElement(elementAttributes, rootUuid, "User1", false));
        assertEquals(DirectoryException.Type.NAME_ALREADY_EXISTS, directoryException.getType());
        assertEquals(DirectoryException.createElementNameAlreadyExists(elementAttributes.getElementName()).getMessage(), directoryException.getMessage());

        // Insert the same element in the same directory with new name generation does not throw an exception
        ElementAttributes newElementAttributes = directoryService.createElement(elementAttributes, rootUuid, "User1", true);
        assertNotEquals(elementAttributes.getElementName(), newElementAttributes.getElementName());

        // Duplicate an element in the same directory with new name generation does not throw an exception
        newElementAttributes = directoryService.duplicateElement(elementUuid, UUID.randomUUID(), rootUuid, "User1");
        assertNotEquals(elementAttributes.getElementName(), newElementAttributes.getElementName());

        // Insert a new element in a new root directory
        UUID root2Uuid = directoryService.createRootDirectory(new RootDirectoryAttributes("root2", "user1", null, null, null, null), "user1").getElementUuid();
        UUID element2Uuid = directoryService.createElement(elementAttributes, root2Uuid, "User1", false).getElementUuid();

        // Duplicate an element in the new root directory with new name generation throw an exception if all retries fail
        InOrder inOrder = inOrder(directoryService);
        when(directoryService.getDuplicateNameCandidate(root2Uuid, elementAttributes.getElementName(), elementAttributes.getType(), "User1")).thenReturn(elementAttributes.getElementName());
        directoryException = assertThrows(DirectoryException.class, () -> directoryService.duplicateElement(element2Uuid, root2Uuid, root2Uuid, "User1"));
        assertEquals(DirectoryException.Type.NAME_ALREADY_EXISTS, directoryException.getType());
        assertEquals(DirectoryException.createElementNameAlreadyExists(elementAttributes.getElementName()).getMessage(), directoryException.getMessage());
        inOrder.verify(directoryService, calls(MAX_RETRY)).getDuplicateNameCandidate(root2Uuid, elementAttributes.getElementName(), elementAttributes.getType(), "User1");
    }
}
