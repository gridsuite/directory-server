package org.gridsuite.directory.server;

import org.gridsuite.directory.server.error.DirectoryBusinessErrorCode;
import org.gridsuite.directory.server.error.DirectoryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@WebMvcTest(controllers = { DirectoryController.class })
class DirectoryControllerTest {

    private static final UUID ELEMENT_ID_1 = UUID.randomUUID();
    private static final UUID ELEMENT_ID_2 = UUID.randomUUID();

    private static final String ELEMENT_NAME_1 = "element1";
    private static final String ELEMENT_NAME_2 = "element2";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DirectoryService directoryService;

    @Test
    void getElementNames() throws Exception {
        List<UUID> elementIds = List.of(ELEMENT_ID_1, ELEMENT_ID_2);
        Map<UUID, String> returnedElementNamesMap = Map.of(
            ELEMENT_ID_1, ELEMENT_NAME_1,
            ELEMENT_ID_2, ELEMENT_NAME_2
        );

        // with strictMode = true
        when(directoryService.getElementNames(elementIds, true))
            .thenReturn(returnedElementNamesMap);

        mockMvc.perform(get("/v1/elements/names")
                .param("ids", ELEMENT_ID_1.toString(), ELEMENT_ID_2.toString())
                .param("strictMode", "true"))
            .andExpectAll(status().isOk())
            .andExpect(jsonPath("$.['" + ELEMENT_ID_1 + "']").value(ELEMENT_NAME_1))
            .andExpect(jsonPath("$.['" + ELEMENT_ID_2 + "']").value(ELEMENT_NAME_2));

        verify(directoryService, times(1)).getElementNames(elementIds, true);

        // with strictMode = false
        when(directoryService.getElementNames(elementIds, false))
            .thenReturn(returnedElementNamesMap);

        mockMvc.perform(get("/v1/elements/names")
                .param("ids", ELEMENT_ID_1.toString(), ELEMENT_ID_2.toString())
                .param("strictMode", "false"))
            .andExpectAll(status().isOk())
            .andExpect(jsonPath("$.['" + ELEMENT_ID_1 + "']").value(ELEMENT_NAME_1))
            .andExpect(jsonPath("$.['" + ELEMENT_ID_2 + "']").value(ELEMENT_NAME_2));

        verify(directoryService, times(1)).getElementNames(elementIds, false);
    }

    @Test
    void getElementNamesWithNotFoundElements() throws Exception {
        List<UUID> elementIds = List.of(ELEMENT_ID_1, ELEMENT_ID_2);
        Map<UUID, String> returnedElementNamesMap = Map.of(
            ELEMENT_ID_1, ELEMENT_NAME_1
        );

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

        // with strictMode = false, returns only found elements
        when(directoryService.getElementNames(elementIds, false))
            .thenReturn(returnedElementNamesMap);

        mockMvc.perform(get("/v1/elements/names")
                .param("ids", ELEMENT_ID_1.toString(), ELEMENT_ID_2.toString())
                .param("strictMode", "false"))
            .andExpectAll(status().isOk())
            .andExpect(jsonPath("$.['" + ELEMENT_ID_1 + "']").value(ELEMENT_NAME_1))
            .andExpect(jsonPath("$.['" + ELEMENT_ID_2 + "']").doesNotExist());

        verify(directoryService, times(1)).getElementNames(elementIds, false);
    }
}
