// KeyCopy.java - C# Cpp.KeyCopy<T> (cs/runtime/Containers.cs): a key stored in a map or set is the container's own
// copy, as in C++, when the key is a library value (a string, a vector, a pair). An engine class as key is a
// pointer in C++ (std::set<const Column*>), so it is stored as it is: the same object, compared by identity.
// Java decides from the key's run-time class (an ICopy class of package cpp) where C# looks at T.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class KeyCopy<T> {
	private KeyCopy() { }
	public static <T> T Of(T x) { return CtUtil.keyCopy(x); }
}
