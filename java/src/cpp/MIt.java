// MIt.java - an iterator over a hashed or sorted container (C# struct Cpp.MIt<E>, cs/runtime/Containers.cs): a node
// (null at end) or a position in a sorted container's list. A struct, so an immutable value here: ++ and -- make
// new iterators, and zero() is its default.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class MIt<E> {
	public final HNode<E> node;
	public final java.util.ArrayList<E> list;
	public final int pos;

	public MIt(HNode<E> n) { node = n; list = null; pos = 0; }
	public MIt(java.util.ArrayList<E> l, int p) { node = null; list = l; pos = p; }

	private static final MIt<Object> ZERO = new MIt<Object>((HNode<Object>) null);
	/** default(MIt&lt;E&gt;): no node, no list (equal to a hashed container's end()). */
	@SuppressWarnings("unchecked")
	public static <E> MIt<E> zero() { return (MIt<E>) ZERO; }

	/** A value: copying it is the copy. */
	public MIt<E> Clone() { return this; }

	public E get_Deref() { return list != null ? list.get(pos) : node.val; }

	public static <E> MIt<E> op_Increment(MIt<E> x) { return x.list != null ? new MIt<E>(x.list, x.pos + 1) : new MIt<E>(x.node.next); }
	public static <E> MIt<E> op_Decrement(MIt<E> x) { return new MIt<E>(x.list, x.pos - 1); }

	/** A list position compares by position (end of a hashed container counts as no position); two nodes by identity. */
	public static <E> boolean op_Eq(MIt<E> a, MIt<E> b) {
		if (a.list != null || b.list != null) return posOf(a) == posOf(b);
		return a.node == b.node;
	}
	public static <E> boolean op_Ne(MIt<E> a, MIt<E> b) { return !op_Eq(a, b); }
	private static int posOf(MIt<?> x) { return x.list != null ? x.pos : Integer.MAX_VALUE; }

	@Override public boolean equals(Object o) {
		if (!(o instanceof MIt<?> x)) return false;
		if (list != null || x.list != null) return posOf(this) == posOf(x);
		return node == x.node;
	}
	@Override public int hashCode() { return pos; }
}
