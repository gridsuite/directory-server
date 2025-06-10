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
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.services.DirectoryElementInfosService;
import org.gridsuite.directory.server.services.SupervisionService;
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

    private final DirectoryElementInfosService directoryElementInfosService;

    private final RestClient restClient;

    public SupervisionController(SupervisionService service, DirectoryElementInfosService directoryElementInfosService, RestClient restClient) {
        this.service = service;
        this.directoryElementInfosService = directoryElementInfosService;
        this.restClient = restClient;
    }

    @DeleteMapping(value = "/elements")
    @Operation(summary = "Delete list of elements without checking owner")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "the list of elements in the trash")})
    public ResponseEntity<Void> deleteElements(@RequestParam("ids") List<UUID> elementsUuid) {
        service.deleteElementsByIds(elementsUuid);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/elements")
    @Operation(summary = "Get all elements of a given type")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of elements")})
    public ResponseEntity<List<ElementAttributes>> getAllElements(
            @RequestParam(value = "elementType") String elementType) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getAllElementsByType(elementType));
    }

    @GetMapping(value = "/elasticsearch-host")
    @Operation(summary = "get the elasticsearch address")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "the elasticsearch address")})
    public ResponseEntity<String> getElasticsearchHost() {
        HttpHost elasticSearchHost = restClient.getNodes().get(0).getHost();
        String host = elasticSearchHost.getHostName()
            + ":"
            + elasticSearchHost.getPort();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(host);
    }

    @GetMapping(value = "/elements/index-name")
    @Operation(summary = "get the indexed directory elements index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed directory elements index name")})
    public ResponseEntity<String> getIndexedDirectoryElementsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(directoryElementInfosService.getDirectoryElementsIndexName());
    }

    @GetMapping(value = "/elements/indexation-count")
    @Operation(summary = "get indexed directory elements count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed directory elements count")})
    public ResponseEntity<String> getIndexedDirectoryElementsCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(service.getIndexedDirectoryElementsCount()));
    }

    @PostMapping(value = "/elements/index")
    @Operation(summary = "Recreate Elasticsearch index")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Elasticsearch index recreated successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to recreate Elasticsearch index")
    })
    public ResponseEntity<Void> recreateESIndex() {
        service.recreateIndex();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/elements/reindex")
    @Operation(summary = "reindex all elements")
    @ApiResponse(responseCode = "200", description = "Elements reindexed")
    public ResponseEntity<Void> reindexElements() {
        service.reindexElements();
        return ResponseEntity.ok().build();
    }
}
