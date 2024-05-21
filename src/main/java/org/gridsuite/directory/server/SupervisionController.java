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
import org.gridsuite.directory.server.dto.elasticsearch.ESIndex;
import org.gridsuite.directory.server.services.SupervisionService;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
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

    private final ESIndex indexConf;

    private final ClientConfiguration elasticsearchClientConfiguration;

    public SupervisionController(SupervisionService service, ClientConfiguration elasticsearchClientConfiguration, ESIndex indexConf) {
        this.service = service;
        this.elasticsearchClientConfiguration = elasticsearchClientConfiguration;
        this.indexConf = indexConf;
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
        String host = elasticsearchClientConfiguration.getEndpoints().get(0).getHostName()
                        + ":"
                        + elasticsearchClientConfiguration.getEndpoints().get(0).getPort();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(host);
    }

    @GetMapping(value = "/indexed-directory-elements-index-name")
    @Operation(summary = "get the indexed directory elements index name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed directory elements index name")})
    public ResponseEntity<String> getIndexedDirectoryElementsIndexName() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(indexConf.getDirectoryElementsIndexName());
    }

    @GetMapping(value = "/indexed-directory-elements-count")
    @Operation(summary = "get indexed directory elements count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Indexed directory elements count")})
    public ResponseEntity<String> getIndexedDirectoryElementsCount() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(service.getIndexedDirectoryElementsCount()));
    }

    @DeleteMapping(value = "/elements/indexed-directory-elements")
    @Operation(summary = "delete indexed elements")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "all indexed elements have been deleted")})
    public ResponseEntity<String> deleteIndexedDirectoryElements() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(service.deleteIndexedDirectoryElements()));
    }

    @PostMapping(value = "/elements/reindex")
    @Operation(summary = "reindex all elements")
    @ApiResponse(responseCode = "200", description = "Elements reindexed")
    public ResponseEntity<Void> reindexElements() {
        service.reindexElements();
        return ResponseEntity.ok().build();
    }
}
