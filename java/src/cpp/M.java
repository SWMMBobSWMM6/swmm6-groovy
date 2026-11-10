// M.java - std::min / std::max / std::clamp exactly as libstdc++ writes them (C# Cpp.M, cs/runtime/Std.cs): the
// comparisons decide which argument comes back, which matters for -0.0 and NaN. Unsigned C# overloads (ulong
// ul, uint u, byte uc, ushort us) compare unsigned.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class M {
	private M() { }
	public static double min_d_d(double a, double b) { return (b < a) ? b : a; }
	public static double max_d_d(double a, double b) { return (a < b) ? b : a; }
	public static float min_f_f(float a, float b) { return (b < a) ? b : a; }
	public static float max_f_f(float a, float b) { return (a < b) ? b : a; }
	public static int min_i_i(int a, int b) { return (b < a) ? b : a; }
	public static int max_i_i(int a, int b) { return (a < b) ? b : a; }
	public static long min_l_l(long a, long b) { return (b < a) ? b : a; }
	public static long max_l_l(long a, long b) { return (a < b) ? b : a; }
	public static long min_ul_ul(long a, long b) { return Long.compareUnsigned(b, a) < 0 ? b : a; }
	public static long max_ul_ul(long a, long b) { return Long.compareUnsigned(a, b) < 0 ? b : a; }
	public static int min_u_u(int a, int b) { return Integer.compareUnsigned(b, a) < 0 ? b : a; }
	public static int max_u_u(int a, int b) { return Integer.compareUnsigned(a, b) < 0 ? b : a; }
	public static byte min_uc_uc(byte a, byte b) { return ((b & 0xFF) < (a & 0xFF)) ? b : a; }
	public static byte max_uc_uc(byte a, byte b) { return ((a & 0xFF) < (b & 0xFF)) ? b : a; }
	public static byte min_c_c(byte a, byte b) { return (b < a) ? b : a; }
	public static byte max_c_c(byte a, byte b) { return (a < b) ? b : a; }
	public static short min_s_s(short a, short b) { return (b < a) ? b : a; }
	public static short max_s_s(short a, short b) { return (a < b) ? b : a; }
	public static char min_us_us(char a, char b) { return (b < a) ? b : a; }
	public static char max_us_us(char a, char b) { return (a < b) ? b : a; }
	public static <T> T min_T_T_FuncTTb(T a, T b, Fn.F2<T, T, Boolean> lt) { return lt.apply(b, a) ? b : a; }
	public static <T> T max_T_T_FuncTTb(T a, T b, Fn.F2<T, T, Boolean> lt) { return lt.apply(a, b) ? b : a; }
	// std::clamp(v, lo, hi): (v < lo) ? lo : (hi < v) ? hi : v
	public static double clamp_d_d_d(double v, double lo, double hi) { return (v < lo) ? lo : (hi < v) ? hi : v; }
	public static int clamp_i_i_i(int v, int lo, int hi) { return (v < lo) ? lo : (hi < v) ? hi : v; }
	public static long clamp_l_l_l(long v, long lo, long hi) { return (v < lo) ? lo : (hi < v) ? hi : v; }
	// initializer-list forms: the first smallest / first largest
	public static double min_dA(double[] il) { double r = il[0]; for (int i = 1; i < il.length; i++) if (il[i] < r) r = il[i]; return r; }
	public static double max_dA(double[] il) { double r = il[0]; for (int i = 1; i < il.length; i++) if (r < il[i]) r = il[i]; return r; }
	public static int min_iA(int[] il) { int r = il[0]; for (int i = 1; i < il.length; i++) if (il[i] < r) r = il[i]; return r; }
	public static int max_iA(int[] il) { int r = il[0]; for (int i = 1; i < il.length; i++) if (r < il[i]) r = il[i]; return r; }
}
