package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementEntity;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.directory.server.dto.ElementAttributes.toElementAttributes;

@Service
public class SupervisionService {
    private final DirectoryElementRepository directoryElementRepository;
    private final DirectoryRepositoryService repositoryService;

    public SupervisionService(DirectoryRepositoryService repositoryService, DirectoryElementRepository directoryElementRepository) {
        this.repositoryService = repositoryService;
        this.directoryElementRepository = directoryElementRepository;
    }

    public List<ElementAttributes> getStashedElementsAttributes() {
        List<DirectoryElementEntity> entities = getStashedElements();
        return entities.stream()
            .map(entity -> toElementAttributes(entity))
            .toList();
    }

    // delete all directory elements without checking owner
    public void deleteElementsByIds(List<UUID> uuids) {
        repositoryService.deleteElements(uuids);
    }

    public List<DirectoryElementEntity> getStashedElements() {
        return directoryElementRepository.findAllByStashed(true);
    }
}
