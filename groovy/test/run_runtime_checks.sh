#!/bin/bash
# run_runtime_checks.sh [N] - check the Groovy runtime (src/cpp, package cpp) with the checks that prove the Java
# runtime it was translated from (tests/ of this repository), unchanged: each Java check program is compiled against
# the Groovy runtime's classes (the Groovy runtime keeps the Java runtime's JVM shape: the same classes, static methods
# and fields), run, and its output compared with the reference program's:
#   crtcheck  tests/crtcheck/javacheck/CrtCheck.java against the system's glibc (libm.c, printf.c, scanf.c, misc.cpp):
#             libm bit for bit, printf output, sscanf results, from_chars / strtol / strtod / atoi / ctime / ctype
#   sort/hash tests/stdcheck/sortcheck.java and hashcheck.java against libstdc++ (sortcheck.cpp, hashcheck.cpp):
#             introsort's permutations, unordered_map / unordered_set node order, std::hash
#   streams   tests/stdcheck/streamcheck_java/StreamCheck.java against libstdc++ (streamcheck.cpp, built here)
#   core      tests/javacheck/CoreCheck.java against the C# runtime (corecheck.cs, built here with the .NET SDK)
# Everything is built in W (default /dev/shm/gv6_rtcheck) and deleted at the end; tests/ is only read.
# Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../README.md.
set -e
HERE=$(cd "$(dirname "$0")" && pwd); K=$(cd "$HERE/.." && pwd); ROOT=$(cd "$K/.." && pwd); T=$ROOT/tests
N=${1:-300000}; W=${W:-/dev/shm/gv6_rtcheck}; rm -rf "$W"; mkdir -p "$W/rt" "$W/j"
GJ=$("$K/groovyc.sh" --jar)
echo "== compiling the Groovy runtime (src/cpp)"
"$K/groovyc.sh" -d "$W/rt" "$K"/src/cpp/*.groovy
CP="$W/rt:$GJ"
JAVA="env -u JAVA_TOOL_OPTIONS java -Xss16m -XX:+UseSerialGC -XX:-DontCompileHugeMethods"
JC="env -u JAVA_TOOL_OPTIONS javac --release 21 -nowarn -encoding UTF-8 -cp $CP"
fail=0
echo "== crtcheck: the C library against glibc"
for p in libm printf scanf; do gcc -O2 -ffp-contract=off -fno-builtin -o "$W/$p" "$T/crtcheck/$p.c" -lm; done
g++ -O2 -std=c++20 -o "$W/misc" "$T/crtcheck/misc.cpp"
# the Groovy runtime calls R.as R.as_ (as is a Groovy keyword): the check program is compiled from a copy with that name changed
mkdir -p "$W/j/crt" "$W/crtsrc"; sed 's/R\.as(/R.as_(/g' "$T/crtcheck/javacheck/CrtCheck.java" > "$W/crtsrc/CrtCheck.java"
grep -c 'R\.as_(' "$W/crtsrc/CrtCheck.java" | sed 's/^/  (CrtCheck.java: R.as renamed R.as_ on /; s/$/ line)/'
$JC -d "$W/j/crt" "$W/crtsrc/CrtCheck.java"
J="$JAVA -cp $W/j/crt:$CP CrtCheck"
"$W/libm" $N | $J libm $N; "$W/printf" | $J printf; "$W/scanf" | $J scanf; "$W/misc" | $J misc; $J units
echo "== sort and hash: StdAlgo, the hash tables and std::hash against libstdc++"
g++ -O2 -std=c++20 -o "$W/sortcheck" "$T/stdcheck/sortcheck.cpp"; g++ -O2 -std=c++20 -o "$W/hashcheck" "$T/stdcheck/hashcheck.cpp"
mkdir -p "$W/j/std"; $JC -d "$W/j/std" "$T/stdcheck/sortcheck.java" "$T/stdcheck/hashcheck.java"
"$W/sortcheck" > "$W/sort_cpp.txt"; "$W/hashcheck" > "$W/hash_cpp.txt"
echo "sort cases: $(wc -l < "$W/sort_cpp.txt")"
for mode in O D I L; do
	$JAVA -cp "$W/j/std:$CP" sortcheck $mode > "$W/sort_gv_$mode.txt"
	if cmp -s "$W/sort_cpp.txt" "$W/sort_gv_$mode.txt"; then echo "sort, stable_sort, partial_sort ($mode): identical permutations"
	else echo "sort ($mode): DIFFERENT"; diff "$W/sort_cpp.txt" "$W/sort_gv_$mode.txt" | head -5 | cut -c1-300; fail=1; fi
done
$JAVA -cp "$W/j/std:$CP" hashcheck > "$W/hash_gv.txt"
echo "hash lines: $(wc -l < "$W/hash_cpp.txt")"
if cmp -s "$W/hash_cpp.txt" "$W/hash_gv.txt"; then echo "unordered_map / unordered_set order and std::hash: identical"
else echo "hash: DIFFERENT"; diff "$W/hash_cpp.txt" "$W/hash_gv.txt" | head -6 | cut -c1-300; fail=1; fi
echo "== streams and file system against libstdc++ and the C# runtime (as tests/stdcheck/run_stream_java.sh compares the Java)"
FW=$(ls -d /usr/lib/dotnet/shared/Microsoft.NETCore.App/8.* | head -1)
CSC=$(ls /usr/lib/dotnet/sdk/*/Roslyn/bincore/csc.dll | head -1)
REFS=$(for f in $FW/*.dll; do case $(basename $f) in Microsoft.VisualBasic*|mscorlib.dll|netstandard.dll|System.Private.*) ;; *) echo -n "-r:$f ";; esac; done)
RT=$(ls "$ROOT"/cs/runtime/*.cs | grep -v -e Program.cs -e /Hand_)
mkdir -p "$W/cs" "$W/s/build"     # the programs write and read build/streamcheck.bin in their working folder
g++ -O2 -std=c++20 -o "$W/streamcheck" "$T/stdcheck/streamcheck.cpp"
dotnet "$CSC" -nologo -optimize+ -nowarn:CS8632,CS0162,CS0168 -langversion:latest -out:"$W/cs/streamcheck.dll" $REFS -r:$FW/netstandard.dll \
  -r:$FW/System.Private.CoreLib.dll "$T/stdcheck/streamcheck.cs" $RT > /dev/null
printf '{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"%s"}}}' $(basename $FW) > "$W/cs/streamcheck.runtimeconfig.json"
# the Groovy runtime calls Ios.in Ios.in_ (in is a Groovy keyword): the check program is compiled from a copy with that
# one name changed
mkdir -p "$W/j/st" "$W/stsrc"; sed 's/Ios\.in\b/Ios.in_/g' "$T/stdcheck/streamcheck_java/StreamCheck.java" > "$W/stsrc/StreamCheck.java"
grep -c 'Ios\.in_' "$W/stsrc/StreamCheck.java" | sed 's/^/  (StreamCheck.java: Ios.in renamed Ios.in_ in /; s/$/ places)/'
$JC -d "$W/j/st" "$W/stsrc/StreamCheck.java"
for s in a c; do (cd "$W/s" && "$W/streamcheck" $s > "$W/stream_cpp_$s.txt"); done
for s in a b c; do
	(cd "$W/s" && $JAVA -cp "$W/j/st:$CP" StreamCheck $s > "$W/stream_gv_$s.txt")
	# the C# side: built here from cs/runtime; if that program fails (in November 2026 cs/runtime's IStream.ReadLine
	# throws IndexOutOfRangeException on section a), the outputs recorded in tests/stdcheck/build are used
	if ! (cd "$W/s" && dotnet "$W/cs/streamcheck.dll" $s > "$W/stream_cs_$s.txt" 2> "$W/stream_cs_$s.err"); then
		echo "  (C# streamcheck $s failed: $(grep -m1 -o 'System[.A-Za-z]*Exception[^\r]*' "$W/stream_cs_$s.err"); using tests/stdcheck/build/stream_cs_$s.txt)"
		cp "$T/stdcheck/build/stream_cs_$s.txt" "$W/stream_cs_$s.txt"
	fi
done
quirk() { local d; d=$(diff "$1" "$2" | grep '^[<>]' || true); [ "$d" = $'< d 01 2 [1.0e+02]\n> d 01 2 [1.e+02]' ] || [ "$d" = $'< d 01 2 [1.e+02]\n> d 01 2 [1.0e+02]' ]; }
echo "section a: $(wc -l < "$W/stream_gv_a.txt") lines"
for o in cs:C# cpp:C++; do
	f=$W/stream_${o%%:*}_a.txt; n=${o#*:}
	if cmp -s $f "$W/stream_gv_a.txt"; then echo "  $n and Groovy: identical"
	elif quirk $f "$W/stream_gv_a.txt"; then echo "  $n and Groovy: identical but for the glibc %#g quirk line (printf \"%#.2g\" of 99.995)"
	else echo "  $n and Groovy: DIFFERENT"; diff $f "$W/stream_gv_a.txt" | head -8; fail=1; fi
done
cmp -s "$W/stream_cs_a.txt" "$W/stream_gv_a.txt" || cmp -s "$W/stream_cpp_a.txt" "$W/stream_gv_a.txt" || { echo "  Groovy equals neither C# nor C++ exactly"; fail=1; }
echo "section b: $(wc -l < "$W/stream_gv_b.txt") lines"
if cmp -s "$W/stream_cs_b.txt" "$W/stream_gv_b.txt"; then echo "  C# and Groovy: identical"; else echo "  C# and Groovy: DIFFERENT"; diff "$W/stream_cs_b.txt" "$W/stream_gv_b.txt" | head -8; fail=1; fi
echo "section c: $(cat "$W/stream_gv_c.txt")"
for o in cpp:C++ cs:C#; do
	if cmp -s "$W/stream_${o%%:*}_c.txt" "$W/stream_gv_c.txt"; then echo "  ${o#*:} and Groovy: identical"; else echo "  ${o#*:} and Groovy: DIFFERENT"; fail=1; fi
done
echo "== core: Vec, iterators, pointers, Str, CPtr, Pair, Opt, ... against the C# runtime"
dotnet "$CSC" -nologo -optimize+ -nullable:enable -nowarn:CS8600,CS8601,CS8602,CS8603,CS8604,CS8618,CS8625,CS0162,CS0168,CS0219,CS0414,CS0649,CS8632 \
  -langversion:latest -out:"$W/cs/corecheck.dll" $REFS -r:$FW/netstandard.dll -r:$FW/System.Private.CoreLib.dll "$T/javacheck/corecheck.cs" $RT > /dev/null
printf '{"runtimeOptions":{"tfm":"net8.0","framework":{"name":"Microsoft.NETCore.App","version":"%s"},"configProperties":{"System.Globalization.Invariant":true}}}' $(basename $FW) > "$W/cs/corecheck.runtimeconfig.json"
mkdir -p "$W/j/core"; $JC -d "$W/j/core" "$T/javacheck/CoreCheck.java"
dotnet "$W/cs/corecheck.dll" > "$W/core_cs.txt"
$JAVA -cp "$W/j/core:$CP" CoreCheck > "$W/core_gv.txt"
echo "lines: $(wc -l < "$W/core_cs.txt")"
if cmp -s "$W/core_cs.txt" "$W/core_gv.txt"; then echo "core: Groovy and C# outputs identical"; else diff "$W/core_cs.txt" "$W/core_gv.txt" | head -20; fail=1; fi
[ "${KEEP:-0}" = 1 ] || rm -rf "$W"
exit $fail
