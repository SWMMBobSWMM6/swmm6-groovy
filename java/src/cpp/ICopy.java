// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** Copy semantics for the engine's classes (C# Cpp.ICopy): Clone is the copy constructor, Assign the copy assignment. */
public interface ICopy<T> {
	T Clone();
	void Assign(T other);
}
