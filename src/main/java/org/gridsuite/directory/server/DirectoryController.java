/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.DirectoryAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + DirectoryApi.API_VERSION)
@Api(tags = "directory-server")
public class DirectoryController {

    private final DirectoryService service;

    public DirectoryController(DirectoryService service) {
        this.service = service;
    }

    @PostMapping(value = "/directories")
    @ApiOperation(value = "Create directory")
    @ApiResponses(@ApiResponse(code = 200, message = "The created directory"))
    public ResponseEntity<Mono<DirectoryElementEntity>> createDirectory(@RequestBody DirectoryAttributes directoryAttributes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createDirectory(directoryAttributes));
    }

    @PutMapping(value = "/directories/{directoryUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Add element/directory to directory")
    @ApiResponses(@ApiResponse(code = 200, message = "The added element/directory"))
    public ResponseEntity<Mono<DirectoryElementEntity>> addElementToDirectory(@PathVariable("directoryUuid") Optional<String> directoryUuid,
                                                      @RequestBody ElementAttributes elementAttributes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.addElementToDirectory(directoryUuid, elementAttributes));
    }

    @GetMapping(value = {"/directories/root-directories", "/directories/{directoryUuid}/content"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get directory content")
    @ApiResponses(@ApiResponse(code = 200, message = "List of directory's elements"))
    public ResponseEntity<Flux<ElementAttributes>> listDirectoryContent(@PathVariable("directoryUuid") Optional<String> directoryUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.listDirectoryContent(directoryUuid));
    }

    @PutMapping(value = "/directories/{elementUuid}/rename/{newElementName}")
    @ApiOperation(value = "Rename element/directory")
    @ApiResponses(@ApiResponse(code = 200, message = "Element/directory was successfully renamed"))
    public ResponseEntity<Mono<Void>> renameElement(@PathVariable("elementUuid") String elementUuid,
                                              @PathVariable("newElementName") String newElementName) {
        return ResponseEntity.ok().body(service.renameElement(elementUuid, newElementName));
    }

    @PutMapping(value = "/directories/{directoryUuid}/rights", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Modify directory's access rights")
    @ApiResponses(@ApiResponse(code = 200, message = "Directory's access rights were successfully modified"))
    public ResponseEntity<Mono<Void>> setDirectoryAccessRights(@PathVariable("directoryUuid") String directoryUuid,
                                                         @RequestBody AccessRightsAttributes accessRightsAttributes) {
        return ResponseEntity.ok().body(service.setDirectoryAccessRights(directoryUuid, accessRightsAttributes));
    }

    @DeleteMapping(value = "/directories/{elementUuid}")
    @ApiOperation(value = "Remove directory/element")
    @ApiResponses(@ApiResponse(code = 200, message = "Directory/element was successfully removed"))
    public ResponseEntity<Mono<Void>> deleteElement(@PathVariable("elementUuid") String elementUuid) {
        return ResponseEntity.ok().body(service.deleteElement(elementUuid));
    }

}
