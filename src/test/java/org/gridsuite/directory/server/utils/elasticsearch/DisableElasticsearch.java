/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.utils.elasticsearch;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration"
})
@Inherited
@Import(DisableElasticsearch.MockConfig.class)
public @interface DisableElasticsearch {

    @TestConfiguration(proxyBeanMethods = false)
    class MockConfig {
        @Bean
        public EmbeddedElasticsearch embeddedElasticsearch() {
            return Mockito.mock(EmbeddedElasticsearch.class);
        }

        @Bean
        public DirectoryElementInfosRepository directoryElementInfosRepository() {
            return Mockito.mock(DirectoryElementInfosRepository.class);
        }
    }
}

