// IosBase.java - the state and format part of std::ios_base (C# Cpp.IosBase, Streams.cs). Overloaded C# methods
// carry the translator's parameter suffix (precision() is precision_, precision(long) is precision_l).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class IosBase {
	public boolean failbit, eofbit, badbit;
	public long prec = 6;
	public int flagsv = 0;
	public long widthv = 0;
	public int fillc = ' ';

	public boolean good() { return !failbit && !eofbit && !badbit; }
	public boolean fail() { return failbit || badbit; }
	public boolean bad() { return badbit; }
	public boolean eof() { return eofbit; }
	public boolean ok() { return !fail(); }
	public boolean notok() { return fail(); }
	/** clear(state): as in the C# port, every flag is reset whatever the argument. */
	public void clear(int s) { failbit = eofbit = badbit = false; }
	public long precision_() { return prec; }
	public long precision_l(long p) { long o = prec; prec = p; return o; }
	public long width_() { return widthv; }
	public long width_l(long w) { long o = widthv; widthv = w; return o; }
	public int fill_() { return fillc; }
	public int fill_i(int c) { int o = fillc; fillc = c; return o; }
	public int setf_i(int f) { int o = flagsv; flagsv |= f; return o; }
	public int setf_i_i(int f, int mask) { int o = flagsv; flagsv = (flagsv & ~mask) | (f & mask); return o; }
	public void unsetf(int f) { flagsv &= ~f; }
	public Object rdbuf() { return null; }
	public void imbue(Object l) { }
	public void exceptions(int m) { }
}
