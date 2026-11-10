// EqualTo.java - std::equal_to<T> (C# Cpp.EqualTo<T>, cs/runtime/Containers.cs): EqualityComparer<T>.Default.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class EqualTo<T> {
	public boolean op_Call(T a, T b) { return Elem.Eq(a, b); }
}
