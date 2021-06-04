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
package com.exactpro.th2.act.ssh.cfg

import com.exactpro.th2.act.ssh.SshService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.net.SocketTimeoutException

@Disabled
class TestSshServiceManual {
    private val parameterName = "SLEEP_TIME"
    private val username = System.getenv("username")
    private val password = System.getenv("password")

    private val endpoint = EndpointParameters("test", "localhost", username, password, connectionTimeout = 5000, authTimeout = 5000)
    private val sshService = SshService(ConnectionParameters(
        listOf(endpoint)),
        listOf(CommandExecution("false", "echo \${$parameterName} && sleep \${$parameterName}", emptyMap(), true, 2_000, false),
            CommandExecution("true", "echo \${$parameterName} && sleep \${$parameterName}", emptyMap(), true, 2_000, true))
    )

    @Test
    fun valid() {
        val sleepTime = 1
        val result = sshService.execute("false", mapOf(parameterName to "$sleepTime"), endpoint)
        with(result.commonResult) {
            assertAll(
                { assertEquals(0, exitCode) },
                { assertEquals("$sleepTime\r\n", output) },
                { assertEquals("", errOut) }
            )
        }
    }

    @Test
    fun `timeout exceeded`() {
        val sleepTime = 3
        assertThrows(SocketTimeoutException::class.java) { sshService.execute("false", mapOf(parameterName to "$sleepTime"), endpoint) }
    }

    @Test
    fun interrupted() {
        val sleepTime = 3
        val result = sshService.execute("true", mapOf(parameterName to "$sleepTime"), endpoint)
        with(result.commonResult) {
            assertAll(
                { assertNull(exitCode) },
                { assertTrue(isInterrupted()) },
                { assertEquals("$sleepTime\r\n", output) },
                { assertEquals("", errOut) }
            )
        }
    }
}