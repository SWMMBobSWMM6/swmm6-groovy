// Check.groovy - compare the Groovy translation of glibc's math routines (Glibm.groovy + GlibmRt.groovy) and the JVM's
// Math.fma bit for bit with vectors the system libm wrote (tools/libm/test/mkvectors.c, mkedge.c): every bit of the
// result is compared, NaN payload and sign included. Copied from the SWMM5 Groovy port (package changed).
// Written by Claude (Anthropic) for Robert Dickinson; see ../README.md.
package cpp

import groovy.transform.CompileStatic
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths

@CompileStatic
final class Check {
  static void main(String[] a) {
    ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(Paths.get(a[0]))).order(ByteOrder.LITTLE_ENDIAN)
    Set<String> only = a.length > 1 ? new HashSet<String>(Arrays.asList(a[1].split(','))) : null
    while (b.hasRemaining()) {
      StringBuilder sb = new StringBuilder()
      while (true) { int c = b.get(); if (c == 0) break; sb.append((char) c) }
      String name = sb.toString()
      int n = b.getInt()
      int na = name == 'pow' ? 2 : name == 'fma' ? 3 : 1
      if (only != null && !only.contains(name)) { b.position(b.position() + n * 8 * (na + 1)); continue }
      long bad = 0L, nanSign = 0L
      int shown = 0
      long t0 = System.nanoTime()
      for (int i = 0; i < n; i++) {
        double x = b.getDouble()
        double y = na > 1 ? b.getDouble() : 0.0d
        double z = na > 2 ? b.getDouble() : 0.0d
        double want = b.getDouble()
        double got
        switch (name) {
          case 'exp': got = Glibm.exp(x); break
          case 'log': got = Glibm.log(x); break
          case 'log10': got = Glibm.log10(x); break
          case 'pow': got = Glibm.pow(x, y); break
          case 'sin': got = Glibm.sin(x); break
          case 'cos': got = Glibm.cos(x); break
          case 'tan': got = Glibm.tan(x); break
          case 'asin': got = Glibm.asin(x); break
          case 'acos': got = Glibm.acos(x); break
          case 'atan': got = Glibm.atan(x); break
          default: got = Math.fma(x, y, z)
        }
        long gb = Double.doubleToRawLongBits(got), wb = Double.doubleToRawLongBits(want)
        if (gb != wb) {
          bad++
          if (Double.isNaN(got) && Double.isNaN(want) && (gb >>> 63) != (wb >>> 63)) nanSign++
          if (shown++ < 4) println('  ' + name + '(' + x + (na > 1 ? ', ' + y : '') + (na > 2 ? ', ' + z : '') + ') = ' +
                                   Long.toHexString(gb) + ', libm ' + Long.toHexString(wb))
        }
      }
      println(String.format('%-6s %d of %d differ (%d of them a NaN sign)   (%.3f us/call)', name, bad, n, nanSign,
                            (System.nanoTime() - t0) / 1e3d / n))
    }
  }
}
