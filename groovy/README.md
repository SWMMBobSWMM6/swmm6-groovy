# The OpenSWMM 6 1D engine in Groovy, byte for byte

A translation of the 1D engine of OpenSWMM 6 (HydroCouple/openswmm.engine, branch `swmm6_rel`, commit 88329d6) into
Groovy 3 (every class `@CompileStatic`), held to the same output as the C++ engine: the same binary `.out` file and
the same `.rpt` report, apart from the three clock lines. It is not SWMM6 and not a HydroCouple or EPA release; it
is a translation checked against SWMM6.

## Result

**480 of 480 models byte-identical** against the C++ reference engine built from the same commit and run on one
thread (`OMP_NUM_THREADS=1`), on the 480-model gate set the SWMM5 ports were checked on, drawn from
1729-SWMM5-Models-2030. Gate runs `gv6_a` (158 of 158), `gv6_b` (160 of 160) and `gv6_c` (162 of 162), the corpus
in three parts (`gate/ids3_corpus_a.txt`, `_b`, `_c`); 11.6, 14.2 and 12.8 minutes, a median of 3.04 s per model.
(The first runs of gv6_b and gv6_c were lost before they started, when the machine restarted; they were run again.)

**452 of 452 extra decks byte-identical**: SWMM6's own tests (the input files in the upstream repository's `tests`
folder at 88329d6) and the synthetic decks of swmm6-rust's gate (the finite-volume solver, its structure coupling,
the newer hydrology and the input checks). Gate runs `gv6_x_a` (145 of 145), `gv6_x_b` (145 of 145) and `gv6_x_c`
(162 of 162); 10.8, 11.1 and 9.9 minutes, a median of 2.30 s per deck.

**The independent rerun: 480 of 480.** A clean copy of commit 96dcd17 (`git archive`) where the translator was
rebuilt, the engine regenerated from `cs/gen` (1,259 files, byte-identical to the committed ones), the runtime
translated again from `java/src/cpp` (186 files, byte-identical) and everything compiled again
(`gate/gv6_v_build.txt`), then gated: runs `gv6_v_a` (158 of 158), `gv6_v_b` (160 of 160) and `gv6_v_c` (162 of
162); 23.2, 23.8 and 23.2 minutes, a median of 6.05 s per model (another agent's runs shared the two cores
throughout).

**The main session's check: 48 of 48 and 12 of 12.** A clean `git clone` of commit 280b0e2 (in memory), where the
translator was rebuilt, the engine regenerated from `cs/gen` and the runtime from `java/src/cpp` (no file changed:
`git status` empty) and the jar built with `build.sh`, then gated: run `gv6_main` on 45 corpus models drawn at random
(`random.Random(20261110)`) plus `Hydrology/save_rtk_long.inp`, `one_sub_one_rdii_node.inp` and
`Use_SeasonalRDIIn.inp`, the decks that SAVE and USE `cho.txt` (48 of 48; 696 s), and run `gv6_main_x` on 12 extra
decks drawn the same way (12 of 12; 119 s). Ids in `gate/ids_main_check.txt` and `gate/ids_main_check_extra.txt`.
Neither this check nor the rerun regenerated the math at first (the GIMPLE dumps `make_glibm.sh` reads were no
longer on the machine). The main session did that later the same day, in a second clean clone (commit 296062e):
glibc 2.39's source compiled to GIMPLE again with `tools/libm/build_glibm.sh` (compiled only; nothing of glibc is
run), its tables read with `extract_tables.py`, then `groovy/make_glibm.sh`: `Glibm.groovy` and `Glibm2.groovy` byte-identical to the
committed files. The math's own tests are in `results/libm_tests.txt`. `Hydrology/cho.txt` was unchanged afterwards.

Every result, with the SHA-256 of each `.out` and `.rpt`, is in `results/NAME.json`, with the run's log beside it.

