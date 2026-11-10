// Main.groovy - the SWMM6 command line (src/cli/main.cpp), a hand port of the Java port's java/src/main/Main.java
// (itself a hand port of cs/runtime/Program.cs): the same calls into the engine's C API (engine.F__global) in the same
// order, the same messages on stdout and the same exit code; R.FlushAll before exit, as returning from main calls
// exit(), which flushes every open FILE. The engine runs on a thread with a 256 MB stack, and an exception that
// escapes the engine ends the process as .NET's unhandled-exception handler does: "Unhandled exception. ..." on
// stderr, no flushing of the C files, and exit code 134 (SIGABRT's).
// Written by Claude (Anthropic) for Robert Dickinson's SWMM6 ports; see ../../README.md.
import cpp.CPtr
import cpp.PtrD
import cpp.R
import engine.F__global

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets

@groovy.transform.CompileStatic
final class Main {
	static final String VERSION = '6.0.0-alpha.4'
	static final long STACK_BYTES = 256L << 20

	static void Out(String s) { byte[] b = s.getBytes(StandardCharsets.ISO_8859_1); R.stdout_.S.Write(b, 0, b.length) }

	static void main(String[] argv) throws InterruptedException {
		// Every Groovy object looks up its class's metaclass when it is constructed, even under @CompileStatic; held
		// weakly (Groovy's default) that lookup took a fifth of a run's time in the profile (RefD.Arr, PtrD, ... are
		// made in the inner loops). Held strongly it is one field read. It changes no computation.
		GroovySystem.setKeepJavaMetaClasses(true)
		final int[] rc = new int[1]
		final Throwable[] crash = new Throwable[1]
		Runnable body = () -> {
			try { rc[0] = Run(argv) } catch (Throwable e) { crash[0] = e }
		}
		Thread t = new Thread(null, body, 'openswmm', STACK_BYTES)
		t.start()
		t.join()
		if (crash[0] !== null) {
			System.err.println('Unhandled exception. ' + crash[0])
			crash[0].printStackTrace(System.err)
			System.err.flush()
			Runtime.getRuntime().halt(134)
		}
		R.FlushAll()          // returning from main calls exit(), which flushes every open FILE
		System.exit(rc[0])
	}

	static int Run(String[] argv) {
		if (argv.length < 1) { Out('\nError: not enough arguments. Use --help for usage.\n\n'); return 1 }
		if (argv[0].equals('--version') || argv[0].equals('-v')) { Out('openswmm.engine ' + VERSION + '\n'); return 0 }
		if (argv.length < 2) {
			Out('\nError: need at least input and report file paths.\n')
			Out('Usage: openswmm <input.inp> <report.rpt> [output.out]\n\n')
			return 1
		}
		String inp = argv[0]
		String rpt = argv[1]
		String outf = argv.length > 2 ? argv[2] : ''
		Out('\n... OpenSWMM Engine ' + VERSION + '\n... Input: ' + inp + '\n... Report: ' + rpt + '\n... Output: ' + outf + '\n')
		R.fflush(R.stdout_)
		long t0 = System.nanoTime()
		Object engine = F__global.swmm_engine_create()
		if (engine === null) { Out('Error: failed to create engine instance.\n'); return 1 }
		int err = F__global.swmm_engine_open(engine, R.Lit(inp), R.Lit(rpt), R.Lit(outf), CPtr.ZERO)
		if (err != 0) {
			Out('Error opening input file: ' + R.Str(F__global.swmm_get_last_error_msg(engine)) + '\n')
			F__global.swmm_engine_destroy(engine)
			return err
		}
		err = F__global.swmm_engine_initialize(engine)
		if (err != 0) {
			Out('Error initializing: ' + R.Str(F__global.swmm_get_last_error_msg(engine)) + '\n')
			F__global.swmm_engine_close(engine)
			F__global.swmm_engine_destroy(engine)
			return err
		}
		int save = outf.length() > 0 ? 1 : 0
		err = F__global.swmm_engine_start(engine, save)
		if (err != 0) {
			Out('Error starting simulation: ' + R.Str(F__global.swmm_get_last_error_msg(engine)) + '\n')
			F__global.swmm_engine_close(engine)
			F__global.swmm_engine_destroy(engine)
			return err
		}
		double[] elapsed = new double[1]
		long steps = 0L
		__L1: while (true) {
			err = F__global.swmm_engine_step(engine, new PtrD(elapsed, 0L))
			if (err != 0) { Out('\nError at step ' + steps + ': ' + R.Str(F__global.swmm_get_last_error_msg(engine)) + '\n'); break __L1 }
			if (elapsed[0] <= 0.0d) break __L1
			steps++
		}
		Out('\n... ' + steps + ' steps completed.\n')
		int[] result = new int[] { err }
		Fin(engine, result, F__global.swmm_engine_end(engine), 'ending simulation')
		Fin(engine, result, F__global.swmm_engine_report(engine), 'writing report')
		Fin(engine, result, F__global.swmm_engine_close(engine), 'closing simulation')
		F__global.swmm_engine_destroy(engine)
		// {1:F2}: the elapsed seconds rounded to two places, half away from zero on the exact value (.NET Core)
		double secs = (System.nanoTime() - t0) / 1e9d
		String f2 = new BigDecimal(secs).setScale(2, RoundingMode.HALF_UP).toPlainString()
		Out('... OpenSWMM Engine ' + (result[0] == 0 ? 'completed' : 'failed') + ' in ' + f2 + ' seconds.\n\n')
		return result[0]
	}

	static void Fin(Object engine, int[] result, int status, String phase) {
		if (status != 0) {
			Out('Error ' + phase + ': ' + R.Str(F__global.swmm_get_last_error_msg(engine)) + '\n')
			if (result[0] == 0) result[0] = status
		}
	}
}
