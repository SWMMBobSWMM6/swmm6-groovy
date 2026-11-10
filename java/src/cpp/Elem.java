// Elem.java - how the containers copy, compare and order their elements (C# Cpp.Elem<T> and Cpp.ArrCopy in
// cs/runtime/Std.cs, EqualityComparer<T>.Default and Comparer<T>.Default). C# decides from the static type T; Java's
// erased generics cannot, so these test the value: an ICopy is copied with Clone, an array element by element, any
// other value (a boxed number, a runtime struct such as CPtr) is itself. The element makers that C#'s Elem<T>.Make
// gives are passed in by the translator (cpp.Fn.F0), so there is no Make here.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.util.concurrent.ConcurrentHashMap;

public final class Elem {
	private Elem() { }

	/** Elem&lt;T&gt;.Copy: C++ copy construction of an element. */
	@SuppressWarnings("unchecked")
	public static <T> T Copy(T x) {
		if (x instanceof ICopy<?> c) return (T) c.Clone();
		if (x != null && x.getClass().isArray()) return (T) Deep(x);
		return x;
	}

	/** Copying means more than copying the reference: a class (ICopy) or an array. */
	public static boolean NeedsCopy(Object x) { return x instanceof ICopy<?> || (x != null && x.getClass().isArray()); }

	/** ArrCopy.Deep: a C array member copied with its object (T a[n][m], or an array of classes), every row and
	 *  element. A primitive array is copied; an array of references gets each non-null element copied. */
	public static Object Deep(Object x) {
		if (x instanceof Object[] a) {
			Object[] b = a.clone();
			for (int i = 0; i < a.length; i++) { Object v = a[i]; if (v != null) b[i] = Copy(v); }
			return b;
		}
		if (x instanceof double[] a) return a.clone();
		if (x instanceof int[] a) return a.clone();
		if (x instanceof long[] a) return a.clone();
		if (x instanceof byte[] a) return a.clone();
		if (x instanceof boolean[] a) return a.clone();
		if (x instanceof float[] a) return a.clone();
		if (x instanceof short[] a) return a.clone();
		if (x instanceof char[] a) return a.clone();
		return x;
	}

	// ---- EqualityComparer<T>.Default
	/** double.Equals: == except that NaN equals NaN (and 0.0 equals -0.0, as == has it). */
	public static boolean eq(double a, double b) { return a == b || (a != a && b != b); }
	public static boolean eq(float a, float b) { return a == b || (a != a && b != b); }
	public static boolean eq(int a, int b) { return a == b; }
	public static boolean eq(long a, long b) { return a == b; }
	public static boolean eq(byte a, byte b) { return a == b; }
	public static boolean eq(short a, short b) { return a == b; }
	public static boolean eq(char a, char b) { return a == b; }
	public static boolean eq(boolean a, boolean b) { return a == b; }

	/** EqualityComparer&lt;T&gt;.Default.Equals for a boxed or reference value. */
	public static boolean Eq(Object a, Object b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		if (a instanceof Double x && b instanceof Double y) return eq(x.doubleValue(), y.doubleValue());
		if (a instanceof Float x && b instanceof Float y) return eq(x.floatValue(), y.floatValue());
		if (a instanceof Str s && b instanceof Str t) return s.Equals_Str(t);
		return a.equals(b);
	}

	// ---- Less<T>.Fn: Str by char_traits, otherwise Comparer<T>.Default.Compare(a, b) < 0
	/** std::less of two boxed or reference values, as the C# runtime's Less&lt;T&gt;.Fn: a Str compares as
	 *  std::string, a double or float as .NET's CompareTo (NaN before every number), a bool false before true,
	 *  anything Comparable by compareTo, an object with a CompareTo(T) method by that, null before non-null.
	 *  A boxed Integer or Long compares signed (Java cannot tell a C# uint or ulong from int or long). */
	public static boolean Less(Object a, Object b) {
		if (a == null) return b != null;
		if (b == null) return false;
		if (a instanceof Str s) return s.compare_Str((Str) b) < 0;
		if (a instanceof Double x) { double p = x, q = (Double) b; return p < q || (p != p && q == q); }
		if (a instanceof Float x) { float p = x, q = (Float) b; return p < q || (p != p && q == q); }
		if (a instanceof Boolean x) return !x && (Boolean) b;
		if (a instanceof Comparable<?>) return compareComparable(a, b) < 0;
		return compareByMethod(a, b) < 0;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static int compareComparable(Object a, Object b) { return ((Comparable) a).compareTo(b); }

	private static final ConcurrentHashMap<Class<?>, java.lang.reflect.Method> compareTo = new ConcurrentHashMap<>();
	private static int compareByMethod(Object a, Object b) {
		java.lang.reflect.Method m = compareTo.computeIfAbsent(a.getClass(), c -> {
			for (java.lang.reflect.Method x : c.getMethods())
				if (x.getName().equals("CompareTo") && x.getParameterCount() == 1 && x.getReturnType() == int.class) return x;
			throw new IllegalArgumentException("cpp.Elem.Less: no ordering for " + c.getName());
		});
		try { return (Integer) m.invoke(a, b); }
		catch (java.lang.reflect.InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException r) throw r;
			throw new RuntimeException(e.getCause());
		}
		catch (IllegalAccessException e) { throw new RuntimeException(e); }
	}

	// ---- element makers
	private static final ConcurrentHashMap<Class<?>, Boolean> weak = new ConcurrentHashMap<>();
	/** Whether a maker stands for C#'s "no maker" (Elem&lt;T&gt;.Make of a type without a parameterless constructor,
	 *  or of an array type, gives null): the translator passes () -> null for those. The answer is a property of
	 *  the lambda's site, so it is asked once per site. */
	public static boolean weakMaker(Fn.F0<?> f) {
		Boolean w = weak.get(f.getClass());
		if (w == null) { w = f.apply() == null; weak.put(f.getClass(), w); }
		return w;
	}
}
