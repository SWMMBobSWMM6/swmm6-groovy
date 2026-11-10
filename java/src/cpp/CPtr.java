// CPtr.java - C char*: a byte buffer and an offset (C# Cpp.CPtr, the struct at the top of cs/runtime/CRuntime.cs).
// An immutable value, as every runtime struct is in Java: arithmetic returns a new pointer. Element reads give the
// signed char (x86 C's char), writes store the low byte.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class CPtr {
	public final byte[] B;
	public final int O;

	public CPtr(byte[] b, int o) { B = b; O = o; }

	/** default(CPtr): the null pointer. */
	public static final CPtr ZERO = new CPtr(null, 0);
	public static final CPtr Null = ZERO;

	public CPtr Clone() { return this; }      // a value: copying it is the copy

	/** A char* made from a std::vector&lt;char&gt; or std::array&lt;char, N&gt; (sbyte storage): the same bytes. */
	public static CPtr from_Ptrc(PtrB p) { return new CPtr(p.a, (int) p.o); }
	/** A char* made from unsigned char storage: the same bytes. */
	public static CPtr from_Ptruc(PtrB p) { return new CPtr(p.a, (int) p.o); }

	public boolean get_IsNull() { return B == null; }

	// ---- the indexer (C# int this[int i]: get gives the signed char, set stores the low byte)
	public int get_Item(int i) { return B[O + i]; }
	public void set_Item(int i, int v) { B[O + i] = (byte) v; }

	// ---- element access as the translator writes it (p[i], p[i] = v as a statement and as a value, ++p[i], ...)
	public int get(int i) { return B[O + i]; }
	public int get(long i) { return B[O + (int) i]; }
	public void set(int i, int v) { B[O + i] = (byte) v; }
	public void set(long i, int v) { B[O + (int) i] = (byte) v; }
	/** p[i] = v as an expression: the value is v (the indexer's int), the byte stored is its low 8 bits. */
	public int assign(int i, int v) { B[O + i] = (byte) v; return v; }
	public int assign(long i, int v) { B[O + (int) i] = (byte) v; return v; }
	/** A reference to the element, typed as the indexer is (int). */
	public RefI ref(int i) { return new Elem(B, O + i); }
	public RefI ref(long i) { return new Elem(B, O + (int) i); }
	public int preInc(int i) { int v = B[O + i] + 1; B[O + i] = (byte) v; return v; }
	public int preInc(long i) { return preInc((int) i); }
	public int preDec(int i) { int v = B[O + i] - 1; B[O + i] = (byte) v; return v; }
	public int preDec(long i) { return preDec((int) i); }
	public int postInc(int i) { int v = B[O + i]; B[O + i] = (byte) (v + 1); return v; }
	public int postInc(long i) { return postInc((int) i); }
	public int postDec(int i) { int v = B[O + i]; B[O + i] = (byte) (v - 1); return v; }
	public int postDec(long i) { return postDec((int) i); }

	/** p[i] as a reference: reads sign-extend the byte, writes keep the low 8 bits. */
	static final class Elem extends RefI {
		final byte[] b; final int k;
		Elem(byte[] b, int k) { this.b = b; this.k = k; }
		@Override public int get() { return b[k]; }
		@Override public void set(int v) { b[k] = (byte) v; }
	}

	// ---- operators
	public static CPtr op_Add_CPtr_i(CPtr p, int n) { return new CPtr(p.B, p.O + n); }
	public static CPtr op_Add_CPtr_l(CPtr p, long n) { return new CPtr(p.B, p.O + (int) n); }
	public static CPtr op_Add_CPtr_ul(CPtr p, long n) { return new CPtr(p.B, p.O + (int) n); }
	public static CPtr op_Sub(CPtr p, int n) { return new CPtr(p.B, p.O - n); }
	public static CPtr op_Increment(CPtr p) { return new CPtr(p.B, p.O + 1); }
	public static CPtr op_Decrement(CPtr p) { return new CPtr(p.B, p.O - 1); }
	public static boolean op_Eq(CPtr a, CPtr b) { return a.B == b.B && (a.B == null || a.O == b.O); }
	public static boolean op_Ne(CPtr a, CPtr b) { return !op_Eq(a, b); }
	public static boolean op_Lt(CPtr a, CPtr b) { return a.O < b.O; }
	public static boolean op_Gt(CPtr a, CPtr b) { return a.O > b.O; }
	public static boolean op_Le(CPtr a, CPtr b) { return a.O <= b.O; }
	public static boolean op_Ge(CPtr a, CPtr b) { return a.O >= b.O; }
	public static long Diff(CPtr a, CPtr b) { return a.O - b.O; }

	public boolean Equals_CPtr(CPtr o) { return op_Eq(this, o); }
	public boolean Equals_o(Object o) { return o instanceof CPtr p && op_Eq(this, p); }
	@Override public boolean equals(Object o) { return o instanceof CPtr p && op_Eq(this, p); }
	@Override public int hashCode() { return (B == null ? 0 : System.identityHashCode(B)) ^ O; }
	/** The NUL-terminated bytes as Latin-1 text, "(null)" for the null pointer. */
	@Override public String toString() {
		if (B == null) return "(null)";
		int e = O;
		while (e < B.length && B[e] != 0) e++;
		return new String(B, O, e - O, java.nio.charset.StandardCharsets.ISO_8859_1);
	}
}
