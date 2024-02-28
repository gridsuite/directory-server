/*
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.directory.server.dto.elasticsearch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@ToString
@EqualsAndHashCode
@Schema(description = "Directory element infos")
@Document(indexName = "#{@environment.getProperty('powsybl-ws.elasticsearch.index.prefix')}directory-elements")
@Setting(settingPath = "elasticsearch_settings.json")
@TypeAlias(value = "DirectoryElementInfos")
public class DirectoryElementInfos {
    @Id
    @Field
    private String id;

    @MultiField(
            mainField = @Field(name = "name", type = FieldType.Text),
            otherFields = {
                @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
                @InnerField(suffix = "raw", type = FieldType.Keyword)
            }
            )
    private String name;

    @Field(type = FieldType.Text)
    private String parentId;

    @Field(type = FieldType.Text)
    private String type;

    @Field(type = FieldType.Text)
    private String owner;

    @Field(type = FieldType.Long)
    private Long subdirectoriesCount;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    LocalDateTime lastModificationDate;

    @Field(type = FieldType.Boolean)
    private Boolean isPrivate;
}
