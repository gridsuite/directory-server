package org.gridsuite.directory.server;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryExceptionTest {

    @Test
    void staticFactoriesProduceExpectedMessages() {
        DirectoryException notification = DirectoryException.createNotificationUnknown("ARCHIVE");
        assertThat(notification.getMessage()).contains("ARCHIVE");
        assertThat(notification.getErrorCode()).contains(DirectoryBusinessErrorCode.DIRECTORY_NOTIFICATION_UNKNOWN);

        DirectoryException notFound = DirectoryException.createElementNotFound("Folder", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(notFound.getMessage()).contains("Folder");
        assertThat(notFound.getErrorCode()).contains(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NOT_FOUND);

        DirectoryException conflict = DirectoryException.createElementNameAlreadyExists("report");
        assertThat(conflict.getMessage()).contains("report");
        assertThat(conflict.getErrorCode()).contains(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_CONFLICT);

        DirectoryException formatted = DirectoryException.of(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_BLANK,
            "Element '%s' invalid", "x");
        assertThat(formatted.getMessage()).isEqualTo("Element 'x' invalid");
        assertThat(formatted.getErrorCode()).contains(DirectoryBusinessErrorCode.DIRECTORY_ELEMENT_NAME_BLANK);
    }

    @Test
    void remoteErrorIsExposedWhenProvided() {
        PowsyblWsProblemDetail remote = PowsyblWsProblemDetail.builder(HttpStatus.BAD_GATEWAY)
            .server("remote")
            .detail("Gateway failure")
            .timestamp(Instant.parse("2025-08-01T00:00:00Z"))
            .path("/remote")
            .build();

        DirectoryException exception = new DirectoryException(DirectoryBusinessErrorCode.DIRECTORY_REMOTE_ERROR,
            "wrapped",
            remote);

        assertThat(exception.getRemoteError()).contains(remote);
        assertThat(exception.getBusinessErrorCode()).contains(DirectoryBusinessErrorCode.DIRECTORY_REMOTE_ERROR);
    }
}
