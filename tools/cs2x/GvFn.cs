// GvFn.cs - method bodies: C# statements and expressions (Roslyn IOperation trees) to Groovy 3 (static compilation).
// The Java translator's JavaFn.cs, copied and changed where Groovy reads Java's syntax differently:
//   * a decimal literal is a BigDecimal: double literals carry d (float f); -0.0d folds to +0.0, so it is cpp.GR.NZ;
//     the most negative int and long are Integer.MIN_VALUE and Long.MIN_VALUE; an int with the high bit set is
//     written in decimal (Groovy reads 0xFFFFFFFF as a long); strings are single-quoted (no $ interpolation)
//   * / on two integers is a BigDecimal: cpp.GR.idiv / ldiv; > and >= on floating-point values are true for NaN
//     (SWMM5 lessons/37): cpp.GR.dgt / dge; float arithmetic is computed in double: cast back to float
//   * == on references calls equals(): === (identity, as in C# and Java)
//   * a store to another object's field is reflective (ScriptBytecodeAdapter.setGroovyObjectProperty): o.@f = v
//   * switch compiles to boxed isCase() calls: an if-chain; { } is a closure: blocks are flattened (local names are
//     unique per method); every loop has a label and every break and continue names its target
//   * X::new and method references compile to dynamic calls: lambdas
// Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see AI-USE.md and groovy/README.md.
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.Operations;

public static partial class Cs2Groovy
{
    // references to fields (ref this.x, ref o.x, ref S.x): one helper each, in engine/FRef.java
    static readonly SortedDictionary<string, string> fieldRefs = new();

    static string FieldRefHelper(IFieldSymbol f)
    {
        var owner = TypeIdent(f.ContainingType);
        var name = (f.IsStatic ? "s_" : "f_") + owner + "_" + FieldIdent(f);
        if (!fieldRefs.ContainsKey(name))
        {
            var rt = RefType(f.Type);
            var jt = JType(f.Type);
            var target = f.IsStatic ? owner + "." + FieldIdent(f) : "o.@" + FieldIdent(f);
            var store = f.IsStatic ? owner + ".@" + FieldIdent(f) : target;
            var get = PrimCode(f.Type) != null ? jt : JBoxed(f.Type);
            // one member per line: Groovy does not parse a declaration after a closing brace on the same line
            var body = "new " + rt + "() {\n\t\tpublic " + get + " get() { return " + target + "; }\n\t\tpublic void set(" + get + " v) { " + store + " = v; }\n\t}";
            fieldRefs[name] = "\tpublic static " + rt + " " + name + "(" + (f.IsStatic ? "" : owner + " o") + ") { return " + body + "; }\n";
            var sb = new StringBuilder(FileHeader + "@groovy.transform.CompileStatic\npublic final class FRef {\n\tprivate FRef() { }\n");
            foreach (var kv in fieldRefs) sb.Append(kv.Value);
            extraFiles["FRef"] = sb.Append("}\n").ToString();
        }
        return "FRef." + name;
    }
}

public static partial class Cs2Groovy
{
public sealed class GvFn
{
    readonly IMethodSymbol? method;
    readonly INamedTypeSymbol cls;
    public List<string> Pre = new();
    readonly Dictionary<ISymbol, string> names;
    readonly HashSet<string> used;
    readonly int[] tmp;
    readonly List<string> prologue = new();
    ITypeSymbol? retType;
    bool retRef;
    readonly bool inLambda;
    readonly Stack<string> condAccess = new();
    readonly Stack<string> implicitRecv = new();     // the object of an object initializer

    public GvFn(IMethodSymbol? m, INamedTypeSymbol c)
    {
        method = m; cls = c;
        names = new(SymbolEqualityComparer.Default); used = new(); tmp = new int[1];
        retType = m?.ReturnType; retRef = m != null && m.RefKind != RefKind.None;
    }

    GvFn(GvFn parent, IMethodSymbol lambda)
    {
        inLambda = true;
        method = lambda; cls = parent.cls;
        names = new(parent.names, SymbolEqualityComparer.Default); used = parent.used; tmp = parent.tmp;
        retType = lambda.ReturnType; retRef = lambda.RefKind != RefKind.None;
        gotoLabels = parent.gotoLabels;
    }

    // a temporary: unique in the method (blocks are flattened, and cs/gen has locals named __i8 and __r7 of its own)
    string Fresh(string b)
    {
        string n;
        do { tmp[0]++; n = "__" + b + tmp[0]; } while (used.Contains(n));
        used.Add(n);
        return n;
    }

    string Declare(ISymbol s, string name)
    {
        var n = Id(name);
        if (n == "_" || n == "__") n = "__discard";
        var b = n; int k = 2;
        while (used.Contains(n)) n = b + "_" + k++;
        used.Add(n);
        names[s] = n;
        return n;
    }

    static bool Boxed(ISymbol s) => boxed.Contains(s);

    /// <summary>Declare a parameter; a cell parameter is renamed and copied into its cell by the prologue, and a
    /// parameter of a boxed interface signature is unboxed there.</summary>
    public string DeclareParam(IParameterSymbol p, bool boxedSig)
    {
        var n = Declare(p, p.Name);
        if (p.RefKind is RefKind.Ref or RefKind.Out) return n;
        if (Boxed(p))
        {
            var pn = n + "__p";
            used.Add(pn);
            prologue.Add(CellType(p.Type) + " " + n + " = new " + CellType(p.Type) + "(" + (boxedSig ? Unbox(pn, p.Type) : pn) + ");");
            return pn;
        }
        if (boxedSig)
        {
            var pn = n + "__b";
            used.Add(pn);
            prologue.Add(JType(p.Type) + " " + n + " = " + Unbox(pn, p.Type) + ";");
            return pn;
        }
        return n;
    }

    static string Unbox(string s, ITypeSymbol t) => PrimCode(t) != null ? "((" + JType(t) + ") " + s + ")" : s;

    public string Prologue() => string.Concat(prologue.Select(h => "\t\t" + h + "\n"));

    // -------------------------------------------------------------------------------------------- statements
    static string Ind(int n) => new string('\t', n);

    void Flush(List<string> lines, int ind)
    {
        foreach (var p in Pre) lines.Add(Ind(ind) + p);
        Pre.Clear();
    }

    // goto: a method whose labels all sit in its top-level block becomes an if-chain over a state in a labelled loop
    Dictionary<ILabelSymbol, int>? gotoLabels;

    public string Body(IOperation op, IMethodSymbol m)
    {
        var lines = new List<string>();
        var ops = op is IBlockOperation b ? b.Operations.ToList() : op is IMethodBodyOperation mb && mb.BlockBody != null ? mb.BlockBody.Operations.ToList() : new List<IOperation> { op };
        bool canEnd;
        if (op.Descendants().Any(d => d is IBranchOperation br && br.BranchKind == BranchKind.GoTo))
            canEnd = Dispatch(ops, lines, 2);
        else
            canEnd = Block(ops, lines, 2);
        if (canEnd && !(m.ReturnsVoid || m.MethodKind == MethodKind.Constructor || m.MethodKind == MethodKind.StaticConstructor))
            lines.Add(Ind(2) + "throw new IllegalStateException('unreachable');");
        return string.Concat(lines.Select(l => l + "\n"));
    }

    bool Dispatch(List<IOperation> ops, List<string> lines, int ind)
    {
        gotoLabels = new(SymbolEqualityComparer.Default);
        int k = 0;
        foreach (var o in ops) if (o is ILabeledOperation l) gotoLabels[l.Label] = ++k;
        int nseg = k;
        hoisting = true;
        var p = Ind(ind);
        var head = lines.Count;
        lines.Add(p + "int __st = 0;");
        lines.Add(p + "__dispatch: for (;;) {");
        var seg = new List<IOperation>();
        int cur = 0;
        void FlushSeg()
        {
            lines.Add(Ind(ind + 1) + (cur == 0 ? "if" : "else if") + " (__st == " + cur + ") {");
            bool ends = Block(seg, lines, ind + 2);
            if (ends) lines.Add(Ind(ind + 2) + (cur < nseg ? "__st = " + (cur + 1) + "; continue __dispatch;" : "break __dispatch;"));
            lines.Add(Ind(ind + 1) + "}");
            seg.Clear();
        }
        foreach (var o in ops)
        {
            if (o is ILabeledOperation l)
            {
                FlushSeg();
                cur = gotoLabels[l.Label];
                if (l.Operation != null) seg.Add(l.Operation);
            }
            else seg.Add(o);
        }
        FlushSeg();
        lines.Add(Ind(ind + 1) + "break __dispatch;");
        lines.Add(p + "}");
        lines.InsertRange(head, hoisted.Select(h => p + h));
        hoisting = false;
        return true;
    }

    bool hoisting;
    readonly List<string> hoisted = new();

    /// <summary>Statements of a block; those after one that cannot complete normally (Java's rule) are dropped.
    /// Returns whether the block can complete normally.</summary>
    bool Block(IEnumerable<IOperation> ops, List<string> lines, int ind)
    {
        var list = ops.ToList();
        for (int i = 0; i < list.Count; i++)
        {
            if (list[i] is IUsingDeclarationOperation ud)
                return UsingRest(ud, list.Skip(i + 1).ToList(), lines, ind);
            if (!Stmt(list[i], lines, ind))
            {
                // Java rejects an unreachable statement; a label after the jump would be reachable, but C# labels only
                // occur in dispatch methods, whose segments are separate blocks
                return false;
            }
        }
        return true;
    }

    bool Stmt(IOperation op, List<string> lines, int ind)
    {
        try { return StmtInner(op, lines, ind); }
        catch (global::Todo e)
        {
            Pre.Clear();
            lines.Add(Ind(ind) + "if (true) throw new IllegalStateException('TODO " + Esc(e.Message) + "');");
            return true;
        }
    }

    public static string Esc(string s) => s.Replace("\\", "\\\\").Replace("'", "\"").Replace("\n", " ");

    bool StmtInner(IOperation op, List<string> lines, int ind)
    {
        var p = Ind(ind);
        switch (op)
        {
            case IBlockOperation b:
                return Block(b.Operations, lines, ind);      // flattened: a bare { } is a closure in Groovy; local names are unique
            case IVariableDeclarationGroupOperation g:
                foreach (var d in g.Declarations)
                    foreach (var v in d.Declarators) LocalDecl(v, d.Initializer, lines, ind);
                return true;
            case IExpressionStatementOperation es:
                {
                    var s = StmtExpr(es.Operation, lines, ind);
                    Flush(lines, ind);
                    if (s.Length > 0) lines.Add(p + s + ";");
                    return true;
                }
            case IReturnOperation r:
                Return(r, lines, ind);
                return false;
            case IConditionalOperation c:
                return If(c, lines, ind);
            case IWhileLoopOperation w: return While(w, lines, ind);
            case IForLoopOperation f: return For(f, lines, ind);
            case IForEachLoopOperation fe: return ForEach(fe, lines, ind);
            case IBranchOperation br: Branch(br, lines, ind); return false;
            case ILabeledOperation l:
                Note("label outside a dispatch method");
                if (l.Operation != null) return Stmt(l.Operation, lines, ind);
                return true;
            case ISwitchOperation sw: return Switch(sw, lines, ind);
            case ITryOperation t: return Try(t, lines, ind);
            case IUsingOperation u: return Using(u, lines, ind);
            case IThrowOperation th:
                {
                    var v = th.Exception == null ? Rethrow() : X(th.Exception);
                    Flush(lines, ind);
                    lines.Add(p + "throw " + ThrowArg(v, th.Exception?.Type) + ";");
                    return false;
                }
            case IEmptyOperation: return true;
            case IUsingDeclarationOperation ud: return UsingRest(ud, new List<IOperation>(), lines, ind);
            case ILocalFunctionOperation: Todo("local function"); return true;
        }
        Todo("statement " + op.Kind);
        return true;
    }

    /// <summary>Java throws a Throwable; a value of a runtime exception type is one.</summary>
    static string ThrowArg(string v, ITypeSymbol? t) => v;

    void LocalDecl(IVariableDeclaratorOperation v, IVariableInitializerOperation? outerInit, List<string> lines, int ind)
    {
        var p = Ind(ind);
        var loc = v.Symbol;
        var init = v.Initializer ?? outerInit;
        if (loc.RefKind != RefKind.None)
        {
            var a = init == null ? "null" : RefOf(init.Value);
            Flush(lines, ind);
            var n = Declare(loc, loc.Name);
            Emit(lines, p, RefType(loc.Type), n, a);
            return;
        }
        var jt = JType(loc.Type);
        string val = init == null ? DefaultOf(loc.Type) : Value(init.Value, loc.Type);
        Flush(lines, ind);
        var name = Declare(loc, loc.Name);
        if (Boxed(loc))
        {
            var ct = CellType(loc.Type);
            Emit(lines, p, ct, name, "new " + ct + "(" + val + ")");
            return;
        }
        Emit(lines, p, jt, name, val);
    }

    void Emit(List<string> lines, string p, string type, string name, string val)
    {
        if (hoisting)
        {
            hoisted.Add(type + " " + name + " = " + DefaultForJava(type) + ";");
            lines.Add(p + name + " = " + val + ";");
            return;
        }
        lines.Add(p + type + " " + name + " = " + val + ";");
    }

