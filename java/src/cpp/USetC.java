// USetC.java - std::unordered_set<T, H, E> with the engine's hasher and key-equal (C# Cpp.USetC, in
// cs/runtime/Containers.cs). As for UMapC, Java takes H and E as makers (C#: new H(), new E()); without them
// the engine's CiHash / CiEqual are used.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class USetC<T, H extends IHashFn<T>, E extends IBinFn<T>> extends USet<T> {
	final IHashFn<T> h0;
	final IBinFn<T> e0;

	public USetC(Fn.F0<H> hm, Fn.F0<E> em) { this((IHashFn<T>) hm.apply(), (IBinFn<T>) em.apply()); }
	public USetC() { this(UMapC.EngineFns.<T>hasher(), UMapC.EngineFns.<T>keyEqual()); }
	public USetC(T[] il, Fn.F0<H> hm, Fn.F0<E> em) { this(hm, em); for (T x : il) Insert(x); }
	public USetC(T[] il) { this(); for (T x : il) Insert(x); }

	USetC(IHashFn<T> h, IBinFn<T> e) {
		super((HashOf<T>) h::HashOf, (EqOf<T>) e::Call2);
		h0 = h;
		e0 = e;
	}

	@Override public USetC<T, H, E> Clone() { USetC<T, H, E> m = new USetC<T, H, E>(h0, e0); m.CopyFrom(this, Elem::Copy); return m; }
	public void Assign(USetC<T, H, E> o) { super.Assign(o); }
	public void MoveFrom(USetC<T, H, E> o) { super.MoveFrom(o); }
}
