// TupN.java - std::tuple of more than three elements (C# Cpp.TupN, cs/runtime/Misc.cs): a map key compared element
// by element, as std::tuple's operator< does. Elements are boxed: Double, Integer (int and int-based enums), Long,
// Boolean, PtrD (compared by the identity of its array, then its offset) or anything Comparable.
// The C# compares a uint element unsigned (through IComparable); Java boxes a uint as an Integer and compares it
// signed, which differs only for values of 2^31 and above.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class TupN implements ICopy<TupN>, Comparable<TupN> {
	public Object[] v;
	public TupN() { v = new Object[0]; }
	public TupN(Object... items) { v = items; }
	@Override public TupN Clone() { return new TupN(v.clone()); }
	@Override public void Assign(TupN o) { v = o.v.clone(); }
	public void MoveFrom(TupN o) { v = o.v; }
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static int Cmp(Object a, Object b) {
		if (a == null) return b == null ? 0 : -1;
		if (a instanceof Double) { double x = (Double) a, y = (Double) b; return x < y ? -1 : y < x ? 1 : 0; }
		if (a instanceof Integer) return Integer.compare((Integer) a, (Integer) b);
		if (a instanceof Long) return Long.compare((Long) a, (Long) b);
		if (a instanceof Boolean) return Boolean.compare((Boolean) a, (Boolean) b);
		if (a instanceof PtrD) {
			PtrD p = (PtrD) a, q = (PtrD) b;
			int c = Long.compare(Ident.Of(p.a), Ident.Of(q.a));
			return c != 0 ? c : Long.compare(p.o, q.o);
		}
		if (a instanceof Comparable) return ((Comparable) a).compareTo(b);
		throw new SysException("NotSupportedException", "tuple element " + a.getClass());
	}
	public int CompareTo(TupN o) {
		for (int i = 0; i < v.length; i++) { int c = Cmp(v[i], o.v[i]); if (c != 0) return c; }
		return 0;
	}
	@Override public int compareTo(TupN o) { return CompareTo(o); }
	public boolean Equals(TupN o) { return o != null && CompareTo(o) == 0; }
	@Override public boolean equals(Object o) { return o instanceof TupN && Equals((TupN) o); }
	@Override public int hashCode() { return v.length; }
}