    static string DefaultForJava(string t) => t switch
    {
        "boolean" => "false", "byte" => "(byte) 0", "short" => "(short) 0", "char" => "(char) 0", "int" => "0", "long" => "0L",
        "float" => "0.0f", "double" => "0.0d", _ => "null"
    };

    void Return(IReturnOperation r, List<string> lines, int ind)
    {
        var p = Ind(ind);
        string? v = null;
        if (r.ReturnedValue != null)
            v = retRef ? RefOf(r.ReturnedValue) : Value(r.ReturnedValue, retType);
        Flush(lines, ind);
        lines.Add(p + "return" + (v != null ? " " + v : "") + ";");
    }

    bool If(IConditionalOperation c, List<string> lines, int ind)
    {
        var p = Ind(ind);
        var cond = X(c.Condition);
        Flush(lines, ind);
        lines.Add(p + "if (" + Strip(cond) + ") {");
        var a = Sub(c.WhenTrue, lines, ind + 1);
        if (c.WhenFalse == null) { lines.Add(p + "}"); return true; }
        lines.Add(p + "} else {");
        var b = Sub(c.WhenFalse, lines, ind + 1);
        lines.Add(p + "}");
        return a || b;
    }

    static string Strip(string s) => s.Length > 1 && s[0] == '(' && s[^1] == ')' && Balanced(s.Substring(1, s.Length - 2)) ? s.Substring(1, s.Length - 2) : s;

    static bool Balanced(string s)
    {
        int d = 0;
        foreach (var ch in s) { if (ch == '(') d++; else if (ch == ')') { d--; if (d < 0) return false; } }
        return d == 0;
    }

    bool Sub(IOperation op, List<string> lines, int ind)
    {
        if (op is IBlockOperation b) return Block(b.Operations, lines, ind);
        return Stmt(op, lines, ind);
    }

    // which loops and switches a break leaves: Java's rule for whether a loop can complete normally
    static bool HasBreakOut(IOperation loop, ILabelSymbol? exit)
    {
        if (exit == null) return false;
        return loop.Descendants().Any(d => d is IBranchOperation b && b.BranchKind == BranchKind.Break && SymbolEqualityComparer.Default.Equals(b.Target, exit));
    }

    static bool IsTrue(IOperation? c) => c == null || c.ConstantValue.HasValue && c.ConstantValue.Value is true;

    // the label of each loop and if-chain switch, by Roslyn's exit and continue labels
    readonly Dictionary<ILabelSymbol, string> jumps = new(SymbolEqualityComparer.Default);
    void Enter(ILoopOperation loop, string label) { jumps[loop.ExitLabel] = label; jumps[loop.ContinueLabel] = label; }

    bool While(IWhileLoopOperation w, List<string> lines, int ind)
    {
        var p = Ind(ind);
        var lab = Fresh("L");
        Enter(w, lab);
        var brk = HasBreakOut(w, w.ExitLabel);
        if (w.ConditionIsTop)
        {
            var cond = w.Condition == null ? "true" : X(w.Condition);
            if (Pre.Count > 0)
            {
                var pre = new List<string>(Pre); Pre.Clear();
                lines.Add(p + lab + ": for (;;) {");
                foreach (var s in pre) lines.Add(Ind(ind + 1) + s);
                lines.Add(Ind(ind + 1) + "if (!(" + cond + ")) break " + lab + ";");
                Sub(w.Body, lines, ind + 1);
                lines.Add(p + "}");
                return true;
            }
            lines.Add(p + lab + ": while (" + Strip(cond) + ") {");
            Sub(w.Body, lines, ind + 1);
            lines.Add(p + "}");
            return !IsTrue(w.Condition) || brk;
        }
        var c = X(w.Condition!);
        if (Pre.Count > 0) Todo("do-while condition with statements");
        lines.Add(p + lab + ": do {");
        var bodyEnds = Sub(w.Body, lines, ind + 1);
        lines.Add(p + "} while (" + Strip(c) + ");");
        var cont = w.Descendants().Any(d => d is IBranchOperation b && b.BranchKind == BranchKind.Continue && SymbolEqualityComparer.Default.Equals(b.Target, w.ContinueLabel));
        return ((bodyEnds || cont) && !IsTrue(w.Condition)) || brk;
    }

    bool For(IForLoopOperation f, List<string> lines, int ind)
    {
        var p = Ind(ind);
        foreach (var b in f.Before) Stmt(b, lines, ind);          // flattened: the loop variables are unique names
        var lab = Fresh("L");
        Enter(f, lab);
        var cond = f.Condition == null ? "" : X(f.Condition);
        var condPre = new List<string>(Pre); Pre.Clear();
        var posts = new List<string>();
        foreach (var a in f.AtLoopBottom)
        {
            var s = a is IExpressionStatementOperation es ? StmtExpr(es.Operation, null, 0) : Todo("for-loop step " + a.Kind);
            if (Pre.Count > 0) Todo("for-loop step with statements");
            if (s.Length > 0) posts.Add(s);
        }
        if (condPre.Count > 0)
        {
            lines.Add(p + lab + ": for (;; " + string.Join(", ", posts) + ") {");
            foreach (var s in condPre) lines.Add(Ind(ind + 1) + s);
            lines.Add(Ind(ind + 1) + "if (!(" + cond + ")) break " + lab + ";");
        }
        else lines.Add(p + lab + ": for (; " + Strip(cond) + "; " + string.Join(", ", posts) + ") {");
        Sub(f.Body, lines, ind + 1);
        lines.Add(p + "}");
        return !IsTrue(f.Condition) || HasBreakOut(f, f.ExitLabel) || condPre.Count > 0;
    }

    bool ForEach(IForEachLoopOperation fe, List<string> lines, int ind)
    {
        var p = Ind(ind);
        var coll = fe.Collection is IConversionOperation cv && cv.IsImplicit ? cv.Operand : fe.Collection;
        var ct = coll.Type!;
        var src = X(coll);
        Flush(lines, ind);
        if (fe.LoopControlVariable is not IVariableDeclaratorOperation vd) { Todo("foreach variable"); return true; }
        var loc = vd.Symbol;
        var name = Declare(loc, loc.Name);
        string iter;
        if (ct is IArrayTypeSymbol) iter = src;
        else if (ct is INamedTypeSymbol nt && IsRuntime(nt)) { iter = Paren(src) + ".Range()"; Use("foreach " + nt.OriginalDefinition.ToDisplayString(), "Range() : java.lang.Iterable"); }
        else if (ct is INamedTypeSymbol ie && IsSystem(ie) && ie.Name == "IEnumerable") iter = src;
        else { Todo("foreach over " + ct.ToDisplayString()); return true; }
        if (Boxed(loc)) Todo("foreach variable in a cell");
        var et = JType(loc.Type);
        var elemT = ct is IArrayTypeSymbol ? et : JBoxed(loc.Type);
        var raw = Fresh("e");
        var lab = Fresh("L");
        Enter(fe, lab);
        lines.Add(p + lab + ": for (" + elemT + " " + raw + " : " + iter + ") {");
        lines.Add(Ind(ind + 1) + et + " " + name + " = " + (PrimCode(loc.Type) != null && !(ct is IArrayTypeSymbol) ? "((" + et + ") " + raw + ")" : raw) + ";");
        Sub(fe.Body, lines, ind + 1);
        lines.Add(p + "}");
        return true;
    }

    void Branch(IBranchOperation br, List<string> lines, int ind)
    {
        var p = Ind(ind);
        switch (br.BranchKind)
        {
            case BranchKind.Break:
                if (!jumps.TryGetValue(br.Target, out var lb)) { Todo("break target"); return; }
                lines.Add(p + "break " + lb + ";"); return;
            case BranchKind.Continue:
                if (!jumps.TryGetValue(br.Target, out var lc)) { Todo("continue target"); return; }
                lines.Add(p + "continue " + lc + ";"); return;
            case BranchKind.GoTo:
                if (gotoLabels != null && gotoLabels.TryGetValue(br.Target, out var k)) { lines.Add(p + "__st = " + k + "; continue __dispatch;"); return; }
                Todo("goto outside a dispatch method");
                return;
        }
        Todo("branch " + br.BranchKind);
    }

    /// <summary>switch as an if-chain on the value (Groovy's switch compares with boxed isCase calls). A break that is
    /// not the last statement of its section leaves a labelled do { } while (false) around the chain.</summary>
    bool Switch(ISwitchOperation sw, List<string> lines, int ind)
    {
        var p = Ind(ind);
        var vt = sw.Value.Type!;
        var v = X(sw.Value);
        Flush(lines, ind);
        bool hasDefault = sw.Cases.Any(c => c.Clauses.Any(cl => cl is IDefaultCaseClauseOperation));
        var lab = Fresh("sw");
        jumps[sw.ExitLabel] = lab;
        bool isStr = vt.SpecialType == SpecialType.System_String;
        bool wrap = sw.Cases.Any(c => c.Body.Select((o, k) => (o, k)).Any(x =>
            x.o.DescendantsAndSelf().Any(d => d is IBranchOperation b && b.BranchKind == BranchKind.Break && SymbolEqualityComparer.Default.Equals(b.Target, sw.ExitLabel))
            && !(x.k == c.Body.Length - 1 && x.o is IBranchOperation lb && lb.BranchKind == BranchKind.Break)));
        var t = Fresh("swv");
        lines.Add(p + JType(vt) + " " + t + " = " + Strip(v) + ";");
        int bi = ind;
        if (wrap) { lines.Add(p + lab + ": do {"); bi = ind + 1; }
        bool first = true;
        bool canEnd = !hasDefault;
        List<IOperation>? defBody = null;
        foreach (var c in sw.Cases)
        {
            var conds = new List<string>();
            bool isDef = false;
            foreach (var cl in c.Clauses)
            {
                if (cl is IDefaultCaseClauseOperation) { isDef = true; continue; }
                if (cl is ISingleValueCaseClauseOperation sv)
                    conds.Add(isStr ? "java.util.Objects.equals(" + t + ", " + X(sv.Value) + ")" : t + " == " + Strip(Value(sv.Value, vt)));
                else Todo("case clause " + cl.CaseKind);
            }
            var body = c.Body.ToList();
            if (body.Count > 0 && body[^1] is IBranchOperation last && last.BranchKind == BranchKind.Break && SymbolEqualityComparer.Default.Equals(last.Target, sw.ExitLabel))
            { body.RemoveAt(body.Count - 1); canEnd = true; }
            if (isDef) { defBody = body; continue; }        // C#'s sections never fall through: default can go last
            lines.Add(Ind(bi) + (first ? "if (" : "} else if (") + string.Join(" || ", conds) + ") {");
            first = false;
            if (Block(body, lines, bi + 1)) canEnd = true;
        }
        if (defBody != null)
        {
            lines.Add(Ind(bi) + (first ? "if (true) {" : "} else {"));
            if (Block(defBody, lines, bi + 1)) canEnd = true;
            first = false;
        }
        if (!first) lines.Add(Ind(bi) + "}");
        if (wrap) lines.Add(p + "} while (false);");
        return canEnd || HasBreakOut(sw, sw.ExitLabel) || sw.Cases.Length == 0;
    }

    // -------------------------------------------------------------------------------------------- try, using
    string? rethrowVar;

    string Rethrow() => rethrowVar ?? Todo("throw; outside a catch");

    bool Try(ITryOperation t, List<string> lines, int ind)
    {
        var p = Ind(ind);
        lines.Add(p + "try {");
        var ends = Block(t.Body.Operations, lines, ind + 1);
        foreach (var cc in t.Catches)
        {
            var et = cc.ExceptionType;
            string jt;
            if (et.SpecialType == SpecialType.System_Object || et is INamedTypeSymbol en && IsSystem(en) && en.Name == "Exception") jt = "Throwable";
            else jt = JType(et);
            string ev;
            if (cc.ExceptionDeclarationOrExpression is IVariableDeclaratorOperation vd) ev = Declare(vd.Symbol, vd.Symbol.Name);
            else { ev = Fresh("ex"); used.Add(ev); }
            lines.Add(p + "} catch (" + jt + " " + ev + ") {");
            var saved = rethrowVar; rethrowVar = ev;
            if (cc.Filter != null) Todo("exception filter");
            if (Block(cc.Handler.Operations, lines, ind + 1)) ends = true;
            rethrowVar = saved;
        }
        if (t.Finally != null)
        {
            lines.Add(p + "} finally {");
            var f = Block(t.Finally.Operations, lines, ind + 1);
            if (!f) ends = false;
        }
        lines.Add(p + "}");
        return ends;
    }

    bool UsingRest(IUsingDeclarationOperation ud, List<IOperation> rest, List<string> lines, int ind)
    {
        var p = Ind(ind);
        var decls = ud.DeclarationGroup.Declarations.SelectMany(d => d.Declarators).ToList();
        foreach (var d in ud.DeclarationGroup.Declarations)
            foreach (var v in d.Declarators) LocalDecl(v, d.Initializer, lines, ind);
        lines.Add(p + "try {");
        var ends = Block(rest, lines, ind + 1);
        lines.Add(p + "} finally {");
        foreach (var v in decls.AsEnumerable().Reverse())
        {
            var n = Read(v.Symbol);
            lines.Add(Ind(ind + 1) + "if (" + n + " != null) " + n + ".Dispose();");
        }
        lines.Add(p + "}");
        Use("using: IDisposable.Dispose()", "cpp.IDisposable.Dispose()");
        return ends;
    }

