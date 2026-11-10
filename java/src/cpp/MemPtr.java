// MemPtr.java - a C++ pointer to data member T C::* (C# delegate ref TF Cpp.MemPtr<TO, TF>(TO o)): given the object,
// a reference to that field of it.
// Limitation: Java's generics cannot give invoke a primitive reference type per TF, and the engine's only
// instantiation is MemPtr<surface_ueb_Parameters, Double>, whose lambdas return cpp.RefD and whose callers use the
// result as a RefD (p.member.invoke(o).get(), RefD.assign(...)); so invoke returns RefD. Another field type would
// need a specialisation (MemPtrD, MemPtrI, ...) from the translator, as RefAt and RefGet have.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

@FunctionalInterface
public interface MemPtr<TO, TF> {
	RefD invoke(TO o);
}
