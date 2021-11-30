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

package com.exactpro.th2.act.ssh

import com.exactpro.th2.act.ssh.cfg.CommandExecution
import com.exactpro.th2.act.ssh.cfg.ConnectionParameters
import com.exactpro.th2.act.ssh.cfg.EndpointParameters
import com.exactpro.th2.act.ssh.cfg.Execution
import com.exactpro.th2.act.ssh.cfg.ScriptExecution
import com.exactpro.th2.act.ssh.messages.MessagePublisher
import com.exactpro.th2.common.grpc.MessageID
import mu.KotlinLogging
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.lookup.StringLookupFactory
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.util.io.NullOutputStream
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.mina.MinaServiceFactoryFactory
import org.apache.sshd.scp.client.ScpClientCreator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.rmi.RemoteException
import java.time.Duration
import java.util.EnumSet

class SshService(
    private val configuration: ConnectionParameters,
    private val executions: List<Execution>,
    private val publisher: MessagePublisher,
) : AutoCloseable {

    private val sshClient: SshClient = ClientBuilder.builder()
        .serverKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE) // todo: should use certificates
        .build()

    init {
        sshClient.ioServiceFactoryFactory = MinaServiceFactoryFactory()
        val keyPathByAlias: Map<String, Path> = configuration.endpoints.asSequence()
            .filter { it.privateKeyPath != null }
            .associateBy({ it.alias }) { it.privateKeyPath!! }

        sshClient.keyIdentityProvider = AliasFileKeyPairProvider(keyPathByAlias)
        CoreModuleProperties.STOP_WAIT_TIME.set(sshClient, Duration.ofMillis(configuration.stopWaitTimeout))
        sshClient.start()
    }

    fun findEndpoint(alias: String? = null): EndpointParameters {
        if (alias == null) {
            require(configuration.endpoints.size == 1) {
                "Explicitly define the endpoint alias. More than one endpoint specified in act: ${configuration.endpoints.map { it.alias }}."
            }
            return configuration.endpoints.first()
        }
        return requireNotNull(configuration.endpoints.find { alias.equals(it.alias, ignoreCase = true) }) {
            "Unknown endpoint alias: '$alias'"
        }
    }

    @Throws(SocketTimeoutException::class, RemoteException::class)
    fun execute(alias: String, parameters: Map<String, String>, endpoint: EndpointParameters): ResultWrapper {
        val execution = findExecutionByAlias(alias)
        return when (execution) {
            is CommandExecution -> executeCommand(execution, parameters, endpoint)
            is ScriptExecution -> executeScript(execution, parameters, endpoint)
        }.let { result ->
            ResultWrapper(result, publishResult(result, execution, parameters, endpoint))
        }
    }

    @Throws(SocketTimeoutException::class, RemoteException::class)
    private fun executeScript(
        execution: ScriptExecution,
        parameters: Map<String, String>,
        endpoint: EndpointParameters
    ): ScriptResult {
        return startSession(endpoint) {
            val script: String? = if (execution.addScriptToReport) {
                downloadScript(execution.scriptPath)
            } else {
                null
            }
            val result = executeCommandInternal(execution, parameters)
            ScriptResult(
                scriptContent = script,
                commonResult = result
            )
        }
    }

    @Throws(SocketTimeoutException::class, RemoteException::class)
    private fun executeCommand(
        execution: CommandExecution,
        parameters: Map<String, String>,
        endpoint: EndpointParameters
    ): CommandResult {
        return startSession(endpoint) {
            val result = executeCommandInternal(execution, parameters)
            CommandResult(result)
        }
    }

    private fun ClientSession.executeCommandInternal(
        execution: Execution,
        parameters: Map<String, String>
    ): CommonExecutionResult {
        val command = getCommand(execution, parameters)
        LOGGER.debug { "Executing command $command" }
        val result = executeCommand(command, execution.addOutputToResponse, execution.executionTimeout, execution.interruptOnTimeout)
        LOGGER.debug { "Command executed with exit code: ${result.exitCode}" }
        return result
    }

    private fun publishResult(
        result: ExecutionResult,
        execution: Execution,
        parameters: Map<String, String>,
        endpoint: EndpointParameters,
    ): MessageID? = with(result.commonResult) {
        output?.let { publisher.publish(it, execution, parameters, endpoint.alias) }
    }

    override fun close() {
        runCatching {
            LOGGER.info { "Stopping ssh client" }
            sshClient.stop()
        }.onFailure { LOGGER.error(it) { "Cannot stop ssh client" } }
            .onSuccess { LOGGER.info { "Ssh client stopped" } }
    }

    @Throws(IOException::class)
    private fun ClientSession.downloadScript(scriptPath: String): String {
        val listener = ScriptTransferListener()
        return ScpClientCreator.instance().createScpClient(this, listener).run {
            ByteArrayOutputStream().apply {
                use {
                    download(scriptPath, it)
                }
                if (listener.exceptions.isNotEmpty()) {
                    throw RemoteException("Exception found during $scriptPath file transferring", listener.exceptions.firstOrNull()?.second)
                }
            }.toString(DEFAULT_CHARSET)
        }
    }

    @Throws(SocketTimeoutException::class, RemoteException::class)
    private fun ClientSession.executeCommand(command: String, addOutput: Boolean, timeout: Duration, interruptOnTimeout: Boolean): CommonExecutionResult {
        return ByteArrayOutputStream().use { err ->
            (if (addOutput) ByteArrayOutputStream() else NullOutputStream()).use { out ->
                val execChannel = createExecChannel(command)
                execChannel.use { channel ->
                    channel.err = err
                    channel.out = out
                    channel.isUsePty = true // This option is required to send SIGHUP signal to the attached process when the channel is closed
                    channel.open().verify(timeout)
                    val results: Set<ClientChannelEvent> = channel.waitFor(EnumSet.of(ClientChannelEvent.EXIT_STATUS, ClientChannelEvent.CLOSED), timeout)
                    if (!interruptOnTimeout && results.contains(ClientChannelEvent.TIMEOUT)) {
                        throw SocketTimeoutException("Cannot execute command $command for specified timeout: $timeout")
                    }
                }
                val exitStatus: Int? = if (interruptOnTimeout) {
                    null
                } else {
                    execChannel.exitStatus ?: throw RemoteException("No exit status returned for command: $command")
                }
                val charset = DEFAULT_CHARSET
                CommonExecutionResult(
                    executedCommand = command,
                    output = if (out is ByteArrayOutputStream) out.toString(charset) else null,
                    errOut = err.toString(charset),
                    exitCode = exitStatus
                )
            }
        }
    }

    private fun getCommand(execution: Execution, parameters: Map<String, String>): String {
        val stringLookup = StringLookupFactory.INSTANCE.mapStringLookup(execution.defaultParameters.toMutableMap().apply {
            putAll(parameters)
        })
        val substituter = StringSubstitutor(stringLookup)
            .setEnableUndefinedVariableException(true)
        return substituter.replace(execution.execution)
    }

    private inline fun <T : ExecutionResult> startSession(connectionParameters: EndpointParameters, block: ClientSession.() -> T): T {
        val session = sshClient.connect(connectionParameters.username, connectionParameters.host, connectionParameters.port)
            .verify(connectionParameters.connectionTimeout)
            .clientSession.apply {
                connectionParameters.password?.also {
                    addPasswordIdentity(it)
                }
                setAttribute(AliasFileKeyPairProvider.ALIAS_ATTRIBUTE, connectionParameters.alias)
                auth().verify(connectionParameters.authTimeout)
            }
        return session.use(block)
    }

    private fun findExecutionByAlias(alias: String): Execution {
        require(alias.isNotBlank()) { "Alias '$alias' must not be blank" }
        val execution = executions.firstOrNull { it.alias.equals(alias, ignoreCase = true) }
        return requireNotNull(execution) {
            "Unknown alias: $alias"
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }

        private val DEFAULT_CHARSET = Charsets.UTF_8
        private val Execution.executionTimeout: Duration
            get() = Duration.ofMillis(timeout)
    }
}
