package org.gridsuite.directory.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@SuperBuilder
@AllArgsConstructor
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
    @Field("id")
    String id;

    @MultiField(
            mainField = @Field(name = "directoryElementName", type = FieldType.Text),
            otherFields = {
                @InnerField(suffix = "fullascii", type = FieldType.Keyword, normalizer = "fullascii"),
                @InnerField(suffix = "raw", type = FieldType.Keyword)
            }
            )
    String name;

    @Field("lastModificationDate")
    public LocalDateTime lastModificationDate;
}
