#!/bin/bash
# build.sh - compile the Groovy SWMM6 engine and its runtime into bin/openswmm.jar (Main-Class Main; at run time the
# classes need only groovy-3.0.24.jar, which runswmm6 puts on the class path).
#   ./build.sh            the runtime (src/cpp), the generated engine (gen/engine) and the runner (src/main)
#   ./build.sh runtime    the runtime alone (a quick compile check; no jar)
# The compiler is groovyc.sh's (Groovy 3.0.24's FileSystemCompiler from Gradle's jars), with a 3 GB heap and the
# serial collector so that it keeps to one core while a gate run may be using the other. Every class is
# @CompileStatic. The classes are built in a temporary folder (GV_BUILD_TMP, default /dev/shm) and only the jar is kept.
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see README.md.
set -e
cd "$(dirname "$0")"
T=$(mktemp -d "${GV_BUILD_TMP:-/dev/shm}/gv6build.XXXXXX"); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/classes" bin
list=$T/files.txt
if [ "$1" = runtime ]; then ls src/cpp/*.groovy > "$list"; else ls src/cpp/*.groovy gen/engine/*.groovy src/main/*.groovy > "$list"; fi
echo "groovyc: $(wc -l < "$list") files"
set +e
./groovyc.sh -d "$T/classes" @"$list" > "$T/log.txt" 2>&1
rc=$?
set -e
[ "$rc" = 0 ] || { head -150 "$T/log.txt"; echo "errors: $(grep -c '^[^ ].*: [0-9]*: ' "$T/log.txt")"; echo "build failed (groovyc exit $rc)"; cp "$T/log.txt" "${GV_BUILD_LOG:-/dev/shm/gv6_build_errors.txt}"; exit 1; }
if [ "$1" = runtime ]; then echo "runtime compiled"; exit 0; fi
rm -f bin/openswmm.jar
env -u JAVA_TOOL_OPTIONS jar --create --file bin/openswmm.jar --main-class Main -C "$T/classes" .
echo "built bin/openswmm.jar ($(du -h bin/openswmm.jar | cut -f1))"
