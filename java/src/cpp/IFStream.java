// IFStream.java - std::ifstream over a FileStream (C# Cpp.IFStream, Streams.cs): a missing file leaves the stream
// closed with failbit set; a directory opens, as libstdc++ opens it, and every read then fails (IStream.dir).
// Dispose is the destructor, which closes the file.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class IFStream extends IStream implements IDisposable {
	public IFStream() { }
	public IFStream(Str path, int mode) { open_Str_i(path, mode); }
	public IFStream(CPtr path, int mode) { open_Str_i(new Str(path), mode); }
	public IFStream(FsPath path, int mode) { open_Str_i(path.p, mode); }

	public void open_Str_i(Str path, int mode) {
		if (src != null) { try { src.close(); } catch (java.io.IOException e) { } src = null; }
		dir = false;
		try {
			if (FsNet.dirExists(FsNet.s(path))) {
				dir = true;
				srcPos = 0;
				failbit = false;
				pos = len = 0;
				return;
			}
			src = FsNet.open(FsNet.s(path), FsNet.READ);
			srcPos = 0;
			failbit = false;
			pos = len = 0;
		} catch (Exception e) {
			src = null;
			failbit = true;
		}
	}
	public void open_FsPath_i(FsPath path, int mode) { open_Str_i(path.p, mode); }
	public boolean is_open() { return src != null || dir; }
	public void close() { if (src != null) { try { src.close(); } catch (java.io.IOException e) { } src = null; } dir = false; }
	@Override public void Dispose() { close(); }
	public void MoveFrom(IFStream o) { src = o.src; srcPos = o.srcPos; buf = o.buf; pos = o.pos; len = o.len; dir = o.dir; o.src = null; o.dir = false; }
}
