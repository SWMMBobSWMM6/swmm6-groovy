// FromCharsResult.java - std::from_chars_result (C# Cpp.FromCharsResult, cs/runtime/Misc.cs): where parsing stopped
// and the error (0, EINVAL 22 or ERANGE 34). A C# struct, so an immutable value with a ZERO default.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class FromCharsResult {
	public final CPtr ptr;
	public final int ec;
	public static final FromCharsResult ZERO = new FromCharsResult(CPtr.ZERO, 0);
	public FromCharsResult(CPtr ptr, int ec) { this.ptr = ptr; this.ec = ec; }
	public FromCharsResult Clone() { return this; }
}
