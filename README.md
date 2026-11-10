# swmm6-groovy: the OpenSWMM 6 1D engine in Groovy, byte for byte

A translation of the 1D engine of OpenSWMM 6 (HydroCouple/openswmm.engine, branch `swmm6_rel`, commit 88329d6)
into Groovy, held to the same output as the C++ engine: the same binary `.out` file and the same `.rpt`
report, apart from the three clock lines. It is not SWMM6 and not a HydroCouple or EPA release; it is a
translation checked against SWMM6.

## Result

**480 of 480 models byte-identical** against the C++ reference engine built from the same commit and run on one thread
(`OMP_NUM_THREADS=1`), on the 480-model gate set (the one the SWMM5 ports were checked on), drawn from
[1729-SWMM5-Models-2030](https://github.com/SWMMBobSWMM6/1729-SWMM5-Models-2030). Gate runs
`gv6_a`, `gv6_b`, `gv6_c`; every result, with the SHA-256 of each `.out` and `.rpt`, is in `gate/evidence/`.

The same engine on the 452 extra decks: **452 of 452 extra decks byte-identical**. These are SWMM6's own tests, which the corpus cannot be:
the input files in the upstream repository's `tests` folder at 88329d6, and the synthetic decks of
[swmm6-rust](https://github.com/SWMMBobSWMM6/swmm6-rust)'s gate, which probe the finite-volume solver, its
structure coupling, the newer hydrology and the input checks. Gate runs
`gv6_x_a`, `gv6_x_b`, `gv6_x_c`, in `gate/evidence/` with the same hashes.

Checked twice more. An independent rerun of the 480 from a clean copy of the repository (`git archive`), with the
translator rebuilt there, the engine regenerated from `cs/gen` and the runtime from `java/src/cpp` (both
byte-identical to the committed files) and the jar built there: gate runs
`gv6_v_a`, `gv6_v_b`, `gv6_v_c`, **480 of 480 byte-identical**. Then the main session's own check, from a
clean `git clone`: the translator rebuilt, the engine and the runtime regenerated (identical again), the jar built
there, and gate runs `gv6_main` on 45 corpus models drawn at random (seed 20261110) plus the three decks that
SAVE and USE `cho.txt` (**48 of 48 byte-identical**) and `gv6_main_x` on 12 extra decks drawn the same way
(**12 of 12 byte-identical**). Neither check regenerated the math (the GIMPLE dumps it is made from were no longer on
the machine); its own tests are in `groovy/results/libm_tests.txt`.

A model passes when its `.out` is byte-identical to the reference's (when the reference writes one), its `.rpt` is
identical after removing the lines "Analysis begun", "Analysis ended" and "Total elapsed time", and its exit code is
the same (the command line exits nonzero when a model stops on an input error).

The translations, all from the same C# and gated the same way: swmm6-csharp, swmm6-go, swmm6-java, swmm6-typescript, swmm6-fortran, swmm6-python, swmm6-kotlin, swmm6-groovy, under
[SWMMBobSWMM6](https://github.com/SWMMBobSWMM6).

## How it was made

The C++ engine was translated to C# first, and the C# gated to 480 of 480. The other languages are translated
from that C#, not from the C++. `tools/cs2x/Cs2Groovy.cs`, begun from the Java translator, translates the verified C# engine (`cs/gen`) through Roslyn into statically compiled Groovy 3 (`groovy/gen`, every class `@CompileStatic`; the mapping is in `tools/cs2x/GROOVY.md`). `groovy/src/cpp` is the runtime library in Groovy: the Java port's runtime rewritten for Groovy by `groovy/tools/J2Gv.java`, plus bit-exact translations of 19 glibc math functions (`Glibm*.groovy`, from GCC's compiled form of glibc 2.39 by `tools/libm/gim2x.py`). Nothing of the Java port is on the class path: the Java runtime's source is only what the Groovy runtime was rewritten from.

Nothing in the generated engine is edited by hand. Where the output depends on the C library (printf's rounding,
strtod, sscanf, the order of a hash table's nodes, introsort's swaps), the runtime reproduces glibc and libstdc++
exactly; the checks in `tests/` compare it with them case by case.

## Build and run

Needs JDK 21 and Groovy 3.0.24 (`groovy/groovyc.sh` uses the jars that ship with Gradle 8.14.3: `GROOVY_LIB`, default `/opt/gradle/lib`). Build: `groovy/build.sh`. Then run a model the way the C++ command line runs it:

    groovy/runswmm6 model.inp model.rpt model.out

To regenerate the engine: tools/cs2x/build_groovy.sh, then dotnet tools/cs2x/bin/cs2groovy.dll cs groovy/gen; the runtime with groovy/make_runtime.sh (from `java/src/cpp`, included for that purpose only) and the math with groovy/make_glibm.sh GLIBM_BUILD (see `tools/libm/README.md`). The C# engine it starts from is regenerated from the C++ source with
`tools/cpp2x` (see the header of `tools/cpp2x/emit_cs.py` and `cs/README.md`), which needs the OpenSWMM source at
commit 88329d6 and libclang.

## The gate

`gate/make_ref.sh` makes the reference outputs with the C++ engine (`ref/runswmm6`: build openswmm.engine at
88329d6 and run it single-threaded); `gate/gate.py NAME "groovy/runswmm6" --resume` runs this engine on every model in
`gate/models_all.tsv` (paths relative to the corpus; set `SWMM_CORPUS`) and compares; `gate/cmp1.sh` checks one
model. The extra decks are listed in `gate/models_extra.tsv`, with paths relative to a folder that holds clones of
openswmm.engine (at 88329d6) and swmm6-rust: make their references with
`SWMM_CORPUS=FOLDER MODELS=gate/models_extra.tsv REF=$PWD/gate/ref_outputs_extra gate/make_ref.sh`, then run
`gate/gate.py NAME "groovy/runswmm6" --models gate/models_extra.tsv --ref gate/ref_outputs_extra --root FOLDER --resume`.
The median time per model in the gate runs was 3.04 s (the C# engine's: 0.76 s, which includes .NET's start-up and JIT compilation). The times were measured on a shared two-core cloud machine while other runs were going, so they are a
rough guide, not a benchmark.

## What this does not show

- Agreement on models outside the 480 and the 452. The two sets are broad (EPA's examples, the OWA EXTRAN set,
  hydrology, hydraulics, water quality, LID, RDII, controls, hot start, input errors, the FV solver and SWMM6's own
  tests), but a feature no gate model or deck uses is untested.
- The reference's parallel runs. Every parallel loop runs on one thread, in order, which is what the C++ engine
  does when it is held to one thread; results with OpenMP threads can differ in the last bits.
- The 2D and other modules of OpenSWMM 6 beyond the 1D engine the command line runs.
- Every input to the 19 translated glibc math functions. Each is tested against the system's glibc 2.39 on about a million arguments, special values and NaN payloads included (`tools/libm/test`), with no difference; that is a test, not a proof.

## License and attribution

This repository is a translation of the OpenSWMM 6 engine.

OpenSWMM Engine — Copyright 2026 HydroCouple Developers, licensed under the Apache License, Version 2.0. See
[LICENSE](LICENSE) and [NOTICE](NOTICE). Source: https://github.com/HydroCouple/openswmm.engine (branch
`swmm6_rel`, commit `88329d6`).

Every generated engine file here is a modified, mechanically translated form of the OpenSWMM sources: its header
names what it was generated from, and each C# file in `cs/gen` names the OpenSWMM source file it came from.

Portions derive from the USEPA Storm Water Management Model, which is in the public domain under 17 USC § 105. The
USEPA does not endorse this product, and neither does HydroCouple.

The translators, the runtime, the gate and the notes: Copyright 2026 Robert Dickinson, licensed under the Apache
License, Version 2.0.

The math routines `groovy/src/cpp/Glibm.groovy` and `groovy/src/cpp/Glibm2.groovy`, with the Java port's `java/src/cpp/Glibm.java` and `Glibm2.java` that come with the Java runtime source (and their sources in `tools/libm/`) are translations of glibc 2.39 code and remain under glibc's licence, the GNU Lesser General Public License 2.1 or later (`LICENSES/LGPL-2.1.txt`).

## AI use

Made with Claude (Anthropic): the translators, the runtime, the gate and the notes are Claude's work, directed by
Robert Dickinson; the engine itself is OpenSWMM's, translated mechanically. See [AI-USE.md](AI-USE.md).
