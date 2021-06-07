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

import static com.exactpro.th2.common.metrics.CommonMetrics.setLiveness;
import static com.exactpro.th2.common.metrics.CommonMetrics.setReadiness;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.act.ssh.SshService;
import com.exactpro.th2.act.ssh.cfg.SshServiceConfiguration;
import com.exactpro.th2.act.ssh.grpc.ActHandler;
import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.event.Event.Status;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.schema.factory.CommonFactory;
import com.exactpro.th2.common.schema.grpc.router.GrpcRouter;
import com.exactpro.th2.common.schema.message.MessageRouter;

public class ActMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActMain.class);

    public static void main(String[] args) {
        Deque<AutoCloseable> resources = new ConcurrentLinkedDeque<>();
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        configureShutdownHook(resources, lock, condition);
        try {
            setLiveness(true);
            CommonFactory factory = CommonFactory.createFromArguments(args);
            resources.add(factory);

            GrpcRouter grpcRouter = factory.getGrpcRouter();
            resources.add(grpcRouter);
            MessageRouter<EventBatch> eventBatchRouter = factory.getEventBatchRouter();

            SshServiceConfiguration configuration = factory.getCustomConfiguration(SshServiceConfiguration.class);

            SshService sshService = new SshService(configuration.getConnection(), configuration.getExecutions());
            resources.add(sshService);

            String rootName = configuration.getReporting().getRootName();
            String rootEventName = rootName + "_" + Instant.now();
            Event rootEvent = Event.start()
                    .endTimestamp()
                    .name(rootEventName)
                    .type("Microservice")
                    .status(Status.PASSED);

            eventBatchRouter.send(EventBatch.newBuilder().addEvents(rootEvent.toProto(null)).build());

            EventID rootEventId = EventID.newBuilder().setId(rootEvent.getId()).build();
            ActHandler actHandler = new ActHandler(
                    sshService,
                    eventBatchRouter,
                    rootEventId,
                    configuration.getReporting()
            );
            ActServer actServer = new ActServer(grpcRouter.startServer(actHandler));
            resources.add(actServer::stop);
            setReadiness(true);
            LOGGER.info("Act started");
            awaitShutdown(lock, condition);
        } catch (InterruptedException e) {
            LOGGER.info("The main thread interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void awaitShutdown(ReentrantLock lock, Condition condition) throws InterruptedException {
        try {
            lock.lock();
            LOGGER.info("Wait shutdown");
            condition.await();
            LOGGER.info("App shutdown");
        } finally {
            lock.unlock();
        }
    }

    private static void configureShutdownHook(Deque<AutoCloseable> resources, ReentrantLock lock, Condition condition) {
        Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook") {
            @Override
            public void run() {
                LOGGER.info("Shutdown start");
                setReadiness(false);
                try {
                    lock.lock();
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }

                resources.descendingIterator().forEachRemaining(resource -> {
                    try {
                        resource.close();
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                });
                setLiveness(false);
                LOGGER.info("Shutdown end");
            }
        });
    }
}
