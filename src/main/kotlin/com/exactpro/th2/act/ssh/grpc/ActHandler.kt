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
package com.exactpro.th2.act.ssh.grpc

import com.exactpro.th2.act.ssh.CommandResult
import com.exactpro.th2.act.ssh.CommonExecutionResult
import com.exactpro.th2.act.ssh.ExecutionResult
import com.exactpro.th2.act.ssh.ScriptResult
import com.exactpro.th2.act.ssh.SshService
import com.exactpro.th2.act.ssh.cfg.ReportingConfiguration
import com.exactpro.th2.act.ssh.events.createParametersTable
import com.exactpro.th2.act.ssh.events.getDescriptionOrDefault
import com.exactpro.th2.act.ssh.events.getParentIdOrDefault
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.EventUtils.createMessageBean
import com.exactpro.th2.common.event.IBodyData
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RequestStatus
import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.common.schema.message.MessageRouter
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import java.time.Duration
import java.time.Instant

class ActHandler(
    private val service: SshService,
    private val eventRouter: MessageRouter<EventBatch>,
    private val rootEvent: EventID,
    private val reporting: ReportingConfiguration
) : ActSshGrpc.ActSshImplBase() {

    override fun execute(request: ExecutionRequest, responseObserver: StreamObserver<ExecutionResponse>) {
        LOGGER.debug { "Start processing request ${request.toJson()}" }
        val timeOfStart = Instant.now()
        try {
            val endpointAlias: String? = request.endpointAlias.ifBlank { null }
            val endpoint = service.findEndpoint(endpointAlias)
            val (result: ExecutionResult, messageId: MessageID?) = service.execute(request.executionAlias, request.parametersMap, endpoint)
            val response: ExecutionResponse = result.commonResult.toExecutionResponse()
            responseObserver.onNext(response)
            reportExecution(request, result, timeOfStart, messageId)
            responseObserver.onCompleted()
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot process request ${request.toJson()}" }
            reportError(request, ex)
            responseObserver.onErrorWithStatus(ex, Status.INTERNAL)
        } finally {
            LOGGER.debug { "Processing finished in ${Duration.between(timeOfStart, Instant.now())} for request ${request.toJson()}" }
        }
    }

    private fun StreamObserver<ExecutionResponse>.onErrorWithStatus(ex: Exception, status: Status) {
        onError(status.withDescription(ExceptionUtils.getRootCauseMessage(ex) ?: ex.toString()).asRuntimeException())
    }

    private fun reportExecution(
        request: ExecutionRequest,
        result: ExecutionResult,
        timeOfStart: Instant,
        messageId: MessageID?
    ) {
        try {
            val commonResult = result.commonResult
            val executionEvent = Event.from(timeOfStart)
                .endTimestamp()
                .name(request.getDescriptionOrDefault("Execution result for ${commonResult.executedCommand}"))
                .type(typeFor(result))
                .status(if (commonResult.isSuccess()) Event.Status.PASSED else Event.Status.FAILED)
                .bodyData(createMessageBean("Command: ${commonResult.executedCommand}"))
                .addParameters(request)
                .bodyData(createMessageBean("Output: ${commonResult.output ?: "<output disabled for command>"}"))
                .bodyData(createMessageBean("Error output: ${commonResult.errOut}"))
                .apply {
                    messageId?.let { messageID(it) }
                    val bodyParts: List<IBodyData> = result.toEventBodyParts()
                    bodyParts.forEach { bodyData(it) }
                }
            eventRouter.storeSingle(executionEvent, request.getParentIdOrDefault(rootEvent))
        } catch (ex: Exception) {
            LOGGER.error(ex) {
                val commonResult = result.commonResult
                with(commonResult) {
                    "Cannot report execution of $executedCommand (${result::class}) with status: $exitCode and err output: $errOut"
                }
            }
        }
    }

    private fun reportError(
        request: ExecutionRequest,
        ex: Exception
    ) {
        try {
            val error = Event.start()
                .endTimestamp()
                .name("${request.executionAlias} call failed")
                .type("ExecutionError")
                .status(Event.Status.FAILED)
                .addParameters(request)
                .addException(ex, reporting.addStackStraceForErrors)
            eventRouter.storeSingle(error, request.getParentIdOrDefault(rootEvent))
        } catch (ex: Exception) {
            LOGGER.error(ex) { "Cannot report error ${ex.message}" }
        }
    }

    companion object {

        @JvmStatic
        private fun Event.addParameters(request: ExecutionRequest): Event = apply {
            apply {
                if (request.parametersCount > 0) {
                    bodyData(createMessageBean("Parameters:"))
                    bodyData(request.createParametersTable())
                }
            }
        }

        @JvmStatic
        private fun typeFor(result: ExecutionResult): String {
            return when (result) {
                is CommandResult -> "CommandExecution"
                is ScriptResult -> "ScriptExecution"
            }
        }

        @JvmStatic
        private fun MessageRouter<EventBatch>.storeSingle(event: Event, parent: EventID) {
            send(EventBatch.newBuilder()
                .addEvents(event.toProto(parent))
                .build())
        }

        @JvmStatic
        private fun Event.addException(ex: Exception, full: Boolean): Event = apply {
            if (full) {
                bodyData(createMessageBean(ExceptionUtils.getStackTrace(ex)))
                return@apply
            }
            ExceptionUtils.getThrowableList(ex).forEach {
                bodyData(createMessageBean(it.message))
            }
        }

        @JvmStatic
        private fun CommonExecutionResult.toExecutionResponse(): ExecutionResponse {
            return ExecutionResponse.newBuilder()
                .setInterupted(isInterrupted())
                .setStatus(
                    RequestStatus.newBuilder()
                        .apply {
                            status = if (isSuccess()) {
                                RequestStatus.Status.SUCCESS
                            } else {
                                RequestStatus.Status.ERROR
                            }
                            message = "Exit code: $exitCode. ErrOut: $errOut"
                        }

                ).also {
                    if (output == null) {
                        it.empty = Empty.getDefaultInstance()
                    } else {
                        it.output = output
                    }
                    if (exitCode == null) {
                        it.unknownExitCode = Empty.getDefaultInstance()
                    } else {
                        it.exitCode = exitCode
                    }
                }.build()
        }

        @JvmStatic
        private fun ExecutionResult.toEventBodyParts(): List<IBodyData> {
            return when (this) {
                is CommandResult -> emptyList()
                is ScriptResult -> listOf(createMessageBean(scriptContent ?: "<script content is disabled>"))
            }
        }

        private val LOGGER = KotlinLogging.logger { }
    }
}
