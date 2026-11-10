// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** A two-argument predicate of the engine's own, such as a key-equal (C# Cpp.IBinFn). */
public interface IBinFn<K> {
	boolean Call2(K a, K b);
}
