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

import com.exactpro.th2.common.grpc.MessageID

data class ResultWrapper(val result: ExecutionResult, val messageID: MessageID?)

sealed class ExecutionResult(
    val commonResult: CommonExecutionResult
)

class CommonExecutionResult(
    val executedCommand: String,
    val output: String?,
    val errOut: String,
    val exitCode: Int?
) {
    fun isSuccess(): Boolean = (exitCode == 0 || isInterrupted()) && errOut.isEmpty()

    fun isInterrupted(): Boolean = exitCode == null
}

class CommandResult(
    commonResult: CommonExecutionResult
) : ExecutionResult(commonResult)

class ScriptResult(
    val scriptContent: String?,
    commonResult: CommonExecutionResult
) : ExecutionResult(commonResult)