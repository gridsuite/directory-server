package org.gridsuite.directory.server;

import org.gridsuite.directory.server.dto.RenameElementAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ContingencyListService.class);
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

    public Mono<Void> deleteContingencyList(UUID id) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/{id}")
            .buildAndExpand(id)
            .toUriString();

        return webClient.delete()
            .uri(actionsServerBaseUri + path)
            .retrieve()
            .onStatus(httpStatus -> httpStatus != HttpStatus.OK, r -> Mono.empty())
            .bodyToMono(Void.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> insertScriptContingencyList(UUID id, String content) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/script-contingency-lists?id={id}")
            .buildAndExpand(id)
            .toUriString();

        return webClient.post()
            .uri(actionsServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(content))
            .retrieve()
            .bodyToMono(Void.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> insertFiltersContingencyList(UUID id, String content) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/filters-contingency-lists?id={id}")
            .buildAndExpand(id)
            .toUriString();

        return webClient.post()
            .uri(actionsServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(content))
            .retrieve()
            .bodyToMono(Void.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> newScriptFromFiltersContingencyList(UUID id, String scriptName, UUID newId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/filters-contingency-lists/{id}/new-script/{scriptName}?newId={newId}")
            .buildAndExpand(id, scriptName, newId)
            .toUriString();

        return webClient.post()
            .uri(actionsServerBaseUri + path)
            .retrieve()
            .bodyToMono(Void.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> replaceFilterContingencyListWithScript(UUID id) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + ACTIONS_API_VERSION + "/filters-contingency-lists/{id}/replace-with-script")
            .buildAndExpand(id)
            .toUriString();

        return webClient.post()
            .uri(actionsServerBaseUri + path)
            .retrieve()
            .bodyToMono(Void.class)
            .publishOn(Schedulers.boundedElastic())
            .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }
}
