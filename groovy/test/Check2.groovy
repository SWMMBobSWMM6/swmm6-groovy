// Check2.groovy - compare set 2 of the Groovy glibc ports (Glibm2: cbrt, hypot, atan2, cosh, sinh, tanh, log2, expm1,
// log1p, and the runtime's frexp and ldexp) bit for bit with system-libm vectors (tools/libm/test/mkvectors2.c). A
// NaN result must match glibc's in sign and payload too. Optional second argument: a comma-separated list of
// functions. A Groovy port of tools/libm/test/Check2.java. Written by Claude (Anthropic) for Robert Dickinson.
package cpp

import groovy.transform.CompileStatic
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths

@CompileStatic
final class Check2 {
  static void main(String[] a) {
    ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(Paths.get(a[0]))).order(ByteOrder.LITTLE_ENDIAN)
    Set<String> only = a.length > 1 ? new HashSet<String>(Arrays.asList(a[1].split(','))) : null
    long total = 0L
    while (b.hasRemaining()) {
      StringBuilder sb = new StringBuilder()
      while (true) { int c = b.get(); if (c == 0) break; sb.append((char) c) }
      String name = sb.toString()
      int n = b.getInt()
      int na = name == 'hypot' || name == 'atan2' || name == 'ldexp' ? 2 : 1
      int nr = name == 'frexp' ? 2 : 1
      if (only != null && !only.contains(name)) { b.position(b.position() + n * 8 * (na + nr)); continue }
      long bad = 0L, nanSign = 0L, nanPay = 0L
      int shown = 0
      long t0 = System.nanoTime()
      for (int i = 0; i < n; i++) {
        double x = b.getDouble()
        double y = na > 1 ? b.getDouble() : 0.0d
        double want = b.getDouble()
        double wantE = nr > 1 ? b.getDouble() : 0.0d
        double got
        int gotE = 0
        switch (name) {
          case 'cbrt': got = Glibm2.cbrt(x); break
          case 'hypot': got = Glibm2.hypot(x, y); break
          case 'atan2': got = Glibm2.atan2(x, y); break
          case 'cosh': got = Glibm2.cosh(x); break
          case 'sinh': got = Glibm2.sinh(x); break
          case 'tanh': got = Glibm2.tanh(x); break
          case 'log2': got = Glibm2.log2(x); break
          case 'expm1': got = Glibm2.expm1(x); break
          case 'log1p': got = Glibm2.log1p(x); break
          case 'frexp': got = Glibm2Rt.frexp(x); gotE = Glibm2Rt.frexpExp(x); break
          default: got = Glibm2Rt.ldexp(x, (int) y)
        }
        long gb = Double.doubleToRawLongBits(got), wb = Double.doubleToRawLongBits(want)
        if (Double.isNaN(got) && Double.isNaN(want)) {
          if ((gb >>> 63) != (wb >>> 63)) nanSign++; else if (gb != wb) nanPay++
          continue
        }
        if (gb != wb || ((double) gotE) != wantE) {
          bad++
          if (shown++ < 4) println(String.format('  %s(%s [%016x], %s [%016x]) = %s [%016x], libm %s [%016x]', name, x,
              Double.doubleToRawLongBits(x), y, Double.doubleToRawLongBits(y), got, gb, want, wb))
        }
      }
      total += bad + nanSign + nanPay
      println(String.format('%-6s %d of %d differ, %d NaN sign, %d NaN payload   (%.3f us/call)', name, bad, n, nanSign, nanPay,
          (System.nanoTime() - t0) / 1e3d / n))
    }
    println('total differences: ' + total)
  }
}
