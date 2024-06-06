/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.services.SupervisionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + DirectoryApi.API_VERSION + "/supervision", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "directory-server - Supervision")
public class SupervisionController {
    private final SupervisionService service;

    public SupervisionController(SupervisionService service) {
        this.service = service;
    }

    @GetMapping(value = "/elements/stash")
    @Operation(summary = "Get the list of elements in the trash")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the list of elements in the trash")})
    public ResponseEntity<List<ElementAttributes>> getStashedElements() {
        return ResponseEntity.ok().body(service.getStashedElementsAttributes());
    }

    @DeleteMapping(value = "/elements")
    @Operation(summary = "Delete list of elements without checking owner")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the list of elements in the trash")})
    public ResponseEntity<Void> deleteElements(@RequestParam("ids") List<UUID> elementsUuid) {
        service.deleteElementsByIds(elementsUuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/elements/recreate-index", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Recreate the index then reindex data")
    @ApiResponse(responseCode = "200", description = "Success of the index recreation & reindexing of elements")
    @ApiResponse(responseCode = "500", description = "An error happen while recreating the index.\nAn manual intervention is needing as no details of the error is available.")
    public ResponseEntity<Optional<String>> deleteElements() {
        if (service.recreateIndexDirectoryElementInfos()) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body(Optional.of("An error happen while re-creating the index. As no details is available an manual intervention is required."));
        }
    }
}
