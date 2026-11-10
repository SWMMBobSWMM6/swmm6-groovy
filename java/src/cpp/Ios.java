// Ios.java - the libstdc++ values of ios_base::openmode, seekdir, fmtflags and iostate (C# Cpp.Ios, Streams.cs).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Ios {
	private Ios() { }
	public static final int in = 8, out = 16, binary = 4, app = 1, trunc = 32, ate = 2;
	public static final int beg = 0, cur = 1, end = 2;
	public static final int boolalpha = 1, dec = 2, fixed = 4, hex = 8, internal = 16, left = 32, oct = 64, right = 128,
		scientific = 256, showbase = 512, showpoint = 1024, showpos = 2048, skipws = 4096, unitbuf = 8192,
		uppercase = 16384, adjustfield = 176, basefield = 74, floatfield = 260;
	public static final int goodbit = 0, badbit = 1, eofbit = 2, failbit = 4;
}
