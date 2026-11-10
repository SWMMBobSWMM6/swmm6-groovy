// Fs.java - the std::filesystem functions the engine calls (C# Cpp.Fs, Fs.cs), over .NET's file system calls as the
// C# port makes them (FsNet reproduces those): canonical and weakly_canonical are Path.GetFullPath (lexical, links
// left alone), equivalent compares the two full paths, the error-code overloads set ENOENT (2), EACCES (13),
// EEXIST (17) or EISDIR (21) as the C# port does, and the plain overloads let .NET's exceptions through (as
// SysException). Overloaded C# methods carry the translator's parameter suffix (exists(FsPath, ErrorCode) is
// exists_FsPath_ErrorCode).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public final class Fs {
	private Fs() { }

	public static boolean exists_FsPath(FsPath p) { String s = p.toString(); return FsNet.fileExists(s) || FsNet.dirExists(s); }
	public static boolean exists_FsPath_ErrorCode(FsPath p, ErrorCode ec) { return exists_FsPath(p); }
	public static boolean is_regular_file_FsPath(FsPath p) { return FsNet.fileExists(p.toString()); }
	public static boolean is_directory_FsPath(FsPath p) { return FsNet.dirExists(p.toString()); }
	/** new FileInfo(p).Length: a missing file or a directory throws (FileNotFoundException). */
	public static long file_size_FsPath(FsPath p) { return FsNet.fileLength(p.toString()); }

	/** file_size(p, ec): libstdc++ sets ENOENT for a missing file, EISDIR for a directory and returns
	 *  static_cast&lt;uintmax_t&gt;(-1) on error. */
	public static long file_size_FsPath_ErrorCode(FsPath p, ErrorCode ec) {
		String s = p.toString();
		if (FsNet.fileExists(s)) { ec.v = 0; return FsNet.fileLength(s); }
		ec.v = FsNet.dirExists(s) ? 21 : 2;
		return -1L;
	}

	/** Removes a file (not a directory); reports whether one was removed. */
	public static boolean remove_FsPath(FsPath p) {
		String s = p.toString();
		try { if (FsNet.fileExists(s)) { FsNet.deleteFile(FsNet.fullPath(s)); return true; } } catch (RuntimeException e) { }
		return false;
	}

	/** Directory.CreateDirectory: true unless it fails (an existing directory is fine). */
	public static boolean create_directories_FsPath(FsPath p) {
		try { FsNet.createDirectories(p.toString()); return true; } catch (RuntimeException e) { return false; }
	}

	public static FsPath current_path() { return new FsPath(FsNet.cwd()); }
	public static FsPath absolute(FsPath p) { return p.is_absolute() ? p.Clone() : current_path().Join_FsPath(p); }
	public static FsPath canonical_FsPath(FsPath p) { return new FsPath(FsNet.fullPath(p.toString())); }
	public static FsPath weakly_canonical_FsPath(FsPath p) { return new FsPath(FsNet.fullPath(p.toString())); }

	/** $TMPDIR, or /tmp when it is not set. */
	public static FsPath temp_directory_path() {
		String t = System.getenv("TMPDIR");
		return new FsPath(t != null ? t : "/tmp");
	}

	/** File.Move(a, b, true), which throws when it fails. */
	public static void rename_FsPath_FsPath(FsPath a, FsPath b) { FsNet.moveFile(a.toString(), b.toString()); }

	public static boolean equivalent_FsPath_FsPath(FsPath a, FsPath b) {
		return FsNet.fullPath(a.toString()).equals(FsNet.fullPath(b.toString()));
	}
	public static boolean equivalent_FsPath_FsPath_ErrorCode(FsPath a, FsPath b, ErrorCode ec) {
		if (!exists_FsPath(a) || !exists_FsPath(b)) { ec.v = 2; return false; }
		ec.v = 0;
		return equivalent_FsPath_FsPath(a, b);
	}

	/** remove(p, ec): a file or an empty directory (EACCES = 13 when that fails). */
	public static boolean remove_FsPath_ErrorCode(FsPath p, ErrorCode ec) {
		ec.v = 0;
		String s = p.toString();
		try {
			if (FsNet.fileExists(s)) { FsNet.deleteFile(FsNet.fullPath(s)); return true; }
			if (FsNet.dirExists(s)) { FsNet.deleteDir(FsNet.fullPath(s)); return true; }
		} catch (RuntimeException e) { ec.v = 13; }
		return false;
	}

	/** rename(a, b, ec): File.Move(a, b, true); FileNotFoundException gives ENOENT (2), anything else EACCES (13). */
	public static void rename_FsPath_FsPath_ErrorCode(FsPath a, FsPath b, ErrorCode ec) {
		String fa, fb;
		try { fa = FsNet.fullPath(a.toString()); fb = FsNet.fullPath(b.toString()); }
		catch (RuntimeException e) { ec.v = 13; return; }
		if (!FsNet.fsFileExists(fa)) { ec.v = 2; return; }
		try { FsNet.moveFull(fa, fb); ec.v = 0; }
		catch (NoSuchFileException e) { ec.v = FsNet.parentExists(fb) ? 2 : 13; }
		catch (IOException | RuntimeException e) { ec.v = 13; }
	}

	/** The C# port's create_hard_link(target, link, ec): EEXIST (17) when link exists, otherwise a copy of target
	 *  at link (ENOENT when that fails). */
	public static void create_hard_link(FsPath target, FsPath link, ErrorCode ec) {
		if (FsNet.fileExists(link.toString())) { ec.v = 17; return; }
		try { FsNet.copyFile(target.toString(), link.toString()); ec.v = 0; } catch (RuntimeException e) { ec.v = 2; }
	}

	public static FsPath weakly_canonical_FsPath_ErrorCode(FsPath p, ErrorCode ec) { ec.v = 0; return weakly_canonical_FsPath(p); }
	public static FsPath canonical_FsPath_ErrorCode(FsPath p, ErrorCode ec) {
		if (!exists_FsPath(p)) { ec.v = 2; return new FsPath(); }
		ec.v = 0;
		return canonical_FsPath(p);
	}
	public static FileStatus status_FsPath(FsPath p) { return FileStatus.Of(p.toString()); }
	public static FileStatus status_FsPath_ErrorCode(FsPath p, ErrorCode ec) { ec.v = 0; return FileStatus.Of(p.toString()); }
	public static FileStatus symlink_status_FsPath(FsPath p) { return FileStatus.Of(p.toString()); }
	public static FileStatus symlink_status_FsPath_ErrorCode(FsPath p, ErrorCode ec) { ec.v = 0; return FileStatus.Of(p.toString()); }
	public static boolean exists_FileStatus(FileStatus s) { return s.kind != FileStatus.NotFound; }
	public static boolean is_regular_file_FileStatus(FileStatus s) { return s.kind == FileStatus.Regular; }
	public static boolean is_directory_FileStatus(FileStatus s) { return s.kind == FileStatus.Directory; }
	public static boolean is_symlink(FileStatus s) { return false; }
	public static boolean create_directories_FsPath_ErrorCode(FsPath p, ErrorCode ec) { ec.v = 0; return create_directories_FsPath(p); }
}
