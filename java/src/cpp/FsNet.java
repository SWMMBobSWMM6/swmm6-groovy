// FsNet.java - the parts of .NET's System.IO that cs/runtime/Streams.cs and Fs.cs rely on, reproduced for the Java
// port of those files: Path.GetFullPath on Unix (lexical: "." and ".." and doubled separators removed against the
// current directory, symbolic links left alone), File.Exists / Directory.Exists (stat, then lstat for a broken
// link; a full path ending in '/' is never a file), File.Move / File.Copy / File.Delete / Directory.Delete /
// Directory.CreateDirectory, a FileStream's write buffer (BufferedFileStreamStrategy: fill the buffer, flush it,
// write a request of a whole buffer or more directly) and position, and a MemoryStream. Every path is made full
// first, as .NET does, so "a/../b" names "b" even when "a" does not exist.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

final class FsNet {
	private FsNet() { }

	static final String SEEK_BEFORE_BEGIN = "An attempt was made to move the position before the beginning of the stream.";
	static final String SEEK_APPEND = "Unable seek backward to overwrite data that previously existed in a file opened in Append mode.";

	static SysException io(String msg) { return new SysException("IO.IOException", msg); }
	static SysException io(IOException e) { return new SysException("IO.IOException", String.valueOf(e.getMessage())); }

	/** The Latin-1 string of a Str's bytes (the C# Str.ToString()). */
	static String s(Str x) { return new String(x.b, 0, x.n, java.nio.charset.StandardCharsets.ISO_8859_1); }

	/** A Str of the Latin-1 bytes of a string (the C# new Str(string)). */
	static Str str(String x) {
		byte[] e = x.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
		return strOf(e, 0, e.length);
	}

	/** A Str of n bytes of src (the C# new Str(byte[], off, n)). */
	static Str strOf(byte[] src, int off, int n) {
		Str r = new Str();
		if (n > 0) r.append_CPtr_ul(new CPtr(src, off), n);
		return r;
	}

	// ------------------------------------------------------------------------------------------- Path.GetFullPath

	/** Directory.GetCurrentDirectory: getcwd(3) (the JVM's starting directory; the engine never changes it). */
	static String cwd() { return System.getProperty("user.dir"); }

	/** Path.GetFullPath on Unix: ArgumentException for an empty path or a NUL; a relative path is put under the
	 *  current directory (Path.Combine), then PathInternal.RemoveRelativeSegments collapses it. */
	static String fullPath(String path) {
		if (path.isEmpty()) throw new SysException("ArgumentException", "The value cannot be an empty string. (Parameter 'path')");
		if (path.indexOf('\0') >= 0) throw new SysException("ArgumentException", "Null character in path. (Parameter 'path')");
		if (path.charAt(0) != '/') {
			String c = cwd();
			path = c.endsWith("/") ? c + path : c + "/" + path;
		}
		String r = removeRelativeSegments(path, 1);
		return r.isEmpty() ? "/" : r;
	}

	/** PathInternal.RemoveRelativeSegments (dotnet/runtime) for '/' separators. */
	static String removeRelativeSegments(String path, int rootLength) {
		int skip = rootLength;
		if (path.charAt(skip - 1) == '/') skip--;
		StringBuilder sb = new StringBuilder(path.length());
		if (skip > 0) sb.append(path, 0, skip);
		int len = path.length();
		for (int i = skip; i < len; i++) {
			char c = path.charAt(i);
			if (c == '/' && i + 1 < len) {
				if (path.charAt(i + 1) == '/') continue;
				if ((i + 2 == len || path.charAt(i + 2) == '/') && path.charAt(i + 1) == '.') { i++; continue; }
				if (i + 2 < len && (i + 3 == len || path.charAt(i + 3) == '/') && path.charAt(i + 1) == '.' && path.charAt(i + 2) == '.') {
					int s;
					for (s = sb.length() - 1; s >= skip; s--) {
						if (sb.charAt(s) == '/') {
							sb.setLength((i + 3 >= len && s == skip) ? s + 1 : s);
							break;
						}
					}
					if (s < skip) sb.setLength(skip);
					i += 2;
					continue;
				}
			}
			sb.append(c);
		}
		if (skip != rootLength && sb.length() < rootLength) sb.append(path.charAt(rootLength - 1));
		return sb.toString();
	}

	// ------------------------------------------------------------------------------------------- existence

