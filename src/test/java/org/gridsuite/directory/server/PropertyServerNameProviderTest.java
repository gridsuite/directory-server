package org.gridsuite.directory.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyServerNameProviderTest {

    @Test
    void returnsProvidedName() {
        PropertyServerNameProvider provider = new PropertyServerNameProvider("custom-server");
        assertThat(provider.serverName()).isEqualTo("custom-server");
    }
}
