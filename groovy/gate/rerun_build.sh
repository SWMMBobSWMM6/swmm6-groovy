#!/bin/bash
# rerun_build.sh [COMMIT] [DIR] - the first half of the independent rerun: a clean copy of COMMIT (default HEAD) of
# this repository (git archive) in DIR (default /dev/shm/gv6_v), where the translator is rebuilt, the engine is
# regenerated from cs/gen and compared byte for byte with the committed groovy/gen/engine, the runtime is translated
# again from java/src/cpp with J2Gv and compared with the committed groovy/src/cpp, and the runtime, engine and
# runner are compiled into DIR/groovy/bin/openswmm.jar. The gate then runs that copy:
#   groovy/gate/run_chunk.sh gv6_v_a corpus groovy/gate/ids3_corpus_a.txt DIR/groovy/runswmm6_saves.sh   (and _b, _c)
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../README.md.
set -e
K=$(cd "$(dirname "$0")/.." && pwd); ROOT=$(cd "$K/.." && pwd)
C=${1:-HEAD}; R=${2:-/dev/shm/gv6_v}
rm -rf "$R"; mkdir -p "$R"
git -C "$ROOT" archive "$C" | tar -x -C "$R"
echo "== clean copy of $(git -C "$ROOT" rev-parse --short "$C") in $R"
cd "$R"
echo "== the translator: tools/cs2x/build_groovy.sh"
nice -n 10 tools/cs2x/build_groovy.sh 2>&1 | grep -v '^Picked up' || true
echo "== the engine, regenerated from cs/gen into $R/regen and compared with groovy/gen/engine"
nice -n 10 dotnet tools/cs2x/bin/cs2groovy.dll cs "$R/regen"
if diff -r -q "$R/regen/engine" groovy/gen/engine; then echo "engine: $(ls groovy/gen/engine | wc -l) files, byte-identical to the commit"; else echo "engine: DIFFERENT"; exit 1; fi
echo "== the runtime, translated again from java/src/cpp with J2Gv and compared with groovy/src/cpp"
cp -a groovy/src/cpp "$R/cpp_committed"
nice -n 10 groovy/make_runtime.sh 2>&1 | grep -v '^Picked up' | tail -1
if diff -r -q "$R/cpp_committed" groovy/src/cpp; then echo "runtime: $(ls groovy/src/cpp | wc -l) files, byte-identical to the commit"; else echo "runtime: DIFFERENT"; exit 1; fi
rm -rf "$R/cpp_committed" "$R/regen"
echo "== build"
GV_BUILD_TMP=/dev/shm nice -n 10 groovy/build.sh
ls -l groovy/bin/openswmm.jar
