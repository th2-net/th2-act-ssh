# th2 act ssh (0.0.1)

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
  custom-config:
    connection:
      host: host
      username: username
      password: pwd
      port: 22
      connectionTimeout: 1000
      authTimeout: 1000
      stopWaitTimeout: 10000
    reporting:
      rootName: YourActSsh
      addStackStraceForErrors: true
    executions:
      - type: command
        alias: YourCommand
        execution: "mkdir ${base_dir}/some_dir"
        addOutputToResponse: true
        timeout: 100
        defaultParameters:
          base_dir: dir
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

### Connection

#### host

The remote machine's address to connect.

#### username

The user's name that will be used to connect to the machine via SSH.

#### password

The password that will be used for authentication (you can set it using environment variables `password: ${YOUR_ENV_VARIABLE}`).

#### port

That port will be used to connect via SSH. The default value is 22.

#### connectionTimeout

The timeout to wait until the connection is established.

#### authTimeout

The timeout to wait until the authentication is finished.

### Reporting

#### rootName

The name of the root event in the report. This event will be used to store events that was triggered by request without parent event ID.

#### addStackStraceForErrors

If it is enabled the full stacktrace will be added to the event if an exception was thrown during execution.
Otherwise, the error will be added to the event in a short form (only error messages)

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

##### defaultParameters

The list default values of the parameters that should be used if the parameter was not specified in the execution request;

#### Command

##### execution

The command that should be executed. You can use the following syntax to specify the parameters `${parameter_name}`.

#### Script

##### scriptPath

The path to the script that should be executed. NOTE: it is always better to specify the full path instead of the relative one.

##### options

The options that will be added to the script. As the result the following command will be executed `${scriptPath} ${options}`

## Release Notes

### 0.0.1

+ First version.