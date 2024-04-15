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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + DirectoryApi.API_VERSION + "/supervision")
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
        return ResponseEntity.ok().body(service.getStashedElements());
    }

    @DeleteMapping(value = "/elements")
    @Operation(summary = "Delete list of elements without checking owner")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the list of elements in the trash")})
    public ResponseEntity<Void> deleteElements(@RequestParam("ids") List<UUID> elementsUuid) {
        service.deleteElementsByIds(elementsUuid);
        return ResponseEntity.ok().build();
    }
}
