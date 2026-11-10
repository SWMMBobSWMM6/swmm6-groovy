// Str.java - std::string (and std::string_view, which the engine only reads) as a mutable byte sequence, so lengths,
// comparisons and printf padding count bytes as libstdc++ does (C# Cpp.Str, cs/runtime/Str.cs). c_str() hands out a
// NUL-terminated CPtr over the same buffer; npos is ulong.MaxValue (-1L here) as in C++. Unsigned C# arguments
// (ulong positions and lengths) are Java longs compared unsigned where the C# compares them.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.nio.charset.StandardCharsets;

public final class Str implements ICopy<Str>, Comparable<Str> {
	/** const char* -> std::string: the converting constructor C++ applies implicitly. */
	public static Str from_CPtr(CPtr p) { return new Str(p); }
	public static final long npos = -1L;
	public byte[] b;       // capacity >= n + 1; b[n] == 0 whenever c_str() is taken
	public int n;

	public Str() { b = new byte[16]; }
	/** The copy constructor (std::string(view)). */
	public Str(Str s) { this(s.b, 0, s.n); }
	/** A C# string's characters as Latin-1 bytes (Encoding.Latin1: a character above U+00FF becomes '?'). */
	public Str(String s) { byte[] e = s.getBytes(StandardCharsets.ISO_8859_1); b = new byte[e.length + 1]; System.arraycopy(e, 0, b, 0, e.length); n = e.length; }
	public Str(byte[] src, int off, int len) { b = new byte[len + 1]; if (len != 0) System.arraycopy(src, off, b, 0, len); n = len; }
	public Str(CPtr p) { this(p.B, p.O, strlen(p)); }
	public Str(CPtr p, long len) { this(p.B, p.O, (int) len); }
	public Str(long count, int ch) { n = (int) count; b = new byte[n + 1]; for (int i = 0; i < n; i++) b[i] = (byte) ch; }
	public Str(Str s, long pos, long len) { int p = (int) pos; int m = (int) umin(len, s.n - p); b = new byte[m + 1]; System.arraycopy(s.b, p, b, 0, m); n = m; }
	public static Str FromBytes(byte[] x) { return new Str(x, 0, x.length); }

	/** string_view::remove_prefix / remove_suffix (a view here is a Str of its own). */
	public void remove_prefix(long k) { int m = (int) k; System.arraycopy(b, m, b, 0, n - m); n -= m; b[n] = 0; }
	public void remove_suffix(long k) { n -= (int) k; b[n] = 0; }

	static int strlen(CPtr p) { int i = p.O; while (p.B[i] != 0) i++; return i - p.O; }
	static long umin(long a, long b) { return Long.compareUnsigned(a, b) <= 0 ? a : b; }

	private void grow(int need) {
		if (need + 1 <= b.length) return;
		int cap = Math.max(need + 1, b.length * 2);
		b = java.util.Arrays.copyOf(b, cap);
	}

	public long size() { return n; }
	public long length() { return n; }
	public boolean empty() { return n == 0; }
	public void clear() { n = 0; }
	public CPtr c_str() { grow(n); b[n] = 0; return new CPtr(b, 0); }
	public CPtr data() { return c_str(); }

	// ---- the indexers (the translator reads s.b[i] directly; these are for completeness)
	public byte get_Item_ul(long i) { return b[(int) i]; }
	public byte get_Item_l(long i) { return b[(int) i]; }
	public byte get_Item_i(int i) { return b[i]; }
	public void set_Item_ul(long i, byte v) { b[(int) i] = v; }
	public void set_Item_l(long i, byte v) { b[(int) i] = v; }
	public void set_Item_i(int i, byte v) { b[i] = v; }
	public RefB ref_Item_ul(long i) { return new RefB.Arr(b, (int) i); }
	public RefB ref_Item_l(long i) { return new RefB.Arr(b, (int) i); }
	public RefB ref_Item_i(int i) { return new RefB.Arr(b, i); }

