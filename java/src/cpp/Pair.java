// Pair.java - std::pair (C# Cpp.Pair<A, B>, cs/runtime/Std.cs): a mutable class so p.first = x works through
// references. Primitive members are boxed (cpp.Pair<Integer, Double>); the translator casts what it reads.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Pair<A, B> implements ICopy<Pair<A, B>>, Comparable<Pair<A, B>> {
	public A first;
	public B second;

	/** Pair(): each member made by its maker (C#'s Elem&lt;A&gt;.Make, Elem&lt;B&gt;.Make); a null maker gives null. */
	public Pair(Fn.F0<A> ma, Fn.F0<B> mb) { first = ma == null ? null : ma.apply(); second = mb == null ? null : mb.apply(); }
	public Pair(A a, B b) { first = a; second = b; }

	@Override public Pair<A, B> Clone() { return new Pair<A, B>(Elem.Copy(first), Elem.Copy(second)); }
	@Override public void Assign(Pair<A, B> o) { first = Elem.Copy(o.first); second = Elem.Copy(o.second); }

	public RefO<A> ref_first() {
		return new RefO<A>() {
			@Override public A get() { return first; }
			@Override public void set(A v) { first = v; }
		};
	}
	public RefO<B> ref_second() {
		return new RefO<B>() {
			@Override public B get() { return second; }
			@Override public void set(B v) { second = v; }
		};
	}

	/** std::pair's operator&lt;: first, then second. */
	public int CompareTo(Pair<A, B> o) {
		if (Elem.Less(first, o.first)) return -1;
		if (Elem.Less(o.first, first)) return 1;
		if (Elem.Less(second, o.second)) return -1;
		if (Elem.Less(o.second, second)) return 1;
		return 0;
	}
	@Override public int compareTo(Pair<A, B> o) { return CompareTo(o); }
	public static <A, B> boolean op_Lt(Pair<A, B> x, Pair<A, B> y) { return x.CompareTo(y) < 0; }
	public static <A, B> boolean op_Gt(Pair<A, B> x, Pair<A, B> y) { return x.CompareTo(y) > 0; }
	public static <A, B> boolean op_Le(Pair<A, B> x, Pair<A, B> y) { return x.CompareTo(y) <= 0; }
	public static <A, B> boolean op_Ge(Pair<A, B> x, Pair<A, B> y) { return x.CompareTo(y) >= 0; }
	public static <A, B> boolean Eq(Pair<A, B> x, Pair<A, B> y) { return Elem.Eq(x.first, y.first) && Elem.Eq(x.second, y.second); }
}
