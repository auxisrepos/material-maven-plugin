# payload-maven-plugin

[![Build Status](https://travis-ci.org/rebaze/payload-maven-plugin.svg?branch=master)](https://travis-ci.org/rebaze/payload-maven-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/payload-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/payload-maven-plugin)

A plugin collecting the total bill of payload for a maven reactor project including dependencies, plugins and reporting artifacts.
Output can be used to create delta maven repisitories.

# Usage:
mvn com.rebaze.maven:payload-maven-plugin:deploy

Payload file is assumed to be in target/build.payload as produced by payload-maven-extension.

You can set another file with -Dpayload=<file>

