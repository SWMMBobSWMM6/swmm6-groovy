// Map.java - std::map (C# Cpp.Map<K, V>, cs/runtime/Containers.cs): keys kept sorted by the comparator in a list
// (a red-black tree's order is the sorted order). Iterators are list positions. The constructors take the
// translator's maker of V (C#: an explicit maker or Elem<V>.Make) for operator[].
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Map<K, V> implements ICopy<Map<K, V>> {
	/** A comparator without boxing. */
	@FunctionalInterface interface LtOf<K> { boolean lt(K a, K b); }

	private final LtOf<K> lt;
	private final Fn.F2<K, K, Boolean> less;
	private java.util.ArrayList<Pair<K, V>> items = new java.util.ArrayList<>();
	public Fn.F0<V> mk;

	/** Map(less, mk): less null is std::less&lt;K&gt;. */
	public Map(Fn.F2<K, K, Boolean> less, Fn.F0<V> mk) {
		this.less = less;
		this.lt = ltOf(less);
		this.mk = mk;
	}

	/** Map(initializer list, less) + the maker of V. */
	public Map(Pair<K, V>[] il, Fn.F2<K, K, Boolean> less, Fn.F0<V> mk) {
		this(less, mk);
		for (Pair<K, V> p : il) insert(p.Clone());
	}

	@SuppressWarnings("unchecked")
	static <K> LtOf<K> ltOf(Fn.F2<K, K, Boolean> less) {
		if (less == null || less == (Object) Less.Fn) return (a, b) -> Less.lt(a, b);
		return (a, b) -> less.apply(a, b);
	}

	private int lower(K k) {
		int lo = 0, hi = items.size();
		while (lo < hi) {
			int mid = (lo + hi) / 2;
			if (lt.lt(items.get(mid).first, k)) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	private int findIdx(K k) { int i = lower(k); return (i < items.size() && !lt.lt(k, items.get(i).first)) ? i : -1; }

	private V makeV() { return mk != null ? mk.apply() : null; }

	private Pair<K, V> indexPair(K k) {
		int i = lower(k);
		if (i == items.size() || lt.lt(k, items.get(i).first)) items.add(i, new Pair<K, V>(CtUtil.keyCopy(k), makeV()));
		return items.get(i);
	}

	/** operator[] read as a value. */
	public V Index(K k) { return indexPair(k).second; }
	/** operator[] as a reference. */
	public RefO<V> ref_Index(K k) { return new UMap.SecondRef<K, V>(indexPair(k)); }

	private Pair<K, V> atPair(K k) {
		int i = findIdx(k);
		if (i < 0) throw new OutOfRange(CtUtil.str("map::at"));
		return items.get(i);
	}
	public V at(K k) { return atPair(k).second; }
	public RefO<V> ref_at(K k) { return new UMap.SecondRef<K, V>(atPair(k)); }

	private Pair<MIt<Pair<K, V>>, Boolean> res(int i, boolean added) {
		return new Pair<MIt<Pair<K, V>>, Boolean>(new MIt<Pair<K, V>>(items, i), added);
	}

	public Pair<MIt<Pair<K, V>>, Boolean> insert(Pair<K, V> p) {
		int i = lower(p.first);
		if (i < items.size() && !lt.lt(p.first, items.get(i).first)) return res(i, false);
		items.add(i, new Pair<K, V>(CtUtil.keyCopy(p.first), p.second));
		return res(i, true);
	}
	public Pair<MIt<Pair<K, V>>, Boolean> emplace_K_V(K k, V v) { return insert(new Pair<K, V>(k, v)); }
	public Pair<MIt<Pair<K, V>>, Boolean> emplace_PairKV(Pair<K, V> p) { return insert(p); }
	public Pair<MIt<Pair<K, V>>, Boolean> try_emplace(K k, V v) { return insert(new Pair<K, V>(k, v)); }
	public Pair<MIt<Pair<K, V>>, Boolean> insert_or_assign(K k, V v) {
		int i = findIdx(k);
		if (i >= 0) { items.get(i).second = v; return res(i, false); }
		return insert(new Pair<K, V>(k, v));
	}

	public MIt<Pair<K, V>> find(K k) { int i = findIdx(k); return new MIt<Pair<K, V>>(items, i < 0 ? items.size() : i); }
	public MIt<Pair<K, V>> lower_bound(K k) { return new MIt<Pair<K, V>>(items, lower(k)); }
	public MIt<Pair<K, V>> upper_bound(K k) {
		int i = lower(k);
		while (i < items.size() && !lt.lt(k, items.get(i).first)) i++;
		return new MIt<Pair<K, V>>(items, i);
	}
	public long count(K k) { return findIdx(k) >= 0 ? 1L : 0L; }
	public boolean contains(K k) { return findIdx(k) >= 0; }
	public long erase_K(K k) { int i = findIdx(k); if (i < 0) return 0; items.remove(i); return 1; }
	public MIt<Pair<K, V>> erase_MItPairKV(MIt<Pair<K, V>> it) { items.remove(it.pos); return new MIt<Pair<K, V>>(items, it.pos); }
	public long size() { return items.size(); }
	public boolean empty() { return items.isEmpty(); }
	public void clear() { items.clear(); }
	public MIt<Pair<K, V>> begin() { return new MIt<Pair<K, V>>(items, 0); }
	public MIt<Pair<K, V>> end() { return new MIt<Pair<K, V>>(items, items.size()); }
	/** The elements in key order (the list itself, as the C# returns it). */
	public Iterable<Pair<K, V>> Items() { return items; }

	@Override public Map<K, V> Clone() {
		Map<K, V> m = new Map<K, V>(less, mk);
		m.items.ensureCapacity(items.size());
		for (Pair<K, V> p : items) m.items.add(p.Clone());
		return m;
	}
	@Override public void Assign(Map<K, V> o) {
		if (this == o) return;
		java.util.ArrayList<Pair<K, V>> l = new java.util.ArrayList<>(o.items.size());
		for (Pair<K, V> p : o.items) l.add(p.Clone());
		items = l;
	}
	public void MoveFrom(Map<K, V> o) {
		if (this == o) return;
		items = o.items;
		o.items = new java.util.ArrayList<>();
	}
}
