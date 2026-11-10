// Que.java - std::queue over std::deque (C# Cpp.Que<T>, cs/runtime/Containers.cs): first in, first out.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Que<T> implements ICopy<Que<T>> {
	private final java.util.ArrayList<T> items = new java.util.ArrayList<>();
	private int head;

	public void push(T x) { items.add(x); }
	public void pop() {
		items.set(head, null);
		head++;
		if (head > 64 && head * 2 > items.size()) { items.subList(0, head).clear(); head = 0; }
	}
	public T front() { return items.get(head); }
	public RefO<T> ref_front() { return at(head); }
	public T back() { return items.get(items.size() - 1); }
	public RefO<T> ref_back() { return at(items.size() - 1); }
	private RefO<T> at(final int i) {
		return new RefO<T>() {
			@Override public T get() { return items.get(i); }
			@Override public void set(T v) { items.set(i, v); }
		};
	}
	public boolean empty() { return items.size() == head; }
	public long size() { return items.size() - head; }

	@Override public Que<T> Clone() { Que<T> q = new Que<T>(); for (int i = head; i < items.size(); i++) q.items.add(Elem.Copy(items.get(i))); return q; }
	@Override public void Assign(Que<T> o) {
		if (this == o) return;
		items.clear(); head = 0;
		for (int i = o.head; i < o.items.size(); i++) items.add(Elem.Copy(o.items.get(i)));
	}
	public void MoveFrom(Que<T> o) {
		if (this == o) return;
		items.clear(); head = 0;
		for (int i = o.head; i < o.items.size(); i++) items.add(o.items.get(i));
		o.items.clear(); o.head = 0;
	}
}
