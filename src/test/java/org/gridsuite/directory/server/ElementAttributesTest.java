/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ElementAttributesTest {

    private static final UUID ELEMENT_UUID = UUID.randomUUID();

    @Autowired
    ObjectMapper mapper;

    @Test
    public void test() {
        AccessRightsAttributes accessRightsAttributes = new AccessRightsAttributes(true);

        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, "description"));
        verifyElementAttributes(toElementAttributes(null, "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, "description"));
        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, null));

        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description"));
        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, true, "userId"));

        verifyElementAttributes(toElementAttributes(new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description")));
        verifyElementAttributes(toElementAttributes(new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description"), 1L));
        verifyElementAttributes(toElementAttributes(new RootDirectoryAttributes("name", new AccessRightsAttributes(true), "userId", "description")));

        assertThrows(NullPointerException.class, () -> toElementAttributes((DirectoryElementEntity) null));
        assertThrows(NullPointerException.class, () -> toElementAttributes((RootDirectoryAttributes) null));
        assertThrows(NullPointerException.class, () -> toElementAttributes(null, 1));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, true, "userId", null));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, null, DIRECTORY, accessRightsAttributes, "userId", 1L, "description"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", null, accessRightsAttributes, "userId", 1L, "description"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, null, "userId", 1L, "description"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, accessRightsAttributes, null, 1L, "description"));
    }

    @SneakyThrows
    private void verifyElementAttributes(ElementAttributes elementAttributes) {
        assertEquals(toJsonString(elementAttributes), mapper.writeValueAsString(elementAttributes));
    }

    private String toJsonString(ElementAttributes elementAttributes) {
        return "{"
            + toJsonString("elementUuid", elementAttributes.getElementUuid()) + ","
            + toJsonString("elementName", elementAttributes.getElementName()) + ","
            + toJsonString("type", elementAttributes.getType()) + ","
            + toJsonString(elementAttributes.getAccessRights()) + ","
            + toJsonString("owner", elementAttributes.getOwner()) + ","
            + toJsonString("subdirectoriesCount", elementAttributes.getSubdirectoriesCount()) + ","
            + toJsonString("description", elementAttributes.getDescription())
            + "}";
    }

    private String toJsonString(AccessRightsAttributes accessRightsAttributes) {
        return "\"accessRights\":{" + toJsonString("private", accessRightsAttributes.isPrivate()) + "}";
    }

    private String toJsonString(String key, Object value) {
        return String.format(value instanceof String || value instanceof UUID ? "\"%s\":\"%s\"" : "\"%s\":%s", key, value);
    }
}
