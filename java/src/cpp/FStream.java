// FStream.java - std::fstream as the C# port has it (C# Cpp.FStream, Streams.cs): the file opened, or created, for
// reading and writing (FileMode.OpenOrCreate, not truncated) and written from its start through a FileStream with
// the default 4 KiB buffer.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class FStream extends OStream implements IDisposable {
	public FStream(Str path, int mode) {
		try {
			java.nio.channels.FileChannel ch = FsNet.open(FsNet.s(path), FsNet.OPEN_OR_CREATE_RW);
			try { s = new FsNet.FileOut(ch, 4096, false); }
			catch (java.io.IOException e) { ch.close(); throw e; }
		} catch (Exception e) {
			failbit = true;
		}
	}
	public boolean is_open() { return s != null; }
	public void close() { if (s != null) { FsNet.FileOut f = s; s = null; f.close(); } }
	@Override public void Dispose() { close(); }
}
