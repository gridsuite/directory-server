package org.gridsuite.directory.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.logging.Level;

import static org.gridsuite.directory.server.DirectoryException.Type.FILTER_NOT_FOUND;
import static org.gridsuite.directory.server.DirectoryService.HEADER_USER_ID;

@Service
public class FilterService {
    private static final String ROOT_CATEGORY_REACTOR = "reactor.";

    private static final String FILTER_SERVER_API_VERSION = "v1";

    private static final String DELIMITER = "/";

    private final WebClient webClient;
    private String filterServerBaseUri;

    @Autowired
    public FilterService(@Value("${backing-services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri,
                                  WebClient.Builder webClientBuilder) {
        this.filterServerBaseUri = filterServerBaseUri;
        this.webClient = webClientBuilder.build();
    }

    public void setFilterServerBaseUri(String filterServerBaseUri) {
        this.filterServerBaseUri = filterServerBaseUri;
    }

    public Mono<Void> replaceFilterWithScript(UUID id) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/{id}/replace-with-script")
                .buildAndExpand(id)
                .toUriString();

        return webClient.put()
                .uri(filterServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(FILTER_NOT_FOUND)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> renameFilter(UUID filterId, String newName) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/rename/{id}/{newName}")
                .buildAndExpand(filterId, newName)
                .toUriString();

        return webClient.post()
                .uri(filterServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(FILTER_NOT_FOUND)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> insertNewScriptFromFilter(UUID id, String scriptName, UUID newId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/{id}/new-script/{scriptId}/{scriptName}")
                .buildAndExpand(id, newId, scriptName)
                .toUriString();

        return webClient.post()
                .uri(filterServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus == HttpStatus.NOT_FOUND, clientResponse -> Mono.error(new DirectoryException(FILTER_NOT_FOUND)))
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> deleteFilter(UUID id) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/{id}")
                .buildAndExpand(id)
                .toUriString();

        return webClient.delete()
                .uri(filterServerBaseUri + path)
                .retrieve()
                .onStatus(httpStatus -> httpStatus != HttpStatus.OK, r -> Mono.empty())
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

    public Mono<Void> insertFilter(String filter, UUID filterId, String userId) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/{id}")
                .buildAndExpand(filterId)
                .toUriString();

        return webClient.post()
                .uri(filterServerBaseUri + path)
                .header(HEADER_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(filter)
                .retrieve()
                .bodyToMono(Void.class)
                .publishOn(Schedulers.boundedElastic())
                .log(ROOT_CATEGORY_REACTOR, Level.FINE);
    }

}
