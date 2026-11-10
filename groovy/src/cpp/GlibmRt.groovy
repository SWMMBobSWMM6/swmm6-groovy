// GlibmRt.groovy - runtime for the Groovy translation of glibc 2.39's double-precision math routines.
//
// The translated routines (Glibm.groovy, written by tools/libm/gim2x.py's Groovy back end from GCC's optimized
// GIMPLE) use Math.fma (the JVM's exactly rounded fused multiply-add, like the hardware FMA glibc's x86-64 variants
// run on), the bit patterns of doubles, x86's float-to-integer conversions, unsigned and truncating integer
// division (Groovy's / on two integers is a BigDecimal) and glibc's error helpers. All are here, plus a hand port of
// branred.c (huge-argument reduction for sin/cos/tan, compiled by glibc without FMA, so plain double arithmetic in
// source order reproduces it). The Java port's GlibmRt.java in Groovy. Nothing from java.lang.Math is used but fma,
// abs (a sign-bit operation), min and floorDiv (integer). Copied from the SWMM5 Groovy port
// (swmm5-ports/groovy/src/swmm5/GlibmRt.groovy) into package cpp; only the package line differs. Derived from glibc
// (branred.c, the math_err.c helpers), so LGPL-2.1-or-later. Written by Claude (Anthropic) for Robert Dickinson; see
// ../../README.md.
package cpp

import groovy.transform.CompileStatic

@CompileStatic
class GlibmRt {
  /** -0.0: Groovy folds the literal -0.0d to +0.0 */
  public static final double NZ = Double.longBitsToDouble(0x8000000000000000L)

  static double p2(int e) { return Double.longBitsToDouble(((long) (e + 1023)) << 52) }

  static long f2u(double d) { return Double.doubleToRawLongBits(d) }
  static double u2f(long b) { return Double.longBitsToDouble(b) }
  static int hi(double d) { return (int) (Double.doubleToRawLongBits(d) >>> 32) }
  static int lo(double d) { return (int) Double.doubleToRawLongBits(d) }

  // x86-64 cvttsd2si: NaN and out-of-range values give the "integer indefinite" value
  static int d2i(double x) {
    if (!(-2147483649.0d < x && x < 2147483648.0d)) return Integer.MIN_VALUE   // never > on doubles (R.dgt)
    return (int) x
  }
  static long d2l(double x) {
    if (!(-9223372036854775809.0d < x && x < 9223372036854775808.0d)) return Long.MIN_VALUE
    return (long) x
  }
  static double u64ToDouble(long u) {               // (double) of an unsigned 64-bit value, rounded once
    if (u >= 0L) return (double) u
    return ((double) ((u >>> 1) | (u & 1L))) * 2.0d
  }
  // C's signed division truncates; Groovy has no integer division operator
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

  static double copysign(double x, double y) {
    return Double.longBitsToDouble((Double.doubleToRawLongBits(x) & 0x7fffffffffffffffL)
                                   | (Double.doubleToRawLongBits(y) & 0x8000000000000000L))
  }

  // glibc's math_err.c helpers (the value they return; errno is not modelled)
  static double math_oflow(int sign) { double y = p2(769); return (sign != 0 ? -y : y) * y }
  static double math_uflow(int sign) { double y = p2(-767); return (sign != 0 ? -y : y) * y }
  static double math_may_uflow(int sign) { double y = 1.5d * p2(-538); return (sign != 0 ? -y : y) * y }
  static double math_divzero(int sign) { double z = 0.0d; return (sign != 0 ? -1.0d : 1.0d) / z }
  static double math_invalid(double x) { return (x - x) / (x - x) }
  static double math_check_oflow(double y) { return y }
  static double math_check_uflow(double y) { return y }

  // tables: concatenated 16-hex-digit bit patterns
  static long[] u64tab(String hex) {
    int n = idiv(hex.length(), 16)
    long[] out = new long[n]
    for (int i = 0; i < n; i++) out[i] = Long.parseUnsignedLong(hex.substring(16 * i, 16 * i + 16), 16)
    return out
  }
  static double[] f64tab(String hex) {
    long[] u = u64tab(hex)
    double[] out = new double[u.length]
    for (int i = 0; i < u.length; i++) out[i] = Double.longBitsToDouble(u[i])
    return out
  }

  // branred.c: x = n*pi/2 + (a + aa), returns n mod 4
  public static double brA, brAA
  public static final double t576 = p2(576), tm600 = p2(-600), tm24 = p2(-24), big = 6755399441055744.0d, big1 = 27021597764222976.0d
  public static final double mp1 = u2f(0x3FF921FB58000000L), mp2 = u2f(0xBE4DDE9740000000L)
  public static final double hp0b = u2f(0x3FF921FB54442D18L), hp1b = u2f(0x3C91A62633145C07L)
  public static final double SPLIT = 134217729.0d

  private static double hb, hbb, hsum
  private static void half(double xx, double[] toverp) {
    double[] r = new double[6]
    double sum = 0.0d, s, t, gor
    int k = (hi(xx) >> 20) & 2047
    k = idiv(k - 450, 24)
    if (k < 0) k = 0
    gor = Double.longBitsToDouble(Double.doubleToRawLongBits(t576) - (((long) (k * 24)) << 52))
    for (int i = 0; i < 6; i++) { r[i] = xx * toverp[k + i] * gor; gor *= tm24 }
    for (int i = 0; i < 3; i++) { s = (r[i] + big) - big; sum += s; r[i] -= s }
    t = 0.0d
    for (int i = 0; i < 6; i++) t += r[5 - i]
    double bb = (((((r[0] - t) + r[1]) + r[2]) + r[3]) + r[4]) + r[5]
    s = (t + big) - big
    sum += s
    t -= s
    double b = t + bb
    bb = (t - b) + bb
    s = (sum + big1) - big1
    sum -= s
    hb = b; hbb = bb; hsum = sum
  }
  static int branred(double x, double[] toverp) {
    x *= tm600
    double t = x * SPLIT
    double x1 = t - (t - x)
    double x2 = x - x1
    half(x1, toverp)
    double b1 = hb, bb1 = hbb, sum1 = hsum
    half(x2, toverp)
    double b2 = hb, bb2 = hbb, sum2 = hsum
    double sum = sum1 + sum2
    double b = b1 + b2
    double bb = (Math.abs(b2) < Math.abs(b1)) ? (b1 - b) + b2 : (b2 - b) + b1
    if (0.5d < b) { b -= 1.0d; sum += 1.0d }
    else if (b < -0.5d) { b += 1.0d; sum -= 1.0d }
    double s = b + (bb + bb1 + bb2)
    t = ((b - s) + bb) + (bb1 + bb2)
    b = s * SPLIT
    double t1 = b - (b - s)
    double t2 = s - t1
    b = s * hp0b
    bb = (((t1 * mp1 - b) + t1 * mp2) + t2 * mp1) + (t2 * mp2 + s * hp1b + t * hp0b)
    s = b + bb
    t = (b - s) + bb
    brA = s; brAA = t
    return d2i(sum) & 3
  }
}
