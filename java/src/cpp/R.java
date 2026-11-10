// R.java - the C runtime layer of the Java SWMM6 engine (C# Cpp.R: cs/runtime/CRuntime.cs and Stash.cs; the C
// library functions of cs/runtime/Fs.cs are part D's class RFs, which R extends so that the engine's cpp.R.getenv,
// cpp.R.stderr_, ... resolve). It reproduces glibc and x86-64 behaviour byte for byte where the engine's output
// depends on it: printf formatting (exact decimal expansion, rounded half to even on the binary value as glibc does
// in its default rounding mode, and %a), strtod / sscanf parsing (with %n, scan sets and assignment suppression),
// NUL-terminated byte strings, stdio files with C's positioning and end-of-file rules, and x86's float-to-integer
// conversion.
// Sources: the C# runtime is the specification; the printf, strtod and stdio code follows the SWMM5 Java port
// (swmm5-ports/java/src/swmm5/R.java), and printf's length modifiers, %a, sscanf and strtod's NaN payloads follow
// the Go port of this runtime (go/cpp/crt2.go, format.go), which passed all 480 gate models. Where the C# departs
// from glibc in a case the gate does not exercise, this file does what glibc does, as the Go port does (the C#
// engine is gated byte-identical to the C++ one, so glibc is the specification there); each such place says so.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public final class R extends RFs {
	private R() { }

	public static double DummyDouble;
	public static int DummyInt;

	// ------------------------------------------------------------------------------------------------ conversions

	/** x86-64 cvttsd2si: NaN and out-of-range values give the "integer indefinite" value. */
	public static int D2I(double x) {
		if (Double.isNaN(x) || x >= 2147483648.0 || x <= -2147483649.0) return Integer.MIN_VALUE;
		return (int) x;
	}
	public static long D2L(double x) {
		if (Double.isNaN(x) || x >= 9223372036854775808.0 || x < -9223372036854775808.0) return Long.MIN_VALUE;
		return (long) x;
	}
	public static void Discard(Object o) { }
	public static <T> T Comma(Object a, T b) { return b; }

	/** A value used as a statement. */
	public static void discard(Object o) { }
	/** A throw expression: throws t (unchecked, whatever its type) and never returns. */
	public static <T> T thr(Throwable t) { throw R.<RuntimeException>sneaky(t); }
	@SuppressWarnings("unchecked")
	static <E extends Throwable> E sneaky(Throwable t) throws E { throw (E) t; }
	/** C#'s as: o if it is a T, else null. */
	public static <T> T as(Object o, Class<T> c) { return c.isInstance(o) ? c.cast(o) : null; }
	public static boolean signbit(double x) { return Double.doubleToRawLongBits(x) < 0; }
	/** ulong to double, correctly rounded (the bit pattern is the unsigned value). */
	public static double u2d(long u) {
		if (u >= 0) return (double) u;
		return ((double) ((u >>> 1) | (u & 1))) * 2.0;
	}
	/** ulong to float, correctly rounded (rounded once: the sticky bit keeps the halving exact for rounding). */
	public static float u2f(long u) {
		if (u >= 0) return (float) u;
		return ((float) ((u >>> 1) | (u & 1))) * 2.0f;
	}
	/** double to ulong as C# (unchecked, x86-64) converts it: Conv.D2UL. */
	public static long d2u(double x) { return Conv.D2UL(x); }
	/** System.IO.File.Exists: an existing regular file (not a directory). */
	public static boolean File_Exists(String path) {
		if (path == null || path.isEmpty()) return false;
		try { return new File(path).isFile(); } catch (SecurityException e) { return false; }
	}

	// ------------------------------------------------------------------------------------------------ memory

	/** A string literal as a fresh NUL-terminated byte array (each char's low byte). */
	public static CPtr Lit(String s) {
		int n = s.length();
		byte[] b = new byte[n + 1];
		for (int i = 0; i < n; i++) b[i] = (byte) s.charAt(i);
		return new CPtr(b, 0);
	}
	/** char a[n] = "literal": n bytes, the literal's first, the rest zero. */
	public static CPtr LitN(String s, int n) {
		byte[] b = new byte[Math.max(n, s.length() + 1)];
		for (int i = 0; i < s.length(); i++) b[i] = (byte) s.charAt(i);
		return new CPtr(b, 0);
	}
	public static CPtr Buf(int n) { return new CPtr(new byte[Math.max(n, 1)], 0); }
	public static CPtr Buf_i(int n) { return Buf(n); }
	public static CPtr Buf_l(long n) { return Buf((int) n); }
	public static CPtr Buf_ul(long n) { return Buf((int) n); }
	/** char a[n] initialised from a literal (at most n bytes of it). */
	public static CPtr BufFrom(CPtr lit, int n) {
		CPtr p = Buf(n);
		for (int i = 0; i < n && lit.B[lit.O + i] != 0; i++) p.B[i] = lit.B[lit.O + i];
		return p;
	}
	/** The copy of a char array member: the whole buffer, the same offset. */
	public static CPtr CopyBuf(CPtr p) {
		if (p.B == null) return p;
		return new CPtr(p.B.clone(), p.O);
	}
	public static CPtr[] NewBufs(int n, int m) { return Bufs(n, m); }
	/** char a[n][m]: n zeroed rows of m bytes. */
	public static CPtr[] Bufs(int n, int m) {
		CPtr[] r = new CPtr[n];
		for (int i = 0; i < n; i++) r[i] = Buf(m);
		return r;
	}
	public static void free(Object o) { }
	/** memset of a double array (the C# runtime zeroes n / 8 elements, whatever the byte). */
	public static void memset(double[] a, int v, long n) {
		int cnt = (int) Long.divideUnsigned(n, 8);
		for (int i = 0; i < cnt && i < a.length; i++) a[i] = 0.0;
	}

	// ------------------------------------------------------------------------------------------------ strings

	/** The Java string of a C string (each byte one char, ISO-8859-1). */
	public static String Str(CPtr p) { return new String(p.B, p.O, strlenI(p), StandardCharsets.ISO_8859_1); }
	/** A copy of a C string's bytes, without the terminator. */
	public static byte[] Bytes(CPtr p) { return Arrays.copyOfRange(p.B, p.O, p.O + strlenI(p)); }
	static int strlenI(CPtr p) {
		byte[] b = p.B;
		int i = p.O;
		while (b[i] != 0) i++;
		return i - p.O;
	}
	public static long strlen(CPtr p) { return strlenI(p); }
	public static CPtr strcpy(CPtr d, CPtr s) {
		int n = strlenI(s);
		System.arraycopy(s.B, s.O, d.B, d.O, n + 1);   // arraycopy handles an overlapping copy as memmove
		return d;
	}
	public static CPtr strcat(CPtr d, CPtr s) {
		strcpy(new CPtr(d.B, d.O + strlenI(d)), s);
		return d;
	}
	/** strcmp: the difference of the first unsigned bytes that differ. */
	public static int strcmp(CPtr a, CPtr b) {
		for (int i = 0;; i++) {
			int x = a.B[a.O + i] & 255, y = b.B[b.O + i] & 255;
			if (x != y) return x - y;
			if (x == 0) return 0;
		}
	}
	public static CPtr strchr(CPtr s, int c) {
		byte ch = (byte) c;
		for (int i = s.O;; i++) {
			if (s.B[i] == ch) return new CPtr(s.B, i);
			if (s.B[i] == 0) return CPtr.ZERO;
		}
	}
	public static CPtr strrchr(CPtr s, int c) {
		byte ch = (byte) c;
		CPtr r = CPtr.ZERO;
		for (int i = s.O;; i++) {
			if (s.B[i] == ch) r = new CPtr(s.B, i);
			if (s.B[i] == 0) return r;
		}
	}
	public static CPtr strstr(CPtr h, CPtr n) {
		int nl = strlenI(n);
		if (nl == 0) return h;
		for (int i = h.O; h.B[i] != 0; i++) {
			int j = 0;
			while (j < nl && h.B[i + j] == n.B[n.O + j]) j++;
			if (j == nl) return new CPtr(h.B, i);
		}
		return CPtr.ZERO;
	}
	public static long strcspn(CPtr s, CPtr reject) {
		int i = 0;
		while (s.B[s.O + i] != 0) {
			byte c = s.B[s.O + i];
			for (int j = reject.O; reject.B[j] != 0; j++) if (reject.B[j] == c) return i;
			i++;
		}
		return i;
	}
	private static CPtr tokNext = CPtr.ZERO;
	public static CPtr strtok(CPtr s, CPtr delim) {
		if (s.B != null) tokNext = s;
		if (tokNext.B == null) return CPtr.ZERO;
		byte[] b = tokNext.B;
		int p = tokNext.O;
		while (b[p] != 0 && isIn(b[p], delim)) p++;                  // skip leading delimiters
		if (b[p] == 0) { tokNext = CPtr.ZERO; return CPtr.ZERO; }
		int start = p;
		while (b[p] != 0 && !isIn(b[p], delim)) p++;
		if (b[p] != 0) { b[p] = 0; tokNext = new CPtr(b, p + 1); }
		else tokNext = CPtr.ZERO;
		return new CPtr(b, start);
	}
	private static boolean isIn(byte c, CPtr set) {
		for (int j = set.O; set.B[j] != 0; j++) if (set.B[j] == c) return true;
		return false;
	}
	public static int toupper(int c) { return (c >= 'a' && c <= 'z') ? c - 32 : c; }
	static boolean isSpace(int c) { return c == ' ' || (c >= 9 && c <= 13); }
	static boolean isDig(int c) { return c >= '0' && c <= '9'; }
	static boolean isHex(int c) { return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'); }
	static int low(int c) { return c >= 'A' && c <= 'Z' ? c | 0x20 : c; }

	// ------------------------------------------------------------------------------------------------ numbers from text

	static final double DEFAULT_NAN = Double.longBitsToDouble(0xfff8000000000000L);   // x86's NaN: negative
	static final double POS_NAN = Double.longBitsToDouble(0x7ff8000000000000L);

	// scanDouble's results besides the value: the end index (-1: no number) and the text parsed (null for inf/nan)
	private static int scanEnd;
	private static String scanText;
	/** scanDouble's end index (-1: no number). */
	static int scanEndOf() { return scanEnd; }
	/** Whether the last scanDouble (strtod, atof, sscanf's conversions) would have set errno to ERANGE in glibc: an
	 *  overflow to +-inf, a nonzero number that became zero, or a result that is tiny (below DBL_MIN after rounding
	 *  to 53 bits, the x86 rule) and inexact. libstdc++'s std::stod throws out_of_range on it (C# Cpp.R.ERange). */
	public static boolean ERange;

	/** glibc's strtod on b[i..] (at most maxw characters when maxw >= 0; the array's end or a NUL ends the input).
	 *  Sets scanEnd and scanText. Decimal text is converted by Double.parseDouble, which is correctly rounded as
	 *  glibc's strtod is; hexadecimal floats, inf/infinity and nan / nan(n-char-sequence) as glibc reads them. */
	static double scanDouble(byte[] b, int i, int maxw, boolean skipSpace) {
		ERange = false;
		int n = b.length;
		if (skipSpace) while (i < n && isSpace(b[i] & 255)) i++;
		final int lim = maxw >= 0 && i + maxw < n ? i + maxw : n;
		boolean neg = false;
		if (g(b, lim, i) == '+' || g(b, lim, i) == '-') { neg = g(b, lim, i) == '-'; i++; }
		if (low(g(b, lim, i)) == 'i' && low(g(b, lim, i + 1)) == 'n' && low(g(b, lim, i + 2)) == 'f') {
			i += 3;
			if (low(g(b, lim, i)) == 'i' && low(g(b, lim, i + 1)) == 'n' && low(g(b, lim, i + 2)) == 'i'
				&& low(g(b, lim, i + 3)) == 't' && low(g(b, lim, i + 4)) == 'y') i += 5;
			scanEnd = i; scanText = null;
			return neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		}
		if (low(g(b, lim, i)) == 'n' && low(g(b, lim, i + 1)) == 'a' && low(g(b, lim, i + 2)) == 'n') {
			i += 3;
			if (g(b, lim, i) == '(') {
				int k = i + 1;
				for (;; k++) {
					int c = g(b, lim, k);
					if (!(isDig(c) || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_')) break;
				}
				if (g(b, lim, k) == ')') {
					// glibc reads the n-char-sequence with strtoull(seq, 0) and keeps it as the payload
					Long pl = k == i + 1 ? Long.valueOf(0) : parseCUnsigned(new String(b, i + 1, k - i - 1, StandardCharsets.ISO_8859_1));
					if (pl != null) {
						double v = Double.longBitsToDouble(0x7ff8000000000000L | (pl & ((1L << 51) - 1)));
						scanEnd = k + 1; scanText = null;
						return neg ? -v : v;
					}
					i = k + 1;
				}
			}
			scanEnd = i; scanText = null;
			return neg ? DEFAULT_NAN : POS_NAN;
		}
		if (g(b, lim, i) == '0' && low(g(b, lim, i + 1)) == 'x') {     // hexadecimal float (C99), as glibc reads it
			int j = i + 2, nh = 0;
			while (isHex(g(b, lim, j))) { j++; nh++; }
			if (g(b, lim, j) == '.') { j++; while (isHex(g(b, lim, j))) { j++; nh++; } }
			if (nh > 0) {
				String mant = new String(b, i + 2, j - i - 2, StandardCharsets.ISO_8859_1);
				String exp = "0";
				if (low(g(b, lim, j)) == 'p') {
					int k = j + 1;
					if (g(b, lim, k) == '+' || g(b, lim, k) == '-') k++;
					if (isDig(g(b, lim, k))) {
						while (isDig(g(b, lim, k))) k++;
						exp = new String(b, j + 1, k - j - 1, StandardCharsets.ISO_8859_1);
						j = k;
					}
				}
				String text = (neg ? "-" : "") + "0x" + mant + "p" + exp;
				scanEnd = j; scanText = text;
				double hv = parseHex(text);
				// (a hexadecimal subnormal is not checked)
				ERange = (hv == 0 && !mant.replace(".", "").replace("0", "").isEmpty()) || Double.isInfinite(hv);
				return hv;
			}
		}
		int ms = i, nd = 0;
		while (isDig(g(b, lim, i))) { i++; nd++; }
		if (g(b, lim, i) == '.') { i++; while (isDig(g(b, lim, i))) { i++; nd++; } }
		if (nd == 0) { scanEnd = -1; scanText = null; return 0.0; }
		if (low(g(b, lim, i)) == 'e') {
			int k = i + 1;
			if (g(b, lim, k) == '+' || g(b, lim, k) == '-') k++;
			if (isDig(g(b, lim, k))) { while (isDig(g(b, lim, k))) k++; i = k; }
		}
		String text = (neg ? "-" : "") + new String(b, ms, i - ms, StandardCharsets.ISO_8859_1);
		scanEnd = i; scanText = text;
		double dv = parseDec(text);
		ERange = decRange(new String(b, ms, i - ms, StandardCharsets.ISO_8859_1), Math.abs(dv));
		return dv;
	}
	/** glibc strtod's ERANGE for the decimal text txt (digits, '.', exponent; no sign) whose correctly rounded value
	 *  is av: overflow, or underflow (tiny after rounding and inexact, decided exactly). */
	static boolean decRange(String txt, double av) {
		if (Double.isInfinite(av)) return true;
		if (av > Double.MIN_NORMAL) return false;                 // above DBL_MIN: normal
		java.math.BigInteger d = java.math.BigInteger.ZERO, ten = java.math.BigInteger.TEN;
		long e10 = 0;
		int k = 0;
		for (; k < txt.length() && isDig(txt.charAt(k)); k++) d = d.multiply(ten).add(java.math.BigInteger.valueOf(txt.charAt(k) - '0'));
		if (k < txt.length() && txt.charAt(k) == '.')
			for (k++; k < txt.length() && isDig(txt.charAt(k)); k++) { d = d.multiply(ten).add(java.math.BigInteger.valueOf(txt.charAt(k) - '0')); e10--; }
		if (d.signum() == 0) return false;                         // a zero is exact
		if (k < txt.length() && (txt.charAt(k) == 'e' || txt.charAt(k) == 'E')) {
			k++;
			boolean en = false;
			if (k < txt.length() && (txt.charAt(k) == '+' || txt.charAt(k) == '-')) { en = txt.charAt(k) == '-'; k++; }
			long ex = 0;
			for (; k < txt.length() && isDig(txt.charAt(k)); k++) ex = Math.min(ex * 10 + (txt.charAt(k) - '0'), 1000000000L);
			e10 += en ? -ex : ex;
		}
		long nd = d.toString().length();
		if (nd + e10 < -340) return true;                          // below 1e-340: zero, and not exact
		if (e10 >= 0) return false;
		java.math.BigInteger p10 = ten.pow((int) -e10);            // the value is d / 10^-e10
		// tiny after rounding: below 2^-1022 - 2^-1076, the midpoint under DBL_MIN of the 53-bit grid
		java.math.BigInteger lim = java.math.BigInteger.ONE.shiftLeft(54).subtract(java.math.BigInteger.ONE).multiply(p10);
		if (d.shiftLeft(1076).compareTo(lim) >= 0) return false;
		// inexact: not av itself, which is m * 2^-1074 for a subnormal (or DBL_MIN, or zero)
		long m = Double.doubleToRawLongBits(av);
		return !d.shiftLeft(1074).equals(java.math.BigInteger.valueOf(m).multiply(p10));
	}
	private static int g(byte[] b, int lim, int k) { return k < lim ? b[k] & 255 : 0; }

	/** A decimal floating text as strtod converts it (correctly rounded; the exponent clamped, which cannot change
	 *  the result, so that a huge exponent does not overflow the parser). */
	static double parseDec(String text) { return Double.parseDouble(clampExp(text)); }
	static float parseDecF(String text) { return Float.parseFloat(clampExp(text)); }
	private static String clampExp(String text) {
		int e = Math.max(text.indexOf('e'), text.indexOf('E'));
		if (e < 0 || text.length() - e < 8) return text;
		int k = e + 1;
		boolean en = false;
		if (text.charAt(k) == '+' || text.charAt(k) == '-') { en = text.charAt(k) == '-'; k++; }
		String digs = text.substring(k).replaceFirst("^0+", "");
		if (digs.length() <= 6) return text;
		return text.substring(0, e) + (en ? "e-999999" : "e999999");
	}
	/** A hexadecimal floating text "[-]0xMANT.FRACpEXP" as strtod converts it (Java's parser is exact for these). */
	static double parseHex(String text) { return Double.parseDouble(clampHexExp(text)); }
	static float parseHexF(String text) { return Float.parseFloat(clampHexExp(text)); }
	private static String clampHexExp(String text) {
		int p = text.indexOf('p');
		String e = text.substring(p + 1);
		boolean en = e.startsWith("-");
		String digs = e.replaceFirst("^[+-]", "").replaceFirst("^0+", "");
		if (digs.length() <= 6) return text;
		return text.substring(0, p) + (en ? "p-999999" : "p999999");
	}
	/** strtoull(s, 0, 0) of a whole n-char-sequence: decimal, 0x hexadecimal or 0 octal; null when it is not one. */
	private static Long parseCUnsigned(String s) {
		try {
			if (s.startsWith("0x") || s.startsWith("0X")) return s.length() > 2 ? Long.parseUnsignedLong(s.substring(2), 16) : null;
			if (s.length() > 1 && s.startsWith("0")) return Long.parseUnsignedLong(s.substring(1), 8);
			return Long.parseUnsignedLong(s, 10);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static double strtod(CPtr s) { return strtod(s, null); }
	public static double strtod(CPtr s, RefO<CPtr> end) {
		double v = scanDouble(s.B, s.O, -1, true);
		if (end != null) end.set(scanEnd < 0 ? s : new CPtr(s.B, scanEnd));
		return scanEnd < 0 ? 0.0 : v;
	}
	public static double strtod_CPtr(CPtr s) { return strtod(s, null); }
	public static double strtod_CPtr_CPtrr(CPtr s, RefO<CPtr> end) { return strtod(s, end); }
	public static double atof(CPtr s) { return strtod(s, null); }

	/** The C# runtime's strtol: optional space and sign, decimal digits (the engine passes base 10), clamped to long. */
	public static long strtol(CPtr s, RefO<CPtr> end, int base) {
		byte[] b = s.B;
		int i = s.O;
		while (isSpace(b[i] & 255)) i++;
		boolean neg = false;
		if (b[i] == '+' || b[i] == '-') { neg = b[i] == '-'; i++; }
		int st = i;
		long u = 0;
		boolean over = false;
		while (isDig(b[i])) {
			int d = b[i] - '0';
			if (Long.compareUnsigned(u, Long.divideUnsigned(-1L - d, 10)) > 0) over = true;
			else u = u * 10 + d;
			i++;
		}
		if (i == st) { if (end != null) end.set(s); return 0; }
		if (end != null) end.set(new CPtr(b, i));
		if (neg) return over || Long.compareUnsigned(u, 1L << 63) > 0 ? Long.MIN_VALUE : -u;
		return over || u < 0 ? Long.MAX_VALUE : u;
	}
	/** atoi: strtol's long, then (int): the low 32 bits. */
	public static int atoi(CPtr s) { return (int) strtol(s, null, 10); }

	// ------------------------------------------------------------------------------------------------ sscanf targets

	/** An int lvalue passed to sscanf (C# Cpp.RefI of CRuntime.cs): the setter stores the value scanned. */
	public static final class SetI { public final Fn.A1<Integer> Set; SetI(Fn.A1<Integer> a) { Set = a; } }
	public static final class SetL { public final Fn.A1<Long> Set; SetL(Fn.A1<Long> a) { Set = a; } }
	public static final class SetD { public final Fn.A1<Double> Set; SetD(Fn.A1<Double> a) { Set = a; } }
	public static final class SetF { public final Fn.A1<Float> Set; SetF(Fn.A1<Float> a) { Set = a; } }
	public static SetI RefI(Fn.A1<Integer> a) { return new SetI(a); }
	public static SetL RefL(Fn.A1<Long> a) { return new SetL(a); }
	public static SetD RefD(Fn.A1<Double> a) { return new SetD(a); }
	public static SetF RefF(Fn.A1<Float> a) { return new SetF(a); }

	// ------------------------------------------------------------------------------------------------ Stash.cs

	/** A sscanf target that is a C pointer parameter (ref in C#): the value is stashed, then R.Back copies it. */
	public static final ScanSlot Slot = new ScanSlot();
	private static final ArrayList<Object> stash = new ArrayList<>();
	static void StashReset() { stash.clear(); }
	static void Stash(Object v) { stash.add(v); }
	private static Object taken(int i) { return i < stash.size() ? stash.get(i) : null; }

	public static int Back_i_T1r__B(int n, RefB a) { Object v = taken(0); if (v != null) a.set((byte) convI(v, Byte.MIN_VALUE, 255)); return n; }
	public static int Back_i_T1r__S(int n, RefS a) { Object v = taken(0); if (v != null) a.set((short) convI(v, Short.MIN_VALUE, 65535)); return n; }
	public static int Back_i_T1r__I(int n, RefI a) { Object v = taken(0); if (v != null) a.set((int) convI(v, Integer.MIN_VALUE, 0xFFFFFFFFL)); return n; }
	public static int Back_i_T1r__L(int n, RefL a) { Object v = taken(0); if (v != null) a.set(convL(v)); return n; }
	public static int Back_i_T1r__D(int n, RefD a) { Object v = taken(0); if (v != null) a.set(((Number) v).doubleValue()); return n; }
	public static int Back_i_T1r__F(int n, RefF a) { Object v = taken(0); if (v != null) a.set(((Number) v).floatValue()); return n; }
	public static int Back_i_T1r_T2r__LB(int n, RefL a, RefB b) {
		Object v = taken(0);
		if (v != null) a.set(convL(v));
		v = taken(1);
		if (v != null) b.set((byte) convI(v, Byte.MIN_VALUE, 255));
		return n;
	}
	/** Convert.ChangeType of a stashed number to an integer type whose values are [lo, hi] (checked, as C#'s
	 *  Convert is; hi covers the unsigned type of the same size, which the Java type cannot tell apart). */
	private static long convI(Object v, long lo, long hi) {
		long x;
		if (v instanceof Double || v instanceof Float) {
			double d = ((Number) v).doubleValue();
			double r = Math.rint(d);                                    // Convert rounds half to even
			if (!(r >= lo && r <= hi)) throw new SysException("OverflowException", "Value was either too large or too small.");
			x = (long) r;
		} else x = ((Number) v).longValue();
		if (x < lo || x > hi) throw new SysException("OverflowException", "Value was either too large or too small.");
		return x;
	}
	private static long convL(Object v) {
		if (v instanceof Double || v instanceof Float) {
			double r = Math.rint(((Number) v).doubleValue());
			if (!(r >= -9223372036854775808.0 && r < 9223372036854775808.0)) throw new SysException("OverflowException", "Value was either too large or too small for an Int64.");
			return (long) r;
		}
		return ((Number) v).longValue();
	}

	// ------------------------------------------------------------------------------------------------ sscanf

	/** glibc's sscanf for the directives the engine uses: %d %i %u (decimal), %f %e %g %a (and capitals), %s, %c,
	 *  %[...], %n and %%, with * and widths and the h/l/L/z/j/t/q modifiers. Targets: the R.RefI / RefL / RefD /
	 *  RefF setters, R.Slot (the value is stashed for R.Back), char buffers (CPtr, PtrB) and the cpp.Ref* cells.
	 *  It returns the number of assignments, or EOF (-1) when the input ends before the first assignment.
	 *  The C# runtime handles fewer directives (no %n, scan sets or %a) and differs from glibc on the EOF rule and on
	 *  %u of negative or huge numbers; glibc is followed (as the Go port does). */
	public static int sscanf(CPtr s, CPtr fmt, Object... args) {
		StashReset();
		byte[] b = s.B;
		int i = s.O;
		byte[] f = fmt.B;
		int k = fmt.O, ai = 0, count = 0;
		while (f[k] != 0) {
			int fc = f[k] & 255;
			if (isSpace(fc)) {
				while (isSpace(f[k] & 255)) k++;
				while (isSpace(at(b, i))) i++;
				continue;
			}
			if (fc != '%') {
				if (at(b, i) == 0) return count == 0 ? -1 : count;
				if (at(b, i) != fc) return count;
				i++;
				k++;
				continue;
			}
			k++;
			boolean suppress = false;
			if (f[k] == '*') { suppress = true; k++; }
			int width = 0;
			while (isDig(f[k])) { width = width * 10 + (f[k] - '0'); k++; }
			int lmod = 0;
			while (f[k] == 'l' || f[k] == 'h' || f[k] == 'L' || f[k] == 'z' || f[k] == 'j' || f[k] == 't' || f[k] == 'q') {
				if (f[k] != 'h') lmod++;
				k++;
			}
			int conv = f[k] & 255;
			k++;
			boolean[] set = null;
			if (conv == '[') {
				set = new boolean[256];
				boolean neg = false;
				if (f[k] == '^') { neg = true; k++; }
				boolean first = true;
				while (f[k] != 0 && (first || f[k] != ']')) {
					int c = f[k] & 255;
					if (c == '-' && !first && f[k + 1] != ']' && f[k + 1] != 0 && (f[k - 1] & 255) <= (f[k + 1] & 255)) {
						for (int x = f[k - 1] & 255; x <= (f[k + 1] & 255); x++) set[x] = true;
						k += 2;
						first = false;
						continue;
					}
					set[c] = true;
					k++;
					first = false;
				}
				if (f[k] == ']') k++;
				if (neg) { for (int x = 0; x < 256; x++) set[x] = !set[x]; set[0] = false; }
			}
			if (conv != 'c' && conv != '[' && conv != 'n') while (isSpace(at(b, i))) i++;
			if (conv == 'n') {
				if (!suppress) { if (ai >= args.length) throw tooFew(); storeInt(args[ai++], i - s.O, lmod); }
				continue;
			}
			if (at(b, i) == 0) return count == 0 ? -1 : count;
			int w = width > 0 ? width : 1 << 30;
			switch (conv) {
				case '%':
					if (at(b, i) != '%') return count;
					i++;
					break;
				case 'd': case 'i': case 'u': {
					int j = i, lim = i + w;
					boolean neg = false;
					if (j < lim && (at(b, j) == '+' || at(b, j) == '-')) { neg = at(b, j) == '-'; j++; }
					int ds = j;
					long u = 0;
					boolean over = false;
					while (j < lim && isDig(at(b, j))) {
						int d = at(b, j) - '0';
						if (Long.compareUnsigned(u, Long.divideUnsigned(-1L - d, 10)) > 0) over = true;
						else u = u * 10 + d;
						j++;
					}
					if (j == ds) return count;
					i = j;
					long v;
					if (conv == 'u') {               // strtoul: the magnitude, negated as an unsigned number (ULONG_MAX on overflow)
						if (over) u = -1L;
						else if (neg) u = -u;
						v = lmod == 0 ? (u & 0xFFFFFFFFL) : u;
					} else {                         // strtol: clamped to long
						if (neg && (over || Long.compareUnsigned(u, 1L << 63) > 0)) v = Long.MIN_VALUE;
						else if (neg) v = -u;
						else if (over || u < 0) v = Long.MAX_VALUE;
						else v = u;
					}
					if (!suppress) {
						if (ai >= args.length) throw tooFew();
						storeInt(args[ai++], v, lmod);
						count++;
					}
					break;
				}
				case 'f': case 'e': case 'g': case 'E': case 'G': case 'a': case 'A': case 'F': {
					int e = scanFloat(b, i, width);
					if (e < 0) return count;
					byte[] num = scanBuf;
					i = e;
					// the characters taken are converted as strtod (or strtof, for a float) converts them
					double v = scanDouble(num, 0, -1, false);
					if (scanEnd <= 0) return count;
					String text = scanText;
					if (!suppress) {
						if (ai >= args.length) throw tooFew();
						Object a = args[ai++];
						float fv = 0f;
						if (lmod == 0) fv = text != null ? (text.indexOf('x') >= 0 ? parseHexF(text) : parseDecF(text)) : (float) v;
						if (a instanceof SetD) ((SetD) a).Set.run(v);
						else if (a instanceof SetF) ((SetF) a).Set.run(fv);
						else if (a instanceof ScanSlot) Stash(lmod > 0 ? (Object) v : (Object) fv);
						else if (a instanceof RefD) ((RefD) a).set(v);
						else if (a instanceof RefF) ((RefF) a).set(fv);
						else throw new IllegalArgumentException("sscanf %" + (char) conv + " target " + a);
						count++;
					}
					break;
				}
				case 's': case '[': {
					int j = i, m = 0;
					while (at(b, j) != 0 && m < w) {
						int c = at(b, j);
						if (conv == 's' ? isSpace(c) : !set[c]) break;
						j++;
						m++;
					}
					if (m == 0) return count;           // only %[ can match nothing: a matching failure
					if (!suppress) {
						if (ai >= args.length) throw tooFew();
						CPtr d = buf(args[ai++]);
						System.arraycopy(b, i, d.B, d.O, m);
						d.B[d.O + m] = 0;
						count++;
					}
					i = j;
					break;
				}
				case 'c': {
					int n = width > 0 ? width : 1;
					Object a = null;
					if (!suppress) { if (ai >= args.length) throw tooFew(); a = args[ai++]; }
					for (int t = 0; t < n && at(b, i) != 0; t++) {
						if (!suppress) {
							byte c = b[i];
							if (a instanceof CPtr) ((CPtr) a).B[((CPtr) a).O + t] = c;
							else if (a instanceof SetI) ((SetI) a).Set.run((int) c);
							else if (a instanceof ScanSlot) Stash(t == 0 ? (Object) Byte.valueOf(c) : (Object) Integer.valueOf(c));
							else if (a instanceof RefB) ((RefB) a).set(c);
							else if (a instanceof RefI) ((RefI) a).set(c);
							else { CPtr d = buf(a); d.B[d.O + t] = c; }
						}
						i++;
					}
					if (!suppress) count++;
					break;
				}
				default:
					throw new IllegalArgumentException("sscanf %" + (char) conv);
			}
		}
		return count;
	}
	private static int at(byte[] b, int j) { return j < b.length ? b[j] & 255 : 0; }
	private static IllegalArgumentException tooFew() { return new IllegalArgumentException("sscanf: too few arguments"); }

	/** An integer conversion's value into its target (an int unless the l modifier was given). */
	private static void storeInt(Object a, long v, int lmod) {
		if (a instanceof SetI) ((SetI) a).Set.run((int) v);
		else if (a instanceof SetL) ((SetL) a).Set.run(v);
		else if (a instanceof ScanSlot) Stash(lmod > 0 ? (Object) Long.valueOf(v) : (Object) Integer.valueOf((int) v));
		else if (a instanceof RefI) ((RefI) a).set((int) v);
		else if (a instanceof RefL) ((RefL) a).set(v);
		else if (a instanceof RefS) ((RefS) a).set((short) v);
		else if (a instanceof RefB) ((RefB) a).set((byte) v);
		else throw new IllegalArgumentException("sscanf integer target " + a);
	}
	/** A char* target: a CPtr, or a char array passed as a pointer. */
	private static CPtr buf(Object a) {
		if (a instanceof CPtr) return (CPtr) a;
		if (a instanceof PtrB) { PtrB p = (PtrB) a; return new CPtr(p.a, (int) p.o); }
		throw new IllegalArgumentException("sscanf string target " + a);
	}

	private static byte[] scanBuf;      // scanFloat's characters, NUL-terminated
	/** The scanner of glibc's vfscanf for a floating conversion at b[i] (not a space, not the end): it collects the
	 *  characters it takes (at most width, 0 for no limit) into scanBuf and returns the index after them, -1 for a
	 *  matching failure. Like glibc, it reads one character ahead and pushes back only that one, so an exponent
	 *  marker or sign with no digits after it ("1e", "1e+") is taken (and ignored by the conversion), and a partial
	 *  "infinity" or a bare "0x" fails. "nan" never takes an n-char-sequence. */
	private static int scanFloat(byte[] b, int i, int width) {
		final int[] st = { width == 0 ? -1 : width, i };      // characters still allowed (-1: no limit), next index
		StringBuilder buf = new StringBuilder(32);
		int c = inchar(b, st);
		dec(st);
		int gotSign = 0;
		if (c == '-' || c == '+') {
			gotSign = 1;
			buf.append((char) c);
			if (st[0] == 0) return -1;
			if ((c = inchar(b, st)) < 0) return -1;
			dec(st);
		}
		if (low(c) == 'n') {                                   // "nan"
			buf.append((char) c);
			if (!expect(b, st, buf, 'a') || !expect(b, st, buf, 'n')) return -1;
			return done(buf, st[1]);
		}
		if (low(c) == 'i') {                                   // "inf" or "infinity"
			buf.append((char) c);
			if (!expect(b, st, buf, 'n') || !expect(b, st, buf, 'f')) return -1;
			if (st[0] != 0) {
				if ((c = inchar(b, st)) >= 0) {
					if (low(c) == 'i') {
						dec(st);
						buf.append((char) c);
						if (!expect(b, st, buf, 'n') || !expect(b, st, buf, 'i') || !expect(b, st, buf, 't') || !expect(b, st, buf, 'y')) return -1;
					} else st[1]--;                            // ungetc
				}
			}
			return done(buf, st[1]);
		}
		int expChar = 'e';
		boolean hexa = false, gotDigit = false, gotDot = false, gotE = false;
		if (st[0] != 0 && c == '0') {
			buf.append((char) c);
			c = inchar(b, st);
			dec(st);
			if (st[0] != 0 && low(c) == 'x') {
				buf.append((char) c);
				hexa = true;
				expChar = 'p';
				c = inchar(b, st);
				dec(st);
			} else gotDigit = true;
		}
		for (;;) {
			if (c >= '0' && c <= '9') { buf.append((char) c); gotDigit = true; }
			else if (!gotE && hexa && isHex(c)) { buf.append((char) c); gotDigit = true; }
			else if (gotE && buf.charAt(buf.length() - 1) == expChar && (c == '-' || c == '+')) buf.append((char) c);
			else if (gotDigit && !gotE && low(c) == expChar) { buf.append((char) expChar); gotE = true; gotDot = true; }
			else if (!gotDot && c == '.') { buf.append((char) c); gotDot = true; }
			else { if (c >= 0) st[1]--; break; }               // not part of the number: pushed back
			if (st[0] == 0) break;
			if ((c = inchar(b, st)) < 0) break;
			dec(st);
		}
		if (buf.length() == gotSign || (hexa && buf.length() == 2 + gotSign)) return -1;
		return done(buf, st[1]);
	}
	private static int inchar(byte[] b, int[] st) {
		int p = st[1];
		if (p < b.length && b[p] != 0) { st[1] = p + 1; return b[p] & 255; }
		return -1;                                             // the end of the string is EOF
	}
	private static void dec(int[] st) { if (st[0] > 0) st[0]--; }
	private static boolean expect(byte[] b, int[] st, StringBuilder buf, int ch) {
		if (st[0] == 0) return false;
		int c = inchar(b, st);
		if (c < 0 || low(c) != ch) return false;
		dec(st);
		buf.append((char) c);
		return true;
	}
	private static int done(StringBuilder buf, int p) {
		byte[] nb = new byte[buf.length() + 1];
		for (int j = 0; j < buf.length(); j++) nb[j] = (byte) buf.charAt(j);
		scanBuf = nb;
		return p;
	}

	// ------------------------------------------------------------------------------------------------ printf family

	public static int fprintf(CFile f, CPtr fmt, Object... args) {
		byte[] bytes = Format(fmt, args);
		if (f == null) return -1;
		f.S.Write(bytes, 0, bytes.length);
		return bytes.length;
	}
	/** snprintf with an unsigned size n: at most n - 1 bytes and the terminator; the length of the whole output. */
	public static int snprintf(CPtr buf, long n, CPtr fmt, Object... args) {
		byte[] bytes = Format(fmt, args);
		if (n != 0) {
			int m = Long.compareUnsigned(bytes.length, n - 1) < 0 ? bytes.length : (int) (n - 1);
			System.arraycopy(bytes, 0, buf.B, buf.O, m);
			buf.B[buf.O + m] = 0;
		}
		return bytes.length;
	}
	public static int snprintf_CPtr_ul_CPtr_oA(CPtr buf, long n, CPtr fmt, Object... args) { return snprintf(buf, n, fmt, args); }
	public static int snprintf_CPtr_l_CPtr_oA(CPtr buf, long n, CPtr fmt, Object... args) { return snprintf(buf, n, fmt, args); }
	public static int snprintf_CPtr_i_CPtr_oA(CPtr buf, int n, CPtr fmt, Object... args) { return snprintf(buf, (long) n, fmt, args); }

	/** The bytes printf writes for fmt and args: glibc's formatting, with the flags - + space # 0, width and
	 *  precision (* too), the length modifiers hh h l ll L q j z t, and the conversions d i u o x X c s f F e E g G
	 *  a A %. The arguments are boxed Java values of the C# types: Integer int, Long long, Short short, Byte sbyte,
	 *  Character char, Boolean, Float, Double, U8/U16/U32/U64 the unsigned types, CPtr / Str / String / PtrB strings.
	 *  An integer argument is taken at the size its length modifier says, as glibc reads it from the va_list (the C#
	 *  runtime prints the whole C# value, which differs only where the C++ would print a truncated value). */
	public static byte[] Format(CPtr fmt, Object[] args) {
		byte[] f = fmt.B;
		int k = fmt.O, ai = 0;
		Out o = new Out();
		while (f[k] != 0) {
			if (f[k] != '%') { o.add(f[k]); k++; continue; }
			k++;
			if (f[k] == '%') { o.add((byte) '%'); k++; continue; }
			boolean left = false, plus = false, space = false, alt = false, zero = false;
			for (;; k++) {
				int c = f[k];
				if (c == '-') left = true;
				else if (c == '+') plus = true;
				else if (c == ' ') space = true;
				else if (c == '#') alt = true;
				else if (c == '0') zero = true;
				else break;
			}
			int width = 0, prec = -1;
			if (f[k] == '*') {
				width = (int) argInt(arg(args, ai++));
				if (width < 0) { left = true; width = -width; }
				k++;
			} else while (isDig(f[k])) { width = width * 10 + (f[k] - '0'); k++; }
			if (f[k] == '.') {
				k++;
				prec = 0;
				if (f[k] == '*') {
					prec = (int) argInt(arg(args, ai++));
					if (prec < 0) prec = -1;
					k++;
				} else while (isDig(f[k])) { prec = prec * 10 + (f[k] - '0'); k++; }
			}
			int size = 32;                      // the integer argument's width in bits, from the length modifier
			for (;; k++) {
				int c = f[k];
				if (c == 'h') size = size == 16 ? 8 : 16;
				else if (c == 'l' || c == 'L' || c == 'q' || c == 'j' || c == 'z' || c == 't') size = 64;
				else break;
			}
			int conv = f[k] & 255;
			k++;
			String sign = "", prefix = "", body;
			boolean numeric = true;
			switch (conv) {
				case 'd': case 'i': {
					long v = argInt(arg(args, ai++));
					if (size == 8) v = (byte) v;
					else if (size == 16) v = (short) v;
					else if (size == 32) v = (int) v;
					if (v < 0) sign = "-"; else if (plus) sign = "+"; else if (space) sign = " ";
					body = Long.toUnsignedString(v < 0 ? -v : v);
					if (prec >= 0) {
						zero = false;
						if (prec == 0 && v == 0) body = "";
						if (body.length() < prec) body = "0".repeat(prec - body.length()) + body;
					}
					break;
				}
				case 'u': case 'x': case 'X': case 'o': {
					long u = argInt(arg(args, ai++));
					if (size == 8) u &= 0xFFL;
					else if (size == 16) u &= 0xFFFFL;
					else if (size == 32) u &= 0xFFFFFFFFL;
					body = conv == 'u' ? Long.toUnsignedString(u) : conv == 'o' ? Long.toOctalString(u)
						: conv == 'x' ? Long.toHexString(u) : Long.toHexString(u).toUpperCase();
					if (prec >= 0) {
						zero = false;
						if (prec == 0 && u == 0) body = "";
						if (body.length() < prec) body = "0".repeat(prec - body.length()) + body;
					}
					if (alt) {
						if (conv == 'o' && (body.isEmpty() || body.charAt(0) != '0')) body = "0" + body;
						else if (conv == 'x' && u != 0) prefix = "0x";
						else if (conv == 'X' && u != 0) prefix = "0X";
					}
					break;
				}
				case 'c':
					numeric = false;
					body = String.valueOf((char) (argInt(arg(args, ai++)) & 255));
					break;
				case 's': {
					numeric = false;
					CPtr p = argStr(arg(args, ai++));
					if (p == null || p.B == null) body = prec < 0 || prec >= 6 ? "(null)" : "";
					else {                              // with a precision, at most that many bytes are read
						int n = 0;
						while (p.O + n < p.B.length && p.B[p.O + n] != 0 && (prec < 0 || n < prec)) n++;
						body = new String(p.B, p.O, n, StandardCharsets.ISO_8859_1);
					}
					break;
				}
				case 'f': case 'F': case 'e': case 'E': case 'g': case 'G': case 'a': case 'A': {
					double v = argF64(arg(args, ai++));
					if (Double.doubleToRawLongBits(v) < 0) sign = "-"; else if (plus) sign = "+"; else if (space) sign = " ";
					boolean upper = conv == 'F' || conv == 'E' || conv == 'G' || conv == 'A';
					if (Double.isNaN(v) || Double.isInfinite(v)) {
						body = Double.isNaN(v) ? "nan" : "inf";
						if (upper) body = body.toUpperCase();
						zero = false;
					} else if (conv == 'a' || conv == 'A') {
						body = HexFloat(Math.abs(v), prec, alt);
						prefix = "0x";
						if (upper) { body = body.toUpperCase(); prefix = "0X"; }
					} else {
						if (prec < 0) prec = 6;
						body = FormatFloat(Math.abs(v), conv, prec, alt);
					}
					break;
				}
				default:
					throw new IllegalArgumentException("printf %" + (char) conv);
			}
			int n = sign.length() + prefix.length() + body.length();
			if (n >= width) o.add(sign).add(prefix).add(body);
			else if (left) o.add(sign).add(prefix).add(body).pad(' ', width - n);
			else if (zero && numeric) o.add(sign).add(prefix).pad('0', width - n).add(body);
			else o.pad(' ', width - n).add(sign).add(prefix).add(body);
		}
		return o.bytes();
	}
	private static Object arg(Object[] args, int i) {
		if (args == null || i >= args.length) throw new IllegalArgumentException("printf: too few arguments");
		return args[i];
	}

	/** A growable byte buffer for Format. */
	private static final class Out {
		byte[] b = new byte[128];
		int n;
		void grow(int m) { if (n + m > b.length) b = Arrays.copyOf(b, Math.max(b.length * 2, n + m)); }
		Out add(byte c) { grow(1); b[n++] = c; return this; }
		Out add(String s) { int m = s.length(); grow(m); for (int i = 0; i < m; i++) b[n++] = (byte) s.charAt(i); return this; }
		Out pad(char c, int m) { grow(m); for (int i = 0; i < m; i++) b[n++] = (byte) c; return this; }
		byte[] bytes() { return Arrays.copyOf(b, n); }
	}

	/** An integer argument as a 64-bit value: sign-extended from a signed C# type, zero-extended from an unsigned
	 *  one (U8 ... U64, char), truncated toward zero from a floating one. */
	static long argInt(Object a) {
		if (a instanceof Integer) return (Integer) a;
		if (a instanceof Long) return (Long) a;
		if (a instanceof Short) return (Short) a;
		if (a instanceof Byte) return (Byte) a;
		if (a instanceof Character) return (Character) a;
		if (a instanceof Boolean) return (Boolean) a ? 1 : 0;
		if (a instanceof U8) return ((U8) a).v & 0xFFL;
		if (a instanceof U16) return ((U16) a).v;
		if (a instanceof U32) return ((U32) a).v & 0xFFFFFFFFL;
		if (a instanceof U64) return ((U64) a).v;
		if (a instanceof Double || a instanceof Float) return Conv.D2L(((Number) a).doubleValue());
		throw new IllegalArgumentException("printf: integer argument " + a);
	}
	static double argF64(Object a) {
		if (a instanceof Double) return (Double) a;
		if (a instanceof Float) return (Float) a;
		if (a instanceof Integer || a instanceof Long || a instanceof Short || a instanceof Byte) return ((Number) a).longValue();
		if (a instanceof U64) return u2d(((U64) a).v);
		if (a instanceof U32 || a instanceof U16 || a instanceof U8 || a instanceof Character) return argInt(a);
		throw new IllegalArgumentException("printf: floating argument " + a);
	}
	/** A %s argument: a C string, a char array pointer, a std::string, a Java string; null for a null pointer. */
	static CPtr argStr(Object a) {
		if (a == null) return null;
		if (a instanceof CPtr) return (CPtr) a;
		if (a instanceof Str) return ((Str) a).c_str();
		if (a instanceof String) return Lit((String) a);
		if (a instanceof PtrB) { PtrB p = (PtrB) a; return p.a == null ? null : new CPtr(p.a, (int) p.o); }
		throw new IllegalArgumentException("printf: string argument " + a);
	}

	/** glibc's %a of a non-negative finite double without the 0x prefix: h.hhhp+e with trailing zero digits dropped
	 *  (0p+0 for zero, 0.hhhp-1022 for a subnormal); with a precision, rounded to that many digits, the leading digit
	 *  and the fraction together (half to even, as glibc does in the default rounding mode). The C# runtime rounds
	 *  the fraction alone; glibc is followed. */
	static String HexFloat(double a, int prec, boolean alt) {
		long bits = Double.doubleToRawLongBits(a);
		int ex = (int) ((bits >>> 52) & 0x7FF);
		long man = bits & 0xFFFFFFFFFFFFFL;
		int lead = 1, e;
		if (ex == 0) { lead = 0; e = man != 0 ? -1022 : 0; }
		else e = ex - 1023;
		String digits;
		if (prec < 0) {
			digits = hex(man, 13);
			int end = digits.length();
			while (end > 0 && digits.charAt(end - 1) == '0') end--;
			digits = digits.substring(0, end);
		} else if (prec < 13) {
			int drop = (13 - prec) * 4;
			long full = ((long) lead << 52) | man;
			long keep = full >>> drop, rem = full & ((1L << drop) - 1), half = 1L << (drop - 1);
			if (rem > half || (rem == half && (keep & 1) == 1)) keep++;
			lead = (int) (keep >>> (prec * 4));
			digits = prec > 0 ? hex(keep & ((1L << (prec * 4)) - 1), prec) : "";
		} else digits = hex(man, 13) + "0".repeat(prec - 13);
		String s = Integer.toString(lead);
		if (!digits.isEmpty() || alt) s += "." + digits;
		return s + (e >= 0 ? "p+" + e : "p-" + (-e));
	}
	private static String hex(long v, int nd) {
		String s = Long.toHexString(v);
		return s.length() >= nd ? s : "0".repeat(nd - s.length()) + s;
	}

	/** Exact decimal formatting of a finite non-negative double for %f %e %g (and capitals), glibc's rounding:
	 *  half to even on the exact binary value. */
	public static String FormatFloat(double a, int conv, int prec, boolean alt) {
		int lc = conv | 0x20;
		if (lc == 'f') return fixedDigits(a, prec, alt);
		if (lc == 'e') return expDigits(a, prec, alt, conv == 'E');
		int P = prec == 0 ? 1 : prec;                 // %g as C99 specifies it
		int[] X = new int[1];
		if (a != 0.0) expRaw(a, P - 1, X);
		// glibc's quirk with '#': when rounding to P digits carries the value up to 10^P from an exponent of P - 1,
		// it keeps the fixed form's count of fraction digits (none) in the exponential form: %#g of 999999.5 is
		// "1.e+06", not "1.00000e+06" (%#.3g of 999.5 is "1.e+03"); without '#' the zeros are stripped anyway
		if (alt && X[0] == P && P > 1) {
			BigDecimal bd = new BigDecimal(a);
			if (bd.precision() - bd.scale() - 1 == P - 1) return expDigits(a, 0, true, conv == 'G');
		}
		String s = (P > X[0] && X[0] >= -4) ? fixedDigits(a, P - 1 - X[0], alt) : expDigits(a, P - 1, alt, conv == 'G');
		if (!alt) {
			int ep = Math.max(s.indexOf('e'), s.indexOf('E'));
			String mant = ep >= 0 ? s.substring(0, ep) : s;
			String ex = ep >= 0 ? s.substring(ep) : "";
			if (mant.indexOf('.') >= 0) {
				int e = mant.length();
				while (e > 0 && mant.charAt(e - 1) == '0') e--;
				if (e > 0 && mant.charAt(e - 1) == '.') e--;
				mant = mant.substring(0, e);
			}
			s = mant + ex;
		}
		return s;
	}
	private static String fixedDigits(double a, int prec, boolean alt) {
		String s = new BigDecimal(a).setScale(prec, RoundingMode.HALF_EVEN).toPlainString();
		if (prec == 0 && alt) s += ".";
		return s;
	}
	/** The digits of a rounded to prec+1 significant digits, and (in X[0]) the decimal exponent. */
	private static String expRaw(double a, int prec, int[] X) {
		if (a == 0.0) { X[0] = 0; return "0".repeat(prec + 1); }
		BigDecimal r = new BigDecimal(a).round(new MathContext(prec + 1, RoundingMode.HALF_EVEN));
		String d = r.unscaledValue().toString();
		int e = d.length() - 1 - r.scale();
		if (d.length() < prec + 1) d = d + "0".repeat(prec + 1 - d.length());
		else if (d.length() > prec + 1) d = d.substring(0, prec + 1);
		X[0] = e;
		return d;
	}
	private static String expDigits(double a, int prec, boolean alt, boolean upper) {
		int[] X = new int[1];
		String d = expRaw(a, prec, X);
		String mant = d.substring(0, 1) + (prec > 0 ? "." + d.substring(1) : (alt ? "." : ""));
		int ax = Math.abs(X[0]);
		return mant + (upper ? "E" : "e") + (X[0] < 0 ? "-" : "+") + (ax < 10 ? "0" + ax : String.valueOf(ax));
	}

	// ------------------------------------------------------------------------------------------------ files

	/** C's stdout: written through to file descriptor 1 at once, as C#'s Console stream is. */
	public static CFile stdout_ = new CFile(new CFile.Stream(new FileOutputStream(FileDescriptor.out)), true);

	/** fopen as the C# runtime does it: r (r+: read and write) an existing file, w (w+) a new or truncated file, a an
	 *  append-only file; b and t are ignored; null when the file cannot be opened. */
	public static CFile fopen(CPtr name, CPtr mode) {
		String fn = Str(name), m = Str(mode);
		try {
			if (m.isEmpty()) return null;
			File file = new File(fn);
			boolean plus = m.indexOf('+') >= 0;
			CFile.Stream s;
			switch (m.charAt(0)) {
				case 'r':
					if (!plus && file.isDirectory()) {
						CFile d = new CFile(new CFile.Stream(java.io.InputStream.nullInputStream()), false);
						d.Dir = true;
						return d;
					}
					if (!file.isFile()) return null;
					s = new CFile.Stream(new RandomAccessFile(file, plus ? "rw" : "r"));
					break;
				case 'w': {
					if (file.isDirectory()) return null;
					RandomAccessFile raf = new RandomAccessFile(file, "rw");
					raf.setLength(0);
					s = new CFile.Stream(raf);
					break;
				}
				case 'a': {
					if (file.isDirectory()) return null;
					RandomAccessFile raf = new RandomAccessFile(file, "rw");
					s = new CFile.Stream(raf);
					s.pos = s.floor = raf.length();
					break;
				}
				default:
					return null;
			}
			return new CFile(s, false);
		} catch (IOException | SecurityException e) {
			return null;
		}
	}
	public static int fclose(CFile f) {
		if (f == null) return -1;
		if (!f.IsStd) {
			f.S.Dispose();
			synchronized (CFile.Open) { CFile.Open.remove(f); }
		} else f.S.Flush();
		return 0;
	}
	/** What C's exit() does to open streams: flush every one (stdout and stderr too). */
	public static void FlushAll() {
		CFile[] open;
		synchronized (CFile.Open) { open = CFile.Open.toArray(new CFile[0]); }
		for (CFile f : open) { try { f.S.Flush(); } catch (RuntimeException e) { } }
		try { stdout_.S.Flush(); } catch (RuntimeException e) { }
		try { stderr_.S.Flush(); } catch (RuntimeException e) { }
	}
	/** fflush; fflush(NULL) flushes every stream, as glibc does (the C# runtime does nothing for null). */
	public static int fflush(CFile f) {
		if (f == null) FlushAll();
		else f.S.Flush();
		return 0;
	}
	public static int feof(CFile f) { return f.Eof ? 1 : 0; }
	public static void rewind(CFile f) {
		try { f.S.Seek(0, 0); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
		f.Eof = false;
	}
	public static int fseek(CFile f, long off, int whence) {
		try {
			f.S.Seek(off, whence);
			f.Eof = false;
			return 0;
		} catch (IOException | RuntimeException e) {
			return -1;
		}
	}
	public static int fseek_CFile_l_i(CFile f, long off, int whence) { return fseek(f, off, whence); }
	public static int fseek_CFile_i_i(CFile f, int off, int whence) { return fseek(f, (long) off, whence); }
	public static long ftell(CFile f) { return f.S.Position(); }
	public static CPtr fgets(CPtr buf, int n, CFile f) {
		int i = 0;
		while (i < n - 1) {
			int c = f.S.ReadByte();
			if (c < 0) { f.ReadFailed(); break; }
			buf.B[buf.O + i++] = (byte) c;
			if (c == '\n') break;
		}
		if (i == 0) return CPtr.ZERO;
		buf.B[buf.O + i] = 0;
		return buf;
	}
	/** remove: 0 when the file was deleted, -1 when it could not be (as glibc; .NET's File.Delete also succeeds
	 *  for a missing file). */
	public static int remove(CPtr name) {
		try { Files.delete(Paths.get(Str(name))); return 0; } catch (IOException | RuntimeException e) { return -1; }
	}
	private static int tmpCounter;
	/** mkstemp: replaces the template's last six characters and creates the file; a stand-in descriptor (3). */
	public static int mkstemp(CPtr template) {
		int n = strlenI(template);
		java.util.Random rnd = new java.util.Random(ProcessHandle.current().pid() ^ System.nanoTime() ^ (tmpCounter++ * 7919L));
		String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		for (int attempt = 0; attempt < 1000; attempt++) {
			for (int i = n - 6; i < n; i++) template.B[template.O + i] = (byte) chars.charAt(rnd.nextInt(chars.length()));
			try {
				Files.createFile(Paths.get(Str(template)));
				return 3;
			} catch (FileAlreadyExistsException e) {
				continue;
			} catch (IOException | RuntimeException e) {
				return -1;
			}
		}
		return -1;
	}

	// fwrite / fread of typed values (little-endian, as x86); a short read returns 0 (or the count read) and leaves
	// the value unchanged
	private static final byte[] io = new byte[8];
	private static void put(long bits, int n) { for (int i = 0; i < n; i++) io[i] = (byte) (bits >>> (8 * i)); }
	private static long get(int n) { long v = 0; for (int i = 0; i < n; i++) v |= (io[i] & 255L) << (8 * i); return v; }
	private static boolean readExact(CFile f, int n) {
		int got = 0;
		while (got < n) {
			int r = f.S.Read(io, got, n - got);
			if (r <= 0) { f.ReadFailed(); return false; }
			got += r;
		}
		return true;
	}
	public static long fwriteF1(float v, CFile f) { put(Float.floatToRawIntBits(v), 4); f.S.Write(io, 0, 4); return 1; }
	public static long fwriteD1(double v, CFile f) { put(Double.doubleToRawLongBits(v), 8); f.S.Write(io, 0, 8); return 1; }
	public static long fwriteI1(int v, CFile f) { put(v, 4); f.S.Write(io, 0, 4); return 1; }
	public static long fwriteL1(long v, CFile f) { put(v, 8); f.S.Write(io, 0, 8); return 1; }
	public static long fwriteF(float[] a, int off, int n, CFile f) { for (int i = 0; i < n; i++) fwriteF1(a[off + i], f); return n; }
	public static long fwriteD(double[] a, int off, int n, CFile f) { for (int i = 0; i < n; i++) fwriteD1(a[off + i], f); return n; }
	public static long fwriteI(int[] a, int off, int n, CFile f) { for (int i = 0; i < n; i++) fwriteI1(a[off + i], f); return n; }
	public static long fwriteC(CPtr p, int off, int n, CFile f) { f.S.Write(p.B, p.O + off, n); return n; }
	public static long freadF1(RefF v, CFile f) { if (!readExact(f, 4)) return 0; v.set(Float.intBitsToFloat((int) get(4))); return 1; }
	public static long freadD1(RefD v, CFile f) { if (!readExact(f, 8)) return 0; v.set(Double.longBitsToDouble(get(8))); return 1; }
	public static long freadI1(RefI v, CFile f) { if (!readExact(f, 4)) return 0; v.set((int) get(4)); return 1; }
	public static long freadL1(RefL v, CFile f) { if (!readExact(f, 8)) return 0; v.set(get(8)); return 1; }
	public static long freadF(float[] a, int off, int n, CFile f) {
		for (int i = 0; i < n; i++) { if (!readExact(f, 4)) return i; a[off + i] = Float.intBitsToFloat((int) get(4)); }
		return n;
	}
	public static long freadD(double[] a, int off, int n, CFile f) {
		for (int i = 0; i < n; i++) { if (!readExact(f, 8)) return i; a[off + i] = Double.longBitsToDouble(get(8)); }
		return n;
	}
	public static long freadI(int[] a, int off, int n, CFile f) {
		for (int i = 0; i < n; i++) { if (!readExact(f, 4)) return i; a[off + i] = (int) get(4); }
		return n;
	}
	public static long freadC(CPtr p, int off, int n, CFile f) {
		int got = 0;
		while (got < n) { int r = f.S.Read(p.B, p.O + off + got, n - got); if (r <= 0) { f.ReadFailed(); break; } got += r; }
		return got;
	}

	// ------------------------------------------------------------------------------------------------ time

	public static long time() { return Math.floorDiv(System.currentTimeMillis(), 1000L); }
	public static long time(PtrL p) {
		long t = time();
		if (!p.get_IsNull()) p.ref_Item_l(0L).set(t);
		return t;
	}
	public static long time_Ptrl(PtrL p) { return time(p); }
	public static CPtr ctime(PtrL p) { return ctime(p.get_Item_l(0L)); }
	public static CPtr ctime_Ptrl(PtrL p) { return ctime(p); }
	public static double difftime(long end, long start) { return (double) (end - start); }
	private static final String[] DAYS = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
	private static final String[] MONS = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
	/** ctime: "Www Mmm dd hh:mm:ss yyyy\n" in the local time zone (asctime's format). */
	public static CPtr ctime(long t) {
		java.time.ZonedDateTime d = java.time.Instant.ofEpochSecond(t).atZone(java.time.ZoneId.systemDefault());
		return Lit(String.format(java.util.Locale.ROOT, "%s %s %2d %02d:%02d:%02d %d\n", DAYS[d.getDayOfWeek().getValue() - 1], MONS[d.getMonthValue() - 1],
			d.getDayOfMonth(), d.getHour(), d.getMinute(), d.getSecond(), d.getYear()));
	}
}
