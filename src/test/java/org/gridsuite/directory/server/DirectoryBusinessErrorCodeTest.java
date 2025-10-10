package org.gridsuite.directory.server;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryBusinessErrorCodeTest {

    @ParameterizedTest
    @EnumSource(DirectoryBusinessErrorCode.class)
    void valueMatchesEnumName(DirectoryBusinessErrorCode code) {
        assertThat(code.value()).startsWith("directory.");
    }
}
