// Mem.java - raw memory on typed arrays (C# Cpp.Mem, cs/runtime/Misc.cs): calloc, memcpy and memset across element
// types, fread / fwrite of typed arrays, and the index of an object in an array of objects. A typed array is seen as
// its little-endian bytes (x86's layout), as C#'s MemoryMarshal.AsBytes sees it: copying part of an element changes
// only those bytes of it. A bool is one byte (0 or 1 when written, any nonzero byte read as true).
// The suffix codes are the element types (B byte/sbyte, S short, C char/ushort, I int/uint, L long/ulong, F float,
// D double, Z bool); memcpy<T, U> has the destination's code first.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Mem {
	private Mem() { }

	// ------------------------------------------------------------------------------------------------ typed arrays as bytes

	/** The size in bytes of one element of the primitive array a. */
	static int esz(Object a) {
		if (a instanceof double[] || a instanceof long[]) return 8;
		if (a instanceof int[] || a instanceof float[]) return 4;
		if (a instanceof short[] || a instanceof char[]) return 2;
		if (a instanceof byte[] || a instanceof boolean[]) return 1;
		throw new IllegalArgumentException("not a primitive array: " + a);
	}
	static int len(Object a) { return java.lang.reflect.Array.getLength(a); }
	/** The bit pattern of element i (zero-extended to 64 bits). */
	static long bits(Object a, int i) {
		if (a instanceof double[]) return Double.doubleToRawLongBits(((double[]) a)[i]);
		if (a instanceof long[]) return ((long[]) a)[i];
		if (a instanceof int[]) return ((int[]) a)[i] & 0xFFFFFFFFL;
		if (a instanceof float[]) return Float.floatToRawIntBits(((float[]) a)[i]) & 0xFFFFFFFFL;
		if (a instanceof short[]) return ((short[]) a)[i] & 0xFFFFL;
		if (a instanceof char[]) return ((char[]) a)[i];
		if (a instanceof byte[]) return ((byte[]) a)[i] & 0xFFL;
		if (a instanceof boolean[]) return ((boolean[]) a)[i] ? 1 : 0;
		throw new IllegalArgumentException("not a primitive array: " + a);
	}
	static void setBits(Object a, int i, long b) {
		if (a instanceof double[]) ((double[]) a)[i] = Double.longBitsToDouble(b);
		else if (a instanceof long[]) ((long[]) a)[i] = b;
		else if (a instanceof int[]) ((int[]) a)[i] = (int) b;
		else if (a instanceof float[]) ((float[]) a)[i] = Float.intBitsToFloat((int) b);
		else if (a instanceof short[]) ((short[]) a)[i] = (short) b;
		else if (a instanceof char[]) ((char[]) a)[i] = (char) b;
		else if (a instanceof byte[]) ((byte[]) a)[i] = (byte) b;
		else if (a instanceof boolean[]) ((boolean[]) a)[i] = (b & 0xFF) != 0;
		else throw new IllegalArgumentException("not a primitive array: " + a);
	}
	/** n bytes of the array a, starting at element o, into dst at doff. */
	static void read(Object a, long o, byte[] dst, int doff, int n) {
		if (n == 0) return;           // no bytes, even at a null pointer (the data() of an empty vector)
		if (a instanceof byte[]) { System.arraycopy((byte[]) a, (int) o, dst, doff, n); return; }
		int sz = esz(a);
		if ((long) n > (len(a) - o) * sz) throw new IndexOutOfBoundsException("memory read past the array");
		int e = (int) o;
		for (int k = 0; k < n; e++) {
			long b = bits(a, e);
			for (int j = 0; j < sz && k < n; j++, k++) dst[doff + k] = (byte) (b >>> (8 * j));
		}
	}
	/** n bytes of src at soff into the array a, starting at element o (a partial last element keeps its other bytes). */
	static void write(Object a, long o, byte[] src, int soff, int n) {
		if (n == 0) return;
		if (a instanceof byte[]) { System.arraycopy(src, soff, (byte[]) a, (int) o, n); return; }
		int sz = esz(a);
		if ((long) n > (len(a) - o) * sz) throw new IndexOutOfBoundsException("memory write past the array");
		int e = (int) o;
		for (int k = 0; k < n; e++) {
			long b = n - k >= sz ? 0 : bits(a, e);
			for (int j = 0; j < sz && k < n; j++, k++) b = (b & ~(0xFFL << (8 * j))) | ((src[soff + k] & 0xFFL) << (8 * j));
			setBits(a, e, b);
		}
	}
	/** The first n bytes a typed pointer addresses (C#'s Mem.BytesOf). */
	static byte[] bytesOf(Object a, long o, int n) { byte[] b = new byte[n]; read(a, o, b, 0, n); return b; }
	/** All the bytes from a typed pointer to the end of its array. */
	static byte[] allBytes(Object a, long o) { return bytesOf(a, o, (int) ((len(a) - o) * esz(a))); }

	// ------------------------------------------------------------------------------------------------ calloc / free / Diff

	/** calloc(n, size) cast to T*: n*size bytes of zeroed T. Zero bytes give an empty array, which, like glibc's
	 *  unique pointer for a zero-size request, is never dereferenced. free() is the collector's. */
	public static PtrD Calloc__D(long n, long size) { return new PtrD(new double[(int) (n * size / 8)], 0L); }
	public static PtrF Calloc__F(long n, long size) { return new PtrF(new float[(int) (n * size / 4)], 0L); }
	public static PtrI Calloc__I(long n, long size) { return new PtrI(new int[(int) (n * size / 4)], 0L); }
	public static PtrL Calloc__L(long n, long size) { return new PtrL(new long[(int) (n * size / 8)], 0L); }
	public static PtrB Calloc__B(long n, long size) { return new PtrB(new byte[(int) (n * size)], 0L); }
	public static void Free(Object p) { }
	/** &obj - p: the index of an object in an array of objects (pointers to objects are references). */
	public static <T> long Diff_T_PtrT(T obj, Ptr<T> p) { return indexOf(p.a, obj) - p.o; }
	public static <T> long Diff_PtrT_T(Ptr<T> p, T obj) { return p.o - indexOf(p.a, obj); }
	private static long indexOf(Object[] a, Object obj) {
		for (int i = 0; i < a.length; i++) if (a[i] == obj) return i;
		return -1;
	}

	// ------------------------------------------------------------------------------------------------ memcpy / memset

	public static PtrI memcpy_PtrT_PtrU_ul__IB(PtrI d, PtrB s, long n) { copy(d.a, d.o, s.a, s.o, n); return d; }
	public static PtrL memcpy_PtrT_PtrU_ul__LD(PtrL d, PtrD s, long n) { copy(d.a, d.o, s.a, s.o, n); return d; }
	public static PtrD memcpy_PtrT_PtrU_ul__DL(PtrD d, PtrL s, long n) { copy(d.a, d.o, s.a, s.o, n); return d; }
	public static PtrB memcpy_PtrT_PtrU_ul__BI(PtrB d, PtrI s, long n) { copy(d.a, d.o, s.a, s.o, n); return d; }
	public static PtrI memcpy_PtrT_PtrU_ul__IF(PtrI d, PtrF s, long n) { copy(d.a, d.o, s.a, s.o, n); return d; }
	public static PtrF memcpy_PtrT_PtrU_ul__FI(PtrF d, PtrI s, long n) { copy(d.a, d.o, s.a, s.o, n); return d; }
	public static PtrB memcpy_PtrT_CPtr_ul__B(PtrB d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static PtrD memcpy_PtrT_CPtr_ul__D(PtrD d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static PtrF memcpy_PtrT_CPtr_ul__F(PtrF d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static PtrI memcpy_PtrT_CPtr_ul__I(PtrI d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static PtrL memcpy_PtrT_CPtr_ul__L(PtrL d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static PtrS memcpy_PtrT_CPtr_ul__S(PtrS d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static PtrZ memcpy_PtrT_CPtr_ul__Z(PtrZ d, CPtr s, long n) { copy(d.a, d.o, s.B, s.O, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__B(CPtr d, PtrB s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__D(CPtr d, PtrD s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__F(CPtr d, PtrF s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__I(CPtr d, PtrI s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__L(CPtr d, PtrL s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__S(CPtr d, PtrS s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_PtrU_ul__Z(CPtr d, PtrZ s, long n) { copy(d.B, d.O, s.a, s.o, n); return d; }
	public static CPtr memcpy_CPtr_CPtr_ul(CPtr d, CPtr s, long n) { if (n != 0) System.arraycopy(s.B, s.O, d.B, d.O, (int) n); return d; }
	/** n bytes from (sa, so) to (da, do): through a byte copy, so overlapping arrays behave as memmove. */
	static void copy(Object da, long dO, Object sa, long sO, long n) {
		if (n == 0) return;
		write(da, dO, bytesOf(sa, sO, (int) n), 0, (int) n);
	}
	public static PtrD memset_PtrT_i_ul__D(PtrD d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static PtrF memset_PtrT_i_ul__F(PtrF d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static PtrI memset_PtrT_i_ul__I(PtrI d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static PtrL memset_PtrT_i_ul__L(PtrL d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static PtrB memset_PtrT_i_ul__B(PtrB d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static PtrS memset_PtrT_i_ul__S(PtrS d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static PtrZ memset_PtrT_i_ul__Z(PtrZ d, int c, long n) { fill(d.a, d.o, c, n); return d; }
	public static CPtr memset_CPtr_i_ul(CPtr d, int c, long n) { java.util.Arrays.fill(d.B, d.O, d.O + (int) n, (byte) c); return d; }
	static void fill(Object a, long o, int c, long n) {
		byte[] b = new byte[(int) n];
		java.util.Arrays.fill(b, (byte) c);
		write(a, o, b, 0, (int) n);
	}

	// ------------------------------------------------------------------------------------------------ fread / fwrite

	/** fwrite of size*count bytes from a typed pointer; returns count. */
	static long fwriteT(Object a, long o, long size, long count, CFile f) {
		byte[] b = bytesOf(a, o, (int) (size * count));
		f.S.Write(b, 0, b.length);
		return count;
	}
	/** fread of size*count bytes into a typed pointer: the bytes read are copied in (a partial element too); the
	 *  number of whole items read; a short read sets the end-of-file flag. */
	static long freadT(Object a, long o, long size, long count, CFile f) {
		int want = (int) (size * count);
		byte[] b = new byte[want];
		int got = 0;
		while (got < want) { int k = f.S.Read(b, got, want - got); if (k <= 0) break; got += k; }
		if (got < want) f.ReadFailed();
		write(a, o, b, 0, got);
		return size == 0 ? 0 : Long.divideUnsigned(got, size);
	}
	public static long fwrite_PtrT_ul_ul_CFile__F(PtrF p, long size, long count, CFile f) { return fwriteT(p.a, p.o, size, count, f); }
	public static long fwrite_PtrT_ul_ul_CFile__I(PtrI p, long size, long count, CFile f) { return fwriteT(p.a, p.o, size, count, f); }
	public static long fwrite_PtrT_ul_ul_CFile__D(PtrD p, long size, long count, CFile f) { return fwriteT(p.a, p.o, size, count, f); }
	public static long fwrite_PtrT_ul_ul_CFile__L(PtrL p, long size, long count, CFile f) { return fwriteT(p.a, p.o, size, count, f); }
	public static long fwrite_PtrT_ul_ul_CFile__B(PtrB p, long size, long count, CFile f) { return fwriteT(p.a, p.o, size, count, f); }
	public static long fread_PtrT_ul_ul_CFile__F(PtrF p, long size, long count, CFile f) { return freadT(p.a, p.o, size, count, f); }
	public static long fread_PtrT_ul_ul_CFile__I(PtrI p, long size, long count, CFile f) { return freadT(p.a, p.o, size, count, f); }
	public static long fread_PtrT_ul_ul_CFile__D(PtrD p, long size, long count, CFile f) { return freadT(p.a, p.o, size, count, f); }
	public static long fread_PtrT_ul_ul_CFile__L(PtrL p, long size, long count, CFile f) { return freadT(p.a, p.o, size, count, f); }
	public static long fread_PtrT_ul_ul_CFile__B(PtrB p, long size, long count, CFile f) { return freadT(p.a, p.o, size, count, f); }
	public static long fwrite_CPtr_ul_ul_CFile(CPtr p, long size, long count, CFile f) {
		if (size * count != 0) f.S.Write(p.B, p.O, (int) (size * count));
		return count;
	}
	public static long fread_CPtr_ul_ul_CFile(CPtr p, long size, long count, CFile f) {
		int want = (int) (size * count), got = 0;
		while (got < want) { int k = f.S.Read(p.B, p.O + got, want - got); if (k <= 0) break; got += k; }
		if (got < want) f.ReadFailed();
		return size == 0 ? 0 : Long.divideUnsigned(got, size);
	}
}
