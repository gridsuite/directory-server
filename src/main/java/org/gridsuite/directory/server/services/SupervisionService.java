package org.gridsuite.directory.server.services;

import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.repository.DirectoryElementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupervisionService {
    private final DirectoryElementRepository directoryElementRepository;

    public SupervisionService(DirectoryElementRepository directoryElementRepository) {
        this.directoryElementRepository = directoryElementRepository;
    }

    public List<ElementAttributes> getAllElementsByType(String type) {
        if(type != null) {
            return directoryElementRepository.findAllByType(type).stream().map(ElementAttributes::toElementAttributes).toList();
        } else {
            return directoryElementRepository.findAll().stream().map(ElementAttributes::toElementAttributes).toList();
        }
    }
}
