// Thread.java - std::thread (C# Cpp.Thread); single-threaded, see Mutex.java: never joinable. (Within package cpp
// this class hides java.lang.Thread; the runtime names that one in full where it needs it.)
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Thread {
	public Thread() { }
	public boolean joinable() { return false; }
	public void join() { }
}
