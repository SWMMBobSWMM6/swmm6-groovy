// Dbg.java - debugging aid (C# Cpp.Dbg): with the environment variable SWMM6_CS_TRACE set, each exception a
// translated catch handler catches is printed to stderr (the engine itself turns most of them into error codes).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Dbg {
	private Dbg() { }
	static final boolean on = System.getenv("SWMM6_CS_TRACE") != null;
	public static void Caught(Throwable e) {
		if (!on) return;
		java.io.StringWriter w = new java.io.StringWriter();
		e.printStackTrace(new java.io.PrintWriter(w));
		System.err.print("[caught] " + w);
		System.err.flush();
	}
}
