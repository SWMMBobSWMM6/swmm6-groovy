// OStream.java - std::ostream with libstdc++'s formatting (C# Cpp.OStream, Streams.cs): a double is written with the
// printf format libstdc++'s num_put builds from the stream flags (%.*g by default, %.*f for fixed, %.*e for
// scientific, %a for fixed|scientific, '#' for showpoint) through the glibc-exact formatter (R.Format); width and
// fill are applied afterwards and width resets. The C# stream writes to a Stream: a FileStream (ofstream, fstream),
// a MemoryStream (ostringstream) or a console stream (cout, cerr); here those are FsNet.FileOut, FsNet.MemStream
// and the C stdio files R.stdout_ / R.stderr_, each write flushed, so stream and printf output keep their order as
// .NET's unbuffered console streams keep it.
// Overloaded C# methods carry the translator's parameter suffix (Put(double) is Put_d, Put(sbyte) is Put_c).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class OStream extends IosBase {
	/** The file this stream writes (the C# s, when it is a FileStream). */
	protected FsNet.FileOut s;
	/** The in-memory bytes (the C# mem). */
	protected FsNet.MemStream mem;
	/** 1 = standard output, 2 = standard error (the C# s, when it is a console stream). */
	protected int std;

	public OStream() { }

	/** cout (1) and cerr (2). */
	OStream(int std) { this.std = std; }

	CFile stdFile() { return std == 1 ? R.stdout_ : R.stderr_; }

	/** (s ?? mem)?.Write(b, off, n). */
	protected void Raw(byte[] b, int off, int n) {
		if (s != null) s.write(b, off, n);
		else if (std != 0) {
			if (n <= 0) return;
			CFile f = stdFile();
			f.S.Write(b, off, n);
			f.S.Flush();
		}
		else if (mem != null) mem.write(b, off, n);
	}

	/** A formatted field: the fill before it (after it when left-adjusted) up to the width, which resets. */
	void Pad(byte[] body, int off, int n) {
		long w = widthv;
		widthv = 0;
		if (n >= w) { Raw(body, off, n); return; }
		byte[] pad = new byte[(int) (w - n)];
		java.util.Arrays.fill(pad, (byte) fillc);
		if ((flagsv & Ios.left) != 0) { Raw(body, off, n); Raw(pad, 0, pad.length); }
		else { Raw(pad, 0, pad.length); Raw(body, off, n); }
	}
	void Pad(byte[] body) { Pad(body, 0, body.length); }

	static byte[] B(String x) { return x.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1); }

	/** strlen. */
	static int clen(CPtr p) {
		byte[] b = p.B;
		int i = p.O;
		while (b[i] != 0) i++;
		return i - p.O;
	}

	public OStream Put_Str(Str x) { Pad(x.b, 0, x.n); return this; }
	public OStream Put_CPtr(CPtr x) { Pad(x.B, x.O, clen(x)); return this; }
	public OStream Put_c(byte c) { Pad(new byte[] { c }); return this; }
	public OStream Put_uc(byte c) { Pad(new byte[] { c }); return this; }
	public OStream Put_i(int v) { Pad(B(Integer.toString(v))); return this; }
	public OStream Put_l(long v) { Pad(B(Long.toString(v))); return this; }
	public OStream Put_u(int v) { Pad(B(Integer.toUnsignedString(v))); return this; }
	public OStream Put_ul(long v) { Pad(B(Long.toUnsignedString(v))); return this; }
	public OStream Put_s(short v) { return Put_i(v); }
	public OStream Put_b(boolean v) { Pad(B((flagsv & Ios.boolalpha) != 0 ? (v ? "true" : "false") : (v ? "1" : "0"))); return this; }
	public OStream Put_f(float v) { return Put_d(v); }

	// the printf formats of libstdc++'s __num_base::_S_format_float: [showpoint][g, f, e, a]
	static final CPtr[][] FLOAT_FMTS = {
		{ R.Lit("%.*g"), R.Lit("%.*f"), R.Lit("%.*e"), R.Lit("%a") },
		{ R.Lit("%#.*g"), R.Lit("%#.*f"), R.Lit("%#.*e"), R.Lit("%#a") },
	};

	/** operator&lt;&lt;(double): the conversion letter from the floatfield, '#' for showpoint, the precision. */
	public OStream Put_d(double v) {
		boolean fx = (flagsv & Ios.fixed) != 0, sci = (flagsv & Ios.scientific) != 0;
		CPtr[] fm = FLOAT_FMTS[(flagsv & Ios.showpoint) != 0 ? 1 : 0];
		byte[] body = fx && sci ? R.Format(fm[3], new Object[] { v })
			: R.Format(fm[fx ? 1 : sci ? 2 : 0], new Object[] { (int) prec, v });
		Pad(body);
		return this;
	}

	public OStream Put_SetPrec(SetPrec p) { prec = p.n; return this; }
	public OStream Put_SetW(SetW w) { widthv = w.n; return this; }
	public OStream Put_SetFill(SetFill f) { fillc = f.c; return this; }
	public OStream Put_OStream(OStream o) { return this; }
	public OStream Manip_endl() { Raw(new byte[] { 10 }, 0, 1); flush(); return this; }
	public OStream Manip_flush() { flush(); return this; }
	public OStream Manip_setprecision(long n) { prec = n; return this; }
	public OStream Manip_setw(long n) { widthv = n; return this; }
	public OStream Manip_setfill(int c) { fillc = c; return this; }

	/** std::quoted(s): s between double quotes, with every '"' and '\\' escaped by a backslash; the width applies
	 *  to the whole quoted string, as in libstdc++ (__ostream_insert of the built string). */
	public OStream Manip_quoted_Str(Str x) {
		byte[] b = new byte[2 * x.n + 2];
		int k = 0;
		b[k++] = '"';
		for (int i = 0; i < x.n; i++) {
			byte c = x.b[i];
			if (c == '"' || c == '\\') b[k++] = '\\';
			b[k++] = c;
		}
		b[k++] = '"';
		Pad(b, 0, k);
		return this;
	}
	public OStream Manip_quoted_CPtr(CPtr x) { return Manip_quoted_Str(new Str(x)); }
	public OStream Manip_fixed() { flagsv = (flagsv & ~(Ios.fixed | Ios.scientific)) | Ios.fixed; return this; }
	public OStream Manip_scientific() { flagsv = (flagsv & ~(Ios.fixed | Ios.scientific)) | Ios.scientific; return this; }
	public OStream Manip_defaultfloat() { flagsv &= ~(Ios.fixed | Ios.scientific); return this; }
	public OStream Manip_left() { flagsv = (flagsv & ~(Ios.left | Ios.right)) | Ios.left; return this; }
	public OStream Manip_right() { flagsv = (flagsv & ~(Ios.left | Ios.right)) | Ios.right; return this; }
	public OStream Manip_boolalpha() { flagsv |= Ios.boolalpha; return this; }
	public OStream Manip_noboolalpha() { flagsv &= ~Ios.boolalpha; return this; }
	public OStream Manip_showpoint() { flagsv |= Ios.showpoint; return this; }
	public OStream Manip_noshowpoint() { flagsv &= ~Ios.showpoint; return this; }

	/** write(p, n): n raw bytes. */
	public OStream write_CPtr_l(CPtr p, long n) { if (n != 0) Raw(p.B, p.O, (int) n); return this; }

	// write((const char*)p, n) of an array of numbers: its first n bytes (Mem.BytesOf)
	public OStream write_PtrT_l__B(PtrB p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }
	public OStream write_PtrT_l__Z(PtrZ p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }
	public OStream write_PtrT_l__S(PtrS p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }
	public OStream write_PtrT_l__I(PtrI p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }
	public OStream write_PtrT_l__L(PtrL p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }
	public OStream write_PtrT_l__F(PtrF p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }
	public OStream write_PtrT_l__D(PtrD p, long n) { byte[] b = PodView.of(p, (int) n); Raw(b, 0, b.length); return this; }

	/** s?.Flush(): a file's buffer reaches the file; the console streams write through at once. */
	public OStream flush() {
		if (s != null) s.flush();
		else if (std != 0) R.fflush(stdFile());
		return this;
	}

	/** The put position: the file's (with what is buffered), the MemoryStream's, 0 with neither. */
	public long tellp() {
		if (s != null) return s.position();
		if (std != 0) throw new SysException("NotSupportedException", "Stream does not support seeking.");
		return mem != null ? mem.pos : 0;
	}

	public OStream seekp(long pos, int dir) {
		if (s != null) s.seek(pos, dir);
		else if (std != 0) throw new SysException("NotSupportedException", "Stream does not support seeking.");
		else if (mem != null) mem.seek(pos, dir);
		return this;
	}
}
