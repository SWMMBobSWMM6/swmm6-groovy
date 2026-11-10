// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** An unsigned 8-bit value passed through a params object[] (printf's arguments): Java has no unsigned types, so
 *  the C# type (byte, ushort, uint, ulong) travels with the bits. */
public final class U8 {
	public final byte v;
	public U8(byte v) { this.v = v; }
}
