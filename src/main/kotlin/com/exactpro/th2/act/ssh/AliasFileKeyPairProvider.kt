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
import org.apache.sshd.common.AttributeRepository
import org.apache.sshd.common.keyprovider.FileKeyPairProvider
import org.apache.sshd.common.session.SessionContext
import java.nio.file.Path
import java.security.KeyPair

class AliasFileKeyPairProvider(
    private val keyPathByAlias: Map<String, Path>,
) : FileKeyPairProvider() {

    override fun loadKeys(session: SessionContext?): Iterable<KeyPair> {
        if (session == null) {
            LOGGER.warn { "Session is null. Cannot load key without alias" }
            return emptyList()
        }
        val aliasAttribute: String? = session.getAttribute(ALIAS_ATTRIBUTE)
        if (aliasAttribute == null) {
            LOGGER.warn { "Session $session does not have alias attribute. Cannot load key without alias" }
            return emptyList()
        }
        return keyPathByAlias[aliasAttribute]?.let { super.loadKeys(session, listOf(it)) } ?: run {
            LOGGER.debug { "Private key is not specified for alias $aliasAttribute" }
            emptyList()
        }
    }

    companion object {
        @JvmField
        val ALIAS_ATTRIBUTE = AttributeRepository.AttributeKey<String>()

        private val LOGGER = KotlinLogging.logger { }
    }
}