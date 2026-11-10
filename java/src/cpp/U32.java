// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** An unsigned 32-bit value passed through a params object[] (printf's arguments): Java has no unsigned types, so
 *  the C# type (byte, ushort, uint, ulong) travels with the bits. */
public final class U32 {
	public final int v;
	public U32(int v) { this.v = v; }
}
