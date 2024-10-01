/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.directory.server.services;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TimerService {
    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    private void postConstruct() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    @PreDestroy
    private void preDestroy() {
        scheduledExecutorService.shutdown();
    }

    public boolean doPause(int ms) {
        try {
            return scheduledExecutorService.schedule(() -> true, ms, TimeUnit.MILLISECONDS).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
