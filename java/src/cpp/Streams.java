// Streams.java - std::cout, std::cerr, std::clog, std::getline and the std::setprecision / setw / setfill values
// (C# Cpp.Streams, Streams.cs). cout and cerr write through the C stdio files R.stdout_ and R.stderr_, each write
// flushed, as the C# port's .NET console streams are unbuffered: stream and printf output stay in order.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Streams {
	private Streams() { }
	public static final OStream cout = new OStream(1);
	public static final OStream cerr = new OStream(2);
	public static final OStream clog = cerr;
	public static IStream getline_IStream_Str(IStream s, Str line) { s.ReadLine(line, '\n'); return s; }
	public static IStream getline_IStream_Str_c(IStream s, Str line, byte delim) { s.ReadLine(line, delim & 0xFF); return s; }
	public static IStream getline_IStream_Str_i(IStream s, Str line, int delim) { s.ReadLine(line, delim & 0xFF); return s; }
	public static SetPrec setprecision(int n) { return new SetPrec(n); }
	public static SetW setw(int n) { return new SetW(n); }
	public static SetFill setfill(int c) { return new SetFill(c); }
}
