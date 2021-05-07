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

import mu.KotlinLogging
import org.apache.sshd.common.session.Session
import org.apache.sshd.scp.common.ScpTransferEventListener
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CopyOnWriteArrayList

class ScriptTransferListener : ScpTransferEventListener {
    private val _exceptions: MutableList<Pair<Path, Throwable>> = CopyOnWriteArrayList()
    val exceptions: List<Pair<Path, Throwable>>
        get() = _exceptions

    override fun startFileEvent(
        session: Session,
        op: ScpTransferEventListener.FileOperation,
        file: Path,
        length: Long,
        perms: MutableSet<PosixFilePermission>
    ) {
        LOGGER.debug { "Transferring file $file ($op) for session ${session.localAddress}/${session.remoteAddress} with length: $length. Permissions: $perms" }
    }

    override fun endFileEvent(
        session: Session,
        op: ScpTransferEventListener.FileOperation,
        file: Path,
        length: Long,
        perms: MutableSet<PosixFilePermission>,
        thrown: Throwable?
    ) {
        val msg: () -> Any =
            { "Transferring file $file ($op) for session ${session.localAddress}/${session.remoteAddress} with length: $length finished. Permissions: $perms" }
        if (thrown == null) {
            LOGGER.debug(msg)
        } else {
            LOGGER.error(thrown, msg)
            _exceptions += file to thrown
        }
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { }
    }
}