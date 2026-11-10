// Less.java - std::less (C# Cpp.Less<T>, cs/runtime/Containers.cs): the ordering of std::map, std::set, std::sort
// without a comparator, lower_bound and the rest. Java's erased generics have no T, so the one comparator decides
// by the values' run-time class: numbers as numbers (double and float with the primitive a < b, as C++ does),
// strings as char_traits compares them (unsigned bytes, then length), pairs first then second, Comparable values
// (TupN) by compareTo.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Less<T> {
	/** Less&lt;T&gt;.Fn: a raw Fn.F2 so it can stand for Func&lt;T, T, bool&gt; of any T (the translator passes it where
	 *  the C# passes Less&lt;double&gt;.Fn, Less&lt;int&gt;.Fn, Less&lt;Pair&lt;int, Str&gt;&gt;.Fn, ...). */
	@SuppressWarnings("rawtypes")
	public static final cpp.Fn.F2 Fn = (cpp.Fn.F2<Object, Object, Boolean>) (a, b) -> lt(a, b);

	public boolean op_Call(T a, T b) { return lt(a, b); }

	/** a &lt; b as std::less does it. */
	@SuppressWarnings("rawtypes")
	public static boolean lt(Object a, Object b) {
		if (a instanceof Double x) return (double) x < (double) (Double) b;
		if (a instanceof Integer x) return (int) x < (int) (Integer) b;
		if (a instanceof Long x) return (long) x < (long) (Long) b;
		if (a instanceof Str x) return CtUtil.strCompare(x, (Str) b) < 0;
		if (a instanceof Pair p) {
			Pair q = (Pair) b;
			if (lt(p.first, q.first)) return true;
			if (lt(q.first, p.first)) return false;
			return lt(p.second, q.second);
		}
		if (a instanceof Float x) return (float) x < (float) (Float) b;
		if (a instanceof Short x) return (short) x < (short) (Short) b;
		if (a instanceof Byte x) return (byte) x < (byte) (Byte) b;
		if (a instanceof Character x) return (char) x < (char) (Character) b;
		if (a instanceof Boolean x) return !x && (Boolean) b;
		if (a == null) return b != null;      // C#'s Comparer: null first
		if (b == null) return false;
		if (a instanceof Comparable c) {
			@SuppressWarnings("unchecked") int r = c.compareTo(b);
			return r < 0;
		}
		return IdOrder.lt(a, b);
	}

	/** An order by identity for objects with no ordering of their own (std::set&lt;const Column*&gt;, which C++
	 *  orders by address): any consistent order gives the same membership. The C# Comparer throws there. */
	static final class IdOrder {
		private static final java.util.IdentityHashMap<Object, Long> ids = new java.util.IdentityHashMap<>();
		private static long next;

		static boolean lt(Object a, Object b) {
			if (a == b) return false;
			int ha = System.identityHashCode(a), hb = System.identityHashCode(b);
			if (ha != hb) return ha < hb;
			return id(a) < id(b);      // two objects with one identity hash: number them
		}

		private static synchronized long id(Object o) {
			Long v = ids.get(o);
			if (v == null) { v = ++next; ids.put(o, v); }
			return v;
		}
	}
}
