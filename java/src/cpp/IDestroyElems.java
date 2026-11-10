// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

/** A container whose destructor destroys its elements (C# Cpp.IDestroyElems). */
public interface IDestroyElems {
	void DestroyElems();
}
