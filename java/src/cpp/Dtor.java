// Dtor.java - runs a C++ destructor (C# Cpp.Dtor, cs/runtime/Misc.cs): an engine class's Dispose (its destructor
// body, members, base), a file stream's close, a container's elements, an array's elements in order. The
// translator calls it only for types whose destruction has an effect.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Dtor {
	private Dtor() { }
	public static void Run(Object o) {
		if (o == null) return;
		if (o instanceof IDisposable d) { d.Dispose(); return; }
		if (o instanceof IDestroyElems c) { c.DestroyElems(); return; }
		if (o instanceof Object[] arr) { for (Object x : arr) Run(x); }
		// a primitive array: its elements have no destructors
	}
}
