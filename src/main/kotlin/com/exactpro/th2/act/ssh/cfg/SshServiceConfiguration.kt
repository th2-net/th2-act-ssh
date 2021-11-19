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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.nio.file.Path

class SshServiceConfiguration(
    val connection: ConnectionParameters,
    val executions: List<Execution>,
    val reporting: ReportingConfiguration = ReportingConfiguration(),
    val messagePublication: PublicationConfiguration = PublicationConfiguration(),
) {
    init {
        require(executions.isNotEmpty()) { "At least one alias must be set" }
        checkCollisions("Execution", executions, Execution::alias)
    }
}

class ReportingConfiguration(
    val rootName: String = "ActSsh",
    val addStackStraceForErrors: Boolean = true
)

class ConnectionParameters(
    val endpoints: List<EndpointParameters>,
    val stopWaitTimeout: Long = 10_000L
) {
    init {
        require(stopWaitTimeout > 0) { "Stop timeout must be greater that zero (0)" }
        checkCollisions("Endpoints", endpoints, EndpointParameters::alias)
    }
}

class EndpointParameters(
    val alias: String,
    val host: String,
    val username: String,
    val password: String? = null,
    val privateKeyPath: Path? = null,
    val port: Int = 22,
    val connectionTimeout: Long = 1000L,
    val authTimeout: Long = 1000L
) {
    init {
        require(alias.isNotBlank()) { "alias must not be blank" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(port > 0) { "port must be a positive integer but was $port" }
        require(privateKeyPath != null && password == null || password != null && privateKeyPath == null) {
            "Either privateKeyPath or password must be set"
        }
    }
}

class PublicationConfiguration(
    val enabled: Boolean = false,
    val sessionAlias: String = DEFAULT_SESSION_ALIAS
) {
    init {
        if (enabled) {
            check(sessionAlias != DEFAULT_SESSION_ALIAS) {
                "session_alias parameter must be overridden"
            }
        }
    }
    companion object {
        private const val DEFAULT_SESSION_ALIAS = "unknown"
    }
}

@JsonSubTypes(
    JsonSubTypes.Type(CommandExecution::class, name = "command"),
    JsonSubTypes.Type(ScriptExecution::class, name = "script")
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = CommandExecution::class
)
sealed class Execution(
    val alias: String,
    val execution: String,
    val defaultParameters: Map<String, String>,
    val addOutputToResponse: Boolean,
    val timeout: Long,
    val interruptOnTimeout: Boolean,
    val messagePublication: PublicationConfiguration?,
)

class CommandExecution(
    alias: String,
    execution: String,
    defaultParameters: Map<String, String> = emptyMap(),
    addOutputToResponse: Boolean = true,
    timeout: Long,
    interruptOnTimeout: Boolean = false,
    messagePublication: PublicationConfiguration? = null,
) : Execution(
    alias,
    execution,
    defaultParameters,
    addOutputToResponse,
    timeout,
    interruptOnTimeout,
    messagePublication,
)

class ScriptExecution(
    alias: String,
    val scriptPath: String,
    options: String = "",
    val addScriptToReport: Boolean = true,
    defaultParameters: Map<String, String> = emptyMap(),
    addOutputToResponse: Boolean = true,
    timeout: Long,
    interruptOnTimeout: Boolean = false,
    messagePublication: PublicationConfiguration?,
) : Execution(
    alias,
    if (options.isBlank()) scriptPath else "$scriptPath $options",
    defaultParameters,
    addOutputToResponse,
    timeout,
    interruptOnTimeout,
    messagePublication,
)

private fun <T> checkCollisions(name: String, executions: List<T>, aliasExtractor: T.() -> String) {
    val aliases = executions.map { it.aliasExtractor() }
    val collisions = aliases.filter { alias -> aliases.asSequence().filter { alias.equals(it, ignoreCase = true) }.take(2).toList().size > 1 }
    require(collisions.isEmpty()) {
        "$name aliases are case insensitive. Collisions found: $collisions"
    }
}