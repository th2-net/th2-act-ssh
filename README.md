# th2 act ssh (1.1.0)

## Overview

Provides user with the ability to execute specified scripts or commands with the parameter he needs

## Custom resources for infra-mgr

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: act
spec:
  type: th2-act
  pins:
    - name: server
      connection-type: grpc
    - name: output
      connection-type: mq
      attributes:
        - "publish"
        - "store"
        - "raw"
        - "first"
  custom-config:
    connection:
      endpoints:
        - alias: "Conn1"
          host: host1
          username: username1
          password: pwd
          # Or you can use path to the private key instead
          # privateKeyPath: path/to/private/key
          port: 22
          connectionTimeout: 1000
          authTimeout: 1000
        - alias: "Conn2"
          host: host2
          username: username2
          privateKeyPath: path/to/private/key
          port: 22
          connectionTimeout: 1000
          authTimeout: 1000
      stopWaitTimeout: 10000
    reporting:
      rootName: YourActSsh
      addStackStraceForErrors: true
    messagePublication:
      enabled: false
      sessionAlias: "default-session-alias"
    executions:
      - type: command
        alias: YourCommand
        execution: "mkdir ${base_dir}/some_dir"
        addOutputToResponse: true
        timeout: 100
        defaultParameters:
          base_dir: dir
        messagePublication:
          enabled: true
          sessionAlias: "another-session-alias"
      - type: script
        alias: YouScript
        scriptPath: ~/script.sh
        options: "${option_A}"
        addScriptToReport: true
        addOutputToResponse: true
        timeout: 1000
        defaultParameters:
          option_A: "some_value"
    
```

#### Required pins

The act-ssh should have the following pins:

+ One for gRPC server with `connection-type`=**grpc** (required pin)
+ Pins for message publication with `connection-type`=**mq**. _Those pins are required only if you are using message publication_.
  The following attributes are required for those pins: `publish`, `raw`, `first`

### Connection

#### Endpoints
The list of endpoints to connect. The endpoint will be chosen according to the endpoint alias specified in the request.
If the act has only one endpoint you can omit the endpoint alias in the request. The endpoint will be chosen automatically.

##### alias (required)

The alias for to identify the endpoint

##### host (required)

The remote machine's address to connect.

##### username (required)

The user's name that will be used to connect to the machine via SSH.

##### password

The password that will be used for authentication (you can set it using environment variables `password: ${YOUR_ENV_VARIABLE}`).
**NOTE:** if you use this parameter the **privateKeyPath** should not be set.

##### privateKeyPath

The path to the private key that will be used for password-less authentication.
**NOTE:** if you use this parameter the **password** should not be set.

##### port

That port will be used to connect via SSH. The default value is 22.

##### connectionTimeout

The timeout to wait until the connection is established.

##### authTimeout

The timeout to wait until the authentication is finished.

### Reporting

#### rootName

The name of the root event in the report. This event will be used to store events that was triggered by request without parent event ID.

#### addStackStraceForErrors

If it is enabled the full stacktrace will be added to the event if an exception was thrown during execution.
Otherwise, the error will be added to the event in a short form (only error messages)

### Message publication

This block is used to configure the default behavior related to message publication (can be overridden for certain execution).
By default, if this block is missing the publication is disabled.

The content of the published message is the execution output.
The published message will have the following properties attached:
+ **act.ssh.execution-alias** - the information about the alias that was executed;
+ call parameters - the call parameters will be put into properties under **their names without changes**.

See the example of the message:
```
messages {
  metadata {
    id {
      connection_id {
        session_alias: "test-msg-alias"
      }
      sequence: 1637232067339937000
      direction: FIRST
    }
    properties {
      key: "act.ssh.execution-alias"
      value: "test-alias"
    }
    properties {
      key: "test-param"
      value: "value"
    }
  }
  body: "you command output"
}
```

#### enabled

Specifies if the publication is enabled or not. The default value is `false`

#### sessionAlias

The session alias that should be used for published messages. If the publication is disabled the parameter can be omitted.

### Executions

This block describes which actions are available for this act. It has two types of actions:
+ command
+ script

The main difference that the `script` type allows to add the content of the script to the report.

#### Common block

##### alias

The name that will be used to execute the action.

##### addOutputToResponse

If it is `true` the action output will be added to the response and to the report.

##### timeout

The timeout to wait until the action is finished.

##### interruptOnTimeout

If it is `true` the action will interrupt command on timeout using the SIGHUP signal otherwise action will be failed when command has not completed on timeout.

##### defaultParameters

The list default values of the parameters that should be used if the parameter was not specified in the execution request;

##### messagePublication

Configuration for message publication related to the particular execution. **Overrides the parameters from the root block**.
Please, see the message publication block description [here](#message-publication).

#### Command

##### execution

The command that should be executed. You can use the following syntax to specify the parameters `${parameter_name}`.

#### Script

##### scriptPath

The path to the script that should be executed. NOTE: it is always better to specify the full path instead of the relative one.

##### options

The options that will be added to the script. As the result the following command will be executed `${scriptPath} ${options}`

## Release Notes

### 1.1.0

#### Changed:

+ Update common version from 3.13.4 to 3.29.1
+ Correct responses on gRPC request when an error is occurred during execution
+ Add ability to send command output as a raw message

### 1.0.1

+ Add description from the request to the event with the result

### 1.0.0

+ Up major version to separate common V2 and V3 versions

### 0.1.0

+ Added the interruptOnTimeout option for execution block

### 0.0.3

+ Use pty option to send SIGHUP signal to the attached process when the channel is closed

### 0.0.2

+ The ability to connect using private key
+ Aliases for endpoints to connect

### 0.0.1

+ First version.
