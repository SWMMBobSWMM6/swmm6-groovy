// StdEq.java - std::equal_to as an engine-style key-equal (C# Cpp.StdEq<K>, cs/runtime/Containers.cs), for a
// container whose hasher is the engine's but whose key-equal is the default.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class StdEq<K> implements IBinFn<K> {
	@Override public boolean Call2(K a, K b) { return Elem.Eq(a, b); }
}
