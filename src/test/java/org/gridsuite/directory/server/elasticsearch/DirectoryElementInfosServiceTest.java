package org.gridsuite.directory.server.elasticsearch;

import org.gridsuite.directory.server.dto.DirectoryElementInfos;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.FILTER;
import static org.gridsuite.directory.server.DirectoryService.STUDY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DirectoryElementInfosServiceTest {

    private static final UUID ELEMENT_UUID = UUID.randomUUID();

    @Autowired
    private DirectoryElementInfosService directoryElementInfosService;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void addDirectoryElementsInfos() {
        LocalDateTime localCreationDate = LocalDateTime.now(ZoneOffset.UTC);
        DirectoryElementEntity elementEntity2 = new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", FILTER, true, "userId", "description", localCreationDate, localCreationDate, "userId");

    }

    @Test
    void findAllDirectoryElementInfos() {
    }
}