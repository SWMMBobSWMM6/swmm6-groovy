// Ident.java - a unique number per object for this run (C# Cpp.Ident): orders pointers the way only their identity
// matters (a std::map key holding table pointers is looked up, never iterated in address order). Identity-based and
// weak, as C#'s ConditionalWeakTable is.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;

public final class Ident {
	private Ident() { }
	private static final class Key extends WeakReference<Object> {
		final int h;
		Key(Object o, ReferenceQueue<Object> q) { super(o, q); h = System.identityHashCode(o); }
		@Override public int hashCode() { return h; }
		@Override public boolean equals(Object x) {
			if (this == x) return true;
			if (!(x instanceof Key)) return false;
			Object a = get();
			return a != null && a == ((Key) x).get();
		}
	}
	private static final HashMap<Key, Long> ids = new HashMap<>();
	private static final ReferenceQueue<Object> dead = new ReferenceQueue<>();
	private static long next;
	public static synchronized long Of(Object o) {
		if (o == null) return 0;
		for (Object k; (k = dead.poll()) != null; ) ids.remove(k);
		Key k = new Key(o, dead);
		Long v = ids.get(k);
		if (v == null) { v = ++next; ids.put(k, v); }
		return v;
	}
}
