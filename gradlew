#!/bin/sh
# Gradle Start Up Script for UN*X
APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
if [ ! -f "$CLASSPATH" ]; then
    echo "Notice: gradle-wrapper.jar not bundled; invoking system 'gradle' or Android Studio embedded Gradle..."
    exec gradle "$@"
fi
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
