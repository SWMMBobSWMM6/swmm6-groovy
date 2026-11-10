// Mutex.java - std::mutex (C# Cpp.Mutex). The ports run single-threaded: the engine's one worker thread (the output
// writer) is replaced by a synchronous call, so Mutex, CondVar, Thread and Lock never block and hold no state.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Mutex {
	public Mutex() { }
}
