/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.AccessRightsAttributes;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.dto.RootDirectoryAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@Service
class DirectoryService {

    private final DirectoryElementRepository directoryElementRepository;

    DirectoryService(DirectoryElementRepository directoryElementRepository) {
        this.directoryElementRepository = directoryElementRepository;
    }

    private static ElementAttributes toElementAttributes(DirectoryElementEntity entity) {
        return new ElementAttributes(entity.getId(), entity.getName(), ElementType.valueOf(entity.getType()), new AccessRightsAttributes(entity.isPrivate()), entity.getOwner());
    }

    public Mono<ElementAttributes> createElement(ElementAttributes elementAttributes, UUID directoryUuid) {
        return Mono.fromCallable(() -> toElementAttributes(directoryElementRepository.save(new DirectoryElementEntity(
                elementAttributes.getElementUuid() == null ? UUID.randomUUID() : elementAttributes.getElementUuid(),
                directoryUuid,
                elementAttributes.getElementName(),
                elementAttributes.getType().toString(),
                elementAttributes.getAccessRights() == null || elementAttributes.getAccessRights().isPrivate(),
                elementAttributes.getOwner()))));
    }

    public Mono<ElementAttributes> createElement(RootDirectoryAttributes rootDirectoryAttributes, UUID directoryUuid) {
        ElementAttributes elementAttributes = new ElementAttributes(null, rootDirectoryAttributes.getElementName(), ElementType.DIRECTORY,
                rootDirectoryAttributes.getAccessRights(), rootDirectoryAttributes.getOwner());
        return createElement(elementAttributes, directoryUuid);
    }

    public Flux<ElementAttributes> listDirectoryContent(String directoryUuid) {
        return Flux.fromStream(directoryContentStream(directoryUuid));
    }

    private Stream<ElementAttributes> directoryContentStream(String directoryUuid) {
        return directoryElementRepository.findByParentId(UUID.fromString(directoryUuid)).stream().map(DirectoryService::toElementAttributes);
    }

    public Flux<ElementAttributes> getRootDirectories() {
        return Flux.fromStream(directoryElementRepository.findByParentId(null).stream()
                .filter(e -> e.getType().equals(ElementType.DIRECTORY.toString())).map(DirectoryService::toElementAttributes));
    }

    public Mono<Void> renameElement(String elementUuid, String newElementName) {
        return Mono.fromRunnable(() -> directoryElementRepository.updateElementName(UUID.fromString(elementUuid), newElementName));
    }

    public Mono<Void> setDirectoryAccessRights(String directoryUuid, AccessRightsAttributes accessRightsAttributes) {
        return Mono.fromRunnable(() -> directoryElementRepository.updateElementAccessRights(UUID.fromString(directoryUuid), accessRightsAttributes.isPrivate()));
    }

    public Mono<Void> deleteElement(String elementUuid) {
        return Mono.fromRunnable(() -> deleteElementTree(elementUuid));
    }

    private void deleteElementTree(String elementUuid) {
        directoryContentStream(elementUuid).map(e -> e.getElementUuid().toString()).forEach(this::deleteElementTree);
        directoryElementRepository.deleteById(UUID.fromString(elementUuid));
    }

    public Mono<ElementAttributes> getDirectoryInfos(String directoryUuid) {
        return Mono.fromCallable(() -> directoryElementRepository.findById(UUID.fromString(directoryUuid)).map(DirectoryService::toElementAttributes).orElse(null));
    }
}