    bool Using(IUsingOperation u, List<string> lines, int ind)
    {
        var p = Ind(ind);
        lines.Add(p + "{");
        var names0 = new List<string>();
        if (u.Resources is IVariableDeclarationGroupOperation g)
        {
            foreach (var d in g.Declarations)
                foreach (var v in d.Declarators) { LocalDecl(v, d.Initializer, lines, ind + 1); names0.Add(Read(v.Symbol)); }
        }
        else
        {
            var r = Fresh("res");
            lines.Add(Ind(ind + 1) + "cpp.IDisposable " + r + " = " + X(u.Resources) + ";");
            names0.Add(r);
        }
        lines.Add(Ind(ind + 1) + "try {");
        var ends = Sub(u.Body, lines, ind + 2);
        lines.Add(Ind(ind + 1) + "} finally {");
        foreach (var n in names0.AsEnumerable().Reverse()) lines.Add(Ind(ind + 2) + "if (" + n + " != null) " + n + ".Dispose();");
        lines.Add(Ind(ind + 1) + "}");
        lines.Add(p + "}");
        return ends;
    }

    string Read(ILocalSymbol l) => Boxed(l) ? Name(l) + ".@v" : l.RefKind != RefKind.None ? Name(l) + ".get()" : Name(l);

    // -------------------------------------------------------------------------------------------- expression statements
    /// <summary>An expression used as a statement, without its semicolon. lines may be null (a for-loop step).</summary>
    string StmtExpr(IOperation op, List<string>? lines, int ind)
    {
        switch (op)
        {
            case ISimpleAssignmentOperation a:
                {
                    if (a.IsRef) return RefRebind(a.Target, RefOf(a.Value));
                    var v = Value(a.Value, a.Target.Type);
                    return Assign(a.Target, v);
                }
            case ICompoundAssignmentOperation ca: return Compound(ca, true);
            case IIncrementOrDecrementOperation inc: return IncDec(inc, true);
            case IInvocationOperation:
            case IObjectCreationOperation:
                {
                    var s = X(op);
                    if (op is IObjectCreationOperation) return "cpp.R.discard(" + s + ")";
                    if (op is IInvocationOperation iv && (iv.TargetMethod.RefKind != RefKind.None || IsUnboxCast(s))) return "cpp.R.discard(" + s + ")";
                    return s;
                }
            case IDiscardOperation: return "";
            case IConditionalAccessOperation ca when lines != null:
                {
                    // a?.M();  ->  if (a != null) a.M();
                    var recv = X(ca.Operation);
                    string r = recv;
                    if (!Simple(recv)) { r = Fresh("ca"); Pre.Add(JType(ca.Operation.Type!) + " " + r + " = " + recv + ";"); }
                    condAccess.Push(r);
                    var inner = StmtExpr(ca.WhenNotNull, lines, ind);
                    condAccess.Pop();
                    return "if (" + r + " != null) " + inner;
                }
            default:
                {
                    var s = X(op);
                    return "cpp.R.discard(" + s + ")";
                }
        }
    }

    /// <summary>s is a cast expression ((T) e), which Java does not accept as a statement.</summary>
    static bool IsUnboxCast(string s)
    {
        if (!(s.StartsWith("((") && s.EndsWith(")") && Balanced(s.Substring(1, s.Length - 2)))) return false;
        var close = s.IndexOf(')', 2);
        if (close < 0) return false;
        var ty = s.Substring(2, close - 2);
        return ty.Length > 0 && ty.All(ch => char.IsLetterOrDigit(ch) || ch is '_' or '.' or '<' or '>' or '[' or ']' or ',' or ' ') && close + 1 < s.Length && s[close + 1] == ' ';
    }

    string RefRebind(IOperation target, string r)
    {
        if (target is ILocalReferenceOperation l && l.Local.RefKind != RefKind.None) return names[l.Local] + " = " + r;
        return Todo("ref reassignment of " + target.Kind);
    }

    // -------------------------------------------------------------------------------------------- places
    /// <summary>A Java lvalue for op, if it has one (a local, a field, an array or vector element).</summary>
    string? Direct(IOperation op)
    {
        switch (op)
        {
            case ILocalReferenceOperation l:
                if (l.Local.RefKind != RefKind.None) return null;
                return Boxed(l.Local) ? Name(l.Local) + ".@v" : Name(l.Local);
            case IParameterReferenceOperation pr:
                if (pr.Parameter.RefKind is RefKind.Ref or RefKind.Out) return null;
                return Boxed(pr.Parameter) ? Name(pr.Parameter) + ".@v" : Name(pr.Parameter);
            case IFieldReferenceOperation f:
                if (IsRuntime(f.Field) && GenericMember(f.Field.OriginalDefinition.Type, f.Field.Type)) return null;
                return Field(f, false);
            case IArrayElementReferenceOperation ae:
                return X(ae.ArrayReference) + "[" + Index(ae.Indices[0]) + "]";
            case IPropertyReferenceOperation p when VecElem(p, out var vs, out var vi):
                return vs + "[" + vi + "]";       // a generic vector's Object[] takes any element
            case IConversionOperation cv when cv.Conversion.IsIdentity: return Direct(cv.Operand);
        }
        return null;
    }

    string Name(ISymbol s) => names.TryGetValue(s, out var n) ? n : Todo("unknown symbol " + s.Name);

    /// <summary>A place read and written in one statement (x = x op v): a receiver, array or index with a call in it
    /// is evaluated once, into a temporary before the statement, as C#'s x op= v evaluates it once.</summary>
    string Once(IOperation target, string d)
    {
        static bool Pure(string s) => !s.Contains('(') || IsCastOfName(s);
        switch (target)
        {
            case IFieldReferenceOperation f when !f.Field.IsStatic && f.Instance != null:
                {
                    var at = d.LastIndexOf(".@", StringComparison.Ordinal);
                    var recv = d.Substring(0, at);
                    if (Pure(recv)) return d;
                    var t = Fresh("o");
                    Pre.Add(JType(f.Instance.Type!) + " " + t + " = " + Strip(recv) + ";");
                    return t + d.Substring(at);
                }
            case IArrayElementReferenceOperation ae:
                {
                    var arr = X(ae.ArrayReference); var ix = Index(ae.Indices[0]);
                    if (!Pure(arr)) { var t = Fresh("a"); Pre.Add(JType(ae.ArrayReference.Type!) + " " + t + " = " + Strip(arr) + ";"); arr = t; }
                    if (!Pure(ix)) { var t = Fresh("i"); Pre.Add("int " + t + " = " + Strip(ix) + ";"); ix = t; }
                    return arr + "[" + ix + "]";
                }
            case IPropertyReferenceOperation p when VecElem(p, out var vs, out var vi):
                {
                    if (!Pure(vs)) { var t = Fresh("a"); Pre.Add((VecGeneric(p) ? "Object[]" : JType(p.Type!) + "[]") + " " + t + " = " + Strip(vs) + ";"); vs = t; }
                    if (!Pure(vi)) { var t = Fresh("i"); Pre.Add("int " + t + " = " + Strip(vi) + ";"); vi = t; }
                    return vs + "[" + vi + "]";
                }
        }
        return d;
    }

    /// <summary>The place d as a value: a static field is stored as Cls.@f (putstatic) and read as Cls.f (getstatic;
    /// Cls.@f as a value is a reflective ScriptBytecodeAdapter.getField).</summary>
    static string ReadForm(IOperation target, string d)
    {
        if (target is IFieldReferenceOperation { Field.IsStatic: true })
        {
            var at = d.LastIndexOf(".@", StringComparison.Ordinal);
            return at < 0 ? d : d.Substring(0, at) + "." + d.Substring(at + 2);
        }
        return d;
    }

    /// <summary>((T) name): a cast of a plain name, which has no call in it.</summary>
    static bool IsCastOfName(string s)
    {
        var m = System.Text.RegularExpressions.Regex.Match(s, @"^\(*\(\(?[A-Za-z_][A-Za-z0-9_.<>, \[\]]*\)\s*[A-Za-z_][A-Za-z0-9_.@]*\)*$");
        return m.Success;
    }

    string Assign(IOperation target, string v)
    {
        var d = Direct(target);
        if (d != null) return d + " = " + v;
        if (target is IPropertyReferenceOperation cp && IsCPtrIndexer(cp)) return Recv(cp.Instance!) + ".set(" + Index(cp.Arguments[0].Value) + ", " + v + ")";
        if (target is IPropertyReferenceOperation sp && !sp.Property.IsIndexer && sp.Property.SetMethod != null && sp.Property.RefKind == RefKind.None)
            return PropSet(sp, v);
        return RefOf(target) + ".set(" + v + ")";
    }

    string Compound(ICompoundAssignmentOperation ca, bool stmt)
    {
        var tt = ca.Target.Type!;
        string Combine(string cur)
        {
            if (ca.OperatorMethod != null) return UserBinary(ca.OperatorMethod, cur, X(ca.Value));
            var opT = ca.InConversion.Exists && !ca.InConversion.IsIdentity ? ca.Value.Type! : tt;
            var l = Conv(cur, tt, opT);
            var r = X(ca.Value);
            return Conv(BinOp(ca.OperatorKind, opT, l, r, ca.Value.Type), opT, tt);
        }
        var d = Direct(ca.Target);
        if (d != null)
        {
            // Java's own op= when it means the same as C#'s: the same promotions and no unsigned arithmetic
            if (ca.OperatorMethod == null && SafeCompound(ca, d)) return (stmt ? "" : "(") + d + " " + JavaOp(ca.OperatorKind) + "= " + X(ca.Value) + (stmt ? "" : ")");
            d = Once(ca.Target, d);
            var dr = ReadForm(ca.Target, d);
            if (!stmt) return "(" + d + " = " + Combine(dr) + ")";
            return d + " = " + Combine(dr);
        }
        if (ca.Target is IPropertyReferenceOperation cp && IsCPtrIndexer(cp))
        {
            var pr = Recv(cp.Instance!); var ix = Index(cp.Arguments[0].Value);
            if (!Simple(pr)) { var t0 = Fresh("p"); Pre.Add("cpp.CPtr " + t0 + " = " + pr + ";"); pr = t0; }
            if (!Simple(ix)) { var t1 = Fresh("i"); Pre.Add("long " + t1 + " = " + ix + ";"); ix = t1; }
            var nv = Combine(pr + ".get(" + ix + ")");
            return stmt ? pr + ".set(" + ix + ", " + nv + ")" : pr + ".assign(" + ix + ", " + nv + ")";
        }
        var r0 = RefOf(ca.Target);
        var rv = r0;
        if (!Simple(r0)) { rv = Fresh("r"); Pre.Add(RefType(tt) + " " + rv + " = " + r0 + ";"); }
        var val = Combine(rv + ".get()");
        if (stmt) return rv + ".set(" + val + ")";
        return RefType(tt) + ".assign(" + rv + ", " + val + ")";
    }

    bool SafeCompound(ICompoundAssignmentOperation ca, string d)
    {
        var t = ca.Target.Type!;
        var vt = ca.Value.Type;
        var k = ca.OperatorKind;
        // Groovy: x.@f op= v reads the field reflectively; a[i] /= v is a dynamic call and int /= int a BigDecimal
        // division; float, byte, short and char arithmetic is not Java's
        if (d.Contains(".@") || k == BinaryOperatorKind.Divide) return false;
        if (t.SpecialType is SpecialType.System_Single or SpecialType.System_Byte or SpecialType.System_SByte or SpecialType.System_Int16 or SpecialType.System_UInt16 or SpecialType.System_Char) return false;
        if (t.SpecialType == SpecialType.System_Boolean) return k is BinaryOperatorKind.And or BinaryOperatorKind.Or or BinaryOperatorKind.ExclusiveOr;
        if (t.SpecialType == SpecialType.System_String) return false;
        if (PrimCode(t) == null) return false;
        bool unsignedT = IsUnsigned(t) || IsUnsigned(vt);
        if (unsignedT && k is BinaryOperatorKind.Divide or BinaryOperatorKind.Remainder or BinaryOperatorKind.RightShift) return false;
        if (t.SpecialType is SpecialType.System_Byte or SpecialType.System_UInt16 && k is not (BinaryOperatorKind.Add or BinaryOperatorKind.Subtract or BinaryOperatorKind.Multiply or BinaryOperatorKind.And or BinaryOperatorKind.Or or BinaryOperatorKind.ExclusiveOr or BinaryOperatorKind.LeftShift)) return false;
        // a float target with a double value: C# and Java both compute in double and narrow
        if (IsFloat(t) && IsIntegral(vt)) return true;
        if (IsIntegral(t) && IsFloat(vt)) return false;      // Java would narrow with its own float-to-int rule
        if (vt != null && IsUnsigned(vt) && !IsUnsigned(t) && Width(vt) >= Width(t)) return true;
        if (vt != null && (IsUnsigned(vt) && Width(vt) < Width(t))) return false;   // the value would be sign-extended
        return true;
    }

    static int Width(ITypeSymbol? t) => t?.SpecialType switch
    {
        SpecialType.System_Byte or SpecialType.System_SByte => 8, SpecialType.System_Int16 or SpecialType.System_UInt16 or SpecialType.System_Char => 16,
        SpecialType.System_Int32 or SpecialType.System_UInt32 => 32, SpecialType.System_Int64 or SpecialType.System_UInt64 => 64, _ => 0
    };

