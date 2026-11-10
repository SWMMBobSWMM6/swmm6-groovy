// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** An unsigned 16-bit value passed through a params object[] (printf's arguments): Java has no unsigned types, so
 *  the C# type (byte, ushort, uint, ulong) travels with the bits. */
public final class U16 {
	public final char v;
	public U16(char v) { this.v = v; }
}
