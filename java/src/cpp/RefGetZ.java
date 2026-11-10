// RefGetZ.java - Cpp.RefGet<bool> (see RefGet.java): a bool& member, the place it refers to.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

@FunctionalInterface
public interface RefGetZ {
	RefZ invoke();
}
