// TimePoint.java - a std::chrono time point (C# Cpp.TimePoint, cs/runtime/Misc.cs): nanoseconds of a monotonic
// clock (C#'s Stopwatch; here System.nanoTime). Used only for timing. A C# struct, so an immutable value.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class TimePoint {
	public final long ns;
	public static final TimePoint ZERO = new TimePoint(0L);
	public TimePoint(long ns) { this.ns = ns; }
	public TimePoint Clone() { return this; }
	public static TimePoint now() { return new TimePoint(System.nanoTime()); }
	public static Duration op_Sub(TimePoint a, TimePoint b) { return new Duration(a.ns - b.ns); }
	public Duration time_since_epoch() { return new Duration(ns); }
}
