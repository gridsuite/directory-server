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
 * @author Slimane Amar <slimane.amar at rte-france.com>
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
    @Operation(summary = "Create root directory")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The created root directory"))
    public ResponseEntity<Mono<ElementAttributes>> createRootDirectory(@RequestBody RootDirectoryAttributes rootDirectoryAttributes,
                                                                       @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createRootDirectory(rootDirectoryAttributes, userId));
    }

    @PostMapping(value = "/directories/{directoryUuid}/elements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an element in a directory")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The created element"),
            @ApiResponse(responseCode = "403", description = "An element with the same name already exists in the directory")})
    public ResponseEntity<Mono<ElementAttributes>> createElement(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                 @RequestBody ElementAttributes elementAttributes,
                                                                 @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createElement(elementAttributes, directoryUuid, userId));
    }

    @DeleteMapping(value = "/elements/{elementUuid}")
    @Operation(summary = "Remove directory/element")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Directory/element was successfully removed"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
    })
    public ResponseEntity<Mono<Void>> deleteElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.deleteElement(elementUuid, userId));
    }

    @GetMapping(value = "/root-directories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get root directories")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The root directories"))
    public ResponseEntity<Flux<ElementAttributes>> getRootDirectories(@RequestHeader(name = "userId") String userId) {
        return ResponseEntity.ok().body(service.getRootDirectories(userId));
    }

    @GetMapping(value = "/directories/{directoryUuid}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get directory elements")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List directory's elements"))
    public ResponseEntity<Flux<ElementAttributes>> getDirectoryElements(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                        @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getDirectoryElements(directoryUuid, userId));
    }

    @GetMapping(value = "/elements/{elementUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get element infos")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The element information"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
    })
    public ResponseEntity<Mono<ElementAttributes>> getElement(@PathVariable("elementUuid") UUID elementUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getElement(elementUuid)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))));
    }

    @GetMapping(value = "/elements")
    @Operation(summary = "Get elements infos from ids given as parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The elements information"),
        @ApiResponse(responseCode = "404", description = "At least one item was not found"),
    })
    public ResponseEntity<Flux<ElementAttributes>> getElements(@RequestParam("id") List<UUID> ids) {
        return ResponseEntity.ok().body(service.getElements(ids));
    }

    @PutMapping(value = "/elements/{elementUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update element/directory")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element/directory was successfully updated"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this element")
    })
    public ResponseEntity<Mono<Void>> updateElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestBody ElementAttributes elementAttributes,
                                                    @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.updateElement(elementUuid, elementAttributes, userId));
    }

    @PostMapping(value = "/elements/{elementUuid}/notification")
    @Operation(summary = "Create change element notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The notification has been sent"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "400", description = "The notification type is unknown")
    })
    public ResponseEntity<Mono<Void>> notify(@PathVariable("elementUuid") UUID elementUuid,
                                             @RequestParam("type") String notificationType,
                                             @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.notify(notificationType, elementUuid, userId));
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/directories/{directoryUuid}/elements/{elementName}/types/{type}")
    @Operation(summary = "Check if an element with this name and this type already exists in the given directory")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The element exists"),
        @ApiResponse(responseCode = "404", description = "The element doesn't exist")})
    public ResponseEntity<Mono<Void>> elementExists(@PathVariable("directoryUuid") UUID directoryUuid,
                                                    @PathVariable("elementName") String elementName,
                                                    @PathVariable("type") String type) {
        return ResponseEntity.ok().body(service.elementExistsMono(directoryUuid, elementName, type));
    }

    @GetMapping(value = "/directories/{elementUuid}/allowed")
    @Operation(summary = "Control access permissions for a user")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Access permissions control done"))
    public ResponseEntity<Mono<Boolean>> isAllowed(@PathVariable("elementUuid") UUID elementUuid, @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().body(service.isAllowed(elementUuid, userId));
    }
}