    string IncDec(IIncrementOrDecrementOperation inc, bool stmt)
    {
        var up = inc.Kind == OperationKind.Increment;
        var post = inc.IsPostfix;
        var tt = inc.Target.Type!;
        if (inc.OperatorMethod != null)
        {
            var m = up ? "op_Increment" : "op_Decrement";
            var cls0 = RuntimeClass((INamedTypeSymbol)tt);
            Use("operator " + inc.OperatorMethod.ToDisplayString(), cls0 + "." + m);
            var d0 = Direct(inc.Target);
            if (d0 != null)
            {
                if (stmt) return d0 + " = " + cls0 + "." + m + "(" + d0 + ")";
                if (!post) return "(" + d0 + " = " + cls0 + "." + m + "(" + d0 + "))";
                var t0 = Fresh("old");
                Pre.Add(JType(tt) + " " + t0 + " = " + d0 + ";");
                Pre.Add(d0 + " = " + cls0 + "." + m + "(" + d0 + ");");
                return t0;
            }
            var r1 = RefOf(inc.Target);
            if (stmt) { var rt = Fresh("r"); Pre.Add(RefType(tt) + " " + rt + " = " + r1 + ";"); return rt + ".set(" + cls0 + "." + m + "(" + rt + ".get()))"; }
            return Todo("increment of a struct through a reference, as a value");
        }
        var d = Direct(inc.Target);
        if (d != null && (d.Contains(".@") || tt.SpecialType is SpecialType.System_Single or SpecialType.System_Byte or SpecialType.System_SByte or SpecialType.System_Int16 or SpecialType.System_UInt16 or SpecialType.System_Char))
        {
            // Groovy: x.@f++ reads the field reflectively, and ++ on a char is Character.next(): written out
            d = Once(inc.Target, d);
            var dr = ReadForm(inc.Target, d);
            var ut = Under(tt);
            var wide = ut.SpecialType is SpecialType.System_Int64 or SpecialType.System_UInt64 ? "L" : ut.SpecialType == SpecialType.System_Double ? ".0d" : ut.SpecialType == SpecialType.System_Single ? ".0f" : "";
            var cur = PrimCode(tt) is "B" or "S" or "C" ? "((int) " + dr + ")" : dr;
            var nv = "(" + cur + (up ? " + 1" : " - 1") + wide + ")";
            if (PrimCode(tt) is "B" or "S" or "C" or "F") nv = "((" + JType(tt) + ") " + nv + ")";
            if (stmt) return d + " = " + Strip(nv);
            if (!post) return "(" + d + " = " + Strip(nv) + ")";
            var old = Fresh("old");
            Pre.Add(JType(tt) + " " + old + " = " + dr + ";");
            Pre.Add(d + " = " + Strip(nv) + ";");
            return old;
        }
        if (d != null) return stmt ? d + (up ? "++" : "--") : "(" + (post ? d + (up ? "++" : "--") : (up ? "++" : "--") + d) + ")";
        if (inc.Target is IPropertyReferenceOperation cp && IsCPtrIndexer(cp))
        {
            var pr = Recv(cp.Instance!); var ix = Index(cp.Arguments[0].Value);
            return pr + "." + (post ? "post" : "pre") + (up ? "Inc" : "Dec") + "(" + ix + ")";
        }
        var r = RefOf(inc.Target);
        return RefType(tt) + "." + (post ? "post" : "pre") + (up ? "Inc" : "Dec") + "(" + r + ")";
    }

    // -------------------------------------------------------------------------------------------- references
    /// <summary>A Java Ref object for the location op names (a ref argument, a ref local, ref return).</summary>
    public string RefOf(IOperation op)
    {
        switch (op)
        {
            case ILocalReferenceOperation l:
                if (l.Local.RefKind != RefKind.None || Boxed(l.Local)) return Name(l.Local);
                return Todo("reference to a local that is not a cell: " + l.Local.Name);
            case IParameterReferenceOperation p:
                if (p.Parameter.RefKind is RefKind.Ref or RefKind.Out || Boxed(p.Parameter)) return Name(p.Parameter);
                return Todo("reference to a parameter that is not a cell: " + p.Parameter.Name);
            case IFieldReferenceOperation f:
                {
                    var fs = f.Field;
                    if (IsRuntime(fs))
                    {
                        var n = "ref_" + fs.Name;
                        Use("field ref " + fs.OriginalDefinition.ToDisplayString(), n + "()");
                        var r = Recv(f.Instance!) + "." + n + "()";
                        return GenericMember(fs.OriginalDefinition.Type, fs.Type) && !SpecializedClass(fs.ContainingType) ? Rewrap(r, fs.Type) : r;
                    }
                    var h = FieldRefHelper(fs);
                    return fs.IsStatic ? h + "()" : h + "(" + X(f.Instance!) + ")";
                }
            case IArrayElementReferenceOperation ae:
                {
                    var et = ((IArrayTypeSymbol)ae.ArrayReference.Type!).ElementType;
                    var rt = PrimCode(et) != null ? RefType(et) : "cpp.RefO";
                    return "new " + rt + ".Arr" + (PrimCode(et) != null ? "" : "<>") + "(" + X(ae.ArrayReference) + ", " + Index(ae.Indices[0]) + ")";
                }
            case IPropertyReferenceOperation pr:
                {
                    if (VecElem(pr, out var vs, out var vi))
                    {
                        var et = pr.Type!;
                        if (VecGeneric(pr)) return "new cpp.RefO.Arr<" + JBoxed(et) + ">(" + vs + ", " + vi + ")";
                        return "new " + RefType(et) + ".Arr(" + vs + ", " + vi + ")";
                    }
                    if (IsCPtrIndexer(pr)) return Recv(pr.Instance!) + ".ref(" + Index(pr.Arguments[0].Value) + ")";
                    if (pr.Property.RefKind != RefKind.None) return Prop(pr, true);
                    return Todo("reference to a property that returns no reference: " + pr.Property.Name);
                }
            case IInvocationOperation inv when inv.TargetMethod.RefKind != RefKind.None:
                return Invoke(inv, true);
            case IConditionalOperation c when c.IsRef:
                return "(" + X(c.Condition) + " ? " + RefOf(c.WhenTrue) + " : " + RefOf(c.WhenFalse!) + ")";
            case IDiscardOperation d:
                // out _: a cell nobody reads
                return "new " + CellType(d.Type!) + "(" + DefaultOf(d.Type!) + ")";
            case IConversionOperation cv when cv.Conversion.IsIdentity:
                return RefOf(cv.Operand);
        }
        return Todo("reference to " + op.Kind);
    }

    /// <summary>A RefO&lt;Double&gt; from a generic container seen as the RefD the code expects.</summary>
    static string Rewrap(string r, ITypeSymbol t)
    {
        var c = PrimCode(t);
        if (c == null) return r;
        Use("Ref" + c + ".ofBoxed(RefO)", "cpp.Ref" + c + ".ofBoxed");
        return "cpp.Ref" + c + ".ofBoxed(" + r + ")";
    }

    /// <summary>A member whose C# type is a type parameter and whose constructed type is primitive: in Java it is
    /// boxed, so reading it needs a cast and a reference to it needs Rewrap.</summary>
    static bool GenericMember(ITypeSymbol def, ITypeSymbol constructed) => def is ITypeParameterSymbol && PrimCode(constructed) != null;

    // -------------------------------------------------------------------------------------------- expressions
    /// <summary>The value of op, converted (as C# does at this point) to target if given.</summary>
    public string Value(IOperation op, ITypeSymbol? target)
    {
        var s = X(op);
        if (target != null && op.Type != null && !SymbolEqualityComparer.Default.Equals(op.Type, target))
            s = Conv(s, op.Type, target);
        return s;
    }

    static bool IsFloat(ITypeSymbol? t) => t != null && (t.SpecialType == SpecialType.System_Double || t.SpecialType == SpecialType.System_Single);
    static bool IsIntegral(ITypeSymbol? t) => t != null && t.SpecialType is SpecialType.System_Int32 or SpecialType.System_Int64 or SpecialType.System_UInt32 or SpecialType.System_UInt64 or SpecialType.System_Int16 or SpecialType.System_UInt16 or SpecialType.System_SByte or SpecialType.System_Byte or SpecialType.System_Char;
    static bool IsUnsigned(ITypeSymbol? t) => t != null && (t.SpecialType is SpecialType.System_UInt32 or SpecialType.System_UInt64 or SpecialType.System_Byte or SpecialType.System_UInt16 or SpecialType.System_Char
        || t.TypeKind == TypeKind.Enum && t is INamedTypeSymbol e && IsUnsigned(e.EnumUnderlyingType));
    static bool IsNumeric(ITypeSymbol? t) => IsFloat(t) || IsIntegral(t);
    static ITypeSymbol Under(ITypeSymbol t) => t.TypeKind == TypeKind.Enum && t is INamedTypeSymbol e ? e.EnumUnderlyingType! : t;

    public string X(IOperation op)
    {
        if (op.ConstantValue.HasValue && !(op is IFieldReferenceOperation fr0 && fr0.Field.ContainingType.TypeKind == TypeKind.Enum && IsEngine(fr0.Field.ContainingType)))
            return Const(op.ConstantValue.Value, op.Type);
        switch (op)
        {
            case IConversionOperation c: return Conversion(c);
            case ILocalReferenceOperation l: return Read(l.Local);
            case IParameterReferenceOperation pr:
                {
                    var n = Name(pr.Parameter);
                    if (pr.Parameter.RefKind is RefKind.Ref or RefKind.Out) return n + ".get()";
                    return Boxed(pr.Parameter) ? n + ".@v" : n;
                }
            case IInstanceReferenceOperation ir:
                if (ir.ReferenceKind == InstanceReferenceKind.ImplicitReceiver) return implicitRecv.Count > 0 ? implicitRecv.Peek() : Todo("implicit receiver");
                return "this";
            case IFieldReferenceOperation f: return Field(f, true);
            case IPropertyReferenceOperation p: return Prop(p, false);
            case IArrayElementReferenceOperation ae:
                {
                    var e = X(ae.ArrayReference) + "[" + Index(ae.Indices[0]) + "]";
                    // a generic runtime class's T[] field (Vec<T>.a) is an Object[] in Java
                    if (ae.ArrayReference is IFieldReferenceOperation af && IsRuntime(af.Field) && af.Field.OriginalDefinition.Type is IArrayTypeSymbol { ElementType: ITypeParameterSymbol }
                        && !SpecializedClass(af.Field.ContainingType))
                        return "((" + JType(ae.Type!) + ") " + e + ")";
                    return e;
                }
            case IInvocationOperation inv: return Invoke(inv, false);
            case IObjectCreationOperation oc: return New(oc);
            case IArrayCreationOperation ac: return NewArray(ac);
            case IBinaryOperation b: return Binary(b);
            case IUnaryOperation u: return Unary(u);
            case IConditionalOperation c: return Ternary(c);
            case ISimpleAssignmentOperation a:
                {
                    var v = Value(a.Value, a.Target.Type);
                    var d = Direct(a.Target);
                    if (d != null) return "(" + d + " = " + v + ")";
                    if (a.Target is IPropertyReferenceOperation cp && IsCPtrIndexer(cp)) return Recv(cp.Instance!) + ".assign(" + Index(cp.Arguments[0].Value) + ", " + v + ")";
                    return RefType(a.Target.Type!) + ".assign(" + RefOf(a.Target) + ", " + v + ")";
                }
            case ICompoundAssignmentOperation ca: return Compound(ca, false);
            case IIncrementOrDecrementOperation inc: return IncDec(inc, false);
            case IConditionalAccessOperation ca: return CondAccess(ca);
            case IConditionalAccessInstanceOperation: return condAccess.Count > 0 ? condAccess.Peek() : Todo("conditional access instance");
            case IAnonymousFunctionOperation af: return Lambda(af, null);
            case IDelegateCreationOperation dc: return DelegateCreation(dc);
            case IDefaultValueOperation dv: return DefaultOf(dv.Type!);
            case ILiteralOperation lit: return Const(lit.ConstantValue.Value, lit.Type);
            case IIsTypeOperation it: return "(" + X(it.ValueOperand) + " instanceof " + InstanceOfType(it.TypeOperand) + ")";
            case IThrowOperation th:
                {
                    var t = th.Type ?? op.Parent?.Type;
                    Use("throw expression", "cpp.R.thr(Throwable)");
                    var e = X(th.Exception!);
                    return t != null && PrimCode(t) != null ? "((" + JType(t) + ") cpp.R.<" + JBoxed(t) + ">thr(" + e + "))" : "cpp.R.thr(" + e + ")";
                }
            case IParenthesizedOperation pp: return X(pp.Operand);
            case IDiscardOperation: return Todo("discard as a value");
        }
        return Todo("expression " + op.Kind);
    }

    static string InstanceOfType(ITypeSymbol t) => PrimCode(t) != null ? JBoxed(t) : Raw(JType(t));
    static string Raw(string t) { var i = t.IndexOf('<'); return i < 0 ? t : t.Substring(0, i); }

