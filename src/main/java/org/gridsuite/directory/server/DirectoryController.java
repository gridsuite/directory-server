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
import org.gridsuite.directory.server.dto.CreateDirectoryAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryRootEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    @GetMapping(value = "/directories/root", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get root directory id")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully get root directory id"))
    public ResponseEntity<Mono<DirectoryRootEntity>> getRootDirectoryId(@RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getRootDirectory());
    }

    @PostMapping(value = "/directories/create")
    @ApiOperation(value = "Create directory")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully created directory"))
    public ResponseEntity<Mono<DirectoryElementEntity>> createDirectory(@RequestBody CreateDirectoryAttributes createDirectoryAttributes,
                                                                        @RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createDirectory(createDirectoryAttributes));
    }

    @PutMapping(value = "/directories/{directoryUuid}/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Add element/directory to directory")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully added element/directory to directory"))
    public ResponseEntity<Mono<DirectoryElementEntity>> addElementToDirectory(@PathVariable("directoryUuid") String directoryUuid,
                                                      @RequestBody ElementAttributes elementAttributes,
                                                      @RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.addElementToDirectory(directoryUuid, elementAttributes));
    }

    @GetMapping(value = "/directories/{directoryUuid}/content", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Get directory content")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully get content of directory"))
    public ResponseEntity<Flux<ElementAttributes>> listDirectoryContent(@PathVariable("directoryUuid") String directoryUuid,
                                                                        @RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.listDirectoryContent(directoryUuid));
    }

    @PutMapping(value = "/directories/{elementUuid}/rename/{newElementName}")
    @ApiOperation(value = "Rename element/directory")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully renamed element/directory"))
    public ResponseEntity<Mono<Void>> renameElement(@PathVariable("elementUuid") String elementUuid,
                                              @PathVariable("newElementName") String newElementName,
                                              @RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().body(service.renameElement(elementUuid, newElementName));
    }

    @PutMapping(value = "/directories/{directoryUuid}/rights", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Modify directory access rights")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully modified directory access rights"))
    public ResponseEntity<Void> setDirectoryAccessRights(@PathVariable("directoryUuid") String directoryUuid,
                                                         @RequestBody AccessRightsAttributes accessRightsAttributes,
                                                         @RequestHeader("userId") String headerUserId) {
        service.setDirectoryAccessRights(directoryUuid, accessRightsAttributes);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/directories/{elementUuid}")
    @ApiOperation(value = "Remove directory/element")
    @ApiResponses(@ApiResponse(code = 200, message = "Successfully removed directory/element"))
    public ResponseEntity<Mono<Void>> deleteElement(@PathVariable("elementUuid") String elementUuid,
                                                    @RequestHeader("userId") String headerUserId) {
        return ResponseEntity.ok().body(service.deleteElement(elementUuid));
    }

}
