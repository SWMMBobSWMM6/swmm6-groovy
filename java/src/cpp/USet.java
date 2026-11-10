// USet.java - std::unordered_set<T> (C# Cpp.USet<T>, cs/runtime/Containers.cs) on HashTable's node order.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class USet<T> extends HashTable<T, T> implements ICopy<USet<T>> {
	/** USet(h, eq): hash null is std::hash&lt;T&gt;, eq null std::equal_to&lt;T&gt;. */
	public USet(Fn.F1<T, Long> h, Fn.F2<T, T, Boolean> eq) { this(hashOf(h), eqOf(eq)); }

	USet(HashOf<T> h, EqOf<T> eq) { super(x -> x, h, eq); }

	/** USet(initializer list, h, eq). */
	public USet(T[] il, Fn.F1<T, Long> h, Fn.F2<T, T, Boolean> eq) {
		this(hashOf(h), eqOf(eq));
		for (T x : il) Insert(x);
	}

	public Pair<MIt<T>, Boolean> insert(T x) {
		long code = hash.of(x);
		HNode<T> f = findNode(x, code);
		if (f != null) return UMap.res(f, false);
		return UMap.res(link(CtUtil.keyCopy(x), code), true);
	}
	public Pair<MIt<T>, Boolean> emplace(T x) { return insert(x); }

	@Override public USet<T> Clone() { USet<T> m = new USet<T>(hash, eq); m.CopyFrom(this, Elem::Copy); return m; }
	@Override public void Assign(USet<T> o) { if (this != o) CopyFrom(o, Elem::Copy); }
	public void MoveFrom(USet<T> o) { if (this != o) { CopyFrom(o, x -> x); o.clear(); } }
}