    string Index(IOperation i)
    {
        // Java indexes arrays with an int
        var t = i.Type;
        var s = X(i);
        if (t == null || t.SpecialType == SpecialType.System_Int32) return Strip(s);
        if (t.SpecialType is SpecialType.System_Int16 or SpecialType.System_SByte or SpecialType.System_Char) return s;
        if (t.SpecialType == SpecialType.System_Byte) return "(" + s + " & 0xFF)";
        if (t.SpecialType == SpecialType.System_UInt16) return s;
        return "(int) " + Paren(s);
    }

    static string Paren(string s) => Simple(s) || s.StartsWith("(") && s.EndsWith(")") && Balanced(s.Substring(1, s.Length - 2)) ? s : "(" + s + ")";

    // ---- constants
    public static string Const(object? v, ITypeSymbol? t)
    {
        if (v == null) return t != null && t.IsValueType ? DefaultOf(t) : "null";
        if (t != null && t.TypeKind == TypeKind.Enum) t = Under(t);
        switch (v)
        {
            case bool b: return b ? "true" : "false";
            case string s: return JStr(s);
            case char ch: return "((char) " + (int)ch + ")";
            case double d: return t != null && t.SpecialType == SpecialType.System_Single ? FloatLit((float)d) : DoubleLit(d);
            case float f: return t != null && t.SpecialType == SpecialType.System_Double ? DoubleLit(f) : FloatLit(f);
        }
        // integers: written as the Java type holds them (an unsigned value as its bit pattern)
        var st = t?.SpecialType ?? SpecialType.System_Int32;
        ulong bits = v switch { int i => (ulong)(long)i, long l => (ulong)l, uint u => u, ulong ul => ul, short sh => (ulong)(long)sh, ushort us => us, byte by => by, sbyte sb => (ulong)(long)sb, _ => 0 };
        // Groovy: decimal, the most negative values by name (-2147483648 would be the negation of a long), negatives in
        // parentheses (a cast followed by a minus sign can read as a subtraction)
        switch (st)
        {
            case SpecialType.System_Int64: case SpecialType.System_UInt64: return LongLit((long)bits);
            case SpecialType.System_Byte: case SpecialType.System_SByte: return "((byte) " + IntLit((sbyte)(byte)bits) + ")";
            case SpecialType.System_Int16: return "((short) " + IntLit((short)bits) + ")";
            case SpecialType.System_UInt16: case SpecialType.System_Char: return "((char) " + (ushort)bits + ")";
            case SpecialType.System_Double: return DoubleLit((double)(long)bits);
            case SpecialType.System_Single: return FloatLit((float)(long)bits);
            default: return IntLit((int)bits);
        }
    }

    static string IntLit(int i) => i == int.MinValue ? "Integer.MIN_VALUE" : i < 0 ? "(" + i.ToString(CultureInfo.InvariantCulture) + ")" : i.ToString(CultureInfo.InvariantCulture);
    static string LongLit(long l) => l == long.MinValue ? "Long.MIN_VALUE" : l < 0 ? "(" + l.ToString(CultureInfo.InvariantCulture) + "L)" : l.ToString(CultureInfo.InvariantCulture) + "L";

    static string DoubleLit(double d)
    {
        if (double.IsNaN(d)) return "Double.longBitsToDouble(" + LongLit(BitConverter.DoubleToInt64Bits(d)) + ")";
        if (double.IsPositiveInfinity(d)) return "Double.POSITIVE_INFINITY";
        if (double.IsNegativeInfinity(d)) return "Double.NEGATIVE_INFINITY";
        if (d == 0 && double.IsNegative(d)) return "cpp.GR.NZ";          // Groovy folds -0.0d to +0.0
        var s = d.ToString("R", CultureInfo.InvariantCulture);
        if (!s.Contains('.') && !s.Contains('E')) s += ".0";
        s += "d";                                                   // without it a decimal literal is a BigDecimal
        return d < 0 ? "(" + s + ")" : s;
    }

    static string FloatLit(float f)
    {
        if (float.IsNaN(f)) return "Float.intBitsToFloat(" + IntLit(BitConverter.SingleToInt32Bits(f)) + ")";
        if (float.IsPositiveInfinity(f)) return "Float.POSITIVE_INFINITY";
        if (float.IsNegativeInfinity(f)) return "Float.NEGATIVE_INFINITY";
        if (f == 0 && float.IsNegative(f)) return "cpp.GR.NZF";
        var s = f.ToString("R", CultureInfo.InvariantCulture);
        if (!s.Contains('.') && !s.Contains('E')) s += ".0";
        s += "f";
        return f < 0 ? "(" + s + ")" : s;
    }

    public static string JStr(string s)
    {
        // single-quoted: a double-quoted Groovy string interpolates $
        var sb = new StringBuilder("'");
        foreach (var ch in s)
        {
            int c = ch;
            if (c > 255) Todo("non-Latin-1 character in a string");
            if (c == '\'') sb.Append("\\'");
            else if (c == '\\') sb.Append("\\\\");
            else if (c >= 32 && c < 127) sb.Append(ch);
            else sb.Append('\\').Append(Convert.ToString(c, 8).PadLeft(3, '0'));
        }
        return sb.Append('\'').ToString();
    }

    // ---- conversions
    string Conversion(IConversionOperation c)
    {
        var from = c.Operand.Type;
        var to = c.Type!;
        if (c.OperatorMethod != null) return RuntimeConvOp(c.OperatorMethod, X(c.Operand), c.Operand.Type);
        if (c.Operand is IAnonymousFunctionOperation af) return Lambda(af, to);
        if (c.Operand is IDelegateCreationOperation dc0 && to.TypeKind == TypeKind.Delegate) return DelegateCreation(dc0);
        if (from == null)
        {
            if (c.Operand is ILiteralOperation || c.Operand is IDefaultValueOperation) return DefaultOf(to);
            return X(c.Operand);
        }
        if (c.IsTryCast) return "cpp.R.as_(" + X(c.Operand) + ", " + InstanceOfType(to) + ".class)";     // as is a Groovy keyword
        return Conv(X(c.Operand), from, to);
    }

    public string Conv(string s, ITypeSymbol from, ITypeSymbol to)
    {
        if (SymbolEqualityComparer.Default.Equals(from, to)) return s;
        var f = Under(from); var t = Under(to);
        if (SymbolEqualityComparer.Default.Equals(f, t)) return s;
        if (IsNumeric(t) && IsNumeric(f))
        {
            var jt = JType(t);
            if (IsFloat(f) && IsIntegral(t))
            {
                // C# on x86-64 (cvttsd2si): NaN and out-of-range values give the integer indefinite
                var k = t.SpecialType switch
                {
                    SpecialType.System_Int32 => "i32", SpecialType.System_UInt32 => "u32", SpecialType.System_Int64 => "i64", SpecialType.System_UInt64 => "u64",
                    SpecialType.System_Int16 => "i16", SpecialType.System_UInt16 => "u16", SpecialType.System_SByte => "i8", SpecialType.System_Byte => "u8", SpecialType.System_Char => "u16",
                    _ => "x"
                };
                Use("float-to-int " + k, "cpp.F2." + k + "(double)");
                return "cpp.F2." + k + "(" + Strip(s) + ")";
            }
            if (IsFloat(t))
            {
                if (f.SpecialType == SpecialType.System_UInt64) { Use("ulong-to-" + jt, "cpp.R.u2" + jt[0]); return "cpp.R.u2" + jt[0] + "(" + Strip(s) + ")"; }
                if (f.SpecialType == SpecialType.System_UInt32) return "((" + jt + ") Integer.toUnsignedLong(" + Strip(s) + "))";
                if (f.SpecialType == SpecialType.System_Byte) return "((" + jt + ") (" + s + " & 0xFF))";
                return "((" + jt + ") " + Paren(s) + ")";
            }
            // integral to integral
            int wf = Width(f), wt = Width(t);
            if (wt > wf)
            {
                if (f.SpecialType == SpecialType.System_Byte) return wt == 64 ? "((long) (" + s + " & 0xFF))" : t.SpecialType is SpecialType.System_UInt16 or SpecialType.System_Char ? "((char) (" + s + " & 0xFF))" : "(" + s + " & 0xFF)";
                if (f.SpecialType == SpecialType.System_UInt32) return "Integer.toUnsignedLong(" + Strip(s) + ")";
                if (f.SpecialType is SpecialType.System_UInt16 or SpecialType.System_Char) return wt == 64 ? "((long) " + Paren(s) + ")" : "((int) " + Paren(s) + ")";
                // signed: Java widens with the sign, as C# does (also to an unsigned type)
                if (t.SpecialType is SpecialType.System_UInt16 or SpecialType.System_Char) return "((char) " + Paren(s) + ")";
                return "((" + jt + ") " + Paren(s) + ")";
            }
            // same width or narrower: the low bits
            if (JType(f) == jt) return s;
            return "((" + jt + ") " + Paren(s) + ")";
        }
        if (t.SpecialType == SpecialType.System_Boolean && f.SpecialType == SpecialType.System_Boolean) return s;
        // boxing and unboxing
        if (to.SpecialType == SpecialType.System_Object || to.TypeKind == TypeKind.Interface && !IsRuntime(to))
        {
            if (PrimCode(from) != null) return "((Object) " + Paren(s) + ")";
            return s;
        }
        if (from.SpecialType == SpecialType.System_Object || from.TypeKind == TypeKind.Interface || from is ITypeParameterSymbol)
        {
            if (PrimCode(to) != null) return "((" + JType(to) + ") (" + JBoxed(to) + ") " + Paren(s) + ")";
            if (to.TypeKind == TypeKind.Interface && IsRuntime(to)) return "((" + JType(to) + ") " + Paren(s) + ")";
            return "((" + JType(to) + ") " + Paren(s) + ")";
        }
        if (to is INamedTypeSymbol tex && IsSystem(tex) && tex.Name == "Exception") return s;
        if (from is INamedTypeSymbol fn && to is INamedTypeSymbol tn && (IsEngine(fn) || IsRuntime(fn)) && (IsEngine(tn) || IsRuntime(tn)))
        {
            if (Derives(fn, tn) || tn.TypeKind == TypeKind.Interface) return s;
            if (Derives(tn, fn)) return "((" + JType(tn) + ") " + Paren(s) + ")";
        }
        if (from.TypeKind == TypeKind.Delegate && to.TypeKind == TypeKind.Delegate) return Todo("delegate conversion " + from.ToDisplayString() + " -> " + to.ToDisplayString());
        if (to is INamedTypeSymbol nl && nl.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T) return s;
        if (from is INamedTypeSymbol nf && nf.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T) return PrimCode(to) != null ? "((" + JType(to) + ") " + Paren(s) + ")" : s;
        if (SymbolEqualityComparer.Default.Equals(from.OriginalDefinition, to.OriginalDefinition)) return s;
        if (from.TypeKind == TypeKind.Array && to.TypeKind == TypeKind.Array) return s;
        return Todo("conversion " + from.ToDisplayString() + " -> " + to.ToDisplayString());
    }

    string RuntimeConvOp(IMethodSymbol op, string arg, ITypeSymbol? argType)
    {
        var t = op.ContainingType;
        var p0 = op.OriginalDefinition.Parameters[0].Type;
        var nm = RuntimeClass(t) + ".from_" + ShortType(p0) + Spec(t, op);
        Use("conversion " + op.ToDisplayString(), nm);
        return nm + "(" + (argType != null ? Conv(arg, argType, op.Parameters[0].Type) : arg) + ")";
    }

    /// <summary>The specialisation suffix of a generic runtime member used with primitive type arguments:
    /// __ and one code per type argument (the class's, then the method's), O for a reference type.</summary>
    static string Spec(INamedTypeSymbol? t, IMethodSymbol? m)
    {
        var args = new List<ITypeSymbol>();
        if (t != null && t.IsGenericType && !SpecializedClass(t)) args.AddRange(t.TypeArguments);
        if (m != null && m.IsGenericMethod) args.AddRange(m.TypeArguments);
        if (args.Count == 0 || args.All(a => PrimCode(a) == null)) return "";
        return "__" + string.Concat(args.Select(a => PrimCode(a) ?? "O"));
    }

    static bool SpecializedClass(INamedTypeSymbol t) => RuntimeTypeName(t) is var n && !n.Contains('<') && t.IsGenericType;

    // ---- fields and properties
    string Field(IFieldReferenceOperation f, bool read)
    {
        var fs = f.Field;
        if (fs.ContainingType.TypeKind == TypeKind.Enum)
        {
            if (IsEngine(fs.ContainingType)) return TypeIdent(fs.ContainingType) + "." + Id(fs.Name);
            return Const(fs.ConstantValue, fs.Type);
        }
        if (IsRuntime(fs))
        {
            if (fs.IsStatic || fs.IsConst)
            {
                var cn = RuntimeClass(fs.ContainingType);
                var n = cn + (fs.IsConst || read ? "." : ".@") + RtName(fs.Name);
                Use("static field " + fs.OriginalDefinition.ToDisplayString(), cn + "." + RtName(fs.Name));
                return n;
            }
            var jf = RtName(JavaFieldName(fs.Name));
            Use("field " + fs.OriginalDefinition.ToDisplayString(), jf);
            var e = Recv(f.Instance!) + ".@" + jf;
            if (read && GenericMember(fs.OriginalDefinition.Type, fs.Type)) return "((" + JType(fs.Type) + ") " + e + ")";
            return e;
        }
        if (fs.IsConst) return TypeIdent(fs.ContainingType) + "." + Id(fs.Name);
        if (fs.IsStatic) return TypeIdent(fs.ContainingType) + (read ? "." : ".@") + FieldIdent(fs);
        return Recv(f.Instance!) + ".@" + FieldIdent(fs);
    }

