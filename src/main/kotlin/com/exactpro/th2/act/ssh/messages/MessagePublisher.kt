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

package com.exactpro.th2.act.ssh.messages

import com.exactpro.th2.act.ssh.cfg.Execution
import com.exactpro.th2.act.ssh.cfg.PublicationConfiguration
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.grpc.RawMessageBatch
import com.exactpro.th2.common.message.toTimestamp
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.common.schema.message.QueueAttribute
import com.google.protobuf.ByteString
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.ThreadSafe

@ThreadSafe
class MessagePublisher(
    private val router: MessageRouter<RawMessageBatch>,
    private val defaultConfiguration: PublicationConfiguration,
    private val bookName: String,
) {
    init {
        require(bookName.isNotBlank()) { "blank book name" }
    }
    private class SessionInfo {
        private var sequence: Long = Instant.now().run { epochSecond * NANOS_IN_SECONDS + nano }
        fun getAndIncrement(): Long = sequence++
    }

    private val sequencesByAlias: MutableMap<String, SessionInfo> = ConcurrentHashMap()

    fun publish(
        output: String,
        execution: Execution,
        parameters: Map<String, String>,
        alias: String
    ): MessageID? {
        val cfg = execution.messagePublication ?: defaultConfiguration
        if (!cfg.enabled) {
            LOGGER.info { "Skip output publication for command ${execution.alias}" }
            return null
        }
        val info = getInfoForAlias(alias)
        return runCatching {
            val builder = RawMessage.newBuilder()
                .setBody(ByteString.copyFrom(output, Charsets.UTF_8))
            synchronized(info) {
                val message = builder.fillMetadata(info, execution, parameters, alias).build()
                router.sendAll(RawMessageBatch.newBuilder().addMessages(message).build(), QueueAttribute.FIRST.value)
                message.metadata.id
            }
        }.onSuccess {
            LOGGER.info { "Output for command ${execution.alias} with parameters $parameters was published under session alias $alias" }
        }.onFailure {
            LOGGER.error(it) { "Cannot publish output for command ${execution.alias} with parameters $parameters" }
        }.getOrNull()
    }

    private fun RawMessage.Builder.fillMetadata(
        info: SessionInfo,
        execution: Execution,
        parameters: Map<String, String>,
        alias: String,
    ) = apply {
        metadataBuilder.apply {
            idBuilder.apply {
                bookName = this@MessagePublisher.bookName
                direction = Direction.FIRST
                sequence = info.getAndIncrement()
                connectionIdBuilder.sessionAlias = alias
                timestamp = Instant.now().toTimestamp()
            }
            putProperties(EXECUTION_ALIAS_PARAMETER, execution.alias)
            putAllProperties(parameters)
        }
    }

    private fun getInfoForAlias(alias: String): SessionInfo = sequencesByAlias.computeIfAbsent(alias) { SessionInfo() }

    companion object {
        private const val EXECUTION_ALIAS_PARAMETER = "act.ssh.execution-alias"
        private val NANOS_IN_SECONDS: Long = TimeUnit.SECONDS.toNanos(1)
        private val LOGGER = KotlinLogging.logger { }
    }
}