A model passes when its `.out` is byte-identical to the reference's (when the reference writes one), its `.rpt` is
identical after removing the lines "Analysis begun", "Analysis ended" and "Total elapsed time", and its exit code is
the same. The gate runs every model through `runswmm6_saves.sh`, which backs up the files a deck SAVEs and restores
them afterwards, so that no deck changes a file another deck reads.

## How it was made

The C++ engine was translated to C# first (`cs/gen`, 480 of 480), and this engine is translated from that C#, not
from the C++, by a translator written for it: `tools/cs2x/Cs2Groovy.cs`, `GvTypes.cs` and `GvFn.cs`, which read the C#
through Roslyn's semantic model and print Groovy (`groovy/gen/engine`, 1,259 files, 187,118 lines). It began as
a copy of the Java translator and keeps its names, runtime calls and analysis; `tools/cs2x/GROOVY.md` lists every
place where Groovy reads Java's syntax differently and what the translator writes instead (decimal literals are
BigDecimals, `/` on integers is a BigDecimal division, `>` on doubles is true for NaN, `==` calls `equals`, a store
to another object's field and a cast lambda compile to reflective calls, a lambda's captured locals become
`groovy.lang.Reference`s, `switch` compares boxed values, and more).

The runtime (`groovy/src/cpp`, package `cpp`, 181 files, 13,036 lines) is the small part of the C++ standard
library and of glibc that the engine calls. It is the Java port's runtime (`java/src/cpp`) translated into Groovy by
`groovy/tools/J2Gv.java`, which reads the Java with the JDK's own compiler (javac's tree API and attribution) and
rewrites the places where Groovy would read the same text differently; nothing in it is edited by hand. The 19 glibc
math functions the engine calls (exp, log, pow, sin, cos, tan, asin, acos, atan, log10, atan2, log2, sinh, tanh,
expm1, log1p, cosh, cbrt, hypot) are `Glibm.groovy` and `Glibm2.groovy` (8,899 lines), written by the Groovy back
end of `tools/libm/gim2x.py` from GCC's compiled form of glibc 2.39's own code (`make_glibm.sh`), with glibc's FMA
variants where x86-64 glibc picks them; `java.lang.Math` and `StrictMath` are not used for any of them. Hand-written:
`GlibmRt.groovy` (copied from the SWMM5 Groovy port) and `Glibm2Rt.groovy` (the helpers the generated math calls),
`GR.groovy` (Groovy's negative zero, integer division and NaN-safe `>`), and `src/main/Main.groovy` (the command
line, a port of `java/src/main/Main.java`). The Groovy back end of gim2x.py came from the SWMM5 Groovy port and was
extended to the second set of functions; the Go, Java and TypeScript files it writes are unchanged
(`results/gim2x_unchanged.txt`).

Nothing in the generated engine is edited by hand: every fix went into the translator or into J2Gv.

## How it was checked

- **The math**: `test/run_libm_tests.sh` compares each of the 19 functions (and fma, frexp, ldexp) with the system's
  glibc 2.39, bit for bit, by the method of `tools/libm/test`: a million arguments per function over the ranges SWMM
  uses with special values, and 100,000 edge cases each, for the first ten; a million per function over wide ranges
  and every branch threshold, with signed zeros, infinities, subnormals and NaNs of both signs with several
  payloads, JIT-compiled and interpreted, for the other nine: **0 differences** (`results/libm_tests.txt`).
- **The runtime**: `test/run_runtime_checks.sh` compiles the check programs that prove the Java runtime (`tests/`)
  against the Groovy runtime's classes and compares their output with glibc's and libstdc++'s: printf (179,756
  cases), sscanf, strtod and the other C library calls, introsort's permutations, the hash tables' node order and
  std::hash, the streams and the file system: **identical**, apart from one printf case C# formats differently from
  glibc, `%#.2g` of 99.995, where the Groovy runtime agrees with glibc (`results/runtime_checks.txt`). Two names the
  check programs call are Groovy keywords and are renamed in the copies the script compiles (`R.as` is `R.as_`,
  `Ios.in` is `Ios.in_`).
