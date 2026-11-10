// Bytes.java - the bytes of the numbers a pointer addresses, as a char* for reading (C# Cpp.Bytes, cs/runtime/Misc.cs):
// a copy of the little-endian bytes from the pointer to the end of its array (C#'s MemoryMarshal.AsBytes view); a
// null pointer (the data() of an empty vector) is a null char*.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

public final class Bytes {
	private Bytes() { }
	public static CPtr View_CPtr(CPtr p) { return p; }
	public static CPtr View_PtrT__B(PtrB p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
	public static CPtr View_PtrT__D(PtrD p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
	public static CPtr View_PtrT__F(PtrF p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
	public static CPtr View_PtrT__I(PtrI p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
	public static CPtr View_PtrT__L(PtrL p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
	public static CPtr View_PtrT__S(PtrS p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
	public static CPtr View_PtrT__Z(PtrZ p) { return p.get_IsNull() ? CPtr.Null : new CPtr(Mem.allBytes(p.a, p.o), 0); }
}
