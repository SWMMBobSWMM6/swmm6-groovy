// PodView.java - the bytes of the numbers a pointer points at, as the C# Mem.BytesOf / Mem.BytesInto give them
// (MemoryMarshal.AsBytes of the span from the pointer to the end of its array, sliced to n bytes): little-endian as
// x86, ArgumentOutOfRangeException when n bytes do not fit, and a write of a partial element changes only its low
// bytes. IStream.read and OStream.write of a Ptr<T> (read((char*)p, n), write((const char*)p, n)) use these.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class PodView {
	private PodView() { }

	static SysException outOfRange() {
		return new SysException("ArgumentOutOfRangeException", "Specified argument was out of the range of valid values.");
	}

	// No bytes at a null pointer are no bytes: libstdc++ passes the data() of an empty vector, which is nullptr
	// (Vec.data), with a length of 0 (os.write(v.data(), 0)); each of / into returns at once for n == 0.

	/** The element offset, after the checks of new Span(a, o, a.Length - o).Slice(0, n). */
	static int start(int alen, long o, int size, long n) {
		if (o < 0 || o > alen || n < 0 || n > (alen - o) * (long) size) throw outOfRange();
		return (int) o;
	}

	static ByteBuffer le(int n) { return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN); }

	// ---------------------------------------------------------------------------------------- bytes of the numbers

	static byte[] of(PtrB p, int n) { if (n == 0) return new byte[0]; int o = start(p.a.length, p.o, 1, n); return Arrays.copyOfRange(p.a, o, o + n); }

	static byte[] of(PtrZ p, int n) { if (n == 0) return new byte[0];
		int o = start(p.a.length, p.o, 1, n);
		byte[] r = new byte[n];
		for (int i = 0; i < n; i++) r[i] = p.a[o + i] ? (byte) 1 : (byte) 0;
		return r;
	}

	static byte[] of(PtrS p, int n) { if (n == 0) return new byte[0];
		int o = start(p.a.length, p.o, 2, n), k = (n + 1) / 2;
		ByteBuffer bb = le(k * 2);
		bb.asShortBuffer().put(p.a, o, k);
		return Arrays.copyOf(bb.array(), n);
	}

	static byte[] of(PtrI p, int n) { if (n == 0) return new byte[0];
		int o = start(p.a.length, p.o, 4, n), k = (n + 3) / 4;
		ByteBuffer bb = le(k * 4);
		bb.asIntBuffer().put(p.a, o, k);
		return Arrays.copyOf(bb.array(), n);
	}

	static byte[] of(PtrL p, int n) { if (n == 0) return new byte[0];
		int o = start(p.a.length, p.o, 8, n), k = (n + 7) / 8;
		ByteBuffer bb = le(k * 8);
		bb.asLongBuffer().put(p.a, o, k);
		return Arrays.copyOf(bb.array(), n);
	}

	static byte[] of(PtrF p, int n) { if (n == 0) return new byte[0];
		int o = start(p.a.length, p.o, 4, n), k = (n + 3) / 4;
		ByteBuffer bb = le(k * 4);
		for (int i = 0; i < k; i++) bb.putInt(i * 4, Float.floatToRawIntBits(p.a[o + i]));
		return Arrays.copyOf(bb.array(), n);
	}

	static byte[] of(PtrD p, int n) { if (n == 0) return new byte[0];
		int o = start(p.a.length, p.o, 8, n), k = (n + 7) / 8;
		ByteBuffer bb = le(k * 8);
		for (int i = 0; i < k; i++) bb.putLong(i * 8, Double.doubleToRawLongBits(p.a[o + i]));
		return Arrays.copyOf(bb.array(), n);
	}

	// ---------------------------------------------------------------------------------------- bytes into the numbers

	static void into(PtrB p, byte[] src, int off, int n) { if (n == 0) return; int o = start(p.a.length, p.o, 1, n); System.arraycopy(src, off, p.a, o, n); }

	static void into(PtrZ p, byte[] src, int off, int n) { if (n == 0) return;
		int o = start(p.a.length, p.o, 1, n);
		for (int i = 0; i < n; i++) p.a[o + i] = src[off + i] != 0;
	}

	static void into(PtrS p, byte[] src, int off, int n) { if (n == 0) return;
		int o = start(p.a.length, p.o, 2, n), k = (n + 1) / 2;
		if (k == 0) return;
		ByteBuffer bb = le(k * 2);
		bb.putShort((k - 1) * 2, p.a[o + k - 1]);
		bb.put(0, src, off, n);
		bb.asShortBuffer().get(p.a, o, k);
	}

	static void into(PtrI p, byte[] src, int off, int n) { if (n == 0) return;
		int o = start(p.a.length, p.o, 4, n), k = (n + 3) / 4;
		if (k == 0) return;
		ByteBuffer bb = le(k * 4);
		bb.putInt((k - 1) * 4, p.a[o + k - 1]);
		bb.put(0, src, off, n);
		bb.asIntBuffer().get(p.a, o, k);
	}

	static void into(PtrL p, byte[] src, int off, int n) { if (n == 0) return;
		int o = start(p.a.length, p.o, 8, n), k = (n + 7) / 8;
		if (k == 0) return;
		ByteBuffer bb = le(k * 8);
		bb.putLong((k - 1) * 8, p.a[o + k - 1]);
		bb.put(0, src, off, n);
		bb.asLongBuffer().get(p.a, o, k);
	}

	static void into(PtrF p, byte[] src, int off, int n) { if (n == 0) return;
		int o = start(p.a.length, p.o, 4, n), k = (n + 3) / 4;
		if (k == 0) return;
		ByteBuffer bb = le(k * 4);
		bb.putInt((k - 1) * 4, Float.floatToRawIntBits(p.a[o + k - 1]));
		bb.put(0, src, off, n);
		for (int i = 0; i < k; i++) p.a[o + i] = Float.intBitsToFloat(bb.getInt(i * 4));
	}

	static void into(PtrD p, byte[] src, int off, int n) { if (n == 0) return;
		int o = start(p.a.length, p.o, 8, n), k = (n + 7) / 8;
		if (k == 0) return;
		ByteBuffer bb = le(k * 8);
		bb.putLong((k - 1) * 8, Double.doubleToRawLongBits(p.a[o + k - 1]));
		bb.put(0, src, off, n);
		for (int i = 0; i < k; i++) p.a[o + i] = Double.longBitsToDouble(bb.getLong(i * 8));
	}
}
