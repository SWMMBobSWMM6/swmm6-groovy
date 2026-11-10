// UMap.java - std::unordered_map<K, V> (C# Cpp.UMap<K, V>, cs/runtime/Containers.cs) on HashTable's node order.
// Java cannot make a V by reflection as C#'s Elem<V>.Make does, so the constructors take the translator's maker
// of V (mk) for operator[] and try_emplace.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class UMap<K, V> extends HashTable<K, Pair<K, V>> implements ICopy<UMap<K, V>> {
	/** How to make a mapped value (the translator's maker; C#: an explicit maker or Elem&lt;V&gt;.Make). */
	public Fn.F0<V> mk;

	/** UMap(h, eq, mk): hash null is std::hash&lt;K&gt;, eq null std::equal_to&lt;K&gt;. */
	public UMap(Fn.F1<K, Long> h, Fn.F2<K, K, Boolean> eq, Fn.F0<V> mk) {
		this(hashOf(h), eqOf(eq), mk);
	}

	UMap(HashOf<K> h, EqOf<K> eq, Fn.F0<V> mk) {
		super(p -> p.first, h, eq);
		this.mk = mk;
	}

	/** UMap(initializer list, h, eq) + the maker of V: each pair copied in, a repeated key keeping the first. */
	public UMap(Pair<K, V>[] il, Fn.F1<K, Long> h, Fn.F2<K, K, Boolean> eq, Fn.F0<V> mk) {
		this(hashOf(h), eqOf(eq), mk);
		for (Pair<K, V> p : il) Insert(p.Clone());
	}

	final V MakeV() { return mk != null ? mk.apply() : null; }

	/** A reference to a pair's mapped value. */
	static final class SecondRef<K, V> extends RefO<V> {
		private final Pair<K, V> p;
		SecondRef(Pair<K, V> p) { this.p = p; }
		@Override public V get() { return p.second; }
		@Override public void set(V v) { p.second = v; }
	}

	/** operator[]: the node for k, inserted with a made value if absent (the map owns a copy of the key). */
	final HNode<Pair<K, V>> indexNode(K k) {
		long code = hash.of(k);
		HNode<Pair<K, V>> f = findNode(k, code);
		if (f == null) f = link(new Pair<K, V>(CtUtil.keyCopy(k), MakeV()), code);
		return f;
	}

	/** operator[] read as a value. */
	public V Index(K k) { return indexNode(k).val.second; }
	/** operator[] as a reference. */
	public RefO<V> ref_Index(K k) { return new SecondRef<K, V>(indexNode(k).val); }

	final HNode<Pair<K, V>> atNode(K k) {
		HNode<Pair<K, V>> f = Find(k);
		if (f == null) throw new OutOfRange(CtUtil.str("_Map_base::at"));
		return f;
	}
	public V at(K k) { return atNode(k).val.second; }
	public RefO<V> ref_at(K k) { return new SecondRef<K, V>(atNode(k).val); }

	static <E> Pair<MIt<E>, Boolean> res(HNode<E> n, boolean added) { return new Pair<MIt<E>, Boolean>(new MIt<E>(n), added); }

	/** insert(k, v) if k is absent, the key copied in. */
	final Pair<MIt<Pair<K, V>>, Boolean> insertKV(K k, V v) {
		long code = hash.of(k);
		HNode<Pair<K, V>> f = findNode(k, code);
		if (f != null) return res(f, false);
		return res(link(new Pair<K, V>(CtUtil.keyCopy(k), v), code), true);
	}

	public Pair<MIt<Pair<K, V>>, Boolean> insert(Pair<K, V> p) { return insertKV(p.first, p.second); }
	public Pair<MIt<Pair<K, V>>, Boolean> emplace_K_V(K k, V v) { return insertKV(k, v); }
	public Pair<MIt<Pair<K, V>>, Boolean> emplace_PairKV(Pair<K, V> p) { return insertKV(p.first, p.second); }
	public Pair<MIt<Pair<K, V>>, Boolean> try_emplace_K_V(K k, V v) { return insertKV(k, v); }
	public Pair<MIt<Pair<K, V>>, Boolean> try_emplace_K(K k) { return insertKV(k, MakeV()); }
	public Pair<MIt<Pair<K, V>>, Boolean> insert_or_assign(K k, V v) {
		HNode<Pair<K, V>> f = Find(k);
		if (f != null) { f.val.second = v; return res(f, false); }
		return insertKV(k, v);
	}

	@Override public UMap<K, V> Clone() { UMap<K, V> m = new UMap<K, V>(hash, eq, mk); m.CopyFrom(this, Pair::Clone); return m; }
	@Override public void Assign(UMap<K, V> o) { if (this != o) CopyFrom(o, Pair::Clone); }
	public void MoveFrom(UMap<K, V> o) { if (this != o) { CopyFrom(o, p -> p); o.clear(); } }
}