    string Recv(IOperation inst)
    {
        var s = X(inst);
        return Simple(s) ? s : "(" + Strip(s) + ")";
    }

    static bool Simple(string s)
    {
        int depth = 0;
        foreach (var ch in s)
        {
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;
            else if (depth == 0 && !(char.IsLetterOrDigit(ch) || ch == '_' || ch == '.')) return false;
        }
        return depth == 0 && s.Length > 0 && !char.IsDigit(s[0]);
    }

    static bool IsCPtrIndexer(IPropertyReferenceOperation p) => p.Property.IsIndexer && p.Property.ContainingType.Name == "CPtr" && IsRuntime(p.Property);

    /// <summary>A vector or string element: the backing array and the index.</summary>
    bool VecElem(IPropertyReferenceOperation p, out string arr, out string idx)
    {
        arr = idx = "";
        var tn = StripArity(p.Property.ContainingType.Name);
        if (!p.Property.IsIndexer || !IsRuntime(p.Property) || p.Property.RefKind == RefKind.None) return false;
        if (tn == "Vec") { arr = Recv(p.Instance!) + ".a"; idx = Index(p.Arguments[0].Value); Use("Vec.a (backing array)", "a"); return true; }
        if (tn == "Str") { arr = Recv(p.Instance!) + ".b"; idx = Index(p.Arguments[0].Value); Use("Str.b (backing array)", "b"); return true; }
        return false;
    }

    static bool VecGeneric(IPropertyReferenceOperation p) => StripArity(p.Property.ContainingType.Name) == "Vec" && PrimCode(p.Property.ContainingType.TypeArguments[0]) == null;

    static string StripArity(string s) { var i = s.IndexOf('`'); return i < 0 ? s : s.Substring(0, i); }

    /// <summary>A property or indexer: a vector element is its backing array; a ref one is get_X() for its value
    /// and ref_X() for a Ref; a plain one is get_X().</summary>
    string Prop(IPropertyReferenceOperation p, bool asRef)
    {
        var ps = p.Property;
        if (IsSystem(ps) && ps.Name == "Length")
            return ps.ContainingType.SpecialType == SpecialType.System_String ? Recv(p.Instance!) + ".length()" : Recv(p.Instance!) + ".length";
        if (!IsRuntime(ps)) return Todo((IsSystem(ps) ? "system" : "engine") + " property " + ps.ContainingType.Name + "." + ps.Name);
        if (!asRef && VecElem(p, out var arr, out var idx))
        {
            var e = arr + "[" + idx + "]";
            return VecGeneric(p) ? "((" + JType(p.Type!) + ") " + e + ")" : e;
        }
        if (IsCPtrIndexer(p) && !asRef) return Recv(p.Instance!) + ".get(" + Index(p.Arguments[0].Value) + ")";
        var cn = RuntimeClass(ps.ContainingType);
        var nm = ps.IsIndexer ? "Item" : ps.Name;
        var sfx = ps.IsIndexer && ps.ContainingType.GetMembers().OfType<IPropertySymbol>().Count(x => x.IsIndexer) > 1 ? "_" + ShortType(ps.OriginalDefinition.Parameters[0].Type) : "";
        var args = string.Join(", ", p.Arguments.Select(a => ps.IsIndexer && IsIntegral(a.Parameter!.Type) && a.Parameter.Type.SpecialType != SpecialType.System_Int32 ? Value(a.Value, a.Parameter.Type) : Value(a.Value, a.Parameter!.Type)));
        var key = "property " + ps.ContainingType.OriginalDefinition.ToDisplayString() + "." + ps.Name + (ps.IsIndexer ? "[" + string.Join(",", ps.Parameters.Select(x => x.Type.ToDisplayString())) + "]" : "");
        string call;
        if (asRef)
        {
            var rn = "ref_" + nm + sfx;
            Use(key + " (ref)", rn);
            call = (ps.IsStatic ? cn : Recv(p.Instance!)) + "." + rn + "(" + args + ")";
            return GenericMember(ps.OriginalDefinition.Type, ps.Type) && !SpecializedClass(ps.ContainingType) ? Rewrap(call, ps.Type) : call;
        }
        var gn = "get_" + nm + sfx;
        Use(key, gn);
        call = (ps.IsStatic ? cn : Recv(p.Instance!)) + "." + gn + "(" + args + ")";
        if (GenericMember(ps.OriginalDefinition.Type, ps.Type)) return "((" + JType(ps.Type) + ") " + call + ")";
        return call;
    }

    string PropSet(IPropertyReferenceOperation p, string v)
    {
        var ps = p.Property;
        if (!IsRuntime(ps)) return Todo("set engine property " + ps.Name);
        var sn = "set_" + ps.Name;
        Use("property set " + ps.ContainingType.OriginalDefinition.ToDisplayString() + "." + ps.Name, sn);
        return (ps.IsStatic ? RuntimeClass(ps.ContainingType) : Recv(p.Instance!)) + "." + sn + "(" + v + ")";
    }

    // ---- calls
    public string Args(IMethodSymbol m, IEnumerable<IArgumentOperation> args)
    {
        var outv = new List<string>();
        foreach (var a in args.OrderBy(a => a.Parameter?.Ordinal ?? 0))
        {
            var prm = a.Parameter!;
            if (a.ArgumentKind == ArgumentKind.ParamArray)
            {
                if (a.Value is IArrayCreationOperation ac && ac.Initializer != null)
                {
                    var et = ((IArrayTypeSymbol)prm.Type).ElementType;
                    foreach (var e in ac.Initializer.ElementValues) outv.Add(VarArg(e, et));
                }
                else outv.Add(X(a.Value));
                continue;
            }
            if (prm.RefKind is RefKind.Ref or RefKind.Out)
            {
                var r = RefOf(a.Value);
                // a generic runtime method's ref T with a primitive T takes the specialised Ref
                outv.Add(r);
            }
            else outv.Add(Strip(Value(a.Value, prm.Type)));
        }
        return string.Join(", ", outv);
    }

    /// <summary>A params object[] element: a primitive is boxed by its exact Java type, so the runtime's printf
    /// can tell an int from a long (and uint/ulong values carry their C# types through cpp.U32/cpp.U64).</summary>
    string VarArg(IOperation e, ITypeSymbol et)
    {
        var src = e is IConversionOperation cv && cv.IsImplicit && et.SpecialType == SpecialType.System_Object ? cv.Operand : e;
        if (et.SpecialType == SpecialType.System_Object && src.Type != null)
        {
            var st = Under(src.Type);
            var v = X(src);
            if (st.SpecialType == SpecialType.System_UInt32) { Use("vararg uint", "cpp.U32"); return "new cpp.U32(" + Strip(v) + ")"; }
            if (st.SpecialType == SpecialType.System_UInt64) { Use("vararg ulong", "cpp.U64"); return "new cpp.U64(" + Strip(v) + ")"; }
            if (st.SpecialType == SpecialType.System_Byte) { Use("vararg byte", "cpp.U8"); return "new cpp.U8(" + Strip(v) + ")"; }
            if (st.SpecialType == SpecialType.System_UInt16) { Use("vararg ushort", "cpp.U16"); return "new cpp.U16(" + Strip(v) + ")"; }
            if (PrimCode(st) != null) return "((Object) " + Paren(v) + ")";
            return v;
        }
        return Strip(Value(e, et));
    }

    string Invoke(IInvocationOperation inv, bool asRef)
    {
        var m = inv.TargetMethod;
        string call;
        if (m.MethodKind == MethodKind.DelegateInvoke)
        {
            var dt = (INamedTypeSymbol)m.ContainingType;
            call = Recv(inv.Instance!) + "." + DelegateMethod(dt) + "(" + Args(m, inv.Arguments) + ")";
            if (IsSystem(dt) && !m.ReturnsVoid && PrimCode(m.ReturnType) != null) call = "((" + JType(m.ReturnType) + ") " + call + ")";
            if (m.RefKind != RefKind.None) return asRef ? call : call + ".get()";
            return call;
        }
        if (IsRuntime(m) || IsSystem(m)) return RuntimeCall(m, inv.Instance, inv.Arguments, asRef);
        if (m.IsStatic) call = TypeIdent(m.ContainingType) + "." + MethodIdent(m) + "(" + Args(m, inv.Arguments) + ")";
        else
        {
            var r = Recv(inv.Instance!);
            var mn = MethodIdent(m);
            if (inv.Instance is IInstanceReferenceOperation && inv.Instance.Syntax is Microsoft.CodeAnalysis.CSharp.Syntax.BaseExpressionSyntax)
                r = "super";      // base.M(): the base class's M, never a derived override
            call = r + "." + mn + "(" + Args(m, inv.Arguments) + ")";
        }
        if (m.RefKind != RefKind.None) return asRef ? call : call + ".get()";
        return call;
    }

    string RuntimeCall(IMethodSymbol m, IOperation? inst, System.Collections.Immutable.ImmutableArray<IArgumentOperation> argOps, bool asRef)
    {
        var t = m.ContainingType;
        if (IsSystem(m)) return SystemCall(m, inst, Args(m, argOps));
        var args = Args(m, argOps);
        if (m.Name == "ToString" && !m.IsStatic) return Recv(inst!) + ".toString()";
        var overloads = t.GetMembers(m.Name).OfType<IMethodSymbol>().Count();
        var sfx = overloads > 1 ? "_" + string.Join("_", m.OriginalDefinition.Parameters.Select(p => ShortType(p.Type) + (p.RefKind != RefKind.None ? "r" : ""))) : "";
        var spec = Spec(m.IsStatic ? t : null, m);
        var nm = RtName(m.Name + sfx + spec);
        var refm = m.RefKind != RefKind.None;
        if (refm && asRef) nm = "ref_" + nm;
        string call;
        if (m.IsStatic && m.IsGenericMethod && spec.Length == 0 && (t.Name == "Mv" && m.Name == "Take" || t.Name == "Arr" && (m.Name == "Make" && m.Parameters.Length == 1 || m.Name == "Make2")))
        {
            // these make a T with Elem<T>.Make: Java needs the maker
            args = (args.Length > 0 ? args + ", " : "") + Maker(m.TypeArguments[0]);
            nm += "";
            Use("static " + m.OriginalDefinition.ToDisplayString() + " + maker", RuntimeClass(t) + "." + nm);
        }
        if (m.IsStatic)
        {
            var cn = RuntimeClass(t);
            Use("static " + m.OriginalDefinition.ToDisplayString() + (spec.Length > 0 ? " " + spec : "") + (refm && asRef ? " (ref)" : ""), cn + "." + nm);
            var targs = spec.Length == 0 ? TypeWitness(t, m) : "";
            call = cn + "." + targs + nm + "(" + args + ")";
        }
        else
        {
            Use("method " + m.OriginalDefinition.ToDisplayString() + (spec.Length > 0 ? " " + spec : "") + (refm && asRef ? " (ref)" : ""), nm);
            var targs = spec.Length == 0 && m.IsGenericMethod ? "<" + string.Join(", ", m.TypeArguments.Select(JBoxed)) + ">" : "";
            call = Recv(inst!) + "." + targs + nm + "(" + args + ")";
        }
        if (refm && asRef) return GenericMember(m.OriginalDefinition.ReturnType, m.ReturnType) && !SpecializedClass(t) ? Rewrap(call, m.ReturnType) : call;
        if (!m.ReturnsVoid && GenericMember(m.OriginalDefinition.ReturnType, m.ReturnType) && spec.Length == 0 && !SpecializedClass(t))
            return "((" + JType(m.ReturnType) + ") " + call + ")";
        return call;
    }

    /// <summary>Java type witnesses for a static generic runtime method (the class's type arguments, then the method's).</summary>
    static string TypeWitness(INamedTypeSymbol t, IMethodSymbol m)
    {
        var a = new List<ITypeSymbol>();
        if (t.IsGenericType && !SpecializedClass(t)) a.AddRange(t.TypeArguments);
        if (m.IsGenericMethod) a.AddRange(m.TypeArguments);
        return a.Count == 0 ? "" : "<" + string.Join(", ", a.Select(JBoxed)) + ">";
    }

