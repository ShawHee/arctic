<!--
 - Licensed to the Apache Software Foundation (ASF) under one
 - or more contributor license agreements.  See the NOTICE file
 - distributed with this work for additional information
 - regarding copyright ownership.  The ASF licenses this file
 - to you under the Apache License, Version 2.0 (the
 - "License"); you may not use this file except in compliance
 - with the License.  You may obtain a copy of the License at
 - 
 -     http://www.apache.org/licenses/LICENSE-2.0
 - 
 - Unless required by applicable law or agreed to in writing, software
 - distributed under the License is distributed on an "AS IS" BASIS,
 - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 - See the License for the specific language governing permissions and 
 - limitations under the License.
-->

# Contributing

This document provides guidelines for contributing to Amoro. While these suggestions are not strict
rules, they aim to facilitate a smooth contribution experience.

## Issues

Regardless of the type of contribution you plan to make, it is recommended that you create an issue
to track it.

* Before creating an issue, please search within the issues to see if a similar one has already been
  reported.
* Choose the appropriate type:
    * Feature: A new feature to be added.
    * Improvement: Enhancement of an existing feature, including code quality, performance, user
      experience, etc.
    * Bug: A problem that prevents the project from functioning as intended.
    * Subtask: A subtask of a Feature/Improvement that can be broken down into smaller steps.

## Pull requests

Pull requests are the preferred mechanism for contributing to Amoro

### Contributor License Agreement
Before your contribution, To get your PR merged, you must submit [Amoro's Contributor License Agreement (CLA) ](https://cla-assistant.io/apache/amoro)first. You only need to submit it ONCE.

* Generally, create a PR only to the master branch.
* PR should be linked to the corresponding issue.
    * The PR title format should be: \[AMORO-{issue_number}\]\[{module}\]{pr_description}.
    * Add fix/resolve #{issue_number} in the description to link the PR to the issue.
    * The linked issue should clearly explain the background, objectives, and implementation methods
      of the PR.
* The change log in the PR should clearly describe the changes made in modules, classes, methods,
  etc.
* The PR should include corresponding testing methods, and the test results should be visible.
* If the PR involves new features, the user document should include instructions for its usage.

## Code review

Code review is a crucial aspect of contributing to a project, and all contributors are encouraged to
actively review and provide feedback on each other's PRs.

* Check whether the PR meet the requirements specified in the previous section on Pull Requests.
* Review each file changed by the PR, and consider the following aspects:
    * Is the java doc complete?
    * Is there new unit or integration test coverage for the code changes?
    * Does the user document explain how to use new features?
    * Are there comments to aid in understanding complex logic?
    * Have any duplicate classes or methods been introduced?
* Track feedback on suggestions and their resolution.
* If a suggestion is resolved, please close it.
* If all suggestions are resolved or there are no suggestions, approve it.

## Design document

Write down your implementation plan and discuss it with other developers in the community before you
start coding officially. If it is just a small change, describe the implementation steps clearly in
the Issue. If it is a relatively large work, it is recommended to write a design document for this
feature. Here is
a [design document template](https://docs.google.com/document/d/1LeTyrlzQJfSs2DkRBsucK_vV5gtHRYLb1KSrpu0hp3g/edit?usp=sharing)
for reference.

## Building the Project Locally

Amoro is built using Maven with Java 1.8 and Java 17(only for `amoro-mixed-format/amoro-mixed-format-trino` module).

* To build Trino module need config `toolchains.xml` in `${user.home}/.m2/` dir, the content is

```
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
    <toolchain>
        <type>jdk</type>
        <provides>
            <version>17</version>
            <vendor>sun</vendor>
        </provides>
        <configuration>
            <jdkHome>${YourJDK17Home}</jdkHome>
        </configuration>
    </toolchain>
</toolchains>
```

* To invoke a build and run tests: `mvn package -P toolchain`
* To skip tests: `mvn -DskipTests package -P toolchain`
* To package without trino module and JAVA 17 dependency: `mvn clean package -DskipTests -pl '!amoro-mixed-format/amoro-mixed-format-trino'`
* To build with hadoop 2.x(the default is 3.x) `mvn clean package -DskipTests -Dhadoop=v2`
* To indicate Flink version for optimizer (the default is 1.18.1): `mvn clean package -Dflink-optimizer.flink-version=1.15.4`. If the version of Flink is below 1.15.0, you also need to add the `-Pflink-pre-1.15` parameter: `mvn clean package -Pflink-pre-1.15 -Dflink-optimizer.flink-version=1.14.6`.
  `mvn clean package -Pflink-pre-1.15 -Dflink-optimizer.flink-version=1.14.6 -DskipTests`

>Spotless is skipped by default in `trino` module. So if you want to perform checkstyle when building `trino` module, you must be in a Java 17 environment.

* To invoke a build include `amoro-mixed-format/amoro-mixed-format-trino` module in Java 17 environment: `mvn clean package -DskipTests -P trino-spotless`
* To only build `amoro-mixed-format/amoro-mixed-format-trino` and its dependent modules in Java 17 environment: `mvn clean package -DskipTests -P trino-spotless -pl 'amoro-mixed-format/amoro-mixed-format-trino' -am`

## Code suggestions

### Code formatting

Amoro uses [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-maven) together with
[google-java-format](https://github.com/google/google-java-format) to format the Java code. For
Scala, it uses Spotless with [scalafmt](https://scalameta.org/scalafmt/).

You can format your code by executing the command `mvn spotless:apply` in the root directory of
project.

Or you can configure your IDEA to automatically format your code. Then you will need to install
the [google-java-format](https://github.com/google/google-java-format) plugin. However, a specific
version of this plugin is required.
Download [google-java-format v1.7.0.6](https://plugins.jetbrains.com/plugin/8527-google-java-format/versions/stable/115957)
and install it as follows. Make sure to never update this plugin.

1. Go to “Settings/Preferences” → “Plugins”.
2. Click the gear icon and select “Install Plugin from Disk”.
3. Navigate to the downloaded ZIP file and select it.

After installing the plugin, format your code automatically by applying the following settings:

1. Go to “Settings/Preferences” → “Other Settings” → “google-java-format Settings”.
2. Tick the checkbox to enable the plugin.
3. Change the code style to “Default Google Java style”.
4. Go to “Settings/Preferences” → Editor → Code Style → Scala.
5. Change the “Formatter” to “scalafmt”.
6. Go to “Settings/Preferences” → “Tools” → “Actions on Save”.
7. Under “Formatting Actions”, select “Optimize imports” and “Reformat file”.
8. From the “All file types list” next to “Reformat code”, select Java and Scala.

### Copyright

All files (including source code, configuration files) in the project are required to declare
CopyRight information at the top, and the project uses Apache License 2. You can configure the
copyright information in IntelliJ IDEA with the following steps:

1. Open Settings → Editor → Copyright → Copyright Profiles.
2. Add a new copyright file named Apache.
3. Add the following text as the license text:

```
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.
```

4. Go to Editor → Copyright and select the Apache copyright file as the default copyright file for
   the project.
5. Click Apply to save the configuration changes.
6. Right-click on the existing File/Package/Module and select `Update Copyrights…` to update the
   Copyright of the file.
