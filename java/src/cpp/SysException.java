// SysException.java - a .NET exception the C# engine throws itself (NotImplementedException, OverflowException, ...),
// by its type name and message. It is not a CppException: only the C# catch-all clauses (Java catch Throwable) take it.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class SysException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	public final String kind;
	public SysException(String kind, String msg) {
		super(msg);
		this.kind = kind;
	}
	@Override public String toString() { return "System." + kind + ": " + getMessage(); }
}
