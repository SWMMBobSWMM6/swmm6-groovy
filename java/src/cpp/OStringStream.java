// OStringStream.java - std::ostringstream over a MemoryStream (C# Cpp.OStringStream, Streams.cs): str() is every byte
// written; ostringstream(init) starts with init's bytes and the put position at the start, so writes overwrite it.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class OStringStream extends OStream {
	public OStringStream() { mem = new FsNet.MemStream(); }
	public OStringStream(Str init) {
		mem = new FsNet.MemStream();
		mem.write(init.b, 0, init.n);
		mem.pos = 0;
	}
	public OStringStream(int mode) { this(); }

	/** str(): the whole MemoryStream. */
	public Str str_() { byte[] b = mem.toArray(); return FsNet.strOf(b, 0, b.length); }

	/** str(x): the contents replaced by x, the put position at its end. */
	public void str_Str(Str x) { mem = new FsNet.MemStream(); mem.write(x.b, 0, x.n); }

	/** The move assignment: the bytes, the precision and the flags (the source is left empty). */
	public void MoveFrom(OStringStream o) { mem = o.mem; prec = o.prec; flagsv = o.flagsv; o.mem = new FsNet.MemStream(); }
}
