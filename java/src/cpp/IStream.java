// IStream.java - std::istream as the C# port has it (C# Cpp.IStream, Streams.cs): a buffer of bytes (the whole string
// of an istringstream, or the last block of up to 64 KiB read from an ifstream's file) and the read position in it.
// Formatted extraction reads one whitespace-delimited token and converts it with strtod / strtol as libstdc++'s
// num_get does for ordinary input (a token that does not convert whole sets failbit and stores 0; an int out of
// range stores the nearest limit). getline consumes the delimiter; at the end of the input it sets eof, and fail too
// when nothing at all was read. seekg clears eof; on a file, ios::cur is relative to the logical read position.
// Overloaded C# methods carry the translator's parameter suffix (Get(ref double) is Get_dr, get() is get_).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class IStream extends IosBase {
	static final byte[] EMPTY = new byte[0];

	protected byte[] buf = EMPTY;
	protected int pos, len;
	/** The file an ifstream reads (the C# src). */
	protected FileChannel src;
	/** The file offset just past the bytes read from src (FileStream.Position). */
	protected long srcPos;
	public long gcountv;
	/** An ifstream opened on a directory: libstdc++ opens it (open(2) of a directory for reading succeeds) and the
	 *  first read fails with EISDIR, which basic_filebuf::underflow throws as ios_base::failure and the extraction
	 *  catches: badbit and failbit, not eofbit (C# Cpp.IStream.dir). */
	protected boolean dir;

	protected boolean Fill() {
		if (pos < len) return true;
		if (dir) { badbit = true; failbit = true; return false; }
		if (src == null) return false;
		if (buf.length == 0) buf = new byte[1 << 16];
		int k;
		try { k = src.read(ByteBuffer.wrap(buf, 0, buf.length)); } catch (IOException e) { throw FsNet.io(e); }
		if (k < 0) k = 0;
		srcPos += k;
		len = k;
		pos = 0;
		return len > 0;
	}

	/** A read found no more input: the end of the input sets eofbit; a failed read (dir) set badbit instead. */
	protected void AtEnd() { if (!dir) eofbit = true; }
	public int peek() { if (!Fill()) { AtEnd(); return -1; } return buf[pos] & 0xFF; }
	public int get_() { if (!Fill()) { AtEnd(); failbit = true; return -1; } gcountv = 1; return buf[pos++] & 0xFF; }
	public IStream get_cr(RefB c) { int x = get_(); if (x >= 0) c.set((byte) x); return this; }
	public IStream ignore(long n, int delim) {
		for (long i = 0; i < n; i++) { int c = get_(); if (c < 0 || c == delim) break; }
		failbit = failbit && !eofbit;
		return this;
	}
	public long gcount() { return gcountv; }

	/** read(p, n): up to n bytes; fewer set eof and fail. gcount is the number read. */
	public IStream read_CPtr_l(CPtr p, long n) {
		long got = 0;
		while (got < n) {
			if (!Fill()) break;
			int k = (int) Math.min(n - got, len - pos);
			System.arraycopy(buf, pos, p.B, (int) (p.O + got), k);
			pos += k;
			got += k;
		}
		gcountv = got;
		if (got < n) { AtEnd(); failbit = true; }
		return this;
	}

	byte[] readTmp(long n) {
		byte[] tmp = new byte[(int) n];
		read_CPtr_l(new CPtr(tmp, 0), n);
		return tmp;
	}

	// read((char*)p, n) into an array of numbers: its first gcount bytes are replaced (Mem.BytesInto)
	public IStream read_PtrT_l__B(PtrB p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }
	public IStream read_PtrT_l__Z(PtrZ p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }
	public IStream read_PtrT_l__S(PtrS p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }
	public IStream read_PtrT_l__I(PtrI p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }
	public IStream read_PtrT_l__L(PtrL p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }
	public IStream read_PtrT_l__F(PtrF p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }
	public IStream read_PtrT_l__D(PtrD p, long n) { byte[] t = readTmp(n); PodView.into(p, t, 0, (int) gcountv); return this; }

	/** getline(s, line, delim): the bytes up to the delimiter (consumed, not stored). */
	public boolean ReadLine(Str s, int delim) {
		s.clear();
		boolean any = false;
		while (true) {
			if (!Fill()) { AtEnd(); if (!any) failbit = true; return any; }
			any = true;
			int k = pos;
			if (delim >= 0 && delim <= 255) {
				byte d = (byte) delim;
				while (k < len && buf[k] != d) k++;
			} else k = len;
			if (k > pos) s.append_CPtr_ul(new CPtr(buf, pos), k - pos);
			if (k < len) { pos = k + 1; return true; }
			pos = len;
		}
	}

	static boolean ws(byte c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == 11 || c == '\f'; }

	void SkipWs() { while (Fill() && ws(buf[pos])) pos++; }

	Str Token() {
		SkipWs();
		Str t = new Str();
		while (Fill() && !ws(buf[pos])) t.push_back(buf[pos++] & 0xFF);
		if (t.n == 0) { failbit = true; if (!Fill()) AtEnd(); }
		else if (!Fill()) AtEnd();
		return t;
	}

	public IStream Get_Str(Str s) { Str t = Token(); if (t.n > 0) s.Assign_Str(t); return this; }

	/** is &gt;&gt; std::ws: skip whitespace (eof is set if the input ends there, failbit is not). */
	public IStream Manip_ws() {
		while (true) {
			if (!Fill()) { AtEnd(); break; }
			int c = buf[pos] & 0xFF;
			if (c == ' ' || (c >= 9 && c <= 13)) pos++; else break;
		}
		return this;
	}

	/** is &gt;&gt; v for a double, as libstdc++ does it (C# Cpp.IStream.Get(ref double)): the sentry (a stream not
	 *  good extracts nothing; white space skipped; at the end of the input eof and fail, v unchanged); num_get's
	 *  _M_extract_float takes the characters a C-locale number can have (a sign, one of any leading zeros, digits,
	 *  one '.', and an 'e' after a digit with its sign), stopping at the first other one, which stays in the stream
	 *  ("0x10" is read as 0, then x10 is left); the gnu __convert_to_v converts them with strtod: not all of them
	 *  converted (nothing, "1e", "inf") is failbit with v = 0, an overflow (+-inf) is failbit with v = +-DBL_MAX,
	 *  an underflow is no error. eofbit when the number ends the input. */
	public IStream Get_dr(RefD v) {
		if (!good()) { failbit = true; return this; }
		SkipWs();
		if (!Fill()) { AtEnd(); failbit = true; return this; }
		byte[] acc = new byte[32];
		int na = 0;
		boolean more = true;
		int c = buf[pos] & 0xFF;
		boolean mantissa = false, dec = false, sci = false;
		boolean signed = c == '+' || c == '-';
		for (int stage = signed ? 0 : 1; ; ) {
			if (stage == 0) {                                  // the sign
				acc[na++] = (byte) c;
				stage = 1;
			} else if (stage == 1) {                           // leading zeros, one kept
				if (c != '0') { stage = 2; continue; }
				if (!mantissa) { acc[na++] = '0'; mantissa = true; }
			} else {
				if (c >= '0' && c <= '9') { acc[na++] = (byte) c; mantissa = true; }
				else if (c == '.' && !dec && !sci) { acc[na++] = '.'; dec = true; }
				else if ((c == 'e' || c == 'E') && !sci && mantissa) {
					acc[na++] = 'e';
					sci = true;
					pos++;
					more = Fill();
					if (!more) break;
					c = buf[pos] & 0xFF;
					if (c == '+' || c == '-') acc[na++] = (byte) c;
					else continue;                                 // look at this character again
				} else break;
			}
			if (na + 2 > acc.length) acc = java.util.Arrays.copyOf(acc, acc.length * 2);
			pos++;
			more = Fill();
			if (!more) break;
			c = buf[pos] & 0xFF;
		}
		if (!more) AtEnd();
		byte[] a = java.util.Arrays.copyOf(acc, na + 1);
		double r = R.scanDouble(a, 0, -1, false);
		int n = R.scanEndOf();
		if (n <= 0 || a[n] != 0) { v.set(0); failbit = true; }
		else if (r == Double.POSITIVE_INFINITY) { v.set(Double.MAX_VALUE); failbit = true; }
		else if (r == Double.NEGATIVE_INFINITY) { v.set(-Double.MAX_VALUE); failbit = true; }
		else v.set(r);
		return this;
	}
	public IStream Get_fr(RefF v) { RefD.Cell d = new RefD.Cell(0); Get_dr(d); v.set((float) d.v); return this; }
	public IStream Get_ir(RefI v) {
		Str t = Token();
		if (t.n == 0) { v.set(0); return this; }
		CPtr p = t.c_str();
		RefO.Cell<CPtr> end = new RefO.Cell<>(p);
		long r = R.strtol(p, end, 10);
		if (end.v.O != t.n || r < Integer.MIN_VALUE || r > Integer.MAX_VALUE) {
			failbit = true;
			v.set(r < Integer.MIN_VALUE ? Integer.MIN_VALUE : r > Integer.MAX_VALUE ? Integer.MAX_VALUE : 0);
			return this;
		}
		v.set((int) r);
		return this;
	}
	public IStream Get_lr(RefL v) {
		Str t = Token();
		if (t.n == 0) { v.set(0); return this; }
		CPtr p = t.c_str();
		RefO.Cell<CPtr> end = new RefO.Cell<>(p);
		long r = R.strtol(p, end, 10);
		if (end.v.O != t.n) { failbit = true; v.set(0); return this; }
		v.set(r);
		return this;
	}
	public IStream Get_ulr(RefL v) { RefL.Cell x = new RefL.Cell(0); Get_lr(x); v.set(x.v); return this; }
	public IStream Get_ur(RefI v) { RefL.Cell x = new RefL.Cell(0); Get_lr(x); v.set((int) x.v); return this; }
	public IStream Get_cr(RefB c) { SkipWs(); int x = get_(); if (x >= 0) c.set((byte) x); return this; }

	/** The read position: the file offset of the next byte, or the index in the string. */
	public long tellg() { return src != null ? srcPos - (len - pos) : pos; }

	public IStream seekg(long off, int dir) {
		eofbit = false;
		if (src != null) {
			// ios::cur is relative to the read position, not to the file's, which is past the buffered block
			long np;
			try { np = dir == Ios.beg ? off : dir == Ios.cur ? tellg() + off : src.size() + off; }
			catch (IOException e) { throw FsNet.io(e); }
			if (np < 0) { failbit = true; return this; }
			try { src.position(np); } catch (IOException e) { throw FsNet.io(e); }
			srcPos = np;
			pos = len = 0;
		}
		else pos = (int) (dir == Ios.beg ? off : dir == Ios.cur ? pos + off : len + off);
		return this;
	}
}
