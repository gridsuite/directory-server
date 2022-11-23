package org.gridsuite.directory.server.services;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import org.gridsuite.directory.server.DirectoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
public class ConsumerService {

    static final String HEADER_ELEMENT_UUID = "elementUuid";
    static final String HEADER_MODIFIED_BY = "modifiedBy";
    static final String HEADER_MODIFICATION_DATE = "modificationDate";

    DirectoryService directoryService;

    @Autowired
    public ConsumerService(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Bean
    public Consumer<Message<String>> consumeElementUpdate() {
        return message -> {
            String elementUpdatedUuidStr = message.getHeaders().get(HEADER_ELEMENT_UUID, String.class);
            String modifiedBy = message.getHeaders().get(HEADER_MODIFIED_BY, String.class);
            String modificationDateStr = message.getHeaders().get(HEADER_MODIFICATION_DATE, String.class);

            UUID elementUpdatedUuid = UUID.fromString(elementUpdatedUuidStr);
            LocalDateTime modificationDate = LocalDateTime.parse(modificationDateStr);

            directoryService.updateElementLastModifiedAttributes(elementUpdatedUuid, modificationDate, modifiedBy);
        };
    }
}
