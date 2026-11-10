#!/bin/bash
# run_libm_tests.sh [N] [N2]
# Check the Groovy translations of glibc's math routines (src/cpp/Glibm.groovy, Glibm2.groovy and their runtimes
# GlibmRt.groovy, Glibm2Rt.groovy) bit for bit against the system libm (glibc 2.39, the library the C++ SWMM6 engine
# calls), by the method of tools/libm/test/run_tests.sh:
#   set 1: N random arguments per function over the ranges SWMM uses plus special values (tools/libm/test/mkvectors.c)
#     and 100,000 edge cases per function (mkedge.c): exp, log, log10, pow, sin, cos, tan, asin, acos, atan and fma;
#   set 2: N2 random arguments per function over wide ranges and every branch threshold, +-0, +-inf, subnormals and
#     NaNs of both signs with several payloads, every pair of special values for atan2 and hypot (mkvectors2.c):
#     cbrt, hypot, atan2, cosh, sinh, tanh, log2, expm1, log1p, frexp, ldexp; a NaN must match in sign and payload;
#     run with the JIT (and the flags runswmm6 uses) and with -Xint (the interpreter).
# The vectors (about 0.5 GB at the defaults) go to W (default /dev/shm/gv6_libm_test) and are deleted at the end.
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../README.md.
set -e
HERE=$(cd "$(dirname "$0")" && pwd); G=$(cd "$HERE/.." && pwd); ROOT=$(cd "$G/.." && pwd)
N=${1:-1000000}; N2=${2:-1000000}; W=${W:-/dev/shm/gv6_libm_test}; rm -rf "$W"; mkdir -p "$W/classes"
T=$ROOT/tools/libm/test
gcc -O2 -o "$W/mkvectors" "$T/mkvectors.c" -lm
gcc -O2 -o "$W/mkedge" "$T/mkedge.c" -lm
gcc -O2 -fno-builtin -o "$W/mkvectors2" "$T/mkvectors2.c" -lm
"$W/mkvectors" "$N" "$W/vectors.bin" > /dev/null 2>&1
"$W/mkedge" 100000 "$W/edge.bin" > /dev/null 2>&1
"$W/mkvectors2" "$N2" "$W/vectors2.bin" > /dev/null 2>&1
"$G/groovyc.sh" -d "$W/classes" "$G/src/cpp/Glibm.groovy" "$G/src/cpp/GlibmRt.groovy" "$G/src/cpp/Glibm2.groovy" \
    "$G/src/cpp/Glibm2Rt.groovy" "$HERE/Check.groovy" "$HERE/Check2.groovy"
GJ=$("$G/groovyc.sh" --jar)
JV="-XX:-DontCompileHugeMethods -XX:+UseSerialGC"
J="env -u JAVA_TOOL_OPTIONS java $JV -cp $W/classes:$GJ"
echo "== Groovy $(basename "$GJ" .jar | sed 's/groovy-//'), set 1: vectors.bin (N = $N per function)"; $J cpp.Check "$W/vectors.bin"
echo "== Groovy, set 1: edge.bin"; $J cpp.Check "$W/edge.bin"
echo "== Groovy, set 2: vectors2.bin (N2 = $N2 per function)"; $J cpp.Check2 "$W/vectors2.bin"
echo "== Groovy, set 2, -Xint (interpreter)"; env -u JAVA_TOOL_OPTIONS java -Xint -cp "$W/classes:$GJ" cpp.Check2 "$W/vectors2.bin"
rm -rf "$W"
