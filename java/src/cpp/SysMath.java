// SysMath.java - C#'s System.Math as the C# engine runs it on Linux (cpp.SysMath.<Name>_<code>): .NET computes Abs,
// Floor, Ceiling, Truncate, CopySign and Sqrt itself, exactly, and calls glibc's libm for the rest.
// exp, log, log10, pow, sin, cos, tan, asin, acos and atan come from Glibm (bit-exact translations of glibc 2.39,
// copied from the SWMM5 Java port), atan2, cbrt, cosh, sinh, tanh and log2 from Glibm2 (bit-exact translations
// of glibc 2.39 made by tools/libm), NaNs included (tools/libm/test checks both against the system libm).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class SysMath {
	private SysMath() { }

	public static double Abs_d(double x) { return Math.abs(x); }
	public static double Acos_d(double x) { return Glibm.acos(x); }
	public static double Asin_d(double x) { return Glibm.asin(x); }
	public static double Atan_d(double x) { return Glibm.atan(x); }
	public static double Ceiling_d(double x) { return Math.ceil(x); }
	/** Math.CopySign: x's magnitude with y's sign bit (also for a NaN y, which StrictMath.copySign treats as positive). */
	public static double CopySign_d(double x, double y) {
		return Double.longBitsToDouble((Double.doubleToRawLongBits(x) & 0x7fffffffffffffffL) | (Double.doubleToRawLongBits(y) & 0x8000000000000000L));
	}
	public static double Cos_d(double x) { return Glibm.cos(x); }
	public static double Exp_d(double x) { return Glibm.exp(x); }
	public static double Floor_d(double x) { return Math.floor(x); }
	public static double Log_d(double x) { return Glibm.log(x); }
	public static double Log10_d(double x) { return Glibm.log10(x); }
	/** Math.Pow. glibc's pow returns x + y when both are NaN, which x86 makes x's NaN (quieted); Java's x + y on two
	 *  NaNs may give either operand (the interpreter and the JIT differ), so that case is decided here. */
	public static double Pow_d(double x, double y) {
		if (Double.isNaN(x) && Double.isNaN(y)) return LibM.quiet(x);
		return Glibm.pow(x, y);
	}
	public static double Sin_d(double x) { return Glibm.sin(x); }
	public static double Sqrt_d(double x) { return Math.sqrt(x); }
	public static double Tan_d(double x) { return Glibm.tan(x); }
	/** Math.Truncate: toward zero (the sign of zero kept). */
	public static double Truncate_d(double x) { return x < 0 ? Math.ceil(x) : Math.floor(x); }

	public static double Cbrt_d(double x) { return Glibm2.cbrt(x); }
	public static double Cosh_d(double x) { return Glibm2.cosh(x); }
	public static double Sinh_d(double x) { return Glibm2.sinh(x); }
	public static double Tanh_d(double x) { return Glibm2.tanh(x); }
	public static double Log2_d(double x) { return Glibm2.log2(x); }
	/** Math.Atan2(y, x). */
	public static double Atan2_d(double y, double x) { return Glibm2.atan2(y, x); }
}
