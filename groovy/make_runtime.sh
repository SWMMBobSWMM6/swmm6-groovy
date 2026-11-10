#!/bin/bash
# make_runtime.sh - regenerate the Groovy runtime (src/cpp/*.groovy, all but the math) from the Java port's runtime
# (java/src/cpp/*.java) with tools/J2Gv.java, which reads the Java with the JDK's own compiler (javac's tree API and
# attribution) and writes Groovy. Glibm*.groovy are not written here: they come from tools/libm/gim2x.py (make_glibm.sh)
# and GlibmRt.groovy, Glibm2Rt.groovy and GR.groovy are hand-written. Needs a JDK 17 or newer.
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see README.md.
set -e
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/.." && pwd)
X="--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
   --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
B=$(mktemp -d); trap 'rm -rf "$B"' EXIT
javac $X -nowarn -d "$B" "$HERE/tools/J2Gv.java" 2>&1 | grep -v '^Picked up' || true
env -u JAVA_TOOL_OPTIONS java $X -cp "$B" J2Gv "$HERE/src/cpp" "$ROOT"/java/src/cpp/*.java