	/** stat(2), or lstat(2) when stat fails (a broken link): null when nothing is there. */
	static BasicFileAttributes stat(String full) {
		Path p;
		try { p = Path.of(full); } catch (InvalidPathException e) { return null; }
		try { return Files.readAttributes(p, BasicFileAttributes.class); }
		catch (IOException | SecurityException e) {
			try { return Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
			catch (IOException | SecurityException e2) { return null; }
		}
	}

	/** FileSystem.FileExists of a full path: something that is not a directory (a path ending in '/' names a
	 *  directory or nothing, as stat sees it; Java's Path would drop the '/'). */
	static boolean fsFileExists(String full) {
		if (full.length() > 1 && full.endsWith("/")) return false;
		BasicFileAttributes a = stat(full);
		return a != null && !a.isDirectory();
	}

	static boolean fsDirExists(String full) {
		BasicFileAttributes a = stat(full);
		return a != null && a.isDirectory();
	}

	/** System.IO.File.Exists. */
	static boolean fileExists(String path) {
		if (path == null || path.isEmpty()) return false;
		String f;
		try { f = fullPath(path); } catch (SysException e) { return false; }
		if (f.endsWith("/")) return false;
		return fsFileExists(f);
	}

	/** System.IO.Directory.Exists. */
	static boolean dirExists(String path) {
		if (path == null || path.isEmpty()) return false;
		String f;
		try { f = fullPath(path); } catch (SysException e) { return false; }
		return fsDirExists(f);
	}

	// ------------------------------------------------------------------------------------------- file operations

	static Path jpath(String full) throws IOException {
		try { return Path.of(full); } catch (InvalidPathException e) { throw new IOException(e.getMessage()); }
	}

	/** File.Move(a, b, overwrite: true): only a file moves; rename(2), or copy and delete across file systems.
	 *  Throws SysException: FileNotFoundException for a missing source (or a missing name in an existing directory),
	 *  DirectoryNotFoundException for a missing destination directory, IOException otherwise. */
	static void moveFile(String a, String b) {
		String fa = fullPath(a), fb = fullPath(b);
		if (!fsFileExists(fa)) throw new SysException("IO.FileNotFoundException", "Could not find file '" + fa + "'.");
		try { moveFull(fa, fb); }
		catch (NoSuchFileException e) {
			if (!parentExists(fb)) throw new SysException("IO.DirectoryNotFoundException", "Could not find a part of the path '" + fb + "'.");
			throw new SysException("IO.FileNotFoundException", "Could not find file '" + fb + "'.");
		} catch (IOException e) {
			throw io(e);
		}
	}

	/** FileSystem.MoveFile(fa, fb, overwrite: true) of two full paths: rename(2), or copy and delete across file
	 *  systems (EXDEV). */
	static void moveFull(String fa, String fb) throws IOException {
		Path pa = jpath(fa), pb = jpath(fb);
		try { Files.move(pa, pb, StandardCopyOption.ATOMIC_MOVE); }
		catch (AtomicMoveNotSupportedException e) {
			Files.copy(pa, pb, StandardCopyOption.REPLACE_EXISTING);
			Files.delete(pa);
		}
	}

	/** Whether the directory of a full path exists (.NET tells a missing directory from a missing file). */
	static boolean parentExists(String full) {
		int k = full.lastIndexOf('/');
		return fsDirExists(k <= 0 ? "/" : full.substring(0, k));
	}

	/** File.Copy(a, b) without overwrite: the bytes and the permission bits. */
	static void copyFile(String a, String b) {
		String fa = fullPath(a), fb = fullPath(b);
		try {
			Path pa = jpath(fa);
			if (Files.isDirectory(pa)) throw new SysException("UnauthorizedAccessException", "Access to the path '" + fa + "' is denied.");
			Files.copy(pa, jpath(fb));
		} catch (IOException e) {
			throw io(e);
		}
	}

	/** File.Delete of a full path known to be a file (or a link). */
	static void deleteFile(String full) {
		try { Files.deleteIfExists(jpath(full)); } catch (IOException e) { throw io(e); }
	}

	/** Directory.Delete(path) (not recursive): rmdir(2). */
	static void deleteDir(String full) {
		try { Files.delete(jpath(full)); } catch (IOException e) { throw io(e); }
	}

	/** Directory.CreateDirectory: the directory and its missing parents; an existing directory is fine. */
	static void createDirectories(String path) {
		String f = fullPath(path);
		try { Files.createDirectories(jpath(f)); } catch (IOException e) { throw io(e); }
	}

	/** new FileInfo(path).Length: FileNotFoundException for a missing path or a directory. */
	static long fileLength(String path) {
		String f = fullPath(path);
		BasicFileAttributes a = f.length() > 1 && f.endsWith("/") ? null : stat(f);
		if (a == null || a.isDirectory()) throw new SysException("IO.FileNotFoundException", "Could not find file '" + f + "'.");
		return a.size();
	}

	/** The FileStream constructor's open: the full path, a directory refused (UnauthorizedAccessException in .NET). */
	static FileChannel open(String path, OpenOption... opts) throws IOException {
		String full = fullPath(path);
		if (full.length() > 1 && full.endsWith("/")) throw new IOException("Is a directory");
		Path p = jpath(full);
		if (Files.isDirectory(p)) throw new IOException("Is a directory");
		return FileChannel.open(p, opts);
	}

	static final OpenOption[] READ = { StandardOpenOption.READ };
	static final OpenOption[] CREATE = { StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING };
	static final OpenOption[] APPEND = { StandardOpenOption.WRITE, StandardOpenOption.CREATE };
	static final OpenOption[] OPEN_OR_CREATE_RW = { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE };

	// ------------------------------------------------------------------------------------------- FileStream (write)

	/** A FileStream opened for writing, with its write buffer (BufferedFileStreamStrategy over the file). */
	static final class FileOut {
		final FileChannel ch;
		final byte[] buf;
		int wpos;           // bytes in buf
		long fpos;          // the file offset buf starts at (OSFileStreamStrategy._filePosition)
		final long appendStart;

		FileOut(FileChannel ch, int bufferSize, boolean append) throws IOException {
			this.ch = ch;
			this.buf = new byte[bufferSize];
			if (append) { fpos = ch.size(); ch.position(fpos); appendStart = fpos; }
			else appendStart = -1;
		}

		void raw(byte[] b, int off, int n) {
			ByteBuffer bb = ByteBuffer.wrap(b, off, n);
			try { while (bb.hasRemaining()) ch.write(bb); } catch (IOException e) { throw io(e); }
			fpos += n;
		}

		void flushWrite() {
			if (wpos > 0) { int k = wpos; wpos = 0; raw(buf, 0, k); }
		}

		/** BufferedFileStreamStrategy.WriteSpan. */
		void write(byte[] b, int off, int n) {
			int size = buf.length;
			if (wpos > 0) {
				int room = size - wpos;
				if (room > 0) {
					if (room >= n) { System.arraycopy(b, off, buf, wpos, n); wpos += n; return; }
					System.arraycopy(b, off, buf, wpos, room);
					wpos += room;
					off += room;
					n -= room;
				}
				flushWrite();
			}
			if (n >= size) { raw(b, off, n); return; }
			if (n == 0) return;
			System.arraycopy(b, off, buf, 0, n);
			wpos = n;
		}

		long position() { return fpos + wpos; }

		long length() {
			long len;
			try { len = ch.size(); } catch (IOException e) { throw io(e); }
			return Math.max(len, fpos + wpos);
		}

		/** FileStream.Seek: the buffer flushed, then the new position (IOException before 0 or, in append mode,
		 *  before the end the file had when it was opened). */
		long seek(long off, int dir) {
			flushWrite();
			long np = dir == Ios.beg ? off : dir == Ios.end ? length() + off : fpos + off;
			if (np < 0) throw io(SEEK_BEFORE_BEGIN);
			if (appendStart != -1 && np < appendStart) throw io(SEEK_APPEND);
			fpos = np;
			try { ch.position(np); } catch (IOException e) { throw io(e); }
			return np;
		}

		void flush() { flushWrite(); }

		void close() {
			try { flushWrite(); }
			finally { try { ch.close(); } catch (IOException e) { /* the descriptor is gone either way */ } }
		}
	}

	// ------------------------------------------------------------------------------------------- MemoryStream

	/** A MemoryStream: the bytes, their length and the position (writes overwrite, extend, and zero-fill a gap). */
	static final class MemStream {
		byte[] b = new byte[0];
		int len, pos;

		void write(byte[] src, int off, int n) {
			if (n <= 0) return;
			int end = pos + n;
			if (end > len) {
				if (end > b.length) b = Arrays.copyOf(b, Math.max(Math.max(256, end), b.length * 2));
				if (pos > len) Arrays.fill(b, len, pos, (byte) 0);
				len = end;
			}
			System.arraycopy(src, off, b, pos, n);
			pos = end;
		}

		byte[] toArray() { return Arrays.copyOf(b, len); }

		long seek(long off, int dir) {
			long np = dir == Ios.beg ? off : dir == Ios.cur ? pos + off : len + off;
			if (np < 0) throw io(SEEK_BEFORE_BEGIN);
			if (np > Integer.MAX_VALUE) throw new SysException("ArgumentOutOfRangeException", "Stream length must be non-negative and less than 2^31 - 1 - origin. (Parameter 'offset')");
			pos = (int) np;
			return np;
		}
	}
}
