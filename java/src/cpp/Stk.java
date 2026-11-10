// Stk.java - std::stack over std::deque (C# Cpp.Stk<T>, cs/runtime/Containers.cs): push, pop, top.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Stk<T> implements ICopy<Stk<T>> {
	private final java.util.ArrayList<T> items = new java.util.ArrayList<>();

	public void push(T x) { items.add(x); }
	public void pop() { items.remove(items.size() - 1); }
	/** top() read as a value. */
	public T top() { return items.get(items.size() - 1); }
	/** top() as a reference. */
	public RefO<T> ref_top() {
		final int i = items.size() - 1;
		return new RefO<T>() {
			@Override public T get() { return items.get(i); }
			@Override public void set(T v) { items.set(i, v); }
		};
	}
	public boolean empty() { return items.isEmpty(); }
	public long size() { return items.size(); }

	@Override public Stk<T> Clone() { Stk<T> s = new Stk<T>(); for (T x : items) s.items.add(Elem.Copy(x)); return s; }
	@Override public void Assign(Stk<T> o) { if (this == o) return; items.clear(); for (T x : o.items) items.add(Elem.Copy(x)); }
	public void MoveFrom(Stk<T> o) { if (this == o) return; items.clear(); items.addAll(o.items); o.items.clear(); }
}
