// Box.java - a reference member of a class (bool& held by an object), C# Cpp.Box<T> (cs/runtime/Misc.cs): the
// place it refers to, an array cell or a field of some object reached through a getter of its reference
// (cpp.RefGet, or cpp.RefGetZ for bool, both part C's). ref_V() is a reference to the boxed value; for a Box of a
// primitive the translator views it as the primitive reference (RefZ.ofBoxed(box.ref_V())).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Box<T> {
	public Object[] a; public long o;
	private final Fn.F0<RefO<T>> g;
	public Box(Object[] a, long o) { this.a = a; this.o = o; g = null; }
	public Box(RefGet<T> g) { this.g = g::invoke; a = null; }
	private Box(Fn.F0<RefO<T>> g, boolean unused) { this.g = g; a = null; }

	public static <T> Box<T> Of(RefGet<T> g) { return new Box<T>(g); }
	/** Box&lt;bool&gt;.Of: the getter gives a RefZ, seen here as a reference to a Boolean. */
	public static Box<Boolean> Of__Z(RefGetZ g) {
		return new Box<Boolean>(() -> {
			RefZ r = g.invoke();
			return new RefO<Boolean>() {
				@Override public Boolean get() { return r.get(); }
				@Override public void set(Boolean v) { r.set(v); }
			};
		}, false);
	}

	public RefO<T> ref_V() { return g != null ? g.apply() : new RefO.Arr<T>(a, (int) o); }
	public T get_V() { return ref_V().get(); }
	public void set_V(T v) { ref_V().set(v); }
}
