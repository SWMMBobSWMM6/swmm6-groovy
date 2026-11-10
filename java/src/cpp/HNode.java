// HNode.java - a hash table node (C# Cpp.HNode<E>, cs/runtime/Containers.cs); the before-begin sentinel is a node too.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class HNode<E> {
	public HNode<E> next;
	public E val;
	/** The key's hash code, a size_t (unsigned 64-bit) held as a long. */
	public long code;
}