	public byte at(long i) {
		if (Long.compareUnsigned(i, n) >= 0)
			throw new OutOfRange("basic_string::at: __n (which is " + Long.toUnsignedString(i) + ") >= this->size() (which is " + n + ")");
		return b[(int) i];
	}
	public byte front() { return b[0]; }
	public RefB ref_front() { return new RefB.Arr(b, 0); }
	public byte back() { return b[n - 1]; }
	public RefB ref_back() { return new RefB.Arr(b, n - 1); }
	public byte Char(long i) { return b[(int) i]; }

	@Override public Str Clone() { return new Str(b, 0, n); }
	@Override public void Assign(Str o) { Assign_Str(o); }
	public void Assign_Str(Str o) { if (this == o) return; grow(o.n); System.arraycopy(o.b, 0, b, 0, o.n); n = o.n; }
	public void Assign_CPtr(CPtr p) { int m = strlen(p); byte[] t = new byte[m + 1]; System.arraycopy(p.B, p.O, t, 0, m); b = t; n = m; }
	public void Assign_i(int ch) { grow(1); b[0] = (byte) ch; n = 1; }
	public void MoveFrom(Str o) { if (this == o) return; b = o.b; n = o.n; o.b = new byte[16]; o.n = 0; }
	public Str assign_Str(Str o) { Assign_Str(o); return this; }
	public Str assign_CPtr(CPtr p) { Assign_CPtr(p); return this; }
	/** assign(first, last). */
	public Str assign_CPtr_CPtr(CPtr b0, CPtr e0) { return assign_CPtr_ul(b0, e0.O - b0.O); }
	public Str assign_Ptrc_Ptrc(PtrB b0, PtrB e0) { return assign_CPtr_ul(CPtr.from_Ptrc(b0), e0.o - b0.o); }
	public Str assign_CPtr_ul(CPtr p, long len) { byte[] t = new byte[(int) len + 1]; if (len != 0) System.arraycopy(p.B, p.O, t, 0, (int) len); b = t; n = (int) len; return this; }
	public Str assign_ul_i(long count, int ch) { grow((int) count); for (int i = 0; i < (int) count; i++) b[i] = (byte) ch; n = (int) count; return this; }

	public Str append_Str(Str o) { grow(n + o.n); System.arraycopy(o.b, 0, b, n, o.n); n += o.n; return this; }
	public Str append_Str_ul_ul(Str o, long pos, long len) { int p = (int) pos; int m = (int) umin(len, o.n - p); grow(n + m); System.arraycopy(o.b, p, b, n, m); n += m; return this; }
	public Str append_CPtr(CPtr p) { int m = strlen(p); grow(n + m); System.arraycopy(p.B, p.O, b, n, m); n += m; return this; }
	public Str append_CPtr_ul(CPtr p, long len) { int m = (int) len; if (m == 0) return this; grow(n + m); System.arraycopy(p.B, p.O, b, n, m); n += m; return this; }
	public Str append_ul_i(long count, int ch) { int m = (int) count; grow(n + m); for (int i = 0; i < m; i++) b[n + i] = (byte) ch; n += m; return this; }
	public Str append_i(int ch) { return append_ul_i(1L, ch); }
	public void push_back(int ch) { grow(n + 1); b[n++] = (byte) ch; }
	public void pop_back() { n--; }
	public void resize(long m, int ch) { int k = (int) m; grow(k); for (int i = n; i < k; i++) b[i] = (byte) ch; n = k; }
	public void reserve(long m) { grow((int) m); }
	public void shrink_to_fit() { }

	public Str substr(long pos, long len) {
		if (Long.compareUnsigned(pos, n) > 0)
			throw new OutOfRange("basic_string::substr: __pos (which is " + Long.toUnsignedString(pos) + ") > this->size() (which is " + n + ")");
		int m = (int) umin(len, n - pos);
		return new Str(b, (int) pos, m);
	}

