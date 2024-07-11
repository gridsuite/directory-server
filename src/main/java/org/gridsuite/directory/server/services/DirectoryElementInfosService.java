/*
Copyright (c) 2024, RTE (http://www.rte-france.com)
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package org.gridsuite.directory.server.services;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import lombok.Getter;
import lombok.NonNull;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.ESConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;
import static org.gridsuite.directory.server.elasticsearch.ESUtils.searchHitsToPage;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class DirectoryElementInfosService {

    private final ElasticsearchOperations elasticsearchOperations;

    private static final String ELEMENT_NAME = "name.fullascii";
    private static final String FULL_PATH_UUID = "fullPathUuid.keyword";
    private static final String PARENT_ID = "parentId.keyword";
    static final String ELEMENT_TYPE = "type.keyword";

    @Value(ESConfig.DIRECTORY_ELEMENT_INFOS_INDEX_NAME)
    @Getter
    private String directoryElementsIndexName;

    public DirectoryElementInfosService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public Page<DirectoryElementInfos> searchElements(@NonNull String userInput, String currentDirectoryUuid, Pageable pageable) {
        float defaultBoostValue = 1.0f;

        // We don't want to show the directories
        Query directoryQuery = Queries.termQuery(ELEMENT_TYPE, DIRECTORY)._toQuery();

        // The documents whose name contains the user input
        Query matchNameWilcardQuery = Queries.wildcardQuery(ELEMENT_NAME, "*" + escapeLucene(userInput) + "*")._toQuery();

        // The document is in path
        Query fullPathQuery = TermQuery.of(m -> m
                .field(FULL_PATH_UUID)
                .value(currentDirectoryUuid)
                .boost(defaultBoostValue)
        )._toQuery();

        // The document is in the current search directory
        Query parentIdQuery = MatchQuery.of(m -> m
                .field(PARENT_ID)
                .query(currentDirectoryUuid)
                .boost(defaultBoostValue)
        )._toQuery();

        // All queries with default default value
        List<Query> queriesWithDefaultBoostValue = List.of(parentIdQuery, fullPathQuery);

        // The documents whose name exactly matches the user input
        // If parentIdQuery match then fullPathQuery will also match
        // So exactMatchNameQuery defaultBoostValue value need to be greater than the two others
        Query exactMatchNameQuery = MatchQuery.of(m -> m
                .field(ELEMENT_NAME)
                .query(userInput)
                .boost(2 * defaultBoostValue * queriesWithDefaultBoostValue.size())
        )._toQuery();

        BoolQuery query = new BoolQuery.Builder()
                .mustNot(directoryQuery)
                .must(matchNameWilcardQuery)
                .should(queriesWithDefaultBoostValue) // All queries with default default value
                .should(exactMatchNameQuery)
                .build();
        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(query._toQuery())
                .withPageable(pageable)
                .build();

        return searchHitsToPage(
            elasticsearchOperations.search(nativeQuery, DirectoryElementInfos.class),
            pageable
        );
    }

    public static String escapeLucene(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);

        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if ("+\\-!()^[]\"{}~*?|&/ ".indexOf(c) != -1) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}


