#!/bin/bash
# dynamic_calls.sh [JAR] - list the calls into Groovy's dynamic runtime (ScriptBytecodeAdapter, NumberNumber*,
# DefaultTypeTransformation, ...) that remain in the compiled engine and runtime (bin/openswmm.jar), by kind and by
# method: every class disassembled with javap -c, Groovy's own generated methods left out (dyncalls.py). A reflective
# field store, a Number-arithmetic operator or a compareTo comparison here would be a place where @CompileStatic did
# not compile what the translator meant (lessons/38, 39).
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../README.md.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
JAR=${1:-$HERE/../bin/openswmm.jar}
T=$(mktemp -d "${TMPDIR:-/tmp}/gvdyn.XXXXXX"); trap 'rm -rf "$T"' EXIT
unzip -q "$JAR" -d "$T/c"
(cd "$T/c" && env -u JAVA_TOOL_OPTIONS javap -c -p engine/*.class cpp/*.class 2>/dev/null) > "$T/javap.txt"
echo "== engine and runtime classes: $(ls "$T/c/engine" "$T/c/cpp" | grep -c '\.class$')"
python3 "$HERE/dyncalls.py" "$T/javap.txt" 40