	public Str erase(long pos, long len) {
		int p = (int) pos; int m = (int) umin(len, n - p);
		System.arraycopy(b, p + m, b, p, n - p - m);
		n -= m;
		return this;
	}
	public Str insert_ul_Str(long pos, Str s) { int p = (int) pos; grow(n + s.n); System.arraycopy(b, p, b, p + s.n, n - p); System.arraycopy(s.b, 0, b, p, s.n); n += s.n; return this; }
	public Str insert_ul_CPtr(long pos, CPtr s) { return insert_ul_Str(pos, new Str(s)); }
	public Str insert_ul_ul_i(long pos, long count, int ch) { return insert_ul_Str(pos, new Str(count, ch)); }
	public Str replace_ul_ul_Str(long pos, long len, Str s) {
		int p = (int) pos; int m = (int) umin(len, n - p);
		byte[] t = new byte[n - m + s.n + 1];
		System.arraycopy(b, 0, t, 0, p); System.arraycopy(s.b, 0, t, p, s.n); System.arraycopy(b, p + m, t, p + s.n, n - p - m);
		b = t; n = n - m + s.n;
		return this;
	}
	public Str replace_ul_ul_CPtr(long pos, long len, CPtr s) { return replace_ul_ul_Str(pos, len, new Str(s)); }

	private static boolean in(byte c, Str set) { for (int i = 0; i < set.n; i++) if (set.b[i] == c) return true; return false; }

	public long find_Str_ul(Str s, long pos) {
		if (s.n == 0) return Long.compareUnsigned(pos, n) <= 0 ? pos : npos;
		for (long i = pos; i + s.n <= n; i++) {
			int j = 0;
			while (j < s.n && b[(int) (i + j)] == s.b[j]) j++;
			if (j == s.n) return i;
		}
		return npos;
	}
	public long find_CPtr_ul(CPtr s, long pos) { return find_Str_ul(new Str(s), pos); }
	public long find_i_ul(int ch, long pos) { for (long i = pos; i < n; i++) if (b[(int) i] == (byte) ch) return i; return npos; }
	public long rfind_Str_ul(Str s, long pos) {
		if (s.n > n) return npos;
		long start = umin(pos, n - s.n);
		for (long i = start; i >= 0; i--) {
			int j = 0;
			while (j < s.n && b[(int) (i + j)] == s.b[j]) j++;
			if (j == s.n) return i;
		}
		return npos;
	}
	public long rfind_CPtr_ul(CPtr s, long pos) { return rfind_Str_ul(new Str(s), pos); }
	public long rfind_i_ul(int ch, long pos) { if (n == 0) return npos; long i = umin(pos, n - 1); for (; i >= 0; i--) if (b[(int) i] == (byte) ch) return i; return npos; }
	public long find_first_of_Str_ul(Str set, long pos) { for (long i = pos; i < n; i++) if (in(b[(int) i], set)) return i; return npos; }
	public long find_first_of_CPtr_ul(CPtr set, long pos) { return find_first_of_Str_ul(new Str(set), pos); }
	public long find_first_of_i_ul(int ch, long pos) { return find_i_ul(ch, pos); }
	public long find_first_not_of_Str_ul(Str set, long pos) { for (long i = pos; i < n; i++) if (!in(b[(int) i], set)) return i; return npos; }
	public long find_first_not_of_CPtr_ul(CPtr set, long pos) { return find_first_not_of_Str_ul(new Str(set), pos); }
	public long find_first_not_of_i_ul(int ch, long pos) { for (long i = pos; i < n; i++) if (b[(int) i] != (byte) ch) return i; return npos; }
	public long find_last_of_Str_ul(Str set, long pos) { if (n == 0) return npos; long i = umin(pos, n - 1); for (; i >= 0; i--) if (in(b[(int) i], set)) return i; return npos; }
	public long find_last_of_CPtr_ul(CPtr set, long pos) { return find_last_of_Str_ul(new Str(set), pos); }
	public long find_last_of_i_ul(int ch, long pos) { return rfind_i_ul(ch, pos); }
	public long find_last_not_of_Str_ul(Str set, long pos) { if (n == 0) return npos; long i = umin(pos, n - 1); for (; i >= 0; i--) if (!in(b[(int) i], set)) return i; return npos; }
	public long find_last_not_of_CPtr_ul(CPtr set, long pos) { return find_last_not_of_Str_ul(new Str(set), pos); }
	public long find_last_not_of_i_ul(int ch, long pos) { if (n == 0) return npos; long i = umin(pos, n - 1); for (; i >= 0; i--) if (b[(int) i] != (byte) ch) return i; return npos; }

