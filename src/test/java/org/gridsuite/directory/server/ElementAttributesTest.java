/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.utils.MatcherJson;
import org.gridsuite.directory.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThrows;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DisableElasticsearch
public class ElementAttributesTest {

    private static final UUID ELEMENT_UUID = UUID.randomUUID();
    public static final String TYPE_01 = "TYPE_01";

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
        Instant localCreationDate = Instant.now();

        DirectoryElementEntity elementEntity = new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, "userId", "description", localCreationDate, localCreationDate, "userId", false, null);
        DirectoryElementEntity elementEntity2 = new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", TYPE_01, "userId", "description", localCreationDate, localCreationDate, "userId", false, null);

        assertTrue(elementEntity.isAttributesUpdatable(ElementAttributes.builder().elementName("newName").build(), "userId"));
        assertTrue(elementEntity.isAttributesUpdatable(ElementAttributes.builder().build(), "userId"));
        assertTrue(elementEntity.isAttributesUpdatable(ElementAttributes.builder().description("newDescription").build(), "userId"));

        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().build(), "userId2"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().elementUuid(UUID.randomUUID()).build(), "userId"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().type(TYPE_01).build(), "userId"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().owner("newUser").build(), "userId"));
        assertFalse(elementEntity.isAttributesUpdatable(ElementAttributes.builder().subdirectoriesCount(1L).build(), "userId"));

        assertFalse(elementEntity2.isAttributesUpdatable(ElementAttributes.builder().build(), "userId"));

        elementEntity.update(ElementAttributes.builder().elementName("newName").build());
        org.hamcrest.MatcherAssert.assertThat(toElementAttributes(ELEMENT_UUID, "newName", DIRECTORY, "userId", "description", elementEntity.getCreationDate(), elementEntity.getLastModificationDate(), "userId"), new MatcherJson<>(mapper, toElementAttributes(elementEntity)));
    }

    @Test
    public void testElementAttributesCreation() {
        Instant creationDate = Instant.now();
        Instant lastModificationDate = Instant.now();

        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, "userId", 1L, "description", creationDate, creationDate, "userId"));
        verifyElementAttributes(toElementAttributes(null, "name", DIRECTORY, "userId", 1L, "description", creationDate, creationDate, "userId"));
        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, "userId", 1L, null, creationDate, creationDate, "userId"));

        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, "userId", "description"));
        verifyElementAttributes(toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, "userId"));

        verifyElementAttributes(toElementAttributes(new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, "userId", "description", lastModificationDate, lastModificationDate, "userId", false, null)));
        verifyElementAttributes(toElementAttributes(new DirectoryElementEntity(ELEMENT_UUID, ELEMENT_UUID, "name", DIRECTORY, "userId", "description", lastModificationDate, lastModificationDate, "userId", false, null), 1L));
        verifyElementAttributes(toElementAttributes(new RootDirectoryAttributes("name", "userId", "description", creationDate, creationDate, "userId")));

        assertThrows(NullPointerException.class, () -> toElementAttributes((DirectoryElementEntity) null));
        assertThrows(NullPointerException.class, () -> toElementAttributes((RootDirectoryAttributes) null));
        assertThrows(NullPointerException.class, () -> toElementAttributes(null, 1));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, "userId", null));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, null, DIRECTORY, "userId", 1L, "description", creationDate, creationDate, "userId"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", null, "userId", 1L, "description", creationDate, creationDate, "userId"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, null, DIRECTORY, "userId", 1L, "description", creationDate, creationDate, "userId"));
        assertThrows(NullPointerException.class, () -> toElementAttributes(ELEMENT_UUID, "name", DIRECTORY, null, 1L, "description", creationDate, creationDate, "userId"));
    }

    @SneakyThrows
    @Test
    public void testNullValues() {
        ElementAttributes elementAttributes = mapper.readValue("{}", ElementAttributes.class);
        assertEquals("{}", mapper.writeValueAsString(elementAttributes));
    }

    @Test
    public void testJsonString() {
        Instant creationDate = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        String formattedCreationDate = formatter.format(creationDate);

        assertEquals(
            "{\"elementUuid\":\"21297976-7445-44f1-9ccf-910cbb2f84f8\",\"elementName\":\"name\",\"type\":\"DIRECTORY\",\"owner\":\"userId\",\"subdirectoriesCount\":1,\"description\":\"description\",\"creationDate\":\"" + formattedCreationDate + "\",\"lastModificationDate\":\"" + formattedCreationDate + "\",\"lastModifiedBy\":\"userId\"}",
            toJsonString(toElementAttributes(UUID.fromString("21297976-7445-44f1-9ccf-910cbb2f84f8"), "name", DIRECTORY, "userId", 1L, "description", creationDate, creationDate, "userId"))
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

    private String toJsonString(String key, Object value) {
        if (value == null) {
            return (String) value;
        }
        if (value instanceof Instant) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
            return String.format("\"%s\":\"%s\"", key, formatter.format((Instant) value));
        }
        return String.format(value instanceof String || value instanceof UUID ? "\"%s\":\"%s\"" : "\"%s\":%s", key, value);
    }
}
