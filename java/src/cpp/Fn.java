// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** C#'s System.Func and System.Action delegates: Fn.F2<A, B, R> is Func<A, B, R> (call apply), Fn.A1<A> is
 *  Action<A> (call run). Primitive arguments and results are boxed; the translator casts results back. */
public final class Fn {
	private Fn() { }
	@FunctionalInterface public interface F0<R> { R apply(); }
	@FunctionalInterface public interface F1<A, R> { R apply(A a); }
	@FunctionalInterface public interface F2<A, B, R> { R apply(A a, B b); }
	@FunctionalInterface public interface F3<A, B, C, R> { R apply(A a, B b, C c); }
	@FunctionalInterface public interface F4<A, B, C, D, R> { R apply(A a, B b, C c, D d); }
	@FunctionalInterface public interface A0 { void run(); }
	@FunctionalInterface public interface A1<A> { void run(A a); }
	@FunctionalInterface public interface A2<A, B> { void run(A a, B b); }
	@FunctionalInterface public interface A3<A, B, C> { void run(A a, B b, C c); }
	@FunctionalInterface public interface A4<A, B, C, D> { void run(A a, B b, C c, D d); }
}
