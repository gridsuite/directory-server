/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.DirectoryInfos;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.gridsuite.directory.server.services.ConsumerService;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.gridsuite.directory.server.NotificationService.HEADER_USER_ID;
import static org.gridsuite.directory.server.services.ConsumerService.HEADER_ELEMENT_UUID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
public class ConsumerServiceTest {

    @Autowired
    private ConsumerService consumerService;

    @MockitoSpyBean
    private NotificationService notificationService;

    @MockitoBean
    private DirectoryElementRepository directoryElementRepository;

    @MockitoBean
    private DirectoryRepositoryService repositoryService;

    @Test
    public void testConsumeSharedElementUpdated() {
        Consumer<Message<String>> consumer = consumerService.consumeSharedElementUpdate();
        UUID elementUuid = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID parentUuid = UUID.randomUUID();
        DirectoryElementEntity directoryElementEntity = new DirectoryElementEntity();
        directoryElementEntity.setParentId(parentUuid);
        when(directoryElementRepository.findById(elementUuid)).thenReturn(Optional.of(directoryElementEntity));

        consumer.accept(MessageBuilder.withPayload("")
                .setHeader(HEADER_ELEMENT_UUID, elementUuid.toString())
                .setHeader(HEADER_USER_ID, userId.toString())
                .build());

        when(repositoryService.isRootDirectory(parentUuid)).thenReturn(false);

        List<DirectoryInfos> directoryInfos = List.of(new DirectoryInfos(parentUuid, false));
        verify(notificationService)
                .emitDirectoryChanged(directoryInfos, null, userId.toString(), null,
                        false, NotificationType.UPDATE_DIRECTORY);
    }
}
