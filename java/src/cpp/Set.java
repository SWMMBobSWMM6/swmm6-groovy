// Set.java - std::set (C# Cpp.Set<T>, cs/runtime/Containers.cs): elements kept sorted by the comparator in a list.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Set<T> implements ICopy<Set<T>> {
	private final Map.LtOf<T> lt;
	private final Fn.F2<T, T, Boolean> less;
	private java.util.ArrayList<T> items = new java.util.ArrayList<>();

	/** Set(less): less null is std::less&lt;T&gt;. */
	public Set(Fn.F2<T, T, Boolean> less) { this.less = less; this.lt = Map.ltOf(less); }
	/** Set(initializer list, less). */
	public Set(T[] il, Fn.F2<T, T, Boolean> less) { this(less); for (T x : il) insert(x); }

	private int lower(T k) {
		int lo = 0, hi = items.size();
		while (lo < hi) {
			int mid = (lo + hi) / 2;
			if (lt.lt(items.get(mid), k)) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}
	private boolean has(int i, T k) { return i < items.size() && !lt.lt(k, items.get(i)); }

	public Pair<MIt<T>, Boolean> insert(T x) {
		int i = lower(x);
		if (has(i, x)) return new Pair<MIt<T>, Boolean>(new MIt<T>(items, i), false);
		items.add(i, CtUtil.keyCopy(x));
		return new Pair<MIt<T>, Boolean>(new MIt<T>(items, i), true);
	}
	public Pair<MIt<T>, Boolean> emplace(T x) { return insert(x); }
	public MIt<T> find(T k) { int i = lower(k); return new MIt<T>(items, has(i, k) ? i : items.size()); }
	public long count(T k) { return has(lower(k), k) ? 1L : 0L; }
	public boolean contains(T k) { return count(k) > 0; }
	public long erase_T(T k) { int i = lower(k); if (has(i, k)) { items.remove(i); return 1; } return 0; }
	public MIt<T> erase_MItT(MIt<T> it) { items.remove(it.pos); return new MIt<T>(items, it.pos); }
	public long size() { return items.size(); }
	public boolean empty() { return items.isEmpty(); }
	public void clear() { items.clear(); }
	public MIt<T> begin() { return new MIt<T>(items, 0); }
	public MIt<T> end() { return new MIt<T>(items, items.size()); }
	/** The elements in order (the list itself, as the C# returns it). */
	public Iterable<T> Items() { return items; }

	@Override public Set<T> Clone() {
		Set<T> m = new Set<T>(less);
		m.items.ensureCapacity(items.size());
		for (T x : items) m.items.add(Elem.Copy(x));
		return m;
	}
	@Override public void Assign(Set<T> o) {
		if (this == o) return;
		java.util.ArrayList<T> l = new java.util.ArrayList<>(o.items.size());
		for (T x : o.items) l.add(Elem.Copy(x));
		items = l;
	}
	public void MoveFrom(Set<T> o) {
		if (this == o) return;
		items = o.items;
		o.items = new java.util.ArrayList<>();
	}
}
