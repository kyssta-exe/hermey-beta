#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_HOME=`cd "$(dirname "$0")" >/dev/null 2>&1 && pwd`
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
