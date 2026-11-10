# C# to Groovy: how tools/cs2x/Cs2Groovy.cs maps the generated engine

The Groovy engine (groovy/gen/engine) is written from the verified C# engine (cs/gen) the way the Java engine is:
Roslyn's semantic model, one IOperation tree per method, printed as Groovy 3 source in which every class is
`@groovy.transform.CompileStatic`. The translator (Cs2Groovy.cs, GvTypes.cs, GvFn.cs; built by build_groovy.sh)
began as a copy of the Java translator, so it keeps the Java translator's types, names, runtime calls and analysis
(cells, renamed methods, element makers; see JAVA.md), and changes what Groovy reads differently from Java. The
Groovy runtime (package `cpp`, groovy/src/cpp) is the Java port's runtime translated by groovy/tools/J2Gv.java
(below), with the same classes and members.

    tools/cs2x/build_groovy.sh && dotnet tools/cs2x/bin/cs2groovy.dll cs groovy/gen

Groovy 3 parses most of Java, and with static compilation most of it compiles to the same bytecode. The
exceptions below were each found by compiling small test programs and reading the bytecode (`javap -c`), or by a
gate failure; they are the whole difference from JAVA.md.

## Numbers

| Java | Groovy | why |
|---|---|---|
| `1.5` | `1.5d` | an unsuffixed decimal literal is a `BigDecimal` |
| `-0.0` | `cpp.GR.NZ` (`NZF` for float) | Groovy folds `-0.0d` to +0.0 |
| `-2147483648`, `0xFFFFFFFF` | `Integer.MIN_VALUE`, `(-1)` | the first is the negation of a `long` literal, the second a `long`; integers are written in decimal, negatives in parentheses (`(byte) -5` can read as a subtraction) |
| `a / b` on ints, longs | `cpp.GR.idiv(a, b)`, `ldiv` | `/` on two integers is a `BigDecimal` division |
| `a > b`, `a >= b` on doubles | `cpp.GR.dgt(a, b)`, `dge` (`b < a`, `b <= a`) | Groovy 3 compiles them to `dcmpg`, which is true when either side is NaN (SWMM5 lessons/37) |
| float `a * b` | `((float) (a * b))` | Groovy computes float arithmetic in double; rounding the double result to float is the float result |
| `c++` on a char, short or byte; `x op= v` on them | written out with casts | `++` on a char is `Character.next()`, and char arithmetic is a `Number` |
| `x /= v` | `x = x / v` (with `idiv` for integers) | `a[i] /= v` compiles to dynamic `getAt`/`div`/`putAt` calls |
| `"text"` | `'text'` | a double-quoted string interpolates `$` |

## Objects

- `==` and `!=` on two references compile to `equals()` (and on numbers to `compareTo`): reference identity is
  `===` / `!==`, as in C#; a comparison with `null` keeps `==`.
- A field access `o.f` may call a getter, and a store `o.f = v` (to any field but the current class's own, and
  to any static field) compiles to `ScriptBytecodeAdapter.setProperty`, which is reflective and slow (lessons/39). Every
  instance field is read and written as `o.@f` (a plain `getfield`/`putfield`); a static field is written
  `C.@f = v` (`putstatic`) and read `C.f` (`getstatic`; `C.@f` as a value is a reflective `getField`). A cell's
  value is `cell.@v`. `o.@f op= v` reads the field reflectively, so a compound assignment or increment of a field
  is written out, `o.@f = o.@f + v`, with the receiver, array or index in a temporary when it has a call in it.
- A lambda compiles to a native lambda (`invokedynamic`) only when its target type is declared: as a cast
  (`(F) (a) -> ...`) or in a field initializer it is a closure converted by `castToType`. Every lambda and method
  reference is therefore a typed local before its statement, `F __lam3 = (double a) -> { ... }`, and the element
  makers (JAVA.md) are static fields of engine/Mk.groovy, each made by a method that holds it in a typed local
  (lessons/39).
- A local that a lambda captures becomes a `groovy.lang.Reference`, in the lambda and in the method that declares
  it, typed as its wrapper class: arithmetic on it is then Groovy's `Number` arithmetic, comparisons are
  `compareTo` (which orders NaN and -0.0), and `o.@f` on it is a reflective `getField`. Each captured local is
  therefore copied into a fresh local just before the lambda (only that one becomes a Reference), and copied again
  into a typed local at the start of the lambda body (lessons/38). A captured local is never assigned after the
  lambda is made (the Java translator's analysis puts such a local in a cell, and the cell is what is copied).
- A method reference (`X::new`, `o::m`) compiles to dynamic calls: it is a lambda with the delegate's parameters.

## Statements and names

- `switch` compiles to boxed `isCase()` calls: it is an if/else-if chain on a temporary of the selector's type
  (`java.util.Objects.equals` for strings), with C#'s trailing breaks dropped and a labelled
  `do { ... } while (false)` around it when a break is not the last statement of its section.
- A bare `{ }` is a closure: blocks are flattened (every local and temporary has a name unique in its method,
  which also keeps cs/gen's own `__i8` and `__r7` apart from the translator's), and a for loop's declarations
  go before it.
- Every loop is labelled (`__Lk:`) and every `break` and `continue` names its target.
- The gotos of hydrology/surface/UebReference become a state loop:
  `int __st = 0; __dispatch: for (;;) { if (__st == 0) { ... } else if (__st == 1) { ... } break __dispatch; }`,
  and `goto Lk` is `__st = k; continue __dispatch;`.
- Groovy's grammar reads a method whose return type starts with a lower-case letter or `_` (`io_X m() { }`) as a
  command expression, so such an engine type is `L` + its Java name (`Lio_CheckpointOutputPublication`; lessons/37).
- Groovy's keywords `in`, `as`, `def`, `trait` and `it` are renamed with an underscore, in the engine and in the
  runtime (`R.as_`, `Ios.in_`).
- A member declaration after a closing brace on the same line does not parse: the field-reference helpers
  (engine/FRef.groovy) put each member on its own line.

## The runtime: groovy/tools/J2Gv.java

J2Gv reads the Java runtime (java/src/cpp/*.java) with the JDK's own compiler (javac's tree API with
attribution) and edits the Java text where Groovy would read it differently: the literal, division, comparison,
identity and float rules above; a `char` operand of arithmetic, a method argument or a return value cast to `int`
where Java widens it; a boxed operand unboxed explicitly; switch labels written in the selector's type (Groovy's
`isCase` compares a `char` label with an `int` selector as unequal; lessons/35); lambdas parenthesized; array initializers
given `new T[]`; bare blocks as `if (true) { }`; a line break after each compound statement; instanceof patterns
as casts; method references as lambdas; `@SuppressWarnings` dropped; every top-level class `@CompileStatic`; and
`@groovy.transform.PackageScope` on every member without an access modifier (Java's package-private, Groovy's
public, and for a field a property with a getter and setter; lessons/36). Glibm.groovy and Glibm2.groovy (the math) come from
tools/libm/gim2x.py's Groovy back end, and GlibmRt.groovy, Glibm2Rt.groovy and GR.groovy are hand-written.

J2Gv does not rewrite the runtime's stores to other objects' fields (`node.next = n`): they stay reflective
`setGroovyObjectProperty` calls (241 sites, groovy/results/dynamic_calls.txt), which give the same result as a
`putfield` and cost time; the runtime checks pass with them. The engine has none.

Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see AI-USE.md and groovy/README.md.
