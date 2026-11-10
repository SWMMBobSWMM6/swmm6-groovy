// HashTable.java - libstdc++'s _Hashtable for unique keys (GCC 13 bits/hashtable.h), as C# Cpp.HashTable<K, E>
// (cs/runtime/Containers.cs) has it: one singly linked list of nodes, a bucket array pointing at the node before
// each bucket's first node, new nodes at the front of their bucket, the prime rehash policy. E is the stored
// element (Pair<K, V> or K). The engine iterates hashed containers, so this order reaches its output;
// tests/stdcheck (hashcheck.java) compares it with libstdc++'s.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public class HashTable<K, E> {
	/** A hasher without boxing: std::hash&lt;K&gt; or the engine's own (a size_t, held as a long). */
	@FunctionalInterface interface HashOf<K> { long of(K k); }
	/** A key-equal without boxing. */
	@FunctionalInterface interface EqOf<K> { boolean eq(K a, K b); }

	protected final Fn.F1<E, K> key;
	protected final HashOf<K> hash;
	protected final EqOf<K> eq;
	protected HNode<E>[] buckets = newBuckets(1);
	protected final HNode<E> beforeBegin = new HNode<E>();
	protected long nelem;
	protected final RehashPolicy pol = new RehashPolicy();

	/** hash null is std::hash&lt;K&gt;, eq null std::equal_to&lt;K&gt;. */
	public HashTable(Fn.F1<E, K> key, Fn.F1<K, Long> hash, Fn.F2<K, K, Boolean> eq) {
		this(key, hashOf(hash), eqOf(eq));
	}

	HashTable(Fn.F1<E, K> key, HashOf<K> hash, EqOf<K> eq) {
		this.key = key;
		this.hash = hash != null ? hash : StdHash::hashObj;
		this.eq = eq != null ? eq : Elem::Eq;
	}

	static <K> HashOf<K> hashOf(Fn.F1<K, Long> h) { return h == null ? null : k -> h.apply(k); }
	static <K> EqOf<K> eqOf(Fn.F2<K, K, Boolean> e) { return e == null ? null : (a, b) -> e.apply(a, b); }

	@SuppressWarnings("unchecked")
	static <E> HNode<E>[] newBuckets(int n) { return (HNode<E>[]) new HNode[n]; }

	/** code % bucket_count, the code unsigned. */
	final int bkt(long code) {
		int n = buckets.length;
		return (int) (code >= 0 ? code % n : Long.remainderUnsigned(code, n));
	}

	/** The node holding k (whose hash is code), or null. */
	final HNode<E> findNode(K k, long code) {
		int b = bkt(code);
		HNode<E> before = buckets[b];
		if (before == null) return null;
		for (HNode<E> p = before.next; p != null; p = p.next) {
			if (p.code == code && eq.eq(k, key.apply(p.val))) return p;
			if (p.next == null || bkt(p.next.code) != b) break;
		}
		return null;
	}

	protected HNode<E> Find(K k) { return findNode(k, hash.of(k)); }

	/** Adds e, whose key is absent and hashes to code: the rehash check, then the node at its bucket's front. */
	final HNode<E> link(E e, long code) {
		long n = pol.NeedRehash(buckets.length, nelem, 1);
		if (n != 0) Rehash(n);
		HNode<E> node = new HNode<E>();
		node.val = e;
		node.code = code;
		insertBucketBegin(bkt(code), node);
		nelem++;
		return node;
	}

	/** Inserts e if its key is absent; returns the node holding the key. */
	protected final HNode<E> Insert(E e) {
		K k = key.apply(e);
		long code = hash.of(k);
		HNode<E> found = findNode(k, code);
		if (found != null) return found;
		return link(e, code);
	}

	private void insertBucketBegin(int b, HNode<E> node) {
		if (buckets[b] != null) {
			node.next = buckets[b].next;
			buckets[b].next = node;
		} else {
			node.next = beforeBegin.next;
			beforeBegin.next = node;
			if (node.next != null) buckets[bkt(node.next.code)] = node;
			buckets[b] = beforeBegin;
		}
	}

	/** _M_rehash_aux for unique keys: relink every node into n buckets, keeping each bucket's order. */
	protected void Rehash(long n) {
		HNode<E>[] nb = newBuckets((int) n);
		HNode<E> p = beforeBegin.next;
		beforeBegin.next = null;
		int bbeginBkt = 0;
		while (p != null) {
			HNode<E> next = p.next;
			int b = (int) Long.remainderUnsigned(p.code, n);
			if (nb[b] == null) {
				p.next = beforeBegin.next;
				beforeBegin.next = p;
				nb[b] = beforeBegin;
				if (p.next != null) nb[bbeginBkt] = p;
				bbeginBkt = b;
			} else {
				p.next = nb[b].next;
				nb[b].next = p;
			}
			p = next;
		}
		buckets = nb;
	}

	protected boolean EraseKey(K k) {
		long code = hash.of(k);
		int b = bkt(code);
		HNode<E> prev = buckets[b];
		if (prev == null) return false;
		HNode<E> p = prev.next;
		while (p != null && !(p.code == code && eq.eq(k, key.apply(p.val)))) {
			if (p.next == null || bkt(p.next.code) != b) return false;
			prev = p;
			p = p.next;
		}
		if (p == null) return false;
		eraseNode(b, prev, p);
		return true;
	}

	private void eraseNode(int b, HNode<E> prev, HNode<E> n) {
		if (prev == buckets[b]) {
			// removing the bucket's first node (_M_remove_bucket_begin)
			HNode<E> next = n.next;
			int nextBkt = next != null ? bkt(next.code) : 0;
			if (next == null || nextBkt != b) {
				if (next != null) buckets[nextBkt] = buckets[b];
				if (buckets[b] == beforeBegin) beforeBegin.next = next;
				buckets[b] = null;
			}
		} else if (n.next != null) {
			int nextBkt = bkt(n.next.code);
			if (nextBkt != b) buckets[nextBkt] = prev;
		}
		prev.next = n.next;
		nelem--;
	}

	public void EraseAt(HNode<E> n) {
		int b = bkt(n.code);
		HNode<E> prev = buckets[b];
		while (prev.next != n) prev = prev.next;
		eraseNode(b, prev, n);
	}

	public long size() { return nelem; }
	public boolean empty() { return nelem == 0; }
	/** Keeps the bucket count, as libstdc++'s clear does. */
	public void clear() { buckets = newBuckets(buckets.length); beforeBegin.next = null; nelem = 0; }
	public long bucket_count() { return buckets.length; }

	public void reserve(long n) { rehash(pol.BktForElements(n)); }
	/** _M_rehash_policy._M_next_bkt(max(n, bkt_for_elements(size + 1))), only if it differs. */
	public void rehash(long n) {
		long want = pol.NextBkt(CtUtil.maxU(n, pol.BktForElements(nelem + 1)));
		if (want != buckets.length) Rehash(want);
	}

	/** The elements in iteration (node) order. Like the C# iterator, each step reads the next link only when
	 *  the loop asks for the next element. */
	public Iterable<E> Items() {
		return () -> new java.util.Iterator<E>() {
			private HNode<E> cur;
			private boolean started;
			@Override public boolean hasNext() { return (started ? cur.next : beforeBegin.next) != null; }
			@Override public E next() {
				HNode<E> nx = started ? cur.next : beforeBegin.next;
				if (nx == null) throw new java.util.NoSuchElementException();
				cur = nx;
				started = true;
				return nx.val;
			}
		};
	}

	public MIt<E> begin() { return new MIt<E>(beforeBegin.next); }
	public MIt<E> end() { return MIt.zero(); }
	public MIt<E> find(K k) { return new MIt<E>(Find(k)); }
	public long count_(K k) { return Find(k) != null ? 1L : 0L; }
	public long count(K k) { return count_(k); }
	public boolean contains(K k) { return Find(k) != null; }
	public long erase_K(K k) { return EraseKey(k) ? 1L : 0L; }
	public MIt<E> erase_MItE(MIt<E> it) { HNode<E> nx = it.node.next; EraseAt(it.node); return new MIt<E>(nx); }

	/** _M_assign: the same bucket count, the same node order, each element copied by copy. */
	protected void CopyFrom(HashTable<K, E> o, Fn.F1<E, E> copy) {
		buckets = newBuckets(o.buckets.length);
		beforeBegin.next = null;
		nelem = 0;
		pol.NextResize = o.pol.NextResize;
		HNode<E> prev = beforeBegin;
		for (HNode<E> p = o.beforeBegin.next; p != null; p = p.next) {
			HNode<E> n = new HNode<E>();
			n.val = copy.apply(p.val);
			n.code = p.code;
			prev.next = n;
			int b = bkt(n.code);
			if (buckets[b] == null) buckets[b] = prev;
			prev = n;
			nelem++;
		}
	}
}
