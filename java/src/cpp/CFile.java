// CFile.java - C's FILE* (C# Cpp.CFile in cs/runtime/CRuntime.cs): a stream with C's end-of-file flag, and the
// registry of the files fopen opened, which C's exit() flushes (R.FlushAll).
// S is the C# Stream the C# runtime holds, with the members it calls (ReadByte, Read, Write, Flush, Seek,
// Position, Length, Dispose). A file is a RandomAccessFile with a 64 KiB write buffer and read buffer and its own
// position (the scheme of the SWMM5 Java port's CFile), which behaves as the C# FileStream + BufferedStream does:
// reads, writes and seeks interleave freely and the position is the logical one. stdout and stderr write through
// to their file descriptors at once, as C#'s Console streams do.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CFile {
	public final Stream S;
	public boolean Eof;
	public final boolean IsStd;
	/** A directory opened with fopen(dir, "r"): glibc opens it, and every read then fails with EISDIR, which sets
	 *  the error indicator, not end-of-file (C# Cpp.CFile.Dir). */
	public boolean Dir, Err;
	/** A read found no more data: end-of-file, or for a directory an error. */
	public void ReadFailed() { if (Dir) Err = true; else Eof = true; }

	/** The files fopen opened and fclose has not closed: exit() flushes them (R.FlushAll). */
	public static final Set<CFile> Open = new LinkedHashSet<>();

	public CFile(Stream s, boolean std) {
		S = s;
		IsStd = std;
		if (!std) synchronized (Open) { Open.add(this); }
	}
	public CFile(Stream s) { this(s, false); }

	/** The C# Stream under a CFile. Seek's whence is C's: 0 SEEK_SET, 1 SEEK_CUR, 2 SEEK_END. */
	public static final class Stream {
		static final int BUFSZ = 1 << 16;
		final RandomAccessFile raf;      // a file, or
		final OutputStream out;          // a standard output stream (written through), or
		final InputStream in;            // a standard input stream
		long pos;
		long floor;                      // append mode: no seeking before the length at open (.NET's FileMode.Append)
		byte[] wbuf; int wlen; long wpos;
		byte[] rbuf; long rstart; int rlen;

		public Stream(RandomAccessFile raf) { this.raf = raf; out = null; in = null; }
		public Stream(OutputStream out) { raf = null; this.out = out; in = null; }
		public Stream(InputStream in) { raf = null; out = null; this.in = in; }

		public void Flush() {
			try {
				if (out != null) { out.flush(); return; }
				if (wlen == 0 || raf == null) return;
				raf.seek(wpos);
				raf.write(wbuf, 0, wlen);
				wlen = 0;
			} catch (IOException e) { throw new UncheckedIOException(e); }
		}

		public void Write(byte[] src, int off, int n) {
			try {
				if (out != null) { out.write(src, off, n); pos += n; return; }
				if (raf == null) throw new UnsupportedOperationException("write to an input stream");
				rlen = 0;
				if (wbuf == null) wbuf = new byte[BUFSZ];
				if (wlen > 0 && pos != wpos + wlen) Flush();
				if (wlen == 0) wpos = pos;
				if (wlen + n > BUFSZ) {
					Flush();
					if (n > BUFSZ) {
						raf.seek(pos);
						raf.write(src, off, n);
						pos += n;
						return;
					}
					wpos = pos;
				}
				System.arraycopy(src, off, wbuf, wlen, n);
				wlen += n;
				pos += n;
			} catch (IOException e) { throw new UncheckedIOException(e); }
		}

		/** Up to n bytes into dst at off; the number read, 0 at the end. */
		public int Read(byte[] dst, int off, int n) {
			try {
				if (in != null) {
					int got = 0;
					while (got < n) { int r = in.read(dst, off + got, n - got); if (r <= 0) break; got += r; }
					pos += got;
					return got;
				}
				if (raf == null) throw new UnsupportedOperationException("read from an output stream");
				Flush();
				int got = 0;
				while (got < n) {
					if (rlen > 0 && pos >= rstart && pos < rstart + rlen) {
						int a = (int) (pos - rstart);
						int m = Math.min(n - got, rlen - a);
						System.arraycopy(rbuf, a, dst, off + got, m);
						got += m;
						pos += m;
						continue;
					}
					if (rbuf == null) rbuf = new byte[BUFSZ];
					rstart = pos;
					raf.seek(pos);
					int r = raf.read(rbuf, 0, BUFSZ);
					rlen = r < 0 ? 0 : r;
					if (rlen == 0) break;
				}
				return got;
			} catch (IOException e) { throw new UncheckedIOException(e); }
		}

		/** The next byte (0..255), or -1 at the end. */
		public int ReadByte() {
			if (raf != null && wlen == 0 && rlen > 0 && pos >= rstart && pos < rstart + rlen) return rbuf[(int) (pos++ - rstart)] & 255;
			byte[] t = new byte[1];
			return Read(t, 0, 1) == 1 ? t[0] & 255 : -1;
		}

		public long Length() {
			if (raf == null) throw new UnsupportedOperationException("length of a standard stream");
			Flush();
			try { return raf.length(); } catch (IOException e) { throw new UncheckedIOException(e); }
		}

		/** The new position; an IOException for a negative one (as .NET's FileStream.Seek throws). */
		public long Seek(long off, int whence) throws IOException {
			if (raf == null) throw new IOException("seek on a standard stream");
			Flush();
			long p = whence == 0 ? off : whence == 1 ? pos + off : Length() + off;
			if (p < floor) throw new IOException(p < 0 ? "negative position" : "seek before the start of an append");
			pos = p;
			return p;
		}

		public long Position() { return pos; }

		public void Dispose() {
			try {
				Flush();
				if (raf != null) raf.close();
			} catch (IOException e) { throw new UncheckedIOException(e); }
		}
	}
}
