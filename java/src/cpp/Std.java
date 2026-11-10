// Std.java - <utility>, <exception>, <mutex> and <charconv> helpers of the engine (C# Cpp.Std, cs/runtime/Misc.cs):
// rethrow_exception, call_once, get<i> of a pair, swap / exchange, the C++ NaN constants and std::from_chars.
// Std.Span and Std.OptCmp, which build part A's CSpan and Opt, are not used by the Java engine and are left out.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Std {
	private Std() { }

	public static final double QNaN = Double.longBitsToDouble(0x7FF8000000000000L);   // C++'s quiet NaN
	public static final double SNaN = Double.longBitsToDouble(0x7FF4000000000000L);
	public static final float QNaNf = Float.intBitsToFloat(0x7FC00000);

	/** std::rethrow_exception: throw the stored exception again (unchecked, whatever its type). */
	public static void Rethrow(Throwable e) { throw R.<RuntimeException>sneaky(e); }
	/** std::call_once: run f the first time only (single-threaded; an exception leaves the flag unset). */
	public static void call_once(OnceFlag flag, Fn.A0 f) {
		if (flag.done) return;
		f.run();
		flag.done = true;
	}
	public static int Nop(Object x) { return 0; }
	public static int iabs_i(int x) { return x < 0 ? -x : x; }
	public static long iabs_l(long x) { return x < 0 ? -x : x; }

	// std::get<0> / std::get<1> of a pair (structured bindings of map entries and emplace results); the suffix
	// codes are the pair's type arguments (O a reference type), the result the element's Java type
	public static <A, B> A get0_PairAB(Pair<A, B> p) { return p.first; }
	public static <A, B> B get1_PairAB(Pair<A, B> p) { return p.second; }
	public static int get0_PairAB__ID(Pair<Integer, Double> p) { return p.first; }
	public static double get1_PairAB__ID(Pair<Integer, Double> p) { return p.second; }
	public static int get0_PairAB__II(Pair<Integer, Integer> p) { return p.first; }
	public static int get1_PairAB__II(Pair<Integer, Integer> p) { return p.second; }
	public static <B> int get0_PairAB__IO(Pair<Integer, B> p) { return p.first; }
	public static <B> B get1_PairAB__IO(Pair<Integer, B> p) { return p.second; }
	public static <B> long get0_PairAB__LO(Pair<Long, B> p) { return p.first; }
	public static <B> B get1_PairAB__LO(Pair<Long, B> p) { return p.second; }
	public static <A> A get0_PairAB__OD(Pair<A, Double> p) { return p.first; }
	public static <A> double get1_PairAB__OD(Pair<A, Double> p) { return p.second; }
	public static <A> A get0_PairAB__OI(Pair<A, Integer> p) { return p.first; }
	public static <A> int get1_PairAB__OI(Pair<A, Integer> p) { return p.second; }
	public static <A> A get0_PairAB__OL(Pair<A, Long> p) { return p.first; }
	public static <A> long get1_PairAB__OL(Pair<A, Long> p) { return p.second; }
	public static <A> A get0_PairAB__OS(Pair<A, Short> p) { return p.first; }
	public static <A> short get1_PairAB__OS(Pair<A, Short> p) { return p.second; }
	public static <A> A get0_PairAB__OZ(Pair<A, Boolean> p) { return p.first; }
	public static <A> boolean get1_PairAB__OZ(Pair<A, Boolean> p) { return p.second; }

	// std::swap / std::exchange / release through references
	public static <T> void swap(RefO<T> a, RefO<T> b) { T t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__D(RefD a, RefD b) { double t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__F(RefF a, RefF b) { float t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__I(RefI a, RefI b) { int t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__L(RefL a, RefL b) { long t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__B(RefB a, RefB b) { byte t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__S(RefS a, RefS b) { short t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__C(RefC a, RefC b) { char t = a.get(); a.set(b.get()); b.set(t); }
	public static void swap__Z(RefZ a, RefZ b) { boolean t = a.get(); a.set(b.get()); b.set(t); }
	public static <T> T exchange(RefO<T> a, T v) { T old = a.get(); a.set(v); return old; }
	public static <T> T release(RefO<T> a) { T r = a.get(); a.set(null); return r; }

	// ------------------------------------------------------------------------------------------------ std::from_chars

	/** std::from_chars for double (chars_format::general), as libstdc++ has it: the value strtod reads from the same
	 *  characters (both are correctly rounded), except that a sign other than '-', leading white space and a
	 *  hexadecimal prefix are not read ("0x10" is 0 and stops at the x), and a value too large or too small for a
	 *  double is result_out_of_range (34) with the value left unchanged. The C# runtime does not make the last two
	 *  distinctions (it would read "0x10" as hexadecimal and store an overflowed or underflowed value); libstdc++ is
	 *  followed, as in the Go port. */
	public static FromCharsResult from_chars_CPtr_CPtr_dr(CPtr first, CPtr last, RefD v) {
		int n = last.O - first.O;
		byte[] tmp = new byte[n + 2];
		if (n > 0) System.arraycopy(first.B, first.O, tmp, 0, n);
		int k = tmp[0] == '-' ? 1 : 0;
		int c = tmp[k] & 255;
		if (!(R.isDig(c) || c == '.' || R.low(c) == 'i' || R.low(c) == 'n')) return new FromCharsResult(first, 22);
		if (c == '0' && R.low(tmp[k + 1] & 255) == 'x') tmp[k + 1] = 0;   // read the 0 alone
		CPtr p = new CPtr(tmp, 0);
		RefO.Cell<CPtr> end = new RefO.Cell<>(p);
		double r = R.strtod(p, end);
		if (end.v.O == 0) return new FromCharsResult(first, 22);
		FromCharsResult res = new FromCharsResult(new CPtr(first.B, first.O + end.v.O), 0);
		if (Double.isInfinite(r) && R.low(c) != 'i') return new FromCharsResult(res.ptr, 34);   // overflow
		if (r == 0) {                                     // underflow: a nonzero digit before the exponent
			for (int j = k; j < end.v.O; j++) {
				int d = tmp[j];
				if (d == 'e' || d == 'E') break;
				if (d >= '1' && d <= '9') return new FromCharsResult(res.ptr, 34);
			}
		}
		v.set(r);
		return res;
	}

	// std::from_chars for integers, base 10: an optional '-' (signed types only), digits; an empty match is
	// invalid_argument (22) with ptr = first; a value out of the type's range is result_out_of_range (34), all the
	// digits consumed, the value left unchanged
	private static boolean icNeg, icOk;
	private static long icMag;
	private static FromCharsResult intChars(CPtr first, CPtr last, boolean signed, long lo, long hiUnsigned) {
		int i = first.O;
		icNeg = false;
		icOk = false;
		icMag = 0;
		if (signed && i < last.O && first.B[i] == '-') { icNeg = true; i++; }
		int start = i;
		boolean over = false;
		long mag = 0;
		while (i < last.O && first.B[i] >= '0' && first.B[i] <= '9') {
			int d = first.B[i] - '0';
			if (Long.compareUnsigned(mag, Long.divideUnsigned(-1L - d, 10)) > 0) over = true;
			else mag = mag * 10 + d;
			i++;
		}
		if (i == start) return new FromCharsResult(first, 22);
		CPtr end = new CPtr(first.B, i);
		icMag = mag;
		if (over || (!icNeg && Long.compareUnsigned(mag, hiUnsigned) > 0) || (icNeg && Long.compareUnsigned(mag, -lo) > 0))
			return new FromCharsResult(end, 34);
		icOk = true;
		return new FromCharsResult(end, 0);
	}
	public static FromCharsResult from_chars_CPtr_CPtr_ir(CPtr first, CPtr last, RefI v) {
		FromCharsResult r = intChars(first, last, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if (icOk) v.set((int) (icNeg ? -icMag : icMag));
		return r;
	}
	public static FromCharsResult from_chars_CPtr_CPtr_lr(CPtr first, CPtr last, RefL v) {
		FromCharsResult r = intChars(first, last, true, Long.MIN_VALUE, Long.MAX_VALUE);
		if (icOk) v.set(icNeg ? -icMag : icMag);
		return r;
	}
	/** unsigned int: the bit pattern in an int. */
	public static FromCharsResult from_chars_CPtr_CPtr_ur(CPtr first, CPtr last, RefI v) {
		FromCharsResult r = intChars(first, last, false, 0, 0xFFFFFFFFL);
		if (icOk) v.set((int) icMag);
		return r;
	}
	/** unsigned long: the bit pattern in a long. */
	public static FromCharsResult from_chars_CPtr_CPtr_ulr(CPtr first, CPtr last, RefL v) {
		FromCharsResult r = intChars(first, last, false, 0, -1L);
		if (icOk) v.set(icMag);
		return r;
	}
}
