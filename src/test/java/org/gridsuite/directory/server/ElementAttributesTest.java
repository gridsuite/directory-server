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
import org.gridsuite.directory.server.utils.MatcherJson;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.DirectoryService.STUDY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.*;
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

    private static <T> Collector<T, ?, List<T>> toReversedList() {
        return Collectors.collectingAndThen(Collectors.toList(), list -> {
            Collections.reverse(list);
            return list;
        });
    }

    @Test
    public void testElementEntityUpdate() {
        LocalDateTime localCreationDate = LocalDateTime.now(ZoneOffset.UTC);

        DirectoryElementEntity elementEntity = new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description", localCreationDate, localCreationDate, "userId");
        DirectoryElementEntity elementEntity2 = new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", STUDY, true, "userId", "description", localCreationDate, localCreationDate, "userId");

        assertTrue(elementEntity.isAttributesUpdatable(ElementAttributes.builder().elementName("newName").build(), "userId"));
        assertTrue(elementEntity.isAttributesUpdatable(ElementAttributes.builder().accessRights(new AccessRightsAttributes(false)).build(), "userId"));
        assertTrue(elementEntity.isAttributesUpdatable(ElementAttributes.builder().description("newDescription").build(), "userId"));

        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().accessRights(new AccessRightsAttributes(false)).build(), "userId2"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().elementUuid(UUID.randomUUID()).build(), "userId"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().type(STUDY).build(), "userId"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().owner("newUser").build(), "userId"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().subdirectoriesCount(1L).build(), "userId"));

        assertFalse(elementEntity2.isAttributesUpdatable(ElementAttributes.builder().accessRights(new AccessRightsAttributes(false)).build(), "userId"));

        elementEntity.update(ElementAttributes.builder().elementName("newName").accessRights(new AccessRightsAttributes(false)).build());
        org.hamcrest.MatcherAssert.assertThat(toElementAttributes(ELEMENT_UUID, "newName", DIRECTORY, false, "userId", "description", ZonedDateTime.ofInstant(elementEntity.getCreationDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), ZonedDateTime.ofInstant(elementEntity.getLastModificationDate().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), "userId"), new MatcherJson<>(mapper, toElementAttributes(elementEntity)));
    }

    @Test
    public void testElementAttributesCreation() {
        AccessRightsAttributes accessRightsAttributes = new AccessRightsAttributes(true);
        ZonedDateTime zonedCreationDate = ZonedDateTime.now(ZoneOffset.UTC);
        LocalDateTime localCreationDate = LocalDateTime.now(ZoneOffset.UTC);

        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, "description", zonedCreationDate, zonedCreationDate, "userId"));
        verifyElementAttributes(toElementAttributes(null, "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, "description", zonedCreationDate, zonedCreationDate, "userId"));
        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, null, zonedCreationDate, zonedCreationDate, "userId"));

        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description"));
        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, true, "userId"));

        verifyElementAttributes(toElementAttributes(new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description", localCreationDate, localCreationDate, "userId")));
        verifyElementAttributes(toElementAttributes(new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, true, "userId", "description", localCreationDate, localCreationDate, "userId"), 1L));
        verifyElementAttributes(toElementAttributes(new RootDirectoryAttributes("name", new AccessRightsAttributes(true), "userId", "description", zonedCreationDate, zonedCreationDate, "userId")));

        assertThrows(NullPointerException.class, () -> toElementAttributes((DirectoryElementEntity) null));
        assertThrows(NullPointerException.class, () -> toElementAttributes((RootDirectoryAttributes) null));
        assertThrows(NullPointerException.class, () -> toElementAttributes(null, 1));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, true, "userId", null));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, null, DIRECTORY, accessRightsAttributes, "userId", 1L, "description", zonedCreationDate, zonedCreationDate, "userId"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", null, accessRightsAttributes, "userId", 1L, "description", zonedCreationDate, zonedCreationDate, "userId"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, null, "userId", 1L, "description", zonedCreationDate, zonedCreationDate, "userId"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, accessRightsAttributes, null, 1L, "description", zonedCreationDate, zonedCreationDate, "userId"));
    }

    @Test
    public void testAllowedUser() {
        assertTrue(toElementAttributes(ELEMENT_UUID, "dir", DIRECTORY, false, "user").isAllowed("user"));
        assertTrue(toElementAttributes(ELEMENT_UUID, "dir", DIRECTORY, false, "user").isAllowed("user1"));
        assertTrue(toElementAttributes(ELEMENT_UUID, "dir", DIRECTORY, true, "user").isAllowed("user"));
        assertFalse(toElementAttributes(ELEMENT_UUID, "dir", DIRECTORY, true, "user").isAllowed("user1"));

        ElementAttributes notDirectory = toElementAttributes(ELEMENT_UUID, "study", STUDY, false, "userId");
        assertThrows("NOT_DIRECTORY", DirectoryException.class, () -> notDirectory.isAllowed("userId"));
    }

    @SneakyThrows
    @Test
    public void testNullValues() {
        ElementAttributes elementAttributes = mapper.readValue("{}", ElementAttributes.class);
        assertEquals("{}", mapper.writeValueAsString(elementAttributes));
    }

    @Test
    public void testJsonString() {
        ZonedDateTime creationDate = ZonedDateTime.now(ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        String formattedCreationDate = creationDate.format(formatter);

        assertEquals(
            "{\"elementUuid\":\"21297976-7445-44f1-9ccf-910cbb2f84f8\",\"elementName\":\"name\",\"type\":\"DIRECTORY\",\"accessRights\":{\"isPrivate\":true},\"owner\":\"userId\",\"subdirectoriesCount\":1,\"description\":\"description\",\"creationDate\":\"" + formattedCreationDate + "\",\"lastModificationDate\":\"" + formattedCreationDate + "\",\"lastModifiedBy\":\"userId\"}",
            toJsonString(toElementAttributes(UUID.fromString("21297976-7445-44f1-9ccf-910cbb2f84f8"), "name", DIRECTORY, new AccessRightsAttributes(true), "userId", 1L, "description", creationDate, creationDate, "userId"))
        );
    }

    @SneakyThrows
    private void verifyElementAttributes(ElementAttributes elementAttributes) {
        assertEquals(toJsonString(elementAttributes), mapper.writeValueAsString(elementAttributes));
    }

    private String toJsonString(ElementAttributes elementAttributes) {
        Optional<String> jsonStringStream = Stream.of(
                toJsonString("elementUuid", elementAttributes.getElementUuid()),
                toJsonString("elementName", elementAttributes.getElementName()),
                toJsonString("type", elementAttributes.getType()),
                toJsonString(elementAttributes.getAccessRights()),
                toJsonString("owner", elementAttributes.getOwner()),
                toJsonString("subdirectoriesCount", elementAttributes.getSubdirectoriesCount()),
                toJsonString("description", elementAttributes.getDescription()),
                toJsonString("creationDate", elementAttributes.getCreationDate()),
                toJsonString("lastModificationDate", elementAttributes.getLastModificationDate()),
                toJsonString("lastModifiedBy", elementAttributes.getLastModifiedBy())
            )
            .filter(Objects::nonNull)
            .collect(toReversedList()).stream()
            .reduce((j, r) -> r + "," + j);
        return "{" + jsonStringStream.orElse("") + "}";
    }

    private String toJsonString(AccessRightsAttributes accessRightsAttributes) {
        return "\"accessRights\":{" + toJsonString("isPrivate", accessRightsAttributes.isPrivate()) + "}";
    }

    private String toJsonString(String key, Object value) {
        if (value == null) {
            return (String) value;
        }
        if (value instanceof ZonedDateTime) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            return String.format("\"%s\":\"%s\"", key, ((ZonedDateTime) value).format(formatter));
        }
        return String.format(value instanceof String || value instanceof UUID ? "\"%s\":\"%s\"" : "\"%s\":%s", key, value);
    }
}
