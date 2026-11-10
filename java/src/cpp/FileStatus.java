// FileStatus.java - std::filesystem::file_status as far as the engine asks (C# Cpp.FileStatus, Fs.cs): not found, a
// regular file or a directory (File.Exists, then Directory.Exists, as .NET sees them: links followed).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class FileStatus {
	public static final int NotFound = -1, Regular = 1, Directory = 2, Other = 3;
	public int kind;

	public FileStatus() { }
	public static FileStatus Of(String s) {
		FileStatus r = new FileStatus();
		r.kind = FsNet.fileExists(s) ? Regular : FsNet.dirExists(s) ? Directory : NotFound;
		return r;
	}
	public int type() { return kind; }
	public FileStatus Clone() { FileStatus r = new FileStatus(); r.kind = kind; return r; }
}
