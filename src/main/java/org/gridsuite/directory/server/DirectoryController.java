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

    @PostMapping(value = "/elements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create root directory")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The created root directory"))
    public ResponseEntity<Mono<ElementAttributes>> createRootDirectory(@RequestBody RootDirectoryAttributes rootDirectoryAttributes,
                                                                       @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createRootDirectory(rootDirectoryAttributes, userId));
    }

    @PostMapping(value = "/elements/{elementUuid}/elements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a sub element")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The created element"))
    public ResponseEntity<Mono<ElementAttributes>> createElement(@PathVariable("elementUuid") UUID parentElementUuid,
                                                                 @RequestBody ElementAttributes elementAttributes,
                                                                 @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createElement(elementAttributes, parentElementUuid, userId));
    }

    @GetMapping(value = "/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get root directories or element list from ids given as parameters")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The elements informations"))
    public ResponseEntity<Flux<ElementAttributes>> getElementsAttributes(@RequestHeader(name = "userId", required = false) String userId,
                                                                         @RequestParam(name = "id", required = false) List<UUID> ids) {
        return ResponseEntity.ok().body(ids == null ? service.getRootDirectories(userId) : service.getElementsAttribute(ids));
    }

    @GetMapping(value = "/elements/{elementUuid}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get sub elements list ")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List directory's elements"))
    public ResponseEntity<Flux<ElementAttributes>> listDirectoryContent(@PathVariable("elementUuid") UUID elementUuid,
                                                                        @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.listDirectoryContent(elementUuid, userId));
    }

    @GetMapping(value = "/elements/{elementUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get element infos")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "element's infos"))
    public ResponseEntity<Mono<ElementAttributes>> getElementInfos(@PathVariable("elementUuid") UUID elementUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getElementInfos(elementUuid)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @PutMapping(value = "/elements/{elementUuid}")
    @Operation(summary = "Rename element/directory")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element/directory was successfully renamed"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to rename this element")
    })
    public ResponseEntity<Mono<Void>> renameElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestParam("name") String newElementName,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.renameElement(elementUuid, newElementName, userId));
    }

    @PutMapping(value = "/elements/{elementUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modify element/directory's access rights")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element/directory was successfully updated"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this element access rights")
    })
    public ResponseEntity<Mono<Void>> setAccessRights(@PathVariable("elementUuid") UUID elementUuid,
                                                      @RequestParam("private") boolean isPrivate,
                                                      @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.setAccessRights(elementUuid, isPrivate, userId));
    }

    @DeleteMapping(value = "/elements/{elementUuid}")
    @Operation(summary = "Remove directory/element")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Directory/element was successfully removed"))
    public ResponseEntity<Mono<Void>> deleteElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.deleteElement(elementUuid, userId));
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/elements/{elementUuid}/elements/{elementName}")
    @Operation(summary = "Check if a sub element exists")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The element exists"),
        @ApiResponse(responseCode = "404", description = "The element doesn't exist")})
    public ResponseEntity<Mono<Void>> elementExists(@PathVariable("elementUuid") UUID elementUuid,
                                                    @PathVariable("elementName") String elementName,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.elementExists(elementUuid, elementName, userId));
    }

    @PostMapping(value = "/elements/{elementUuid}/notification")
    @Operation(summary = "Create change element notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The notification has been sent"),
    })
    public ResponseEntity<Mono<Void>> emitDirectoryChangedNotification(@PathVariable("elementUuid") UUID elementUuid,
                                                                       @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.emitDirectoryChangedNotification(elementUuid, userId));
    }
}
