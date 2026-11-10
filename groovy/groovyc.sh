#!/bin/bash
# groovyc.sh ARGS... - run Groovy 3's compiler (org.codehaus.groovy.tools.FileSystemCompiler) from the jars Gradle 8.14.3
# ships (GROOVY_LIB, default /opt/gradle/lib: groovy-3.0.24.jar with the ASM and ANTLR jars beside it), with a 3 GB heap
# and the serial collector. `groovyc.sh --jar` prints the path of groovy-3.x.jar, which the classes need at run time.
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see README.md.
L=${GROOVY_LIB:-/opt/gradle/lib}
GJ=$(ls "$L"/groovy-3.*.jar | grep -E '/groovy-3\.[0-9.]+\.jar$' | sort -V | tail -1)
[ "$1" = --jar ] && { echo "$GJ"; exit 0; }
CP=$GJ:$(ls "$L"/asm-[0-9]*.jar | head -1):$(ls "$L"/asm-commons-*.jar | head -1):$(ls "$L"/asm-tree-*.jar | head -1)
CP=$CP:$(ls "$L"/antlr4-runtime-*.jar | head -1)
exec env -u JAVA_TOOL_OPTIONS java -Xmx3g -Xss64m -XX:+UseSerialGC ${GROOVYC_JOPTS:-} -cp "$CP${GV_CP:+:$GV_CP}" org.codehaus.groovy.tools.FileSystemCompiler "$@"