- **The bytecode**: every engine class was disassembled (`javap -c`) and searched for calls into Groovy's dynamic
  runtime; what remains is `castToType` after a conditional or a generic return, `compareIdentical` (`===`) and the
  unboxing helpers (lessons 38 and 39).
- **The engine**: the gate (above), run on the corpus and on the extra decks.

## Build and run

Needs JDK 21 and Groovy 3.0.24: `groovyc.sh` uses the jars that ship with Gradle 8.14.3 (`GROOVY_LIB`, default
`/opt/gradle/lib`: groovy-3.0.24.jar with the ASM and ANTLR jars beside it). Build (about 6 minutes, 3 GB of
memory): `groovy/build.sh`, which writes `groovy/bin/openswmm.jar`. Then run a model the way the C++ command line
runs it (the script puts groovy-3.0.24.jar on the class path):

    groovy/runswmm6 model.inp model.rpt model.out

To regenerate: `tools/cs2x/build_groovy.sh && dotnet tools/cs2x/bin/cs2groovy.dll cs groovy/gen` (the engine, from
`cs/gen`; needs the .NET 8 SDK), `groovy/make_runtime.sh` (the runtime, from `java/src/cpp`), `groovy/make_glibm.sh
GLIBM_BUILD` (the math, from the GIMPLE dumps that `tools/libm/build_glibm.sh` writes).

The gate: `groovy/gate/run_chunk.sh NAME corpus|extra [IDS_FILE]` runs `gate/gate.py` on this port with the
SAVE-file wrapper; `groovy/gate/rerun_build.sh` makes the clean copy of a commit that the independent rerun gates.

## What this does not show

- Agreement on models outside the 480 and the 452. A feature no gate model or deck uses is untested.
- The reference's parallel runs: every parallel loop runs on one thread, in order, as the C++ engine does when it
  is held to one thread.
- The 2D and other modules of OpenSWMM 6 beyond the 1D engine the command line runs.
- Every input to the 19 math functions: about a million arguments each is a test, not a proof.
- Speed. Groovy's start-up (it builds a metaclass for each class it constructs, and a closure object for each
  lambda it makes) adds about a second and a half to every run: on the 480 the median model took 1.6 s longer than
  in the Kotlin port's rerun, and the whole corpus 1.8 times as long (38.2 minutes against 21.0). The times were
  measured on a shared two-core machine while other gate runs were going.

## License and attribution

The engine is a translation of the OpenSWMM Engine, Copyright 2026 HydroCouple Developers, licensed under the
Apache License, Version 2.0 (https://github.com/HydroCouple/openswmm.engine, branch `swmm6_rel`, commit 88329d6).
Portions derive from the USEPA Storm Water Management Model, which is in the public domain; the USEPA does not
endorse this translation, and neither does HydroCouple. The math routines `src/cpp/Glibm.groovy` and
`src/cpp/Glibm2.groovy` are translations of glibc 2.39 code and remain under glibc's licence, the GNU Lesser General
Public License 2.1 or later.

## AI use

The translator (`tools/cs2x/Cs2Groovy.cs`, `GvTypes.cs`, `GvFn.cs`), the runtime (the Java port's runtime translated by J2Gv, which Claude also wrote, and the
hand-written parts), the port's scripts and checks, the Groovy back end of gim2x.py and these notes were written by
Claude, Anthropic's AI model, for Robert Dickinson, who directed the work. The engine is a mechanical translation of
OpenSWMM 6 (Apache-2.0, HydroCouple Developers), itself derived in part from the public-domain EPA SWMM; it was
checked by running it against the C++ engine, not by reading it. The glibc-derived math is LGPL-2.1-or-later. See
`../AI-USE.md`.
