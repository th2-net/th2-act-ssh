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

import com.exactpro.th2.act.ssh.cfg.CommandExecution
import com.exactpro.th2.act.ssh.cfg.PublicationConfiguration
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.RawMessageBatch
import com.exactpro.th2.common.schema.message.MessageRouter
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyVararg
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.single

class TestMessagePublisher {
    private val router: MessageRouter<RawMessageBatch> = mock { }

    @Nested
    inner class WithoutDefaultConfiguration {
        private val publisher = MessagePublisher(router, PublicationConfiguration())

        @Test
        fun `publishes output when enabled`() {
            val messageID = publisher.publish("test", createExecution("test-alias"), mapOf("test-param" to "value"), "test-msg-alias")

            val captor = argumentCaptor<RawMessageBatch>()
            verify(router).sendAll(captor.capture(), anyVararg())
            expect {
                that(messageID).isNotNull().apply {
                    get { connectionId }.get { sessionAlias }.isEqualTo("test-msg-alias")
                    get { direction }.isEqualTo(Direction.FIRST)
                    get { sequence }.isGreaterThan(0)
                }
                that(captor.firstValue)
                    .get { messagesList }
                    .single()
                    .apply {
                        get { metadata }.apply {
                            get { id }.apply {
                                get { direction }.isEqualTo(Direction.FIRST)
                                get { sequence }.isNotEqualTo(0)
                                get { connectionId }.get { sessionAlias }.isEqualTo("test-msg-alias")
                            }
                            get { timestamp }.apply {
                                get { seconds }.isGreaterThan(0)
                                get { nanos }.isGreaterThan(0)
                            }
                            get { propertiesMap }.isEqualTo(mapOf(
                                "act.ssh.execution-alias" to "test-alias",
                                "test-param" to "value"
                            ))
                        }
                        get { body.toString(Charsets.UTF_8) }.isEqualTo("test")
                    }
            }
        }

        @Test
        fun `does not publish if no publication parameters set`() {
            publisher.publish("test", createExecution("test-alias", enabled = null), mapOf("test-param" to "value"), "test-msg-alias")

            verify(router, never()).sendAll(any(), anyVararg())
        }

        @Test
        fun `does not publish if no publication is disabled`() {
            publisher.publish("test", createExecution("test-alias", enabled = false), mapOf("test-param" to "value"), "test-msg-alias")

            verify(router, never()).sendAll(any(), anyVararg())
        }
    }

    @Nested
    inner class WithDefaultConfiguration {
        private val publisher = MessagePublisher(router, PublicationConfiguration(
            enabled = true,
        ))

        @Test
        fun `publishes output when enabled`() {
            val messageID = publisher.publish("test", createExecution("test-alias"), mapOf("test-param" to "value"), "test-msg-alias")

            val captor = argumentCaptor<RawMessageBatch>()
            verify(router).sendAll(captor.capture(), anyVararg())
            expect {
                that(messageID).isNotNull().apply {
                    get { connectionId }.get { sessionAlias }.isEqualTo("test-msg-alias")
                    get { direction }.isEqualTo(Direction.FIRST)
                    get { sequence }.isGreaterThan(0)
                }
                that(captor.firstValue)
                    .get { messagesList }
                    .single()
                    .apply {
                        get { metadata }.apply {
                            get { id }.apply {
                                get { direction }.isEqualTo(Direction.FIRST)
                                get { sequence }.isNotEqualTo(0)
                                get { connectionId }.get { sessionAlias }.isEqualTo("test-msg-alias")
                            }
                            get { timestamp }.apply {
                                get { seconds }.isGreaterThan(0)
                                get { nanos }.isGreaterThan(0)
                            }
                            get { propertiesMap }.isEqualTo(mapOf(
                                "act.ssh.execution-alias" to "test-alias",
                                "test-param" to "value"
                            ))
                        }
                        get { body.toString(Charsets.UTF_8) }.isEqualTo("test")
                    }
            }
        }

        @Test
        fun `publishes with default parameters`() {
            val messageID = publisher.publish("test", createExecution("test-alias", enabled = null), mapOf("test-param" to "value"), "test-msg-alias")

            val captor = argumentCaptor<RawMessageBatch>()
            verify(router).sendAll(captor.capture(), anyVararg())
            expect {
                that(messageID).isNotNull().apply {
                    get { connectionId }.get { sessionAlias }.isEqualTo("test-msg-alias")
                    get { direction }.isEqualTo(Direction.FIRST)
                    get { sequence }.isGreaterThan(0)
                }
                that(captor.firstValue)
                    .get { messagesList }
                    .single()
                    .apply {
                        get { metadata }.apply {
                            get { id }.apply {
                                get { direction }.isEqualTo(Direction.FIRST)
                                get { sequence }.isNotEqualTo(0)
                                get { connectionId }.get { sessionAlias }.isEqualTo("test-msg-alias")
                            }
                            get { timestamp }.apply {
                                get { seconds }.isGreaterThan(0)
                                get { nanos }.isGreaterThan(0)
                            }
                            get { propertiesMap }.isEqualTo(mapOf(
                                "act.ssh.execution-alias" to "test-alias",
                                "test-param" to "value"
                            ))
                        }
                        get { body.toString(Charsets.UTF_8) }.isEqualTo("test")
                    }
            }
        }

        @Test
        fun `does not publish if no publication is disabled`() {
            publisher.publish("test", createExecution("test-alias", enabled = false), mapOf("test-param" to "value"), "test-msg-alias")

            verify(router, never()).sendAll(any(), anyVararg())
        }
    }

    private fun createExecution(commandAlias: String, enabled: Boolean? = true) = CommandExecution(
        alias = commandAlias,
        execution = "data",
        addOutputToResponse = true,
        timeout = 100,
        messagePublication = enabled?.let { PublicationConfiguration(it) }
    )
}