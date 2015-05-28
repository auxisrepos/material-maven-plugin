# material-maven-plugin

[![Build Status](https://travis-ci.org/auxisrepos/material-maven-plugin.svg?branch=master)](https://travis-ci.org/auxisrepos/material-maven-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.auxis.maven/material-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.auxis.maven/material-maven-plugin)

A plugin collecting the total bill of material for a maven reactor project including dependencies, plugins and reporting artifacts.
Output can be used to create delta maven repisitories.

# Usage:
mvn org.auxis.maven:material-maven-plugin:create-bill -Dbill=/path/to/bill
