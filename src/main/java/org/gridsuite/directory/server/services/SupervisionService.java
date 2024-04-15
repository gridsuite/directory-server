package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;

@Service
public class SupervisionService {
    private final DirectoryRepositoryService repositoryService;

    public SupervisionService(DirectoryRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public List<ElementAttributes> getStashedElements() {
        List<DirectoryElementEntity> entities = repositoryService.getElementsStashed();
        return entities.stream()
            .map(entity -> toElementAttributes(entity))
            .toList();
    }

    // delete all directory elements without checking owner
    public void deleteElementsByIds(List<UUID> uuids) {
        repositoryService.deleteElements(uuids);
    }
}
