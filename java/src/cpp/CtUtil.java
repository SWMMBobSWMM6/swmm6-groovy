// CtUtil.java - helpers shared by the containers and algorithms (part B of the Java runtime): C#'s KeyCopy<T>
// decided from the value at run time (Java's generics are erased, so the static type C# looks at is gone), the
// Str ordering std::less uses, and the ulong <-> double conversions the rehash policy needs. Element copies and
// default equality are Elem.Copy and Elem.Eq (Std.cs's Elem<T>), shared with the rest of the runtime.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

final class CtUtil {
	private CtUtil() { }

	/** Cpp.KeyCopy&lt;T&gt;.Of: a key stored in a map or set is the container's own copy when it is a library value
	 *  (a string, a vector, a pair: an ICopy class of package cpp); an engine object as key is a pointer in C++, so
	 *  it is stored as it is, the same object. */
	@SuppressWarnings("unchecked")
	static <T> T keyCopy(T x) {
		if (x instanceof ICopy && x.getClass().getPackageName().equals("cpp")) return ((ICopy<T>) x).Clone();
		return x;
	}

	/** Str.compare (char_traits&lt;char&gt;::compare is memcmp): unsigned bytes, then length. */
	static int strCompare(Str x, Str y) {
		int m = Math.min(x.n, y.n);
		byte[] p = x.b, q = y.b;
		for (int i = 0; i < m; i++) {
			int c = p[i] & 0xFF, d = q[i] & 0xFF;
			if (c != d) return c < d ? -1 : 1;
		}
		return Integer.compare(x.n, y.n);
	}

	/** A Str holding an ASCII message (for the exceptions the containers throw). */
	static Str str(String s) {
		Str r = new Str();
		byte[] b = new byte[s.length() + 1];
		for (int i = 0; i < s.length(); i++) b[i] = (byte) s.charAt(i);
		r.b = b;
		r.n = s.length();
		return r;
	}

	/** ulong to double, correctly rounded (C#'s conversion). */
	static double u2d(long v) {
		if (v >= 0) return (double) v;
		return ((double) ((v >>> 1) | (v & 1))) * 2.0;
	}

	/** double to ulong (C#'s (ulong) cast) for the values the rehash policy produces: below 2^63 the signed
	 *  conversion, from 2^63 to 2^64 that of x - 2^63 with the top bit set, above it the largest ulong. */
	static long d2u(double d) {
		if (d < 9.223372036854775808E18) return (long) d;
		if (d < 1.8446744073709551616E19) return ((long) (d - 9.223372036854775808E18)) ^ Long.MIN_VALUE;
		return -1L;
	}

	/** Math.Max on ulongs. */
	static long maxU(long a, long b) { return Long.compareUnsigned(a, b) >= 0 ? a : b; }
}
