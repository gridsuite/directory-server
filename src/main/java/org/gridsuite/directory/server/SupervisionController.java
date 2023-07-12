package org.gridsuite.directory.server;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.directory.server.dto.ElementAttributes;
import org.gridsuite.directory.server.services.SupervisionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/" + DirectoryApi.API_VERSION + "/supervision")
@Tag(name = "Supervision directory-server")
public class SupervisionController {

    private SupervisionService supervisionService;

    public SupervisionController (SupervisionService supervisionService) {
        this.supervisionService = supervisionService;
    }

    @GetMapping("/elements")
    public ResponseEntity<List<ElementAttributes>> getAllElements (
        @RequestParam(value = "elementType", required = false) String elementType) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.getAllElementsByType(elementType));
    }
}
