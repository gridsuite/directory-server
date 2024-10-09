/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.services.DirectoryRepositoryService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    private final DirectoryRepositoryService repositoryService;

    public DirectoryController(DirectoryService service, DirectoryRepositoryService repositoryService) {
        this.service = service;
        this.repositoryService = repositoryService;
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
        @ApiResponse(responseCode = "409", description = "An element with the same name already exists in the directory")})
    public ResponseEntity<ElementAttributes> createElement(@PathVariable("directoryUuid") UUID directoryUuid,
                                             @Parameter(description = "if element already exists a new incremental name is provided") @RequestParam(value = "allowNewName", required = false, defaultValue = "false") Boolean allowNewName,
                                             @RequestBody ElementAttributes elementAttributes,
                                             @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.createElement(elementAttributes, directoryUuid, userId, allowNewName));
    }

    @PostMapping(value = "/elements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Duplicate an element in a directory")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The created element"),
        @ApiResponse(responseCode = "409", description = "An element with the same name already exists in the directory")})
    public ResponseEntity<ElementAttributes> duplicateElement(
                                                           @RequestParam("duplicateFrom") UUID elementUuid,
                                                           @Parameter(description = "ID of the new element") @RequestParam("newElementUuid") UUID newElementUuid,
                                                           @Parameter(description = "Optional UUID of the target directory where the new element will be placed. Defaults to the same directory as the original element if not specified.")
                                                           @RequestParam(name = "targetDirectoryId", required = false) UUID targetDirectoryId,
                                                           @RequestHeader("userId") String userId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.duplicateElement(elementUuid, newElementUuid, targetDirectoryId, userId));
    }

    @PostMapping(value = "/directories/paths/elements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an element inside the given directory described by the path, if one of more directory of the path are missing we create them.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The element is imported"),
    })
    public ResponseEntity<Void> createElementInDirectoryPath(@RequestParam("directoryPath") String directoryPath, @RequestBody ElementAttributes elementAttributes,
                                              @RequestHeader("userId") String userId) {
        service.createElementInDirectoryPath(directoryPath, elementAttributes, userId);
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/elements/{elementUuid}/path", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get path of element")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List info of an element and its parents in order to get its path"),
        @ApiResponse(responseCode = "403", description = "Access forbidden for the element"),
        @ApiResponse(responseCode = "404", description = "The searched element was not found")})
    public ResponseEntity<List<ElementAttributes>> getPath(@PathVariable("elementUuid") UUID elementUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getPath(elementUuid));
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

    @DeleteMapping(value = "/elements")
    @Operation(summary = "Remove directories/elements")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Directory/element was successfully removed"))
    public ResponseEntity<Void> deleteElements(@Parameter(description = "elements UUIDs") @RequestParam("ids") List<UUID> elementsUuid,
                                               @Parameter(description = "parent directory UUID of elements to delete") @RequestParam("parentDirectoryUuid") UUID parentDirectoryUuid,
                                               @RequestHeader("userId") String userId) {
        service.deleteElements(elementsUuid, parentDirectoryUuid, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/root-directories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get root directories")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The root directories"))
    public ResponseEntity<List<ElementAttributes>> getRootDirectories(@RequestParam(value = "elementTypes", required = false, defaultValue = "") List<String> types) {
        return ResponseEntity.ok().body(service.getRootDirectories(types));
    }

    @RequestMapping(value = "/root-directories", method = RequestMethod.HEAD)
    @Operation(summary = "Get if a root directory of this name exists")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The root directory exists"),
        @ApiResponse(responseCode = "204", description = "The root directory doesn't exist"),
    })
    public ResponseEntity<Void> rootDirectoryExists(@RequestParam("directoryName") String directoryName) {
        HttpStatus status = service.getDirectoryUuid(directoryName, null) != null ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/directories/{directoryUuid}/elements", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get directory elements")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List directory's elements"))
    public ResponseEntity<List<ElementAttributes>> getDirectoryElements(@PathVariable("directoryUuid") UUID directoryUuid,
                                                                        @RequestParam(value = "elementTypes", required = false, defaultValue = "") List<String> types) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.getDirectoryElements(directoryUuid, types));
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
                                                               @RequestParam(value = "elementTypes", required = false, defaultValue = "") List<String> types,
                                                               @RequestParam(value = "strictMode", required = false, defaultValue = "true") Boolean strictMode) {
        return ResponseEntity.ok().body(service.getElements(ids, strictMode, types));
    }

    @GetMapping(value = "/users/{userId}/cases/count")
    @Operation(summary = "Get the cases count of the given user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The cases count"),
    })
    public ResponseEntity<Integer> getCasesCount(@PathVariable("userId") String userId) {
        return ResponseEntity.ok().body(service.getCasesCount(userId));
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/elements")
    @Operation(summary = "Control elements access permissions for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All elements are accessible"),
        @ApiResponse(responseCode = "404", description = "At least one element was not found"),
        @ApiResponse(responseCode = "204", description = "Access forbidden for at least one element")
    })
    public ResponseEntity<Void> areElementsAccessible(@RequestParam("ids") List<UUID> elementUuids,
                                                      @RequestParam(value = "forDeletion", required = false, defaultValue = "false") Boolean forDeletion,
                                                      @RequestHeader("userId") String userId) {
        boolean result = Boolean.TRUE.equals(forDeletion) ? service.areDirectoryElementsDeletable(elementUuids, userId) : service.areDirectoryElementsAccessible(elementUuids, userId);
        return result ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
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

    @PutMapping(value = "/elements", params = "targetDirectoryUuid")
    @Operation(summary = "Move elements within directory tree")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Elements was successfully updated"),
        @ApiResponse(responseCode = "404", description = "The elements or the targeted directory was not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized execute this update")
    })
    public ResponseEntity<Void> moveElementsDirectory(
            @RequestParam UUID targetDirectoryUuid,
            @RequestBody List<UUID> elementsUuids,
            @RequestHeader("userId") String userId) {
        service.moveElementsDirectory(elementsUuids, targetDirectoryUuid, userId);
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
        HttpStatus status = repositoryService.isElementExists(directoryUuid, elementName, type) ? HttpStatus.OK : HttpStatus.NO_CONTENT;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).build();
    }

    @GetMapping(value = "/elements/indexation-infos", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search elements in elasticsearch")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of elements found")})
    public ResponseEntity<Page<DirectoryElementInfos>> searchElements(
            @Parameter(description = "User input") @RequestParam(value = "userInput") String userInput,
            @Parameter(description = "Current directory UUID") @RequestParam(value = "directoryUuid", required = false, defaultValue = "") String directoryUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(service.searchElements(userInput, directoryUuid));
    }

    @GetMapping(value = "/directories/uuid")
    @Operation(summary = "Get directory uuid from given path")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The directory uuid"),
        @ApiResponse(responseCode = "404", description = "The directory was not found")})
    public ResponseEntity<UUID> getDirectoryUuidFromPath(@RequestParam("directoryPath") List<String> directoryPath) {
        List<String> decodedDirectoryPath = directoryPath.stream().map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8)).toList();
        return ResponseEntity.ok().body(service.getDirectoryUuidFromPath(decodedDirectoryPath));
    }
}
