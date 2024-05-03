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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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

    // Simple solution to get index name (with the prefix by environment).
    // Maybe use the Spring boot actuator or other solution ?
    // Keep indexName in sync with the annotation @Document in DirectoryElementInfos
    @Value("#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}directory-elements")
    public String indexNameDirectoryElements;

    @Value("#{@environment.getProperty('spring.data.elasticsearch.host')}" + ":" + "#{@environment.getProperty('spring.data.elasticsearch.port')}")
    public String elasticSerachHost;

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

    @GetMapping(value = "/elasticsearch-host")
    @Operation(summary = "get the elasticsearch address")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "the elasticsearch address")})
    public ResponseEntity<String> getElasticsearchHost() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(elasticSerachHost);
    }

    @GetMapping(value = "/indexed-directory-elements-index-name")
    @Operation(summary = "get the indexed directory elements index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed directory elements index name")})
    public ResponseEntity<String> getIndexedDirectoryElementsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(indexNameDirectoryElements);
    }

    @GetMapping(value = "/indexed-directory-elements-count")
    @Operation(summary = "get indexed directory elements count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed directory elements count")})
    public ResponseEntity<Long> getIndexedDirectoryElementsCount() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getIndexedDirectoryElementsCount());
    }

    @DeleteMapping(value = "/directories/{directoryUuid}/indexed-directory-elements")
    @Operation(summary = "delete indexed directory elements for the given directory")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all indexed directory elements for the given directory have been deleted")})
    public ResponseEntity<Long> deleteIndexedDirectoryElements(@PathVariable("directoryUuid") UUID directoryUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.deleteIndexedDirectoryElements(directoryUuid));
    }

    @GetMapping(value = "/directories")
    @Operation(summary = "get directories")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Directories")})
    public ResponseEntity<List<ElementAttributes>> getDirectories() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getDirectories());
    }

    @PostMapping(value = "/directoris/{directoryUuid}/reindex")
    @Operation(summary = "reindex elements of the directory and itslef if it's a root directory")
    @ApiResponse(responseCode = "200", description = "Elements reindexed")
    public ResponseEntity<Void> reindexElements(@PathVariable("directoryUuid") UUID directoryUuid) {
        service.reindexElements(directoryUuid);
        return ResponseEntity.ok().build();
    }
}
