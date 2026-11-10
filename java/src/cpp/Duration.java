// Duration.java - a std::chrono duration held in nanoseconds (C# Cpp.Duration, cs/runtime/Misc.cs); count() gives
// seconds (the engine counts durations as std::chrono::duration<double>). Used only for timing; none of it reaches
// the results. A C# struct, so an immutable value.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Duration {
	public final long ns;
	public static final Duration ZERO = new Duration(0L);
	public Duration(long n) { ns = n; }
	public Duration(Duration d) { ns = d.ns; }
	public Duration Clone() { return this; }
	public double count() { return ns * 1e-9; }
	public static Duration op_Add(Duration a, Duration b) { return new Duration(a.ns + b.ns); }
	public static Duration op_Sub(Duration a, Duration b) { return new Duration(a.ns - b.ns); }
}
