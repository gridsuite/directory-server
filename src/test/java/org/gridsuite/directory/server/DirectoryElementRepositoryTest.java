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
    @Autowired
    DirectoryElementRepository directoryElementRepository;

    @Test
    void testFindAllByIdInAndParentIdAndTypeNot() {
        DirectoryElementEntity parentDirectory = directoryElementRepository.save(
            createRootElement("root", DIRECTORY, false, "user1")
        );
        UUID parentDirectoryUuid = parentDirectory.getId();

        List<DirectoryElementEntity> insertedElement = directoryElementRepository.saveAll(List.of(
            createElement(parentDirectoryUuid, "dir1", DIRECTORY, false, "user1"),
            createElement(parentDirectoryUuid, "filter1", "FILTER", false, "user1"),
            createElement(parentDirectoryUuid, "study1", "STUDY", false, "user2"),
            createElement(parentDirectoryUuid, "study2", "STUDY", false, "user2"),
            createElement(UUID.randomUUID(), "studyFromOtherDir", "STUDY", false, "user2")
        ));

        List<DirectoryElementEntity> expectedResult = insertedElement.stream()
            .filter(e -> !DIRECTORY.equals(e.getType()))
            .filter(e -> parentDirectoryUuid.equals(e.getParentId()))
            .filter(e -> !e.isStashed())
            .toList();

        assertThat(expectedResult).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(directoryElementRepository.findAllByIdInAndParentIdAndTypeNotAndStashed(insertedElement.stream().map(e -> e.getId()).toList(), parentDirectoryUuid, DIRECTORY, false));
    }
}
