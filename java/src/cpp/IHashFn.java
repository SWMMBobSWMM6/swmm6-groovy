// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** A hasher of the engine's own (C# Cpp.IHashFn): the result is a size_t, held as a long. */
public interface IHashFn<K> {
	long HashOf(K k);
}
