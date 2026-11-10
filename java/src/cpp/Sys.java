// Sys.java - std::thread::hardware_concurrency (C# Cpp.Sys): libstdc++ on Linux asks glibc's get_nprocs, the
// processors online (/sys/devices/system/cpu/online), not the cgroup or affinity limit that the JVM's
// availableProcessors (like .NET's ProcessorCount) reports. The engine prints it in THREADS warnings.
// Written by Claude (Anthropic) for the SWMM6 ports; see AI-USE.md.
package cpp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class Sys {
	private Sys() { }
	/** unsigned int: the number of processors online (glibc's get_nprocs), 0 if unknown. */
	public static int hardware_concurrency() {
		try {
			String s = new String(Files.readAllBytes(Paths.get("/sys/devices/system/cpu/online")), StandardCharsets.ISO_8859_1).trim();
			int n = 0;
			for (String part : s.split(",")) {
				if (part.isEmpty()) continue;
				int dash = part.indexOf('-');
				if (dash < 0) n++;
				else n += Integer.parseInt(part.substring(dash + 1)) - Integer.parseInt(part.substring(0, dash)) + 1;
			}
			if (n > 0) return n;
		} catch (Exception e) { }
		try {                                                // glibc's fallback: the cpuN lines of /proc/stat
			int n = 0;
			for (String line : Files.readAllLines(Paths.get("/proc/stat"), StandardCharsets.ISO_8859_1))
				if (line.startsWith("cpu") && line.length() > 3 && Character.isDigit(line.charAt(3))) n++;
			if (n > 0) return n;
		} catch (Exception e) { }
		return Runtime.getRuntime().availableProcessors();
	}
}