    string SystemCall(IMethodSymbol m, IOperation? inst, string args)
    {
        var t = m.ContainingType.Name;
        if (t == "Math" || t == "MathF")
        {
            var n = "cpp.SysMath." + m.Name + (m.Parameters.Length > 0 ? "_" + ShortType(m.Parameters[0].Type) : "");
            Use("System.Math." + m.Name + "(" + string.Join(",", m.Parameters.Select(p => p.Type.Name)) + ")", n);
            return n + "(" + args + ")";
        }
        if (t == "Object" && m.Name == "ReferenceEquals") return "(" + string.Join(" === ", args.Split(", ")) + ")";
        if (t == "Double" && m.Name == "IsFinite") return "Double.isFinite(" + args + ")";
        if (t == "Double" && m.Name == "IsNaN") return "Double.isNaN(" + args + ")";
        if (t == "Double" && m.Name == "IsInfinity") return "Double.isInfinite(" + args + ")";
        if (t == "Double" && m.Name == "IsPositiveInfinity") return "(" + args + " == Double.POSITIVE_INFINITY)";
        if (t == "Double" && m.Name == "IsNegativeInfinity") return "(" + args + " == Double.NEGATIVE_INFINITY)";
        if (t == "Double" && m.Name == "IsNegative") { Use("Double.IsNegative", "cpp.R.signbit(double)"); return "cpp.R.signbit(" + args + ")"; }
        if (t == "BitConverter" && m.Name == "DoubleToInt64Bits") return "Double.doubleToRawLongBits(" + args + ")";
        if (t == "BitConverter" && m.Name == "Int64BitsToDouble") return "Double.longBitsToDouble(" + args + ")";
        if (t == "Array" && m.Name == "Fill") { Use("Array.Fill", "java.util.Arrays.fill"); return "java.util.Arrays.fill(" + args + ")"; }
        if (t == "File" && m.Name == "Exists") { Use("File.Exists(string)", "cpp.R.File_Exists(String)"); return "cpp.R.File_Exists(" + args + ")"; }
        if (m.Name == "Dispose" && inst != null) return Recv(inst) + ".Dispose()";
        if (m.Name == "GetHashCode" && inst != null) return Recv(inst) + ".hashCode()";
        if (m.Name == "Equals" && inst != null) return "java.util.Objects.equals(" + X(inst) + ", " + args + ")";
        return Todo("system call " + m.ContainingType.ToDisplayString() + "." + m.Name);
    }

    string New(IObjectCreationOperation oc)
    {
        var t = (INamedTypeSymbol)oc.Type!;
        var c = oc.Constructor!;
        string e;
        if (t.TypeKind == TypeKind.Delegate) return Todo("delegate construction");
        if (IsEngine(t))
            e = "new " + TypeIdent(t) + "(" + Args(c, oc.Arguments) + ")";
        else if (IsRuntime(t))
        {
            var jt = JType(t);
            var args = MakerArgs(c, oc.Arguments, t);
            Use("new " + c.OriginalDefinition.ToDisplayString() + (jt.Contains('<') || !t.IsGenericType ? "" : " (" + jt + ")") + (args.appended ? " + makers" : ""), jt.Contains('<') ? Raw(jt) + "<>" : jt);
            e = "new " + jt + "(" + args.text + ")";
        }
        else if (IsSystem(t))
        {
            if (t.Name.EndsWith("Exception"))
            {
                Use("system exception", "cpp.SysException");
                e = "new cpp.SysException('" + t.Name + "', " + (oc.Arguments.Length > 0 ? "String.valueOf(" + X(oc.Arguments[0].Value) + ")" : "''") + ")";
            }
            else return Todo("new " + t.ToDisplayString());
        }
        else return Todo("new " + t.ToDisplayString());
        if (oc.Initializer == null || oc.Initializer.Initializers.Length == 0) return e;
        // new X() { a = 1, b = 2 }: a temporary, set member by member before the statement
        var o = Fresh("o");
        Pre.Add(JType(t) + " " + o + " = " + e + ";");
        implicitRecv.Push(o);
        foreach (var init in oc.Initializer.Initializers)
        {
            var st = StmtExpr(init, null, 0);
            if (st.Length > 0) Pre.Add(st + ";");
        }
        implicitRecv.Pop();
        return o;
    }

    // ---- element makers
    // C# makes a container's elements with reflection (Elem<T>.Make: the type's parameterless constructor); Java's
    // erased generics cannot, so a generic runtime container is handed a maker, cpp.Fn.F0<T>, for the elements it
    // creates itself: appended to a constructor that has no Func<T> parameter, or standing in for a null one.
    static readonly Dictionary<string, int[]> makerSlots = new() { ["Vec"] = new[] { 0 }, ["Map"] = new[] { 1 }, ["UMap"] = new[] { 1 }, ["UMapC"] = new[] { 1 } };

    (string text, bool appended) MakerArgs(IMethodSymbol c, System.Collections.Immutable.ImmutableArray<IArgumentOperation> argOps, INamedTypeSymbol t)
    {
        var name = StripArity(t.Name);
        var specialized = !RuntimeTypeName(t).Contains('<');
        var outv = new List<string>();
        bool hasFunc = false;
        foreach (var a in argOps.OrderBy(a => a.Parameter?.Ordinal ?? 0))
        {
            var prm = a.Parameter!;
            if (prm.Type is INamedTypeSymbol ft && IsSystem(ft) && ft.Name == "Func" && ft.TypeArguments.Length == 1 && !specialized)
            {
                hasFunc = true;
                if (a.Value.ConstantValue is { HasValue: true, Value: null } || a.Value is IDefaultValueOperation || a.ArgumentKind == ArgumentKind.DefaultValue && a.Value.ConstantValue.HasValue)
                { outv.Add(Maker(ft.TypeArguments[0])); continue; }
            }
            outv.Add(ArgOne(a));
        }
        bool appended = false;
        if (!specialized && !hasFunc)
        {
            if (makerSlots.TryGetValue(name, out var slots))
                foreach (var k in slots) { outv.Add(Maker(t.TypeArguments[k])); appended = true; }
            else if ((name == "Pair" || name == "Tup") && c.Parameters.Length == 0)
                foreach (var ta in t.TypeArguments) { outv.Add(Maker(ta)); appended = true; }
        }
        // a container with the engine's own hasher and key-equal (C# makes them with new H(), new E())
        if (name == "UMapC") { outv.Add(Maker(t.TypeArguments[2])); outv.Add(Maker(t.TypeArguments[3])); appended = true; }
        if (name == "USetC") { outv.Add(Maker(t.TypeArguments[1])); outv.Add(Maker(t.TypeArguments[2])); appended = true; }
        return (string.Join(", ", outv), appended);
    }

    string ArgOne(IArgumentOperation a)
    {
        var prm = a.Parameter!;
        if (prm.RefKind is RefKind.Ref or RefKind.Out) return RefOf(a.Value);
        return Strip(Value(a.Value, prm.Type));
    }

    /// <summary>A cpp.Fn.F0 that makes a default T, as C#'s Elem&lt;T&gt;.Make does: a static field of engine/Mk.groovy,
    /// made once by a method whose typed local holds the lambda (Groovy compiles a lambda to a native one only when
    /// its target type is declared: in a cast or a field initializer it is a closure converted by castToType).</summary>
    static string Maker(ITypeSymbol t)
    {
        var bt = JBoxed(t);
        string body;
        if (PrimCode(t) != null) body = DefaultOf(t);
        else if (IsStructType(t)) body = DefaultOf(t);
        else if (t is INamedTypeSymbol n && IsEngine(n) && n.TypeKind == TypeKind.Class && !n.IsAbstract && !n.IsStatic) body = "new " + TypeIdent(n) + "()";
        else if (t is INamedTypeSymbol r && IsRuntime(r) && r.TypeKind == TypeKind.Class && !r.IsAbstract && r.InstanceConstructors.Any(k => k.Parameters.Length == 0 || k.Parameters.All(p => p.IsOptional)))
        {
            var jt = JType(r);
            var nm = StripArity(r.Name);
            if (jt.Contains('<'))
            {
                var inner = makerSlots.TryGetValue(nm, out var slots) ? string.Join(", ", slots.Select(k => Maker(r.TypeArguments[k])))
                          : nm is "Pair" or "Tup" ? string.Join(", ", r.TypeArguments.Select(Maker)) : "";
                if (nm == "UMapC") inner += ", " + Maker(r.TypeArguments[2]) + ", " + Maker(r.TypeArguments[3]);
                if (nm == "USetC") inner = Maker(r.TypeArguments[1]) + ", " + Maker(r.TypeArguments[2]);
                if (nm is "Map" or "UMap")
                {
                    // the map constructors' leading optional parameters (hash, equality, ordering) are null
                    var ctor = r.InstanceConstructors.First(k => k.Parameters.All(p => p.IsOptional) && k.Parameters.Length > 0);
                    var nulls = string.Join(", ", ctor.Parameters.Take(ctor.Parameters.Length - 1).Select(_ => "null"));
                    inner = nulls.Length > 0 ? nulls + ", " + inner : inner;
                }
                body = "new " + jt + "(" + inner + ")";
            }
            else body = "new " + jt + "()";
        }
        else body = "null";
        return MakerField(bt, body);
    }

    static readonly SortedDictionary<string, string> makers = new();          // name -> declaration
    static readonly Dictionary<string, string> makerNames = new();            // type + body -> name

    static string MakerField(string bt, string body)
    {
        var key = bt + "\u0001" + body;
        if (!makerNames.TryGetValue(key, out var name))
        {
            var b = System.Text.RegularExpressions.Regex.Replace(bt.Replace("cpp.", ""), "[^A-Za-z0-9]+", "_").Trim('_');
            if (body == "null") b += "_null";
            name = b; int k = 2;
            while (makers.ContainsKey(name)) name = b + "_" + k++;
            makerNames[key] = name;
            var ft = "cpp.Fn.F0<" + bt + ">";
            makers[name] = "\tpublic static final " + ft + " " + name + " = " + name + "__()\n\tprivate static " + ft + " " + name + "__() { " + ft + " f = () -> " + body + "; return f; }\n";
            var sb = new StringBuilder(FileHeader + "/** The makers of default elements (C#'s Elem<T>.Make) that the generic containers are given. */\n@groovy.transform.CompileStatic\npublic final class Mk {\n\tprivate Mk() { }\n");
            foreach (var kv in makers) sb.Append(kv.Value);
            extraFiles["Mk"] = sb.Append("}\n").ToString();
        }
        return "Mk." + name;
    }

    string NewArray(IArrayCreationOperation ac)
    {
        var at = (IArrayTypeSymbol)ac.Type!;
        var et = at.ElementType;
        var jet = JType(et);
        var generic = jet.Contains('<');
        if (ac.Initializer != null && ac.Initializer.ElementValues.Length > 16 && PrimCode(et) != null &&
            ac.Initializer.ElementValues.All(v => v.ConstantValue.HasValue && v.ConstantValue.Value is not null && Convert.ToDouble(v.ConstantValue.Value) == 0 && !(v.ConstantValue.Value is double dz && double.IsNegative(dz)) && !(v.ConstantValue.Value is float fz && float.IsNegative(fz))))
            // T a[N] = {} : a long run of zeros is Java's default (an initializer that long exceeds a method's 64 KB)
            return "new " + jet + "[" + ac.Initializer.ElementValues.Length + "]";
        if (ac.Initializer != null)
        {
            var elems = string.Join(", ", ac.Initializer.ElementValues.Select(v => Strip(Value(v, et))));
            if (generic) return "(" + jet + "[]) new " + Raw(jet) + "[] {" + elems + "}";
            return "new " + jet + "[] {" + elems + "}";
        }
        var n = Index(ac.DimensionSizes[0]);
        string arr;
        if (jet.EndsWith("[]")) arr = "new " + jet.Substring(0, jet.IndexOf('[')) + "[" + n + "]" + jet.Substring(jet.IndexOf('['));
        else arr = generic ? "(" + jet + "[]) new " + Raw(jet) + "[" + n + "]" : "new " + jet + "[" + n + "]";
        if (IsStructType(et))
        {
            Use("Arr.filled(T[], T)", "cpp.Arr.filled");
            return "cpp.Arr.filled(" + arr + ", " + DefaultOf(et) + ")";
        }
        return arr;
    }

    // ---- operators
    static string JavaOp(BinaryOperatorKind k) => k switch
    {
        BinaryOperatorKind.Add => "+", BinaryOperatorKind.Subtract => "-", BinaryOperatorKind.Multiply => "*",
        BinaryOperatorKind.Divide => "/", BinaryOperatorKind.Remainder => "%", BinaryOperatorKind.LeftShift => "<<",
        BinaryOperatorKind.RightShift => ">>", BinaryOperatorKind.And => "&", BinaryOperatorKind.Or => "|",
        BinaryOperatorKind.ExclusiveOr => "^", BinaryOperatorKind.ConditionalAnd => "&&", BinaryOperatorKind.ConditionalOr => "||",
        BinaryOperatorKind.Equals => "==", BinaryOperatorKind.NotEquals => "!=", BinaryOperatorKind.LessThan => "<",
        BinaryOperatorKind.LessThanOrEqual => "<=", BinaryOperatorKind.GreaterThan => ">", BinaryOperatorKind.GreaterThanOrEqual => ">=",
        BinaryOperatorKind.UnsignedRightShift => ">>>",
        _ => Todo("operator " + k)
    };

