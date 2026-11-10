// GR.groovy - what the Groovy runtime and engine need that Groovy writes differently from Java: integer division
// (Groovy's / on two integers is a BigDecimal), > and >= on doubles (Groovy 3's static compiler makes them true when
// either side is NaN; these are < and <= with the operands swapped, which it compiles correctly), and negative zero
// (Groovy folds the literal -0.0d to +0.0). Hand-written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports;
// see ../../README.md.
package cpp

import groovy.transform.CompileStatic

@CompileStatic
final class GR {
	private GR() { }
	public static final double NZ = Double.longBitsToDouble(0x8000000000000000L)
	public static final float NZF = Float.intBitsToFloat(Integer.MIN_VALUE)
	/** a > b and a >= b as C, C# and Java compute them: false when either is NaN. */
	static boolean dgt(double a, double b) { return b < a }
	static boolean dge(double a, double b) { return b <= a }
	/** C's and Java's integer division: truncated toward zero (Integer.MIN_VALUE / -1 wraps). */
	static int idiv(int a, int b) {
		int q = Math.floorDiv(a, b)
		if ((a % b) != 0 && (a ^ b) < 0) q++
		return q
	}
	static long ldiv(long a, long b) {
		long q = Math.floorDiv(a, b)
		if ((a % b) != 0L && (a ^ b) < 0L) q++
		return q
	}
}