	public boolean starts_with_Str(Str s) { if (s.n > n) return false; for (int i = 0; i < s.n; i++) if (b[i] != s.b[i]) return false; return true; }
	public boolean starts_with_CPtr(CPtr s) { return starts_with_Str(new Str(s)); }
	public boolean starts_with_i(int ch) { return n > 0 && b[0] == (byte) ch; }
	public boolean ends_with_Str(Str s) { if (s.n > n) return false; for (int i = 0; i < s.n; i++) if (b[n - s.n + i] != s.b[i]) return false; return true; }
	public boolean ends_with_CPtr(CPtr s) { return ends_with_Str(new Str(s)); }
	public boolean ends_with_i(int ch) { return n > 0 && b[n - 1] == (byte) ch; }
	public boolean contains_Str(Str s) { return find_Str_ul(s, 0L) != npos; }
	public boolean contains_i(int ch) { return find_i_ul(ch, 0L) != npos; }

	/** char_traits&lt;char&gt;::compare is memcmp: unsigned bytes, then length. */
	public int compare_Str(Str o) {
		int m = Math.min(n, o.n);
		for (int i = 0; i < m; i++) if (b[i] != o.b[i]) return (b[i] & 0xFF) < (o.b[i] & 0xFF) ? -1 : 1;
		long d = (long) n - o.n;
		return d > Integer.MAX_VALUE ? Integer.MAX_VALUE : d < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) d;
	}
	public int compare_CPtr(CPtr o) { return compare_Str(new Str(o)); }
	public int compare_ul_ul_Str(long pos, long len, Str o) { return substr(pos, len).compare_Str(o); }
	public int CompareTo(Str o) { return compare_Str(o); }
	@Override public int compareTo(Str o) { return compare_Str(o); }
	public boolean Equals_Str(Str o) {
		if (o == null || o.n != n) return false;
		for (int i = 0; i < n; i++) if (b[i] != o.b[i]) return false;
		return true;
	}
	public boolean Equals_o(Object o) { return o instanceof Str s && Equals_Str(s); }
	@Override public boolean equals(Object o) { return o instanceof Str s && Equals_Str(s); }
	/** C#'s GetHashCode (h = h * 31 + byte, unsigned bytes); std::hash is StdHash's, not this. */
	@Override public int hashCode() { int h = n; for (int i = 0; i < n; i++) h = h * 31 + (b[i] & 0xFF); return h; }
	public boolean EqLit(CPtr p) { int m = strlen(p); if (m != n) return false; for (int i = 0; i < n; i++) if (b[i] != p.B[p.O + i]) return false; return true; }

	public static Str Cat_Str_Str(Str x, Str y) { Str r = new Str(x.b, 0, x.n); r.append_Str(y); return r; }
	public static Str Cat_Str_CPtr(Str x, CPtr y) { Str r = new Str(x.b, 0, x.n); r.append_CPtr(y); return r; }
	public static Str Cat_CPtr_Str(CPtr x, Str y) { Str r = new Str(x); r.append_Str(y); return r; }
	public static Str Cat_Str_i(Str x, int ch) { Str r = new Str(x.b, 0, x.n); r.push_back(ch); return r; }
	public static Str Cat_i_Str(int ch, Str y) { Str r = new Str(1L, ch); r.append_Str(y); return r; }

	public byte[] Bytes() { return java.util.Arrays.copyOf(b, n); }
	@Override public String toString() { return new String(b, 0, n, StandardCharsets.ISO_8859_1); }

	public IterB begin() { return new IterB(b, 0L); }
	public IterB end() { return new IterB(b, (long) n); }

	// ---- std::to_string / std::stod and friends (libstdc++: to_string(double) is "%f")
	public static Str to_string_i(int v) { return new Str(Integer.toString(v)); }
	public static Str to_string_l(long v) { return new Str(Long.toString(v)); }
	public static Str to_string_ul(long v) { return new Str(Long.toUnsignedString(v)); }
	public static Str to_string_u(int v) { return new Str(Integer.toUnsignedString(v)); }
	private static final byte[] PCT_F = { '%', 'f', 0 };
	/** "%f" through the runtime's printf (glibc's exact decimal expansion); %f of a double is at most 317 bytes. */
	public static Str to_string_d(double v) {
		CPtr buf = new CPtr(new byte[400], 0);
		R.snprintf_CPtr_ul_CPtr_oA(buf, 400L, new CPtr(PCT_F, 0), v);
		return new Str(buf);
	}
	public static Str to_string_f(float v) { return to_string_d((double) v); }

	/** std::stod (libstdc++'s __stoa over strtod): invalid_argument("stod") when nothing converts,
	 *  out_of_range("stod") when strtod sets ERANGE (R.ERange: overflow, or an underflow such as "1e-400" or the
	 *  inexact subnormal 2.2250738585072011e-308). */
	public static double stod(Str s, PtrL idx) {
		CPtr p = s.c_str();
		RefO.Cell<CPtr> end = new RefO.Cell<CPtr>(p);
		double v = R.strtod_CPtr_CPtrr(p, end);
		if (end.v.O == p.O) throw new InvalidArgument("stod");
		if (R.ERange) throw new OutOfRange("stod");
		if (!idx.get_IsNull()) idx.set_Deref((long) (end.v.O - p.O));
		return v;
	}
	public static int stoi(Str s, PtrL idx, int bas) {
		CPtr p = s.c_str();
		int[] end = { p.O };
		long v = strtol(p, end);
		if (end[0] == p.O) throw new InvalidArgument("stoi");
		if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) throw new OutOfRange("stoi");
		if (!idx.get_IsNull()) idx.set_Deref((long) (end[0] - p.O));
		return (int) v;
	}
	public static long stol(Str s, PtrL idx, int bas) {
		CPtr p = s.c_str();
		int[] end = { p.O };
		long v = strtol(p, end);
		if (end[0] == p.O) throw new InvalidArgument("stol");
		if (!idx.get_IsNull()) idx.set_Deref((long) (end[0] - p.O));
		return v;
	}
	/** The C# runtime's strtol (CRuntime.cs): decimal digits whatever the base, clamped to long's range. */
	private static long strtol(CPtr s, int[] end) {
		byte[] a = s.B; int i = s.O;
		while (a[i] == ' ' || a[i] == '\t' || a[i] == '\n' || a[i] == 0x0B || a[i] == '\f' || a[i] == '\r') i++;
		boolean neg = false;
		if (a[i] == '+' || a[i] == '-') { neg = a[i] == '-'; i++; }
		int st = i;
		java.math.BigInteger v = java.math.BigInteger.ZERO;
		while (a[i] >= '0' && a[i] <= '9') { v = v.multiply(java.math.BigInteger.TEN).add(java.math.BigInteger.valueOf(a[i] - '0')); i++; }
		if (i == st) { end[0] = s.O; return 0; }
		end[0] = i;
		if (neg) v = v.negate();
		if (v.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0) return Long.MAX_VALUE;
		if (v.compareTo(java.math.BigInteger.valueOf(Long.MIN_VALUE)) < 0) return Long.MIN_VALUE;
		return v.longValue();
	}
}
