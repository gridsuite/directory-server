/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.utils.elasticsearch;

import org.gridsuite.directory.server.elasticsearch.DirectoryElementInfosRepository;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@MockBean(EmbeddedElasticsearch.class)
@MockBean(DirectoryElementInfosRepository.class)
@TestPropertySource(properties = DisableElasticsearch.DISABLE_PROPERTY_NAME + "=true")
public @interface DisableElasticsearch {
    String DISABLE_PROPERTY_NAME = "test.disable.data-elasticsearch";
}

