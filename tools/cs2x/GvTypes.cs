// GvTypes.cs - C# types to Groovy (Java) types, the boxing analysis, and the type declarations of the Groovy engine.
// The Java translator's JavaTypes.cs, copied: Groovy declares types with Java's syntax; each type is
// @groovy.transform.CompileStatic.
// Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see AI-USE.md and groovy/README.md.
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis.Operations;

public static partial class Cs2Groovy
{
    static readonly List<INamedTypeSymbol> engineTypes = new();

    static bool IsEngine(ITypeSymbol t) => t is INamedTypeSymbol n && !IsRuntime(n) && !IsSystem(n) && n.Locations.Any(l => l.IsInSource);

    static void CollectTypes(List<SyntaxTree> gen)
    {
        var seen = new HashSet<INamedTypeSymbol>(SymbolEqualityComparer.Default);
        foreach (var t in gen)
        {
            foreach (var td in t.GetRoot().DescendantNodes().OfType<BaseTypeDeclarationSyntax>())
            {
                var s = (INamedTypeSymbol)M(td).GetDeclaredSymbol(td)!;
                if (seen.Add(s)) engineTypes.Add(s);
            }
            foreach (var dd in t.GetRoot().DescendantNodes().OfType<DelegateDeclarationSyntax>())
            {
                var s = (INamedTypeSymbol)M(dd).GetDeclaredSymbol(dd)!;
                if (seen.Add(s)) engineTypes.Add(s);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ boxing analysis
    // A local or parameter is a cell (cpp.RefX.Cell) when its address is taken (a ref argument, ref local or ref
    // return) or when a lambda captures it and it is assigned anywhere but its declaration (Java captures only
    // effectively final locals).
    static readonly HashSet<ISymbol> boxed = new(SymbolEqualityComparer.Default);

    static void Analyze(List<SyntaxTree> gen)
    {
        var addr = new HashSet<ISymbol>(SymbolEqualityComparer.Default);
        var mutated = new HashSet<ISymbol>(SymbolEqualityComparer.Default);
        var captured = new HashSet<ISymbol>(SymbolEqualityComparer.Default);
        foreach (var t in gen)
        {
            var m = models[t];
            foreach (var node in t.GetRoot().DescendantNodes().Where(n => n is BaseMethodDeclarationSyntax || n is VariableDeclaratorSyntax && n.Parent?.Parent is FieldDeclarationSyntax))
            {
                IOperation? root = node switch
                {
                    BaseMethodDeclarationSyntax bm => bm.Body != null ? m.GetOperation(bm.Body) : bm.ExpressionBody != null ? m.GetOperation(bm.ExpressionBody) : null,
                    VariableDeclaratorSyntax vd => vd.Initializer != null ? m.GetOperation(vd.Initializer) : null,
                    _ => null
                };
                if (root == null) continue;
                if (node is ConstructorDeclarationSyntax cds && cds.Initializer != null && m.GetOperation(cds.Initializer) is IOperation ci) Scan(ci);
                Scan(root);
            }
        }
        void Scan(IOperation root)
        {
            foreach (var op in root.DescendantsAndSelf())
            {
                switch (op)
                {
                    case IArgumentOperation a when a.Parameter != null && (a.Parameter.RefKind == RefKind.Ref || a.Parameter.RefKind == RefKind.Out):
                        Mark(a.Value, addr); break;
                    case IVariableDeclaratorOperation vd when vd.Symbol.RefKind != RefKind.None && vd.Initializer != null:
                        Mark(vd.Initializer.Value, addr); break;
                    case IReturnOperation r when r.ReturnedValue != null && IsRefReturn(r):
                        Mark(r.ReturnedValue, addr); break;
                    case ISimpleAssignmentOperation sa when sa.IsRef:
                        Mark(sa.Value, addr); break;
                    case IConditionalOperation c when c.IsRef:
                        Mark(c.WhenTrue, addr); if (c.WhenFalse != null) Mark(c.WhenFalse, addr); break;
                    case IAssignmentOperation asg:
                        Mark(asg.Target, mutated); break;
                    case IIncrementOrDecrementOperation inc:
                        Mark(inc.Target, mutated); break;
                    case IAnonymousFunctionOperation af:
                        foreach (var r in af.Body.Descendants())
                        {
                            ISymbol? s = r is ILocalReferenceOperation lr ? lr.Local : r is IParameterReferenceOperation pr ? pr.Parameter : null;
                            if (s == null) continue;
                            if (!SymbolEqualityComparer.Default.Equals(s.ContainingSymbol, af.Symbol) && !IsInside(s.ContainingSymbol, af.Symbol)) captured.Add(s);
                        }
                        break;
                }
            }
        }
        foreach (var s in addr) if (!IsRefSym(s)) boxed.Add(s);
        foreach (var s in captured) if ((mutated.Contains(s) || addr.Contains(s)) && !IsRefSym(s)) boxed.Add(s);
    }

    static bool IsRefSym(ISymbol s) => s is ILocalSymbol l && l.RefKind != RefKind.None || s is IParameterSymbol p && p.RefKind != RefKind.None;

    static bool IsInside(ISymbol? s, ISymbol lambda)
    {
        for (var x = s; x != null; x = x.ContainingSymbol) if (SymbolEqualityComparer.Default.Equals(x, lambda)) return true;
        return false;
    }

    static bool IsRefReturn(IReturnOperation r)
    {
        for (var p = r.Parent; p != null; p = p.Parent)
        {
            if (p is IAnonymousFunctionOperation af) return af.Symbol.RefKind != RefKind.None;
            if (p is IMethodBodyOperation || p is IBlockOperation && p.Parent == null) break;
        }
        var sm = r.SemanticModel;
        var md = r.Syntax.Ancestors().OfType<MethodDeclarationSyntax>().FirstOrDefault();
        return md != null && sm != null && sm.GetDeclaredSymbol(md) is IMethodSymbol ms && ms.RefKind != RefKind.None;
    }

    static void Mark(IOperation op, HashSet<ISymbol> set)
    {
        while (op is IConversionOperation cv && cv.Conversion.IsIdentity) op = cv.Operand;
        if (op is ILocalReferenceOperation l) set.Add(l.Local);
        else if (op is IParameterReferenceOperation p) set.Add(p.Parameter);
    }

    // ------------------------------------------------------------------------------------------------ types
    static readonly HashSet<string> Specializable = new() { "Vec", "Ptr", "Iter", "RevIter", "CSpan", "Opt", "Atomic", "RefAt", "RefGet" };

    /// <summary>The one-letter code of a primitive Java type (D, F, I, L, B, Z, S, C), or null.</summary>
    static string? PrimCode(ITypeSymbol t)
    {
        if (t.TypeKind == TypeKind.Enum && t is INamedTypeSymbol en) return PrimCode(en.EnumUnderlyingType!);
        return t.SpecialType switch
        {
            SpecialType.System_Double => "D", SpecialType.System_Single => "F",
            SpecialType.System_Int32 or SpecialType.System_UInt32 => "I",
            SpecialType.System_Int64 or SpecialType.System_UInt64 => "L",
            SpecialType.System_Byte or SpecialType.System_SByte => "B",
            SpecialType.System_Boolean => "Z",
            SpecialType.System_Int16 => "S",
            SpecialType.System_UInt16 or SpecialType.System_Char => "C",
            _ => null
        };
    }

    static string JType(ITypeSymbol t) => JT(t, false);
    static string JBoxed(ITypeSymbol t) => JT(t, true);

    static string JT(ITypeSymbol t, bool box)
    {
        switch (t.SpecialType)
        {
            case SpecialType.System_Boolean: return box ? "Boolean" : "boolean";
            case SpecialType.System_SByte: case SpecialType.System_Byte: return box ? "Byte" : "byte";
            case SpecialType.System_Int16: return box ? "Short" : "short";
            case SpecialType.System_UInt16: case SpecialType.System_Char: return box ? "Character" : "char";
            case SpecialType.System_Int32: case SpecialType.System_UInt32: return box ? "Integer" : "int";
            case SpecialType.System_Int64: case SpecialType.System_UInt64: return box ? "Long" : "long";
            case SpecialType.System_Single: return box ? "Float" : "float";
            case SpecialType.System_Double: return box ? "Double" : "double";
            case SpecialType.System_String: return "String";
            case SpecialType.System_Object: return "Object";
            case SpecialType.System_Void: return box ? "Void" : "void";
        }
        if (t is IArrayTypeSymbol at)
        {
            if (at.Rank != 1) Todo("multidimensional array");
            return JT(at.ElementType, false) + "[]";
        }
        if (t is ITypeParameterSymbol tp) return tp.Name;
        if (t is INamedTypeSymbol n)
        {
            if (n.TypeKind == TypeKind.Enum) return JT(n.EnumUnderlyingType!, box);
            if (n.TypeKind == TypeKind.Delegate)
            {
                if (IsSystem(n)) return SysDelegate(n);
                if (IsRuntime(n)) return RuntimeTypeName(n);
                return TypeIdent(n);
            }
            if (IsRuntime(n)) return RuntimeTypeName(n);
            if (IsSystem(n))
            {
                switch (StripArity(n.Name))
                {
                    case "Exception": return "Throwable";
                    case "IDisposable": return "cpp.IDisposable";
                    case "Nullable": return JT(n.TypeArguments[0], true);
                    case "IEnumerable": return "java.lang.Iterable<" + JT(n.TypeArguments[0], true) + ">";
                    case "ValueType": return "Object";
                }
                return Todo("system type " + n.ToDisplayString());
            }
            return TypeIdent(n);
        }
        return Todo("type " + t.ToDisplayString());
    }

    /// <summary>A runtime type: cpp.Name, a specialised cpp.NameD for a primitive argument of the containers in
    /// Specializable, otherwise cpp.Name&lt;boxed arguments&gt;.</summary>
    static string RuntimeTypeName(INamedTypeSymbol n)
    {
        var parts = new List<string>();
        for (var c = n; c != null; c = c.ContainingType) parts.Insert(0, StripArity(c.Name));
        var s = "cpp." + string.Join(".", parts);
        if (n.TypeArguments.Length == 1 && Specializable.Contains(StripArity(n.Name)) && PrimCode(n.TypeArguments[0]) is string code)
        {
            Use("type " + n.ConstructUnboundGenericType().ToDisplayString().Replace("<>", "<" + n.TypeArguments[0].ToDisplayString() + ">"), s + code);
            return s + code;
        }
        Use("type " + n.OriginalDefinition.ToDisplayString(), s);
        if (n.TypeArguments.Length > 0) s += "<" + string.Join(", ", n.TypeArguments.Select(JBoxed)) + ">";
        return s;
    }

    /// <summary>The raw (unparameterised) Java class of a runtime type, for static members.</summary>
    static string RuntimeClass(INamedTypeSymbol n)
    {
        var s = RuntimeTypeName(n);
        var i = s.IndexOf('<');
        return i < 0 ? s : s.Substring(0, i);
    }

    static string SysDelegate(INamedTypeSymbol n)
    {
        var inv = n.DelegateInvokeMethod!;
        var name = StripArity(n.Name);
        if (name == "Func") return "cpp.Fn.F" + inv.Parameters.Length + "<" + string.Join(", ", inv.Parameters.Select(p => JBoxed(p.Type)).Append(JBoxed(inv.ReturnType))) + ">";
        if (name == "Action") return "cpp.Fn.A" + inv.Parameters.Length + (inv.Parameters.Length > 0 ? "<" + string.Join(", ", inv.Parameters.Select(p => JBoxed(p.Type))) + ">" : "");
        if (name == "Predicate") return "cpp.Fn.F1<" + JBoxed(inv.Parameters[0].Type) + ", Boolean>";
        if (name == "Comparison") return "cpp.Fn.F2<" + JBoxed(inv.Parameters[0].Type) + ", " + JBoxed(inv.Parameters[1].Type) + ", Integer>";
        return Todo("system delegate " + n.ToDisplayString());
    }

    /// <summary>The method a delegate of type t is called through.</summary>
    static string DelegateMethod(INamedTypeSymbol t)
    {
        if (IsSystem(t)) return StripArity(t.Name) == "Action" ? "run" : "apply";
        return "invoke";
    }

    /// <summary>The Java type of a ref to a value of type t: cpp.RefD ... cpp.RefO&lt;T&gt;.</summary>
    static string RefType(ITypeSymbol t) => PrimCode(t) is string c ? "cpp.Ref" + c : "cpp.RefO<" + JBoxed(t) + ">";
    static string CellType(ITypeSymbol t) => PrimCode(t) is string c ? "cpp.Ref" + c + ".Cell" : "cpp.RefO.Cell<" + JBoxed(t) + ">";

    static string ParamType(IParameterSymbol p) => p.RefKind is RefKind.Ref or RefKind.Out ? RefType(p.Type) : JType(p.Type);
    static string RetType(IMethodSymbol m) => m.RefKind != RefKind.None ? RefType(m.ReturnType) : JType(m.ReturnType);

    static string JavaFieldName(string n) => n;

    static bool IsStructType(ITypeSymbol t) => t.TypeKind == TypeKind.Struct && t.SpecialType == SpecialType.None && !(t is INamedTypeSymbol n && n.OriginalDefinition.SpecialType == SpecialType.System_Nullable_T);

    /// <summary>The value a field or array element of type t starts with (C#'s default).</summary>
    static string DefaultOf(ITypeSymbol t)
    {
        if (t.SpecialType == SpecialType.System_Boolean) return "false";
        if (PrimCode(t) is string c)
            return c switch { "D" => "0.0d", "F" => "0.0f", "L" => "0L", "B" => "((byte) 0)", "S" => "((short) 0)", "C" => "((char) 0)", _ => "0" };
        if (IsStructType(t) && t is INamedTypeSymbol n)
        {
            if (IsRuntime(n))
            {
                var tn = RuntimeTypeName(n);
                if (tn.Contains('<')) { Use("static " + n.OriginalDefinition.ToDisplayString() + ".zero()", RuntimeClass(n) + ".zero()"); return "(" + tn + ") " + RuntimeClass(n) + ".zero()"; }
                Use("static " + n.ToDisplayString() + ".ZERO", tn + ".ZERO");
                return tn + ".ZERO";
            }
            return Todo("engine struct " + n.Name);
        }
        return "null";
    }

    // ------------------------------------------------------------------------------------------------ declarations
    static void TypeDecl(INamedTypeSymbol s, StringBuilder sb)
    {
        var nm = TypeIdent(s);
        sb.Append("@groovy.transform.CompileStatic\n");
        if (s.TypeKind == TypeKind.Delegate)
        {
            var inv = s.DelegateInvokeMethod!;
            sb.Append("@FunctionalInterface\npublic interface ").Append(nm).Append(" {\n\t").Append(RetType(inv)).Append(" invoke(")
              .Append(string.Join(", ", inv.Parameters.Select(p => ParamType(p) + " " + Id(p.Name)))).Append(");\n}\n");
            return;
        }
        if (s.TypeKind == TypeKind.Enum)
        {
            sb.Append("public final class ").Append(nm).Append(" {\n\tprivate ").Append(nm).Append("() { }\n");
            var ut = JType(s.EnumUnderlyingType!);
            foreach (var f in s.GetMembers().OfType<IFieldSymbol>().Where(f => f.HasConstantValue))
                sb.Append("\tpublic static final ").Append(ut).Append(' ').Append(Id(f.Name)).Append(" = ").Append(GvFn.Const(f.ConstantValue, s.EnumUnderlyingType!)).Append(";\n");
            sb.Append("}\n");
            return;
        }
        if (s.TypeKind == TypeKind.Interface) { sb.Append("public interface ").Append(nm).Append(" { }\n"); Note("engine interface " + s.Name); return; }
        if (s.TypeKind != TypeKind.Class) { sb.Append("// TODO type kind ").Append(s.TypeKind).Append('\n'); Note("type kind " + s.TypeKind); return; }

        var head = new StringBuilder("public ");
        if (s.IsAbstract && !s.IsStatic) head.Append("abstract ");
        head.Append("class ").Append(nm);
        var b = s.BaseType;
        if (b != null && b.SpecialType != SpecialType.System_Object)
        {
            if (IsEngine(b)) head.Append(" extends ").Append(TypeIdent(b));
            else if (IsRuntime(b)) head.Append(" extends ").Append(RuntimeTypeName(b));
            else if (IsSystem(b) && b.Name == "Exception") head.Append(" extends RuntimeException");
            else Note("base type " + b.ToDisplayString());
        }
        var ifs = new List<string>();
        foreach (var i in s.Interfaces)
        {
            // Java cannot implement ICopy<Base> (inherited) and ICopy<Derived> both: keep the base's
            if (IsRuntime(i) && i.IsGenericType && b != null && IsEngine(b) && b.AllInterfaces.Any(bi => SymbolEqualityComparer.Default.Equals(bi.OriginalDefinition, i.OriginalDefinition)))
                continue;
            if (IsRuntime(i)) ifs.Add(RuntimeTypeName(i));
            else if (IsSystem(i) && i.Name == "IDisposable") ifs.Add("cpp.IDisposable");
            else if (IsSystem(i) && (i.Name == "IEquatable" || i.Name == "IComparable")) { }
            else if (IsEngine(i)) ifs.Add(TypeIdent(i));
            else Note("interface " + i.ToDisplayString());
        }
        if (ifs.Count > 0) head.Append(" implements ").Append(string.Join(", ", ifs));
        sb.Append(head).Append(" {\n");

        // the members of every partial declaration
        var decls = s.DeclaringSyntaxReferences.Select(r => r.GetSyntax()).OfType<ClassDeclarationSyntax>().ToList();
        foreach (var f in s.GetMembers().OfType<IFieldSymbol>().Where(f => !f.IsImplicitlyDeclared))
        {
            try { FieldDecl(f, sb); }
            catch (Todo e) { sb.Append("\t// TODO field ").Append(f.Name).Append(": ").Append(e.Message).Append('\n'); }
        }
        foreach (var cd in decls)
            foreach (var m in cd.Members)
            {
                try
                {
                    switch (m)
                    {
                        case MethodDeclarationSyntax md: MethodDecl(md, s, sb); break;
                        case ConstructorDeclarationSyntax ctor: CtorDecl(ctor, s, sb); break;
                        case FieldDeclarationSyntax: break;
                        case BaseTypeDeclarationSyntax: break;      // nested: a class of its own
                        case DelegateDeclarationSyntax: break;
                        default: sb.Append("\t// TODO member ").Append(m.Kind()).Append('\n'); Note("member " + m.Kind()); break;
                    }
                }
                catch (Todo e) { sb.Append("\t// TODO ").Append(e.Message).Append('\n'); }
                catch (Exception e) when (e is not global::Todo) { sb.Append("\t// TODO translator error: ").Append(e.GetType().Name).Append(' ').Append(e.Message.Replace("\n", " ")).Append('\n'); Note("translator error " + e.GetType().Name); }
            }
        sb.Append("}\n");
    }

    static void FieldDecl(IFieldSymbol f, StringBuilder sb)
    {
        var jt = JType(f.Type);
        var name = FieldIdent(f);
        if (f.IsConst)
        {
            sb.Append("\tpublic static final ").Append(jt).Append(' ').Append(name).Append(" = ").Append(GvFn.Const(f.ConstantValue, f.Type)).Append(";\n");
            return;
        }
        var stat = f.IsStatic ? "static " : "";
        var syn = f.DeclaringSyntaxReferences.Select(r => r.GetSyntax()).OfType<VariableDeclaratorSyntax>().FirstOrDefault();
        if (syn?.Initializer != null)
        {
            var op = M(syn).GetOperation(syn.Initializer) is IFieldInitializerOperation fi ? fi.Value : M(syn).GetOperation(syn.Initializer.Value);
            if (op == null) Todo("field initializer");
            var fn = new GvFn(null, f.ContainingType);
            var val = fn.Value(op!, f.Type);
            if (fn.Pre.Count == 0) { sb.Append("\tpublic ").Append(stat).Append(jt).Append(' ').Append(name).Append(" = ").Append(val).Append(";\n"); return; }
            if (!f.IsStatic) Todo("instance field initializer with statements");
            sb.Append("\tpublic static ").Append(jt).Append(' ').Append(name).Append(";\n\tstatic {\n");
            foreach (var p in fn.Pre) sb.Append("\t\t").Append(p).Append('\n');
            sb.Append("\t\t").Append(name).Append(" = ").Append(val).Append(";\n\t}\n");
            return;
        }
        sb.Append("\tpublic ").Append(stat).Append(jt).Append(' ').Append(name);
        var d = DefaultOf(f.Type);
        if (d != "null" && !(PrimCode(f.Type) != null)) sb.Append(" = ").Append(d);
        sb.Append(";\n");
    }

    static void MethodDecl(MethodDeclarationSyntax md, INamedTypeSymbol s, StringBuilder sb)
    {
        var m = (IMethodSymbol)M(md).GetDeclaredSymbol(md)!;
        var fn = new GvFn(m, s);
        var boxedSig = BoxedSignature(m);
        var head = new StringBuilder("\tpublic ");
        if (m.IsStatic) head.Append("static ");
        if (m.IsAbstract) head.Append("abstract ");
        if (m.IsGenericMethod) head.Append('<').Append(string.Join(", ", m.TypeParameters.Select(t => t.Name))).Append("> ");
        var rt = boxedSig != null ? boxedSig.Value.ret : RetType(m);
        head.Append(rt).Append(' ').Append(MethodIdent(m)).Append('(');
        head.Append(string.Join(", ", m.Parameters.Select((p, i) => (boxedSig != null ? boxedSig.Value.ps[i] : ParamType(p)) + " " + fn.DeclareParam(p, boxedSig != null && boxedSig.Value.ps[i] != ParamType(p)))));
        head.Append(')');
        if (m.IsAbstract) { sb.Append(head).Append(";\n\n"); return; }
        string body;
        IOperation? op = md.Body != null ? M(md).GetOperation(md.Body) : md.ExpressionBody != null ? M(md).GetOperation(md.ExpressionBody) : null;
        try { body = op == null ? "" : fn.Body(op, m); }
        catch (Todo e) { body = "\t\tthrow new IllegalStateException('TODO " + GvFn.Esc(e.Message) + "');\n"; }
        sb.Append(head).Append(" {\n").Append(fn.Prologue()).Append(body).Append("\t}\n\n");
    }

    /// <summary>A method implementing a member of a generic runtime interface whose type arguments are primitive
    /// (IHashFn&lt;int&gt;.HashOf): Java needs the erased, boxed signature to implement it.</summary>
    static (string ret, string[] ps)? BoxedSignature(IMethodSymbol m)
    {
        if (m.IsStatic) return null;
        foreach (var i in m.ContainingType.AllInterfaces)
        {
            if (!IsRuntime(i) || !i.IsGenericType) continue;
            foreach (var im in i.GetMembers().OfType<IMethodSymbol>())
            {
                var impl = m.ContainingType.FindImplementationForInterfaceMember(im);
                if (!SymbolEqualityComparer.Default.Equals(impl, m)) continue;
                var od = im.OriginalDefinition;
                var ps = od.Parameters.Select((p, k) => p.Type is ITypeParameterSymbol ? (p.RefKind is RefKind.Ref or RefKind.Out ? "cpp.RefO<" + JBoxed(m.Parameters[k].Type) + ">" : JBoxed(m.Parameters[k].Type)) : ParamType(m.Parameters[k])).ToArray();
                var ret = od.ReturnType is ITypeParameterSymbol ? JBoxed(m.ReturnType) : RetType(m);
                return (ret, ps);
            }
        }
        return null;
    }

    static void CtorDecl(ConstructorDeclarationSyntax cd, INamedTypeSymbol s, StringBuilder sb)
    {
        var c = (IMethodSymbol)M(cd).GetDeclaredSymbol(cd)!;
        var fn = new GvFn(c, s);
        if (c.IsStatic)
        {
            var sbody = cd.Body != null ? fn.Body(M(cd).GetOperation(cd.Body)!, c) : "";
            sb.Append("\tstatic {\n").Append(fn.Prologue()).Append(sbody).Append("\t}\n\n");
            return;
        }
        var head = new StringBuilder("\tpublic ").Append(TypeIdent(s)).Append('(')
            .Append(string.Join(", ", c.Parameters.Select(p => ParamType(p) + " " + fn.DeclareParam(p, false)))).Append(')');
        var lines = new StringBuilder();
        if (cd.Initializer != null && M(cd).GetOperation(cd.Initializer) is IOperation init)
        {
            var inv = init is IExpressionStatementOperation es ? es.Operation : init;
            if (inv is IInvocationOperation ci)
            {
                var who = cd.Initializer.ThisOrBaseKeyword.IsKind(SyntaxKind.ThisKeyword) ? "this" : "super";
                var args = fn.Args(ci.TargetMethod, ci.Arguments);
                if (fn.Pre.Count > 0) Todo("constructor initializer with statements");
                lines.Append("\t\t").Append(who).Append('(').Append(args).Append(");\n");
            }
            else Todo("constructor initializer " + inv.Kind);
        }
        var body = cd.Body != null ? fn.Body(M(cd).GetOperation(cd.Body)!, c) : "";
        sb.Append(head).Append(" {\n").Append(lines).Append(fn.Prologue()).Append(body).Append("\t}\n\n");
    }
}
