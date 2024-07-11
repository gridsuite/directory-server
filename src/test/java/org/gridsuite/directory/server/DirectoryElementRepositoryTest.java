/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.utils.DirectoryTestUtils.*;
import static org.assertj.core.api.Assertions.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DirectoryElementRepositoryTest {
    public static final String TYPE_01 = "TYPE_01";
    public static final String TYPE_02 = "TYPE_02";
    @Autowired
    DirectoryElementRepository directoryElementRepository;

    @Test
    void findAllByIdInAndParentIdAndTypeNot() {
        DirectoryElementEntity parentDirectory = directoryElementRepository.save(
            createRootElement("root", DIRECTORY, "user1")
        );
        UUID parentDirectoryUuid = parentDirectory.getId();

        List<DirectoryElementEntity> insertedElement = directoryElementRepository.saveAll(List.of(
            createElement(parentDirectoryUuid, "dir1", DIRECTORY, "user1"),
            createElement(parentDirectoryUuid, "elementName1", TYPE_02, "user1"),
            createElement(parentDirectoryUuid, "elementName2", TYPE_01, "user2"),
            createElement(parentDirectoryUuid, "elementName3", TYPE_01, "user2"),
            createElement(UUID.randomUUID(), "elementFromOtherDir", TYPE_01, "user2")
        ));

        List<DirectoryElementEntity> expectedResult = insertedElement.stream()
            .filter(e -> !DIRECTORY.equals(e.getType()))
            .filter(e -> parentDirectoryUuid.equals(e.getParentId()))
            .toList();

        assertThat(expectedResult).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(directoryElementRepository.findAllByIdInAndParentIdAndTypeNot(insertedElement.stream().map(DirectoryElementEntity::getId).toList(), parentDirectoryUuid, DIRECTORY));
    }

    @Test
    void testCountCasesByUser() {
        //TODO: the specific types such as study and filter are kipper on purpose
        // It's will be removed later
        String userId1 = "user1";
        DirectoryElementEntity parentDirectory = directoryElementRepository.save(
                createRootElement("root", "DIRECTORY", userId1)
        );
        UUID parentDirectoryUuid = parentDirectory.getId();

        List<DirectoryElementEntity> insertedElement = directoryElementRepository.saveAll(List.of(
                createElement(parentDirectoryUuid, "dir1", "DIRECTORY", userId1),
                createElement(parentDirectoryUuid, "filter1", "FILTER", userId1),
                createElement(parentDirectoryUuid, "study1", "STUDY", userId1),
                createElement(parentDirectoryUuid, "study2", "STUDY", userId1),
                createElement(UUID.randomUUID(), "studyFromOtherDir", "STUDY", userId1),
                createElement(parentDirectoryUuid, "case1", "CASE", userId1),
                createElement(parentDirectoryUuid, "case2", "CASE", userId1),
                createElement(parentDirectoryUuid, "case3", "CASE", userId1),
                createElement(UUID.randomUUID(), "case4", "CASE", userId1),
                createElement(parentDirectoryUuid, "case5", "CASE", "user2"),
                createElement(UUID.randomUUID(), "case6", "CASE", "user2"),
                createElement(UUID.randomUUID(), "study3", "STUDY", "user2")
        ));

        long expectedResult = insertedElement.stream()
                .filter(e -> "CASE".equals(e.getType()) || "STUDY".equals(e.getType()))
                .filter(e -> e.getOwner().equals(userId1))
                .count();

        assertThat(expectedResult).isEqualTo(7);
        assertThat(directoryElementRepository.getCasesCountByOwner(userId1)).isEqualTo(expectedResult);
    }
}
