// RefGet.java - a reference member of a class (C# delegate ref T Cpp.RefGet<T>(), cs/runtime/Misc.cs): the place it
// refers to. A reference type argument gives this interface; bool gives RefGetZ (the translator specialises it as it
// does RefAt).
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

@FunctionalInterface
public interface RefGet<T> {
	RefO<T> invoke();
}
