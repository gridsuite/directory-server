/*
Copyright (c) 2024, RTE (http://www.rte-france.com)
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package org.gridsuite.directory.server.services;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import lombok.Getter;
import lombok.NonNull;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.gridsuite.directory.server.elasticsearch.ESConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.gridsuite.directory.server.DirectoryService.DIRECTORY;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class DirectoryElementInfosService {

    private static final int PAGE_MAX_SIZE = 10;

    private final ElasticsearchOperations elasticsearchOperations;

    private static final String ELEMENT_NAME = "name.fullascii";
    static final String ELEMENT_TYPE = "type.keyword";

    @Value(ESConfig.DIRECTORY_ELEMENT_INFOS_INDEX_NAME)
    @Getter
    private String directoryElementsIndexName;

    public DirectoryElementInfosService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public List<DirectoryElementInfos> searchElements(@NonNull String userInput, String currentDirectoryUuid) {

        //we dont want to show the directories
        Query directory = TermQuery.of(m -> m
                .field(ELEMENT_TYPE)
                .value(DIRECTORY)
        )._toQuery();

        // This query is used to search for elements whose name contains the user input.
        Query elementNameContainSearchTerm = WildcardQuery.of(m -> m
                .field(ELEMENT_NAME)
                .wildcard("*" + escapeLucene(userInput) + "*")
        )._toQuery();

        // This query is used to search for elements whose name exactly matches the user input.
        Query exactMatchName = MatchQuery.of(m -> m
                .field(ELEMENT_NAME)
                .query(escapeLucene(userInput))
                .boost(4.0f)
        )._toQuery();

        // the element is in path
        Query fullPathQuery = MatchQuery.of(m -> m
                .field("fullPathUuid")
                .query("*" + currentDirectoryUuid + "*")
                .boost(1.0f)
        )._toQuery();

        // boost the result if the element is in the current search derictory
        Query parentIdQuery = MatchQuery.of(m -> m
                .field("parentId")
                .query(currentDirectoryUuid)
                .boost(1.0f)
        )._toQuery();

        BoolQuery query = new BoolQuery.Builder()
                .mustNot(directory)
                .must(elementNameContainSearchTerm) //if a doccument doesn’t match the must clause, it will be filtered out.
                .should(fullPathQuery, parentIdQuery, exactMatchName) // boost the query the document match
                .build();

        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(query._toQuery())
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .build();

        return elasticsearchOperations.search(nativeQuery, DirectoryElementInfos.class).stream().map(SearchHit::getContent).toList();
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


