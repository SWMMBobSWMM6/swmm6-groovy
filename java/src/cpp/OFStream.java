// OFStream.java - std::ofstream over a FileStream with a 64 KiB buffer (C# Cpp.OFStream, Streams.cs): ios::app
// appends (FileMode.Append), any other mode creates or truncates (FileMode.Create); a file that cannot be opened
// leaves the stream closed with failbit set. Dispose is the destructor, which closes the file; a stream that is
// never closed keeps its buffered bytes, as the C# FileStream does at exit.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class OFStream extends OStream implements IDisposable {
	public OFStream() { }
	public OFStream(Str path, int mode) { open_Str_i(path, mode); }
	public OFStream(CPtr path, int mode) { open_Str_i(new Str(path), mode); }
	public OFStream(FsPath path, int mode) { open_Str_i(path.p, mode); }

	public void open_Str_i(Str path, int mode) {
		if (s != null) { FsNet.FileOut old = s; s = null; old.close(); }   // the C# port drops the old FileStream
		try {
			boolean app = (mode & Ios.app) != 0;
			java.nio.channels.FileChannel ch = FsNet.open(FsNet.s(path), app ? FsNet.APPEND : FsNet.CREATE);
			try { s = new FsNet.FileOut(ch, 1 << 16, app); }
			catch (java.io.IOException e) { ch.close(); throw e; }
			failbit = false;
		} catch (Exception e) {
			s = null;
			failbit = true;
		}
	}
	public void open_FsPath_i(FsPath path, int mode) { open_Str_i(path.p, mode); }

	public boolean is_open() { return s != null; }
	public void close() { if (s != null) { FsNet.FileOut f = s; s = null; f.close(); } }
	/** ~basic_ofstream closes the file. */
	@Override public void Dispose() { close(); }
	public void MoveFrom(OFStream o) { s = o.s; o.s = null; }
}
