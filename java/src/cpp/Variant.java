// Variant.java - std::variant<bool, int, double, std::string> (C# Cpp.Variant, cs/runtime/Std.cs; the engine's
// user-flag value): the alternative held, boxed, and its index in the declared list. Get<T> (std::get) is left
// out: the engine does not call it, and it throws part A's BadVariantAccess.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Variant implements ICopy<Variant> {
	public Object v = Boolean.FALSE;
	public int idx;
	public Variant() { }
	public Variant(boolean x) { Set_b(x); }
	public Variant(int x) { Set_i(x); }
	public Variant(double x) { Set_d(x); }
	public Variant(Str x) { Set_Str(x); }
	public void Set_b(boolean x) { v = x; idx = 0; }
	public void Set_i(int x) { v = x; idx = 1; }
	public void Set_d(double x) { v = x; idx = 2; }
	public void Set_Str(Str x) { v = x.Clone(); idx = 3; }
	public long index() { return idx; }
	public boolean Holds(Class<?> c) { return c.isInstance(v); }
	@Override public Variant Clone() { Variant c = new Variant(); c.Assign(this); return c; }
	@Override public void Assign(Variant o) { idx = o.idx; v = o.v instanceof Str ? ((Str) o.v).Clone() : o.v; }
	public void MoveFrom(Variant o) { idx = o.idx; v = o.v; }
}
