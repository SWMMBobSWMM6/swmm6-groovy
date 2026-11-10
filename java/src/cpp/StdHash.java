// StdHash.java - std::hash as libstdc++ computes it (C# Cpp.StdHash, cs/runtime/Containers.cs): the identity for
// integers, std::_Hash_bytes (libsupc++/hash_bytes.cc, the 64-bit branch) for strings and floating-point values.
// The engine iterates hashed containers, so these values decide an order that reaches its output.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class StdHash {
	private StdHash() { }

	static final long MUL = (0xc6a4a793L << 32) + 0x5bd1e995L;
	static final long SEED = 0xc70f6907L;

	static long shiftMix(long v) { return v ^ (v >>> 47); }

	/** libstdc++'s std::_Hash_bytes over b[off, off + len) (a size_t, held as a long). */
	public static long HashBytes(byte[] b, int off, int len, long seed) {
		int aligned = len & ~7;
		long hash = seed ^ ((long) len * MUL);
		int p = off;
		for (; p < off + aligned; p += 8) {
			long data = shiftMix(load64(b, p) * MUL) * MUL;
			hash ^= data;
			hash *= MUL;
		}
		int rest = len & 7;
		if (rest != 0) {
			long data = 0;
			for (int n = rest - 1; n >= 0; n--) data = (data << 8) + (b[p + n] & 0xFF);
			hash ^= data;
			hash *= MUL;
		}
		hash = shiftMix(hash) * MUL;
		hash = shiftMix(hash);
		return hash;
	}

	/** BitConverter.ToUInt64: eight bytes, little-endian. */
	static long load64(byte[] b, int p) {
		return (b[p] & 0xFFL) | (b[p + 1] & 0xFFL) << 8 | (b[p + 2] & 0xFFL) << 16 | (b[p + 3] & 0xFFL) << 24
			| (b[p + 4] & 0xFFL) << 32 | (b[p + 5] & 0xFFL) << 40 | (b[p + 6] & 0xFFL) << 48 | (b[p + 7] & 0xFFL) << 56;
	}

	public static long Of_Str(Str s) { return HashBytes(s.b, 0, s.n, SEED); }
	public static long Of_i(int v) { return (long) v; }
	public static long Of_l(long v) { return v; }
	public static long Of_u(int v) { return Integer.toUnsignedLong(v); }
	public static long Of_ul(long v) { return v; }

	/** std::hash&lt;double&gt;: 0 for both zeros, otherwise _Hash_bytes of the eight bytes. */
	static long ofDouble(double d) {
		if (d == 0.0) return 0;
		long bits = Double.doubleToRawLongBits(d);
		byte[] b = new byte[8];
		for (int i = 0; i < 8; i++) b[i] = (byte) (bits >>> (8 * i));
		return HashBytes(b, 0, 8, SEED);
	}

	/** std::hash&lt;float&gt;: 0 for both zeros, otherwise _Hash_bytes of the four bytes. */
	static long ofFloat(float f) {
		if (f == 0.0f) return 0;
		int bits = Float.floatToRawIntBits(f);
		byte[] b = new byte[4];
		for (int i = 0; i < 4; i++) b[i] = (byte) (bits >>> (8 * i));
		return HashBytes(b, 0, 4, SEED);
	}

	/** std::hash of a key, by the key's run-time class (C#'s For&lt;K&gt; decides by typeof(K), which erased Java
	 *  generics do not have; for one key type the result is the same). A signed integer (int, long, short,
	 *  sbyte and the enums based on them) is its value sign-extended, a char zero-extended, a bool 0 or 1, a string
	 *  or floating-point value _Hash_bytes. Any other object hashes its identity: libstdc++ hashes the address,
	 *  which no other program reproduces (such containers are looked up, never iterated into the output). */
	static long hashObj(Object k) {
		if (k instanceof Str s) return Of_Str(s);
		if (k instanceof Integer x) return (long) (int) x;
		if (k instanceof Long x) return x;
		if (k instanceof Short x) return (long) (short) x;
		if (k instanceof Byte x) return (long) (byte) x;
		if (k instanceof Character x) return (long) (char) x;
		if (k instanceof Boolean x) return x ? 1L : 0L;
		if (k instanceof Double x) return ofDouble(x);
		if (k instanceof Float x) return ofFloat(x);
		if (k == null) return 0;
		return (long) System.identityHashCode(k);
	}

	/** Cpp.StdHash.For&lt;K&gt;(): std::hash&lt;K&gt; as a function (boxed result). */
	public static <K> Fn.F1<K, Long> For() { return k -> hashObj(k); }
}
