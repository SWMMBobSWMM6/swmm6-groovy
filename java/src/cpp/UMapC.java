// UMapC.java - std::unordered_map<K, V, H, E> with the engine's hasher H and key-equal E (C# Cpp.UMapC, in
// cs/runtime/Containers.cs): the same table, hashed by H, so lookups and the iteration order are the C++ ones.
//
// C# makes the one H and E with new H() / new E(); Java's erased generics cannot, so the hasher and key-equal
// come as makers (the convention of the element makers: Fn.F0<H>, Fn.F0<E>, appended last). The translator does
// not pass them yet (it writes new cpp.UMapC<K, V, H, E>(mk)); until it does, the constructors without them use
// the engine's one pair, CiHash / CiEqual (the only H and E the engine instantiates), found by name.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class UMapC<K, V, H extends IHashFn<K>, E extends IBinFn<K>> extends UMap<K, V> {
	final IHashFn<K> h0;
	final IBinFn<K> e0;

	/** UMapC(mk) + the makers of H and E. */
	public UMapC(Fn.F0<V> mk, Fn.F0<H> hm, Fn.F0<E> em) { this(mk, (IHashFn<K>) hm.apply(), (IBinFn<K>) em.apply()); }

	/** UMapC(mk), the translator's present form: H and E are the engine's CiHash and CiEqual (see the header). */
	public UMapC(Fn.F0<V> mk) { this(mk, EngineFns.<K>hasher(), EngineFns.<K>keyEqual()); }

	/** UMapC(initializer list) + the maker of V (+ the makers of H and E). */
	public UMapC(Pair<K, V>[] il, Fn.F0<V> mk, Fn.F0<H> hm, Fn.F0<E> em) {
		this(mk, hm, em);
		for (Pair<K, V> p : il) Insert(p.Clone());
	}
	public UMapC(Pair<K, V>[] il, Fn.F0<V> mk) {
		this(mk);
		for (Pair<K, V> p : il) Insert(p.Clone());
	}

	UMapC(Fn.F0<V> mk, IHashFn<K> h, IBinFn<K> e) {
		super((HashOf<K>) h::HashOf, (EqOf<K>) e::Call2, mk);
		h0 = h;
		e0 = e;
	}

	@Override public UMapC<K, V, H, E> Clone() { UMapC<K, V, H, E> m = new UMapC<K, V, H, E>(mk, h0, e0); m.CopyFrom(this, Pair::Clone); return m; }
	public void Assign(UMapC<K, V, H, E> o) { super.Assign(o); }
	public void MoveFrom(UMapC<K, V, H, E> o) { super.MoveFrom(o); }

	/** The engine's case-insensitive hasher and key-equal, made once by name (see the header). */
	static final class EngineFns {
		private static Object hash, keyEq;

		@SuppressWarnings("unchecked")
		static synchronized <K> IHashFn<K> hasher() {
			if (hash == null) hash = make("engine.CiHash");
			return (IHashFn<K>) hash;
		}

		@SuppressWarnings("unchecked")
		static synchronized <K> IBinFn<K> keyEqual() {
			if (keyEq == null) keyEq = make("engine.CiEqual");
			return (IBinFn<K>) keyEq;
		}

		private static Object make(String name) {
			try {
				return Class.forName(name).getConstructor().newInstance();
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("cpp.UMapC: no hasher/key-equal given and " + name
					+ " not found; the translator must pass the makers of H and E", ex);
			}
		}
	}
}
