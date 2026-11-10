// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** An unsigned 64-bit value passed through a params object[] (printf's arguments): Java has no unsigned types, so
 *  the C# type (byte, ushort, uint, ulong) travels with the bits. */
public final class U64 {
	public final long v;
	public U64(long v) { this.v = v; }
}
