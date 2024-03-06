/*
Copyright (c) 2024, RTE (http://www.rte-france.com)
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package org.gridsuite.directory.server.services;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.google.common.collect.Lists;
import org.gridsuite.directory.server.dto.elasticsearch.DirectoryElementInfos;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.client.elc.Queries;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import java.util.List;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Service
public class DirectoryElementInfosService {

    private static final int PAGE_MAX_SIZE = 10;

    private final ElasticsearchOperations elasticsearchOperations;

    static final String ELEMENT_NAME = "name";

    public DirectoryElementInfosService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public List<DirectoryElementInfos> searchElements(@NonNull String userInput, String userId) {
        WildcardQuery elementSearchQuery = Queries.wildcardQuery(ELEMENT_NAME, "*" + escapeLucene(userInput) + "*");
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        boolQueryBuilder.mustNot(Queries.termQuery("type", "directory")._toQuery());
        BoolQuery isOwnerQuery = new BoolQuery.Builder()
                .filter(Queries.termQuery("isprivate", "true")._toQuery())
                .must(Queries.termQuery("owner", userId)._toQuery())
                .build();

        BoolQuery isPublicQuery = new BoolQuery.Builder()
                .filter(Queries.termQuery("isprivate", "false")._toQuery())
                .build();

        boolQueryBuilder.should(isOwnerQuery._toQuery());
        boolQueryBuilder.should(isPublicQuery._toQuery());
        boolQueryBuilder.filter(elementSearchQuery._toQuery());
        BoolQuery finalQuery = boolQueryBuilder.build();

        NativeQuery nativeQuery = new NativeQueryBuilder()
                .withQuery(finalQuery._toQuery())
                .withPageable(PageRequest.of(0, PAGE_MAX_SIZE))
                .build();

        return Lists.newArrayList(elasticsearchOperations.search(nativeQuery, DirectoryElementInfos.class)
                .map(SearchHit::getContent));
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


