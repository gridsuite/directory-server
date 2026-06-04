package org.gridsuite.directory.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.directory.server.error.DirectoryBusinessErrorCode;
import org.gridsuite.directory.server.error.DirectoryException;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.gridsuite.directory.server.services.PermissionService;
import org.gridsuite.directory.server.services.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@WebMvcTest(controllers = { DirectoryController.class, PropertyServerNameProvider.class })
class DirectoryControllerTest {

    private static final UUID ELEMENT_ID_1 = UUID.randomUUID();
    private static final UUID ELEMENT_ID_2 = UUID.randomUUID();

    private static final String ELEMENT_NAME_1 = "element1";
    private static final String ELEMENT_NAME_2 = "element2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DirectoryService directoryService;

    @MockitoBean
    private DirectoryRepositoryService directoryRepositoryService;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private PermissionService permissionService;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getElementNames(Boolean strictMode) throws Exception {
        List<UUID> elementIds = List.of(ELEMENT_ID_1, ELEMENT_ID_2);
        Map<UUID, String> returnedElementNamesMap = Map.of(
            ELEMENT_ID_1, ELEMENT_NAME_1,
            ELEMENT_ID_2, ELEMENT_NAME_2
        );

        when(directoryService.getElementNames(elementIds, strictMode))
            .thenReturn(returnedElementNamesMap);

        MvcResult result = mockMvc.perform(get("/v1/elements/names")
                .param("ids", ELEMENT_ID_1.toString(), ELEMENT_ID_2.toString())
                .param("strictMode", strictMode.toString()))
            .andExpectAll(status().isOk())
            .andReturn();

        Map<UUID, String> response =
            objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() { }
            );

        assertThat(response)
            .hasSize(2)
            .containsEntry(ELEMENT_ID_1, ELEMENT_NAME_1)
            .containsEntry(ELEMENT_ID_2, ELEMENT_NAME_2);

        verify(directoryService, times(1)).getElementNames(elementIds, strictMode);
    }

    @Test
    void getElementNamesWithNotFoundElementsWithStrictMode() throws Exception {
        List<UUID> elementIds = List.of(ELEMENT_ID_1, ELEMENT_ID_2);

        // with strictMode = true, throws notFound exception
        when(directoryService.getElementNames(elementIds, true))
            .thenThrow(
                DirectoryException.of(
                    DirectoryBusinessErrorCode.DIRECTORY_SOME_ELEMENTS_ARE_MISSING,
                    "Some requested elements are missing"
                ));

        mockMvc.perform(get("/v1/elements/names")
                .param("ids", ELEMENT_ID_1.toString(), ELEMENT_ID_2.toString())
                .param("strictMode", "true"))
            .andExpectAll(status().isNotFound());

        verify(directoryService, times(1)).getElementNames(elementIds, true);
    }

    @Test
    void getElementNamesWithNotFoundElementsWithoutStrictMode() throws Exception {
        List<UUID> elementIds = List.of(ELEMENT_ID_1, ELEMENT_ID_2);
        Map<UUID, String> returnedElementNamesMap = Map.of(
            ELEMENT_ID_1, ELEMENT_NAME_1
        );

        // with strictMode = false, returns only found elements
        when(directoryService.getElementNames(elementIds, false))
            .thenReturn(returnedElementNamesMap);

        MvcResult result = mockMvc.perform(get("/v1/elements/names")
                .param("ids", ELEMENT_ID_1.toString(), ELEMENT_ID_2.toString())
                .param("strictMode", "false"))
            .andExpectAll(status().isOk())
            .andReturn();

        Map<UUID, String> response =
            objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() { }
            );

        assertThat(response)
            .hasSize(1)
            .containsEntry(ELEMENT_ID_1, ELEMENT_NAME_1)
            .doesNotContainKey(ELEMENT_ID_2);

        verify(directoryService, times(1)).getElementNames(elementIds, false);
    }
}
