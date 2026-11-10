// ErrorCode.java - std::error_code (C# Cpp.ErrorCode, cs/runtime/Misc.cs): the errno value a filesystem call set.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class ErrorCode {
	public int v;
	public ErrorCode() { }
	public boolean Bool() { return v != 0; }
	public int value() { return v; }
	public void clear() { v = 0; }
	/** glibc's strerror text for the errno values the filesystem calls set. */
	public Str message() {
		String m;
		switch (v) {
			case 0: m = "Success"; break;
			case 2: m = "No such file or directory"; break;
			case 13: m = "Permission denied"; break;
			case 17: m = "File exists"; break;
			case 20: m = "Not a directory"; break;
			case 21: m = "Is a directory"; break;
			case 95: m = "Operation not supported"; break;
			default: m = "Unknown error " + v;
		}
		return new Str(R.Lit(m));
	}
}
