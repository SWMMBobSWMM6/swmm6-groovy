// Defer.java - a destructor deferred to the end of the enclosing block (a C# using declaration), unless the object
// is returned to the caller (Cancel). C# Cpp.Defer, cs/runtime/Misc.cs.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Defer implements IDisposable {
	private Fn.A0 a;
	public Defer(Fn.A0 a) { this.a = a; }
	public void Cancel() { a = null; }
	@Override public void Dispose() { Fn.A0 x = a; a = null; if (x != null) x.run(); }
}
