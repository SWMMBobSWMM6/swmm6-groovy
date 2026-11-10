// FsPath.java - std::filesystem::path with POSIX paths, as libstdc++ on Linux (C# Cpp.FsPath, Fs.cs): p holds the
// path's bytes; the element functions work on the text as the C# port's do (filename is the part after the last
// '/', "" when the path ends in '/'; operator/ adds a separator unless the right side is absolute or empty).
// Overloaded C# methods carry the translator's parameter suffix (Join(FsPath) is Join_FsPath).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.util.ArrayList;

public final class FsPath implements ICopy<FsPath>, Comparable<FsPath> {
	public Str p;

	public FsPath() { p = new Str(); }
	public FsPath(Str s) { p = s.Clone(); }
	public FsPath(CPtr s) { p = new Str(s); }
	public FsPath(String s) { p = FsNet.str(s); }

	@Override public FsPath Clone() { return new FsPath(p); }
	public void clear() { p = new Str(); }
	@Override public void Assign(FsPath o) { Assign_FsPath(o); }
	public void Assign_FsPath(FsPath o) { p = o.p.Clone(); }
	public void Assign_Str(Str s) { p = s.Clone(); }
	public void MoveFrom(FsPath o) { p = o.p; o.p = new Str(); }

	/** path::compare as the C# port has it: the bytes, as std::string compares them (memcmp, then length). */
	static int cmp(Str a, Str b) {
		int m = Math.min(a.n, b.n);
		for (int i = 0; i < m; i++) if (a.b[i] != b.b[i]) return (a.b[i] & 0xFF) < (b.b[i] & 0xFF) ? -1 : 1;
		return a.n - b.n;
	}
	public int CompareTo(FsPath o) { return cmp(p, o.p); }
	@Override public int compareTo(FsPath o) { return cmp(p, o.p); }
	public int compare(FsPath o) { return cmp(p, o.p); }

	String S() { return FsNet.s(p); }
	@Override public String toString() { return S(); }

	public Str string() { return p.Clone(); }
	public Str generic_string() { return p.Clone(); }
	public Str native_() { return p.Clone(); }
	public CPtr c_str() { return p.c_str(); }
	public Str u8string() { return p.Clone(); }
	public boolean empty() { return p.n == 0; }
	public boolean is_absolute() { return p.n > 0 && p.b[0] == (byte) '/'; }
	public boolean is_relative() { return !is_absolute(); }
	public boolean has_filename() { return filename().p.n > 0; }
	public boolean has_parent_path() { return parent_path().p.n > 0; }
	public boolean has_extension() { return extension().p.n > 0; }

	/** The last element (libstdc++: the part after the last '/'; "" when the path ends in '/'). */
	public FsPath filename() {
		String s = S();
		int i = s.lastIndexOf('/');
		return new FsPath(i < 0 ? s : s.substring(i + 1));
	}

	public FsPath parent_path() {
		String s = S();
		int i = s.lastIndexOf('/');
		if (i < 0) return new FsPath("");
		if (i == 0) return new FsPath("/");
		int j = i;
		while (j > 0 && s.charAt(j - 1) == '/') j--;
		return new FsPath(s.substring(0, j == 0 ? 1 : j));
	}

	public FsPath stem() {
		String f = filename().S();
		if (f.equals(".") || f.equals("..")) return new FsPath(f);
		int i = f.lastIndexOf('.');
		return new FsPath(i <= 0 ? f : f.substring(0, i));
	}

	public FsPath extension() {
		String f = filename().S();
		if (f.equals(".") || f.equals("..")) return new FsPath("");
		int i = f.lastIndexOf('.');
		return new FsPath(i <= 0 ? "" : f.substring(i));
	}

	/** replace_extension(ext): the extension removed, then ext added (with a '.' in front unless it has one); a
	 *  null or empty ext only removes. */
	public FsPath replace_extension_FsPath(FsPath ext) {
		String e = extension().S();
		String all = S();
		String s = all.substring(0, all.length() - e.length());
		if (ext != null && ext.p.n > 0) s += (ext.p.b[0] == (byte) '.' ? "" : ".") + ext.S();
		p = FsNet.str(s);
		return this;
	}
	public FsPath replace_extension_Str(Str ext) { return replace_extension_FsPath(new FsPath(ext)); }
	public FsPath replace_filename(FsPath f) { FsPath par = parent_path(); p = par.Join_FsPath(f).p; return this; }
	public FsPath remove_filename() { String s = S(); int i = s.lastIndexOf('/'); p = FsNet.str(i < 0 ? "" : s.substring(0, i + 1)); return this; }

	/** operator/ : an absolute right side replaces the path; otherwise a separator is added. */
	public FsPath Join_FsPath(FsPath o) {
		if (o.is_absolute()) return o.Clone();
		String s = S();
		if (s.isEmpty()) return o.Clone();
		if (!s.endsWith("/") && o.p.n > 0) s += "/";
		return new FsPath(s + o.S());
	}
	public FsPath Join_Str(Str o) { return Join_FsPath(new FsPath(o)); }
	public FsPath Join_CPtr(CPtr o) { return Join_FsPath(new FsPath(o)); }

	/** Empty and "." elements dropped, ".." taking the element before it away (dropped at the root), a trailing
	 *  '/' kept, "." for an empty result. */
	public FsPath lexically_normal() {
		String s = S();
		if (s.isEmpty()) return new FsPath("");
		boolean abs = s.startsWith("/");
		ArrayList<String> parts = new ArrayList<>();
		for (String x : s.split("/", -1)) {
			if (x.isEmpty() || x.equals(".")) continue;
			if (x.equals("..") && !parts.isEmpty() && !parts.get(parts.size() - 1).equals("..")) { parts.remove(parts.size() - 1); continue; }
			if (x.equals("..") && abs) continue;
			parts.add(x);
		}
		String r = (abs ? "/" : "") + String.join("/", parts);
		if (s.endsWith("/") && !parts.isEmpty()) r += "/";
		if (r.isEmpty()) r = ".";
		return new FsPath(r);
	}
}