    /// <summary>l op r for operands of C# type t (after C#'s promotions), with Java's unsigned helpers and Groovy's
    /// exceptions: integer / (a BigDecimal in Groovy), floating > and >= (true for NaN in Groovy 3), float arithmetic
    /// (computed in double), reference == (equals() in Groovy).</summary>
    static string BinOp(BinaryOperatorKind k, ITypeSymbol t, string l, string r, ITypeSymbol? rt)
    {
        var u = Under(t);
        if (k == BinaryOperatorKind.Divide && u.SpecialType is SpecialType.System_Int32 or SpecialType.System_Int16 or SpecialType.System_SByte)
            return "cpp.GR.idiv(" + Strip(l) + ", " + Strip(r) + ")";
        if (k == BinaryOperatorKind.Divide && u.SpecialType == SpecialType.System_Int64)
            return "cpp.GR.ldiv(" + Strip(l) + ", " + Strip(r) + ")";
        if (IsFloat(u) && k is BinaryOperatorKind.GreaterThan) return "cpp.GR.dgt(" + Strip(l) + ", " + Strip(r) + ")";
        if (IsFloat(u) && k is BinaryOperatorKind.GreaterThanOrEqual) return "cpp.GR.dge(" + Strip(l) + ", " + Strip(r) + ")";
        if (u.SpecialType == SpecialType.System_Single && k is BinaryOperatorKind.Add or BinaryOperatorKind.Subtract or BinaryOperatorKind.Multiply or BinaryOperatorKind.Divide or BinaryOperatorKind.Remainder)
            return "((float) (" + l + " " + JavaOp(k) + " " + r + "))";
        if (k is BinaryOperatorKind.Equals or BinaryOperatorKind.NotEquals && PrimCode(u) == null && l != "null" && r != "null")
            return "(" + l + (k == BinaryOperatorKind.Equals ? " === " : " !== ") + r + ")";
        bool uns = u.SpecialType is SpecialType.System_UInt32 or SpecialType.System_UInt64;
        bool wide = u.SpecialType == SpecialType.System_UInt64;
        string box = wide ? "Long" : "Integer";
        if (uns)
        {
            switch (k)
            {
                case BinaryOperatorKind.Divide: return box + ".divideUnsigned(" + Strip(l) + ", " + Strip(r) + ")";
                case BinaryOperatorKind.Remainder: return box + ".remainderUnsigned(" + Strip(l) + ", " + Strip(r) + ")";
                case BinaryOperatorKind.RightShift: return "(" + l + " >>> " + r + ")";
                case BinaryOperatorKind.LessThan: return "(" + box + ".compareUnsigned(" + Strip(l) + ", " + Strip(r) + ") < 0)";
                case BinaryOperatorKind.LessThanOrEqual: return "(" + box + ".compareUnsigned(" + Strip(l) + ", " + Strip(r) + ") <= 0)";
                case BinaryOperatorKind.GreaterThan: return "(" + box + ".compareUnsigned(" + Strip(l) + ", " + Strip(r) + ") > 0)";
                case BinaryOperatorKind.GreaterThanOrEqual: return "(" + box + ".compareUnsigned(" + Strip(l) + ", " + Strip(r) + ") >= 0)";
            }
        }
        return "(" + l + " " + JavaOp(k) + " " + r + ")";
    }

    string Binary(IBinaryOperation b)
    {
        if (b.OperatorMethod != null)
        {
            if (IsSystem(b.OperatorMethod) && b.OperatorMethod.ContainingType.SpecialType == SpecialType.System_String)
            {
                var a0 = X(b.LeftOperand); var b0 = X(b.RightOperand);
                if (b.OperatorKind == BinaryOperatorKind.Equals) return "java.util.Objects.equals(" + a0 + ", " + b0 + ")";
                if (b.OperatorKind == BinaryOperatorKind.NotEquals) return "!java.util.Objects.equals(" + a0 + ", " + b0 + ")";
                if (b.OperatorKind == BinaryOperatorKind.Add) return "(" + a0 + " + " + b0 + ")";
            }
            return UserBinary(b.OperatorMethod, Value(b.LeftOperand, b.OperatorMethod.Parameters[0].Type), Value(b.RightOperand, b.OperatorMethod.Parameters[1].Type));
        }
        var lt = b.LeftOperand.Type;
        var l = X(b.LeftOperand);
        var r = X(b.RightOperand);
        if (lt != null && lt.SpecialType == SpecialType.System_String && b.OperatorKind == BinaryOperatorKind.Add)
        {
            if (b.RightOperand.Type is ITypeSymbol rts && IsFloat(rts) || IsFloat(lt)) Note("string concatenation with a floating-point number");
            return "(" + l + " + " + r + ")";
        }
        if (b.OperatorKind is BinaryOperatorKind.Equals or BinaryOperatorKind.NotEquals && lt != null && b.RightOperand.Type != null &&
            (PrimCode(lt) == null) != (PrimCode(b.RightOperand.Type) == null) && b.RightOperand.ConstantValue is not { HasValue: true, Value: null } && b.LeftOperand.ConstantValue is not { HasValue: true, Value: null })
            Note("equality of a primitive and a reference");
        return BinOp(b.OperatorKind, lt ?? b.Type!, l, r, b.RightOperand.Type);
    }

    string UserBinary(IMethodSymbol op, string l, string r)
    {
        var t = op.ContainingType;
        var opn = op.Name switch
        {
            "op_Equality" => "op_Eq", "op_Inequality" => "op_Ne", "op_Addition" => "op_Add", "op_Subtraction" => "op_Sub",
            "op_LessThan" => "op_Lt", "op_GreaterThan" => "op_Gt", "op_LessThanOrEqual" => "op_Le", "op_GreaterThanOrEqual" => "op_Ge",
            "op_Multiply" => "op_Mul", "op_Division" => "op_Div",
            _ => Todo("user operator " + op.Name)
        };
        if (!IsRuntime(op)) return Todo("engine operator");
        var overloads = t.GetMembers(op.Name).OfType<IMethodSymbol>().Count();
        var sfx = overloads > 1 ? "_" + string.Join("_", op.OriginalDefinition.Parameters.Select(p => ShortType(p.Type))) : "";
        var cn = RuntimeClass(t);
        var spec = Spec(t, null);
        var n = cn + "." + (spec.Length == 0 ? TypeWitness(t, op) : "") + opn + sfx + spec;
        Use("operator " + op.ToDisplayString(), cn + "." + opn + sfx + spec);
        return n + "(" + Strip(l) + ", " + Strip(r) + ")";
    }

    string Unary(IUnaryOperation u)
    {
        if (u.OperatorMethod != null) return Todo("user unary operator");
        var o = X(u.Operand);
        return u.OperatorKind switch
        {
            UnaryOperatorKind.Minus => "(-" + Paren(o) + ")",
            UnaryOperatorKind.Plus => o,
            UnaryOperatorKind.BitwiseNegation => "(~" + Paren(o) + ")",
            UnaryOperatorKind.Not => "(!" + Paren(o) + ")",
            _ => Todo("unary " + u.OperatorKind)
        };
    }

    string Ternary(IConditionalOperation c)
    {
        var cond = X(c.Condition);
        var t = c.Type!;
        if (c.IsRef) return "(" + cond + " ? " + RefOf(c.WhenTrue) + " : " + RefOf(c.WhenFalse!) + ").get()";
        var saved = Pre; Pre = new List<string>();
        var a = Value(c.WhenTrue, t); var pa = Pre; Pre = new List<string>();
        var b = Value(c.WhenFalse!, t); var pb = Pre; Pre = saved;
        var jt = JType(t);
        if (pa.Count > 0 || pb.Count > 0)
        {
            // a branch needs statements: an if-else before the statement fills a temporary
            var tv = Fresh("t");
            Pre.Add(jt + " " + tv + " = " + DefaultForJava(jt) + ";");
            Pre.Add("if (" + Strip(cond) + ") {");
            Pre.AddRange(pa); Pre.Add(tv + " = " + a + ";");
            Pre.Add("} else {");
            Pre.AddRange(pb); Pre.Add(tv + " = " + b + ";");
            Pre.Add("}");
            return tv;
        }
        // Java types c ? a : b by its own rules: cast both to the C# type so a boxed branch is never mixed in
        if (PrimCode(t) != null && jt is not ("int" or "double" or "long" or "boolean"))
            return "((" + jt + ") (" + Paren(cond) + " ? " + a + " : " + b + "))";
        // the condition keeps its parentheses: it may itself be a conditional, and c1 ? a : c2 ? 1 : 0 nests right
        return "(" + Paren(cond) + " ? " + a + " : " + b + ")";
    }

    string CondAccess(IConditionalAccessOperation ca)
    {
        var recv = X(ca.Operation);
        var r = recv;
        if (!Simple(recv)) { r = Fresh("ca"); Pre.Add(JType(ca.Operation.Type!) + " " + r + " = " + recv + ";"); }
        condAccess.Push(r);
        var inner = X(ca.WhenNotNull);
        condAccess.Pop();
        var t = ca.Type!;
        var none = PrimCode(t) != null ? DefaultOf(t) : "null";
        return "(" + r + " == null ? " + none + " : " + inner + ")";
    }

    string Lambda(IAnonymousFunctionOperation af, ITypeSymbol? target)
    {
        var m = af.Symbol;
        var sub = new GvFn(this, m);
        var dt = target as INamedTypeSymbol;
        bool sys = dt != null && IsSystem(dt);
        var ps = string.Join(", ", m.Parameters.Select(p => (sys && p.RefKind == RefKind.None ? JBoxed(p.Type) : ParamType(p)) + " " + sub.DeclareParam(p, sys && PrimCode(p.Type) != null)));
        // A local that a Groovy lambda captures becomes a groovy.lang.Reference, in the lambda and in the method that
        // declares it, and Groovy types it as its wrapper class: arithmetic and comparisons on it are then Groovy's
        // Number arithmetic and compareTo (which orders NaN and -0.0), and o.@f on it a reflective getField. So the
        // lambda captures a copy made just before it, and copies that again into a typed local at its start; the
        // local itself stays a plain local. Each captured local is never assigned after the lambda is made (the Java
        // translator's analysis makes such a local a cell, and the copy is then the cell).
        try
        {
            var df = M(af.Syntax).AnalyzeDataFlow(af.Syntax);
            if (df.Succeeded)
                foreach (var v in df.ReadInside.Concat(df.WrittenInside).Distinct(SymbolEqualityComparer.Default))
                {
                    if (v == null || df.VariablesDeclared.Contains(v) || v is not (ILocalSymbol or IParameterSymbol) || v is IParameterSymbol { IsThis: true }) continue;
                    if (!names.TryGetValue(v, out var outer)) continue;
                    var vt = v is ILocalSymbol ls ? ls.Type : ((IParameterSymbol)v).Type;
                    var rk = v is ILocalSymbol ls2 ? ls2.RefKind : ((IParameterSymbol)v).RefKind;
                    var ct = Boxed(v) ? CellType(vt) : rk != RefKind.None ? RefType(vt) : JType(vt);
                    var c1 = Fresh("cap");
                    Pre.Add(ct + " " + c1 + " = " + outer + ";");
                    var c2 = sub.Fresh("cap");
                    sub.names[v] = c2;
                    sub.prologue.Add(ct + " " + c2 + " = " + c1 + ";");
                }
        }
        catch (ArgumentException) { }
        var body = new List<string>();
        var ends = sub.Block(af.Body.Operations, body, 2);
        if (ends && !m.ReturnsVoid) body.Add("\t\tthrow new IllegalStateException('unreachable');");
        var text = "(" + ps + ") -> {\n" + sub.Prologue() + string.Concat(body.Select(l => l + "\n")) + "\t}";
        if (dt == null) return Todo("lambda without a delegate type");
        // a lambda is a native one only when its target type is declared (a cast makes a closure): a typed local
        var lv = Fresh("lam");
        Pre.Add(JType(dt) + " " + lv + " = " + text + ";");
        return lv;
    }

    string DelegateCreation(IDelegateCreationOperation dc)
    {
        var t = dc.Type!;
        if (dc.Target is IAnonymousFunctionOperation af) return Lambda(af, t);
        if (dc.Target is IMethodReferenceOperation mr)
        {
            // a method reference compiles to a dynamic call in Groovy: a lambda with the delegate's parameters, in a
            // typed local; the receiver is evaluated once, as Java's recv::m does
            var m = mr.Method;
            var dt = (INamedTypeSymbol)t;
            var inv = dt.DelegateInvokeMethod!;
            bool sys = IsSystem(dt);
            string recv, head = "";
            if (m.IsStatic) recv = IsRuntime(m) ? RuntimeClass(m.ContainingType) : TypeIdent(m.ContainingType);
            else
            {
                recv = X(mr.Instance!);
                if (recv != "this")
                {
                    // the receiver, evaluated once into a local that only the lambda captures, and copied into a typed
                    // local in the lambda (a captured local is a groovy.lang.Reference; see Lambda)
                    var rt = JType(mr.Instance!.Type!);
                    var rv = Fresh("rcv"); Pre.Add(rt + " " + rv + " = " + Strip(recv) + ";");
                    var r2 = Fresh("rcv"); head = rt + " " + r2 + " = " + rv + "; ";
                    recv = r2;
                }
            }
            var mn = IsRuntime(m) ? RtName(m.Name) : MethodIdent(m);
            var ps = inv.Parameters.Select((p, k) => (sys && p.RefKind == RefKind.None ? JBoxed(p.Type) : ParamType(p)) + " __a" + k + "_" + tmp[0]).ToList();
            var an = inv.Parameters.Select((p, k) => "__a" + k + "_" + tmp[0]).ToList();
            var call = recv + "." + mn + "(" + string.Join(", ", an) + ")";
            var lv = Fresh("lam");
            Pre.Add(JType(t) + " " + lv + " = (" + string.Join(", ", ps) + ") -> { " + head + (inv.ReturnsVoid ? call + "; " : "return " + call + "; ") + "};");
            return lv;
        }
        return Todo("delegate creation " + dc.Target.Kind);
    }
}
}
