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

package com.exactpro.th2.act.ssh.events

import com.exactpro.th2.act.ssh.grpc.ExecutionRequest
import com.exactpro.th2.common.event.bean.IRow
import com.exactpro.th2.common.event.bean.Table
import com.exactpro.th2.common.event.bean.builder.TableBuilder
import com.exactpro.th2.common.grpc.EventID
import com.fasterxml.jackson.annotation.JsonProperty

fun ExecutionRequest.getParentIdOrDefault(default: EventID): EventID {
    if (!hasEventInfo()) {
        return default
    }
    if (!eventInfo.hasParentEventId()) {
        return default
    }
    return eventInfo.parentEventId
}

fun ExecutionRequest.getDescriptionOrDefault(default: String): String {
    if (!hasEventInfo()) {
        return default
    }
    return eventInfo.description.ifBlank { default }
}

class ParametersRow(
    @get:JsonProperty("Name") val name: String,
    @get:JsonProperty("Value") val value: String
) : IRow

fun ExecutionRequest.createParametersTable(): Table {
    return TableBuilder<ParametersRow>().apply {
        parametersMap.forEach { (name, value) ->
            row(ParametersRow(name, value))
        }
    }.build()
}