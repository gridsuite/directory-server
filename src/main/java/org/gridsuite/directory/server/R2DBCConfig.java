/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@Configuration
@PropertySource(value = {"classpath:database.properties"})
@PropertySource(value = {"file:/config/database.properties"}, ignoreResourceNotFound = true)
@EnableR2dbcRepositories(basePackageClasses = {DirectoryElementRepository.class})
public class R2DBCConfig extends AbstractR2dbcConfiguration {

    @Autowired
    Environment env;

    @Bean
    @Override
    public ConnectionFactory connectionFactory() {
        ConnectionFactoryOptions.Builder optionsBuilder = ConnectionFactoryOptions.builder();
        optionsBuilder.option(ConnectionFactoryOptions.DRIVER, env.getRequiredProperty("driver"))
            .option(ConnectionFactoryOptions.DATABASE, env.getRequiredProperty("database"))
            .option(ConnectionFactoryOptions.USER, env.getRequiredProperty("login"))
            .option(ConnectionFactoryOptions.PASSWORD, env.getRequiredProperty("password"));

        if (env.containsProperty("host")) {
            optionsBuilder.option(ConnectionFactoryOptions.HOST, env.getProperty("host"));
        }
        if (env.containsProperty("protocol")) {
            optionsBuilder.option(ConnectionFactoryOptions.PROTOCOL, env.getProperty("protocol"));
        }
        if (env.containsProperty("port")) {
            optionsBuilder.option(ConnectionFactoryOptions.PORT, Integer.valueOf(env.getProperty("port")));
        }

        return ConnectionFactories.get(optionsBuilder.build());
    }
}
