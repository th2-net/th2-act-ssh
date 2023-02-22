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

import com.exactpro.th2.common.schema.configuration.ConfigurationManager
import com.exactpro.th2.common.schema.dictionary.DictionaryType
import com.exactpro.th2.common.schema.factory.AbstractCommonFactory
import com.exactpro.th2.common.schema.factory.extensions.getCustomConfiguration
import java.io.InputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import strikt.assertions.single
import java.nio.file.Files
import java.nio.file.Path

class TestSshServiceConfiguration {
    @Test
    fun deserialization(@TempDir dir: Path) {
        val data = """
        {
          "connection": {
            "endpoints": [
                {
                    "alias": "first",
                    "host": "host",
                    "username": "username",
                    "password": "pwd",
                    "port": 2222,
                    "connectionTimeout": 1000,
                    "authTimeout": 1000
                }
            ],
            "stopWaitTimeout": 10000
          },
          "reporting": {
            "rootName": "YourActSsh",
            "addStackStraceForErrors": true
          },
          "executions": [
            {
              "type": "command",
              "alias": "YourCommand",
              "execution": "mkdir ${"$"}{base_dir}/some_dir",
              "addOutputToResponse": true,
              "timeout": 100,
              "defaultParameters": {
                "base_dir": "dir"
              }
            },
            {
              "type": "script",
              "alias": "YouScript",
              "scriptPath": "~/script.sh",
              "options": "${"$"}{option_A}",
              "addScriptToReport": true,
              "addOutputToResponse": true,
              "timeout": 1000,
              "defaultParameters": {
                "option_A": "some_value"
              }
            }
          ]
        }""".trimIndent()

        val customCfg = dir.resolve("custom.json")
        Files.writeString(customCfg, data)

        val cfg: SshServiceConfiguration = loadConfiguration(customCfg)

        expectThat(cfg) {
            get { connection }.get { endpoints }
                .single().apply {
                    get { alias }.isEqualTo("first")
                    get { host }.isEqualTo("host")
                    get { password }.isEqualTo("pwd")
                    get { privateKeyPath }.isNull()
                    get { port }.isEqualTo(2222)
                }
            get { reporting }.apply {
                get { rootName }.isEqualTo("YourActSsh")
            }
            get { executions }.hasSize(2)
                .apply {
                    get(0).isA<CommandExecution>().apply {
                        get { alias }.isEqualTo("YourCommand")
                        get { execution }.isEqualTo("mkdir \${base_dir}/some_dir")
                        get { defaultParameters }.hasSize(1)["base_dir"].isEqualTo("dir")
                    }
                    get(1).isA<ScriptExecution>().apply {
                        get { alias }.isEqualTo("YouScript")
                        get { scriptPath }.isEqualTo("~/script.sh")
                        get { execution }.isEqualTo("~/script.sh \${option_A}")
                        get { defaultParameters }.hasSize(1)["option_A"].isEqualTo("some_value")
                    }
                }
        }
    }

    @Test
    fun `deserialization with private key`(@TempDir dir: Path) {
        val data = """
        {
          "connection": {
            "endpoints": [
                {
                    "alias": "first",
                    "host": "host",
                    "username": "username",
                    "privateKeyPath": "path/to/private/key",
                    "port": 2222,
                    "connectionTimeout": 1000,
                    "authTimeout": 1000
                }
            ],
            "stopWaitTimeout": 10000
          },
          "reporting": {
            "rootName": "YourActSsh",
            "addStackStraceForErrors": true
          },
          "executions": [
            {
              "type": "command",
              "alias": "YourCommand",
              "execution": "mkdir ${"$"}{base_dir}/some_dir",
              "addOutputToResponse": true,
              "timeout": 100,
              "defaultParameters": {
                "base_dir": "dir"
              }
            },
            {
              "type": "script",
              "alias": "YouScript",
              "scriptPath": "~/script.sh",
              "options": "${"$"}{option_A}",
              "addScriptToReport": true,
              "addOutputToResponse": true,
              "timeout": 1000,
              "defaultParameters": {
                "option_A": "some_value"
              }
            }
          ]
        }""".trimIndent()

        val customCfg = dir.resolve("custom.json")
        Files.writeString(customCfg, data)

        val cfg: SshServiceConfiguration = loadConfiguration(customCfg)

        expectThat(cfg) {
            get { connection }.get { endpoints }
                .single().apply {
                    get { alias }.isEqualTo("first")
                    get { host }.isEqualTo("host")
                    get { password }.isNull()
                    get { privateKeyPath }.isEqualTo(Path.of("path/to/private/key"))
                    get { port }.isEqualTo(2222)
                }
            get { reporting }.apply {
                get { rootName }.isEqualTo("YourActSsh")
            }
            get { executions }.hasSize(2)
                .apply {
                    get(0).isA<CommandExecution>().apply {
                        get { alias }.isEqualTo("YourCommand")
                        get { execution }.isEqualTo("mkdir \${base_dir}/some_dir")
                        get { defaultParameters }.hasSize(1)["base_dir"].isEqualTo("dir")
                    }
                    get(1).isA<ScriptExecution>().apply {
                        get { alias }.isEqualTo("YouScript")
                        get { scriptPath }.isEqualTo("~/script.sh")
                        get { execution }.isEqualTo("~/script.sh \${option_A}")
                        get { defaultParameters }.hasSize(1)["option_A"].isEqualTo("some_value")
                    }
                }
        }
    }

    private inline fun <reified T> loadConfiguration(customCfg: Path): T {
        val factory = object : AbstractCommonFactory() {
            override fun getConfigurationManager(): ConfigurationManager {
                return ConfigurationManager(emptyMap())
            }

            override fun getPathToCustomConfiguration(): Path {
                return customCfg
            }

            override fun getPathToDictionaryTypesDir(): Path { TODO("Not yet implemented") }

            override fun getPathToDictionaryAliasesDir(): Path { TODO("Not yet implemented") }

            override fun getOldPathToDictionariesDir(): Path { TODO("Not yet implemented") }

            override fun loadSingleDictionary(): InputStream { TODO("Not yet implemented") }

            override fun getDictionaryAliases(): MutableSet<String> { TODO("Not yet implemented") }

            override fun loadDictionary(alias: String?): InputStream { TODO("Not yet implemented") }

            override fun readDictionary(): InputStream { TODO("Not yet implemented") }

            override fun readDictionary(dictionaryType: DictionaryType?): InputStream { TODO("Not yet implemented") }
        }

        return factory.getCustomConfiguration()
    }
}