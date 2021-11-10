/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
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
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + DirectoryApi.API_VERSION)
@Tag(name = "directory-server")
public class DirectoryController {

    private final DirectoryService service;

    public DirectoryController(DirectoryService service) {
        this.service = service;
    }

    @PostMapping(value = "/root-directories", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create directory")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The created directory"))
    public ResponseEntity<Mono<ElementAttributes>> createRootDirectory(@RequestBody RootDirectoryAttributes rootDirectoryAttributes,
                                                                      @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createRootDirectory(rootDirectoryAttributes, userId));
    }

    @PostMapping(value = "/directories/{directoryUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an element")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The created element"))
    public ResponseEntity<Mono<ElementAttributes>> createElement(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                 @RequestBody ElementAttributes elementAttributes,
                                                                 @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createElement(elementAttributes, directoryUuid, userId));
    }

    @GetMapping(value = "/root-directories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get root directories")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List root elements"))
    public ResponseEntity<Flux<ElementAttributes>> listRootDirectories(@RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getRootDirectories(userId));
    }

    @GetMapping(value = "/directories/{directoryUuid}/content", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get directory content")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List directory's elements"))
    public ResponseEntity<Flux<ElementAttributes>> listDirectoryContent(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                        @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.listDirectoryContent(directoryUuid, userId));
    }

    @GetMapping(value = "/directories/{directoryUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get element infos")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "element's infos"))
    public ResponseEntity<Mono<ElementAttributes>> getElementInfos(@PathVariable("directoryUuid") UUID directoryUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getElementInfos(directoryUuid)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @PutMapping(value = "/directories/{elementUuid}/rename/{newElementName}")
    @Operation(summary = "Rename element/directory")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element/directory was successfully renamed"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to rename this element")
    })
    public ResponseEntity<Mono<Void>> renameElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @PathVariable("newElementName") String newElementName,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.renameElement(elementUuid, newElementName, userId));
    }

    @PutMapping(value = "/directories/{elementUuid}/rights", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modify element/directory's access rights")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element/directory was successfully updated"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this element access rights")
    })
    public ResponseEntity<Mono<Void>> setAccessRights(@PathVariable("elementUuid") UUID elementUuid,
                                                      @RequestBody boolean isPrivate,
                                                      @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.setAccessRights(elementUuid, isPrivate, userId));
    }

    @DeleteMapping(value = "/directories/{elementUuid}")
    @Operation(summary = "Remove directory/element")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Directory/element was successfully removed"))
    public ResponseEntity<Mono<Void>> deleteElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.deleteElement(elementUuid, userId));
    }

    @GetMapping(value = "/directories/{directoryUuid}/{elementName}/exists")
    @Operation(summary = "Check if the element exists")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "If the element exists or not")})
    public ResponseEntity<Mono<Boolean>> elementExists(@PathVariable("directoryUuid") UUID directoryUuid,
                                                       @PathVariable("elementName") String elementName,
                                                       @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.elementExists(directoryUuid, elementName, userId));
    }

    @GetMapping(value = "/directories/elements")
    @Operation(summary = "get element infos from ids given as parameters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The elements information")})
    public ResponseEntity<Flux<ElementAttributes>> getElementsAttributes(@RequestParam("id") List<UUID> ids) {
        return ResponseEntity.ok().body(service.getElementsAttribute(ids));
    }

    @PutMapping(value = "/directories/{elementUuid}/notify-parent")
    @Operation(summary = "notify parent")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The notification has been sent"),
    })
    public ResponseEntity<Mono<Void>> emitDirectoryChangedNotification(@PathVariable("elementUuid") UUID elementUuid,
                                                 @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.emitDirectoryChangedNotification(elementUuid, userId));
    }
}
