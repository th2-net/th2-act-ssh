/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.exactpro.th2.act.bootstrap;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;

public class ActServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActServer.class);
    private final Server server;

    public ActServer(Server server) throws IOException {
        this.server = server;
        this.server.start();
        LOGGER.info("'{}' started", ActServer.class.getSimpleName());
    }

    public void stop() throws InterruptedException {
        if (server.shutdown().awaitTermination(1, TimeUnit.SECONDS)) {
            LOGGER.warn("Server isn't stopped gracefully");
            server.shutdownNow();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
