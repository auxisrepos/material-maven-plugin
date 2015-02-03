# maven-material-plugin
A plugin collecting the total bill of material for a maven reactor project including dependencies, plugins and reporting artifacts.
Output can be used to create delta maven repisitories.

# Usage:
mvn org.auxis.maven:maven-material-plugin:create-bill -Dbill=/path/to/bill
