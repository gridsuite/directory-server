/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.services;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * @author Abdelsalem HEDHILI <abdelsalem.hedhili at rte-france.com>
 */
@Component
@ConfigurationProperties(prefix = "gridsuite")
@Data
public class RemoteServicesProperties {

    private List<Service> services;

    @Data
    public static class Service {
        private String name;
        private String baseUri;
    }

    public String getServiceUri(String serviceName) {
        String defaultUri = "http://" + serviceName + "/";
        return Objects.isNull(services) ? defaultUri : services.stream()
                .filter(s -> s.getName().equalsIgnoreCase(serviceName))
                .map(Service::getBaseUri)
                .findFirst()
                .orElse(defaultUri);
    }
}
