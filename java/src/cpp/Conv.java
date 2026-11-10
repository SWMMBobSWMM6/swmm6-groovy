// Conv.java - x86-64 float-to-integer conversion as GCC compiles it (C# Cpp.Conv in cs/runtime/Misc.cs):
// cvttsd2si, whose NaN and out-of-range results are the "integer indefinite" value, and GCC's sequences for the
// unsigned and narrower types on top of it.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Conv {
	private Conv() { }

	public static int D2I(double x) { return (x > -2147483649.0 && x < 2147483648.0) ? (int) x : Integer.MIN_VALUE; }
	public static long D2L(double x) { return (x >= -9223372036854775808.0 && x < 9223372036854775808.0) ? (long) x : Long.MIN_VALUE; }
	/** GCC's double -> unsigned int: the 64-bit conversion, truncated. */
	public static int D2U(double x) { return (int) D2L(x); }
	/** GCC's double -> unsigned long: below 2^63 the signed conversion, above it the conversion of x - 2^63. */
	public static long D2UL(double x) {
		if (x < 9223372036854775808.0) return D2L(x);
		return D2L(x - 9223372036854775808.0) ^ 0x8000000000000000L;
	}
	public static short D2S(double x) { return (short) D2I(x); }
	public static char D2US(double x) { return (char) D2I(x); }
	public static byte D2SB(double x) { return (byte) D2I(x); }
	public static byte D2B(double x) { return (byte) D2I(x); }
}
