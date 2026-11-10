// Hash.java - std::hash<T> as an object (C# Cpp.Hash<T>, cs/runtime/Containers.cs).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Hash<T> {
	public static <X> long Of(X x) { return StdHash.hashObj(x); }
	public long op_Call(T x) { return StdHash.hashObj(x); }
}
