// RehashPolicy.java - libstdc++'s prime rehash policy (src/c++11/hashtable_c++0x.cc and hashtable-aux.cc), as C#
// Cpp.RehashPolicy (cs/runtime/Containers.cs) has it. Element and bucket counts are size_t, held as longs and
// compared unsigned; the arithmetic in double is the C++ (and C#) arithmetic.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class RehashPolicy {
	public double MaxLoad = 1.0;
	/** The element count at which the table grows next (unsigned). */
	public long NextResize = 0;
	static final byte[] FastBkt = { 2, 2, 2, 3, 5, 5, 7, 7, 11, 11, 11, 11, 13, 13 };

	/** _M_next_bkt: the bucket count for at least n buckets. */
	public long NextBkt(long n) {
		if (Long.compareUnsigned(n, FastBkt.length) < 0) {
			if (n == 0) return 1;
			NextResize = CtUtil.d2u(Math.floor(FastBkt[(int) n] * MaxLoad));
			return FastBkt[(int) n];
		}
		long[] primes = PrimeList.P;
		int last = primes.length - 1;          // __prime_list + __n_primes - 1 (the sentinel is excluded)
		int lo = 6, hi = last;
		while (lo < hi) {
			int mid = (lo + hi) / 2;
			if (Long.compareUnsigned(primes[mid], n) < 0) lo = mid + 1;
			else hi = mid;
		}
		if (lo == last) NextResize = -1L;      // ulong.MaxValue
		else NextResize = CtUtil.d2u(Math.floor(CtUtil.u2d(primes[lo]) * MaxLoad));
		return primes[lo];
	}

	/** _M_bkt_for_elements. */
	public long BktForElements(long n) { return CtUtil.d2u(Math.ceil(CtUtil.u2d(n) / MaxLoad)); }

	/** _M_need_rehash: the new bucket count, or 0 when no rehash is needed (C# returns (false, 0); a bucket
	 *  count is never 0, so 0 alone says it). */
	public long NeedRehash(long nBkt, long nElt, long nIns) {
		if (Long.compareUnsigned(nElt + nIns, NextResize) > 0) {
			double minBkts = CtUtil.u2d(CtUtil.maxU(nElt + nIns, NextResize != 0 ? 0L : 11L)) / MaxLoad;
			if (minBkts >= CtUtil.u2d(nBkt))
				return NextBkt(CtUtil.maxU(CtUtil.d2u(Math.floor(minBkts)) + 1, nBkt * 2));
			NextResize = CtUtil.d2u(Math.floor(CtUtil.u2d(nBkt) * MaxLoad));
			return 0;
		}
		return 0;
	}
}
