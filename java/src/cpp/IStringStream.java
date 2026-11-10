// IStringStream.java - std::istringstream (C# Cpp.IStringStream, Streams.cs): a copy of the string to read from.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class IStringStream extends IStream {
	public IStringStream() { }
	public IStringStream(Str s) { str_Str(s); }
	public IStringStream(Str s, int mode) { str_Str(s); }

	/** str(x): a copy of x to read from the start; fail and eof are cleared. */
	public void str_Str(Str s) { buf = java.util.Arrays.copyOf(s.b, s.n); pos = 0; len = buf.length; failbit = eofbit = false; }
	public Str str_() { return FsNet.strOf(buf, 0, len); }
	public void MoveFrom(IStringStream o) { buf = o.buf; pos = o.pos; len = o.len; }
}
