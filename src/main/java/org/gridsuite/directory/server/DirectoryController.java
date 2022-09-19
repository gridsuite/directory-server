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
    public ResponseEntity<ElementAttributes> createRootDirectory(@RequestBody RootDirectoryAttributes rootDirectoryAttributes,
                                                                       @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createRootDirectory(rootDirectoryAttributes, userId));
    }

    @PostMapping(value = "/directories/{directoryUuid}/elements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an element in a directory")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The created element"),
        @ApiResponse(responseCode = "403", description = "An element with the same name already exists in the directory")})
    public ResponseEntity<ElementAttributes> createElement(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                 @RequestBody ElementAttributes elementAttributes,
                                                                 @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createElement(elementAttributes, directoryUuid, userId));
    }

    @GetMapping(value = "/elements/{elementUuid}/path", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get path of element")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List info of an element and its parents in order to get its path"),
        @ApiResponse(responseCode = "403", description = "Access forbidden for the element"),
        @ApiResponse(responseCode = "404", description = "The searched element was not found")})
    public ResponseEntity<List<ElementAttributes>> getPath(@PathVariable("elementUuid") UUID elementUuid,
                                                                        @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getPath(elementUuid, userId));
    }

    @DeleteMapping(value = "/elements/{elementUuid}")
    @Operation(summary = "Remove directory/element")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Directory/element was successfully removed"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
    })
    public ResponseEntity<Void> deleteElement(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestHeader("userId") String userId) {
        service.deleteElement(elementUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/root-directories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get root directories")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The root directories"))
    public ResponseEntity<List<ElementAttributes>> getRootDirectories(@RequestHeader(name = "userId") String userId) {
        return ResponseEntity.ok().body(service.getRootDirectories(userId));
    }

    @RequestMapping(value = "/root-directories", method = RequestMethod.HEAD)
    @Operation(summary = "Get if a root directory of this name exists")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The root directory exists"),
        @ApiResponse(responseCode = "204", description = "The root directory doesn't exist"),
    })
    public ResponseEntity<Void> rootDirectoryExists(@RequestParam("directoryName") String directoryName) {
        HttpStatus status = service.rootDirectoryExists(directoryName) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/directories/{directoryUuid}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get directory elements")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List directory's elements"))
    public ResponseEntity<List<ElementAttributes>> getDirectoryElements(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                        @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getDirectoryElements(directoryUuid, userId));
    }

    @GetMapping(value = "/elements/{elementUuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get element infos")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The element information"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
    })
    public ResponseEntity<ElementAttributes> getElement(@PathVariable("elementUuid") UUID elementUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getElement(elementUuid));
    }

    @GetMapping(value = "/elements")
    @Operation(summary = "Get elements infos from ids given as parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The elements information"),
        @ApiResponse(responseCode = "404", description = "At least one item was not found"),
    })
    public ResponseEntity<List<ElementAttributes>> getElements(@RequestParam("ids") List<UUID> ids,
                                                               @RequestParam(value = "strictMode", required = false, defaultValue = "true") Boolean strictMode) {
        return ResponseEntity.ok().body(service.getElements(ids, strictMode));
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/elements")
    @Operation(summary = "Control elements access permissions for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All elements are accessible"),
        @ApiResponse(responseCode = "404", description = "At least one element was not found"),
        @ApiResponse(responseCode = "403", description = "Access forbidden for at least one element")
    })
    public ResponseEntity<Void> areElementsAccessible(@RequestParam("ids") List<UUID> elementUuids,
                                                            @RequestHeader("userId") String userId) {
        service.areElementsAccessible(userId, elementUuids);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/elements/{elementUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update element/directory")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element/directory was successfully updated"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized to update this element")
    })
    public ResponseEntity<Void> updateElement(@PathVariable("elementUuid") UUID elementUuid,
                                              @RequestBody ElementAttributes elementAttributes,
                                              @RequestHeader("userId") String userId) {
        service.updateElement(elementUuid, elementAttributes, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/elements/{elementUuid}", params = "newDirectory")
    @Operation(summary = "Move element within directory tree")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Element was successfully updated"),
        @ApiResponse(responseCode = "404", description = "The element or the targeted directory was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized execute this update")
    })
    public ResponseEntity<Void> updateElementDirectory(@PathVariable("elementUuid") UUID elementUuid,
                                                    @RequestParam UUID newDirectory,
                                                    @RequestHeader("userId") String userId) {
        service.updateElementDirectory(elementUuid, newDirectory, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/directories/{directoryUuid}/{elementName}/newNameCandidate")
    @Operation(summary = "Get a free name in directory based on the one given and it's type")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "If the element exists or not")})
    public ResponseEntity<String> elementNameCandidate(@PathVariable("directoryUuid") UUID directoryUuid,
                                                             @PathVariable("elementName") String elementName,
                                                             @RequestParam("type") String type,
                                                             @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getDuplicateNameCandidate(directoryUuid, elementName, type, userId));
    }

    @PostMapping(value = "/elements/{elementUuid}/notification")
    @Operation(summary = "Create change element notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The notification has been sent"),
        @ApiResponse(responseCode = "404", description = "The element was not found"),
        @ApiResponse(responseCode = "400", description = "The notification type is unknown")
    })
    public ResponseEntity<Void> notify(@PathVariable("elementUuid") UUID elementUuid,
                                             @RequestParam("type") String notificationType,
                                             @RequestHeader("userId") String userId) {
        service.notify(notificationType, elementUuid, userId);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/directories/{directoryUuid}/elements/{elementName}/types/{type}")
    @Operation(summary = "Check if an element with this name and this type already exists in the given directory")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The element exists"),
        @ApiResponse(responseCode = "204", description = "The element doesn't exist")})
    public ResponseEntity<Void> elementExists(@PathVariable("directoryUuid") UUID directoryUuid,
                                                    @PathVariable("elementName") String elementName,
                                                    @PathVariable("type") String type) {
        HttpStatus status = service.elementExists(directoryUuid, elementName, type) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }
}
