// RFs.java - the C library functions of cs/runtime/Fs.cs (the part of the C# partial class Cpp.R in that file):
// errno, stderr and stdin, getenv, ctype in the C locale, atol / strtol0, ferror / fileno / setvbuf (no-ops as in the
// C# port), rename, fgetc, time(object), strncpy, strncmp, memcmp, sprintf and the char-array helpers of the C# back
// end. R (part C's class) extends RFs, so the engine's cpp.R.getenv, cpp.R.stderr_, ... resolve to these static
// members. Nothing here is also declared in R (File_Exists and toupper are R's).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class RFs {
	protected RFs() { }

	public static int errno_;
	public static CFile stderr_ = new CFile(new CFile.Stream(new FileOutputStream(FileDescriptor.err)), true);
	public static CFile stdin_ = new CFile(new CFile.Stream(new FileInputStream(FileDescriptor.in)), true);

	/** The Latin-1 string of a C string (R.Str, without depending on R's initialisation). */
	static String cstr(CPtr p) {
		byte[] b = p.B;
		int i = p.O;
		while (b[i] != 0) i++;
		return new String(b, p.O, i - p.O, StandardCharsets.ISO_8859_1);
	}

	/** Environment.GetEnvironmentVariable: a fresh copy of the value, or a null pointer. */
	public static CPtr getenv(CPtr name) {
		String v = System.getenv(cstr(name));
		return v == null ? CPtr.ZERO : R.Lit(v);
	}

	// ctype in the C locale
	public static int isdigit(int c) { return (c >= '0' && c <= '9') ? 1 : 0; }
	public static int isalpha(int c) { return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) ? 1 : 0; }
	public static int isalnum(int c) { return (isdigit(c) != 0 || isalpha(c) != 0) ? 1 : 0; }
	public static int isspace(int c) { return (c == ' ' || (c >= 9 && c <= 13)) ? 1 : 0; }
	public static int isupper(int c) { return (c >= 'A' && c <= 'Z') ? 1 : 0; }
	public static int islower(int c) { return (c >= 'a' && c <= 'z') ? 1 : 0; }
	public static int ispunct(int c) { return (c > 32 && c < 127 && isalnum(c) == 0) ? 1 : 0; }
	public static int isprint(int c) { return (c >= 32 && c < 127) ? 1 : 0; }
	public static int isxdigit(int c) { return (isdigit(c) != 0 || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) ? 1 : 0; }
	public static int tolower(int c) { return (c >= 'A' && c <= 'Z') ? c + 32 : c; }

	public static long atol(CPtr s) { return R.strtol(s, null, 10); }
	public static long strtol0(CPtr s, int b) { return R.strtol(s, null, b); }
	public static int ferror(CFile f) { return f != null && f.Err ? 1 : 0; }
	public static int fileno(CFile f) { return 3; }
	public static int setvbuf(CFile f, CPtr b, int mode, long size) { return 0; }

	/** File.Move(a, b, overwrite: true): 0, or -1 when it fails. */
	public static int rename(CPtr a, CPtr b) {
		try { FsNet.moveFile(cstr(a), cstr(b)); return 0; } catch (RuntimeException e) { return -1; }
	}

	public static int fgetc(CFile f) { int c = f.S.ReadByte(); if (c < 0) f.ReadFailed(); return c; }

	/** time(p) for a pointer argument the engine passes as an object (C#'s time(object?)); p is not written. */
	public static long time_o(Object p) { return Math.floorDiv(System.currentTimeMillis(), 1000L); }

	public static CPtr strncpy(CPtr d, CPtr s, long n) {
		int m = (int) n, i = 0;
		for (; i < m && s.B[s.O + i] != 0; i++) d.B[d.O + i] = s.B[s.O + i];
		for (; i < m; i++) d.B[d.O + i] = 0;
		return d;
	}

	public static int strncmp(CPtr a, CPtr b, long n) {
		int m = (int) n;
		for (int i = 0; i < m; i++) {
			int x = a.B[a.O + i] & 0xFF, y = b.B[b.O + i] & 0xFF;
			if (x != y) return x - y;
			if (x == 0) return 0;
		}
		return 0;
	}

	public static int memcmp(CPtr a, CPtr b, long n) {
		int m = (int) n;
		for (int i = 0; i < m; i++) { int x = a.B[a.O + i] & 0xFF, y = b.B[b.O + i] & 0xFF; if (x != y) return x - y; }
		return 0;
	}

	/** char buf[n] = { ... }: n bytes (at least one), the given ones first, the rest zero. */
	public static CPtr BufOf(int n, int[] vals) {
		byte[] b = new byte[Math.max(n, 1)];
		for (int i = 0; i < vals.length && i < b.length; i++) b[i] = (byte) vals[i];
		return new CPtr(b, 0);
	}

	/** Copy-assigning a char array member: all its bytes. */
	public static void memcpyBuf(CPtr d, CPtr s) {
		System.arraycopy(s.B, s.O, d.B, d.O, Math.min(d.B.length - d.O, s.B.length - s.O));
	}

	public static int sprintf(CPtr buf, CPtr fmt, Object... args) {
		byte[] b = R.Format(fmt, args);
		System.arraycopy(b, 0, buf.B, buf.O, b.length);
		buf.B[buf.O + b.length] = 0;
		return b.length;
	}
}
