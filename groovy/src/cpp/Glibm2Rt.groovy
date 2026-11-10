// Glibm2Rt.groovy - runtime additions for set 2 of the Groovy translation of glibc 2.39's math routines
// (Glibm2.groovy): frexp and ldexp, which glibc's cbrt calls, and quiet; the rest is GlibmRt's, which this class
// extends. A Groovy port of tools/libm/Glibm2Rt.java (hand ports of glibc 2.39's sysdeps/ieee754/dbl-64/s_frexp.c,
// s_scalbn.c and math/s_ldexp_template.c: exact operations only, scalings by powers of two and, for a subnormal
// result, one correctly rounded multiplication), written without Groovy's > and >= on doubles and with every double
// literal suffixed d. Derived from the GNU C Library and so, like it, licensed under the GNU Lesser General Public
// License, version 2.1 or later (LGPL-2.1-or-later).
// Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../../README.md.
package cpp

import groovy.transform.CompileStatic

@CompileStatic
class Glibm2Rt extends GlibmRt {
  /** A NaN as x86 passes it on: quieted, sign and payload kept. */
  static double quiet(double x) { return Double.longBitsToDouble(Double.doubleToRawLongBits(x) | (1L << 51)) }

  public static final double TWO54 = Double.longBitsToDouble(0x4350000000000000L)     // 0x1p54
  public static final double TWOM54 = Double.longBitsToDouble(0x3c90000000000000L)    // 0x1p-54
  public static final double HUGE = 1.0e+300d, TINY = 1.0e-300d

  /** glibc's __frexp, the mantissa: 0.5 <= |m| < 1; zero, infinity and NaN come back as x + x. */
  static double frexp(double x) {
    long ix = Double.doubleToRawLongBits(x)
    int ex = (int) (0x7ffL & (ix >> 52))
    if (ex != 0x7ff && x != 0.0d) {
      if (ex == 0) {                                   // subnormal
        x *= TWO54
        ix = Double.doubleToRawLongBits(x)
      }
      return Double.longBitsToDouble((ix & -9218868437227405313L) | 0x3fe0000000000000L)   // ix & 0x800fffffffffffff
    }
    return x + x                                       // quiets a signaling NaN
  }
  /** glibc's __frexp, the exponent (0 for zero, infinity and NaN). */
  static int frexpExp(double x) {
    long ix = Double.doubleToRawLongBits(x)
    int ex = (int) (0x7ffL & (ix >> 52))
    if (ex != 0x7ff && x != 0.0d) {
      if (ex == 0) {
        x *= TWO54
        ix = Double.doubleToRawLongBits(x)
        return (int) (0x7ffL & (ix >> 52)) - 1022 - 54
      }
      return ex - 1022
    }
    return 0
  }

  /** glibc's __scalbn (dbl-64/s_scalbn.c). */
  static double scalbn(double x, int n) {
    long ix = Double.doubleToRawLongBits(x)
    long k = (ix >> 52) & 0x7ffL
    if (k == 0L) {                                     // 0 or subnormal x
      if ((ix & 0xfffffffffffffL) == 0L) return x
      x *= TWO54
      ix = Double.doubleToRawLongBits(x)
      k = ((ix >> 52) & 0x7ffL) - 54L
    }
    if (k == 0x7ffL) return x + x                      // NaN or Inf
    if (n < -50000) return TINY * copysign(TINY, x)
    if (n > 50000 || k + n > 0x7feL) return HUGE * copysign(HUGE, x)
    k += n
    if (k > 0L) return Double.longBitsToDouble((ix & -9218868437227405313L) | (k << 52))
    if (k <= -54L) return TINY * copysign(TINY, x)
    k += 54L
    x = Double.longBitsToDouble((ix & -9218868437227405313L) | (k << 52))
    return x * TWOM54
  }
  /** glibc's __ldexp (math/s_ldexp_template.c; errno not modelled). */
  static double ldexp(double value, int exp) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0d) return value + value
    return scalbn(value, exp)
  }
}
