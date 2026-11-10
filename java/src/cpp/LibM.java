// LibM.java - the libm functions the C# runtime calls from glibc directly (C# Cpp.LibM in cs/runtime/Misc.cs).
// fmod, remainder, round, rint, nearbyint, lround, nextafter, fdim, fmax and fmin are exact here, with glibc's
// NaNs; log is Glibm's and expm1, log1p and hypot are Glibm2's (bit-exact translations of glibc 2.39, NaNs
// included; tools/libm). erf, erfc, tgamma,
// lgamma and exp2, which the C# runtime also declares, are left out: the engine does not call them and Java has no
// glibc-exact counterpart.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class LibM {
	private LibM() { }

	/** The NaN an invalid operation makes on x86 (0/0, inf-inf): negative, quiet. */
	static final double DEFAULT_NAN = Double.longBitsToDouble(0xFFF8000000000000L);
	/** A NaN operand as the hardware passes it on: quieted, sign and payload kept. */
	static double quiet(double x) { return Double.longBitsToDouble(Double.doubleToRawLongBits(x) | (1L << 51)); }
	/** The NaN result of an operation on two operands, one at least a NaN: a's if it is one, else b's. */
	static double pick(double a, double b) { return Double.isNaN(a) ? quiet(a) : quiet(b); }
	/** A one-argument function's result r for x with glibc's NaNs: a NaN argument comes back quieted, a domain
	 *  error gives the default NaN. */
	static double lm1(double x, double r) {
		if (Double.isNaN(x)) return quiet(x);
		return Double.isNaN(r) ? DEFAULT_NAN : r;
	}

	/** C nextafter: the next double after x toward y (y itself when equal, NaN if either is NaN). */
	public static double nextafter(double x, double y) {
		if (Double.isNaN(x) || Double.isNaN(y)) return pick(y, x);
		if (x == y) return y;
		if (x == 0) return SysMath.CopySign_d(Double.MIN_VALUE, y);   // either zero: the smallest subnormal toward y
		long b = Double.doubleToRawLongBits(x);
		if ((y > x) == (x > 0)) b++; else b--;
		return Double.longBitsToDouble(b);
	}
	/** C fmod (exact; Java's % on doubles is C's fmod): NaN when y is zero or x infinite. */
	public static double fmod(double x, double y) {
		if (Double.isNaN(x) || Double.isNaN(y)) return pick(x, y);
		if (y == 0 || Double.isInfinite(x)) return DEFAULT_NAN;
		return x % y;
	}
	/** C remainder (exact; the quotient rounded half to even). */
	public static double remainder(double x, double y) {
		if (Double.isNaN(x) || Double.isNaN(y)) return pick(y, x);
		if (y == 0 || Double.isInfinite(x)) return DEFAULT_NAN;
		return Math.IEEEremainder(x, y);
	}
	/** C round: halfway cases away from zero (exact). */
	public static double round(double x) {
		if (Double.isNaN(x) || Double.isInfinite(x)) return x + x;
		double a = Math.abs(x);
		if (a >= 4503599627370496.0) return x;                      // 2^52: already an integer
		double t = Math.floor(a);
		if (a - t >= 0.5) t += 1.0;                                 // a - t is exact below 2^52
		return SysMath.CopySign_d(t, x);
	}
	/** C lround: round, then the integer (LONG_MIN when out of range, as x86-64's conversion gives). */
	public static long lround(double x) { return Conv.D2L(round(x)); }
	/** rint and nearbyint in the default rounding mode: half to even (exact). */
	public static double rint(double x) { return Double.isNaN(x) ? quiet(x) : Math.rint(x); }
	public static double nearbyint(double x) { return rint(x); }
	public static double fdim(double x, double y) {
		if (Double.isNaN(x) || Double.isNaN(y)) return pick(x, y);
		return x > y ? x - y : 0.0;
	}
	/** C fmax / fmin: a NaN argument is ignored unless both are NaN. */
	public static double fmax(double x, double y) {
		if (Double.isNaN(x)) return Double.isNaN(y) ? pick(x, y) : y;
		if (Double.isNaN(y)) return x;
		if (x == 0 && y == 0) return Double.doubleToRawLongBits(x) < 0 ? y : x;
		return x > y ? x : y;
	}
	public static double fmin(double x, double y) {
		if (Double.isNaN(x)) return Double.isNaN(y) ? pick(x, y) : y;
		if (Double.isNaN(y)) return x;
		if (x == 0 && y == 0) return Double.doubleToRawLongBits(x) < 0 ? x : y;
		return x < y ? x : y;
	}
	public static double log(double x) { return Glibm.log(x); }
	public static double hypot(double x, double y) { return Glibm2.hypot(x, y); }
	public static double expm1(double x) { return Glibm2.expm1(x); }
	public static double log1p(double x) { return Glibm2.log1p(x); }
}
