// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** The argument of an engine class's raw constructor, which leaves every field at its default (C# Cpp.Raw). */
public final class Raw {
	private Raw() { }
	public static final Raw V = new Raw();
}
