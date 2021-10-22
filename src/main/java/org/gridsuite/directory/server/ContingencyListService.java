package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.RenameElementAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.logging.Level;

import static org.gridsuite.directory.server.DirectoryException.Type.CONTINGENCY_LIST_NOT_FOUND;

@Service
public class ContingencyListService {
    private static final String ROOT_CATEGORY_REACTOR = "reactor.";

    private static final String ACTIONS_API_VERSION = "v1";
    private static final String DELIMITER = "/";

    private final WebClient webClient;
    private String actionsServerBaseUri;

    @Autowired
    public ContingencyListService(@Value("${backing-services.actions-server.base-uri:http://actions-server/}") String actionsServerBaseUri,
                                  WebClient.Builder webClientBuilder) {
        this.actionsServerBaseUri = actionsServerBaseUri;
        this.webClient = webClientBuilder.build();
    }

    public void setActionsServerBaseUri(String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }

    public Mono<Void> renameContingencyList(UUID id, String newElementName) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{id}/rename")
            .buildAndExpand(id)
            .toUriString();

        return webClient.post()
            .uri(actionsServerBaseUri + path)
            .body(BodyInserters.fromValue(new RenameElementAttributes(newElementName)))
            .retrieve()
            .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(CONTINGENCY_LIST_NOT_FOUND)))
            .bodyToMono(Void.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }
}
