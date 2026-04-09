package experiment;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

import algorithm.*;
import algorithm.SearchAlgorithm.*;
import gametree.ArtificialGamePosition;
import gametree.ArtificialGamePosition.Settings;
import gametree.GameTreeNode;
import gametree.Generator;
import gametree.MetricKeeper;
import gametree.VariantAGP;

/**
 * Large class containing many methods for running the experiments and creating new experiments.
 * Some parameters such as debugging and timeout duration are set in-file and have to be changed 
 * manually in the code.
 */
public class CompareAlgorithms {
	
	public static long num_completed = 0l;
	public static long num_skipped = 0l;
	public static long num_prints = 1000l;
	public static long num_total = 0l;
	
	public static long timeout_amount = 5;
	public static TimeUnit timeout_unit = TimeUnit.DAYS;
	
	public static boolean print_detailed_progress = false;
	public static boolean expansion_rate_printer = false;
	
	/**
	 * Algorithm setup, describing a single algorithmic setup. The number of iterations refers to the number of repetitions performed of each pair of algorithm and tree.
	 */
	private record AlgoInfo(String name, BiFunction<ArtificialGamePosition, Integer, SearchAlgorithm> algo_getter, int iterations, Function<Integer, Limits> limits) {
		public SearchAlgorithm get(ArtificialGamePosition root, int iteration) { return algo_getter.apply(root, iteration); }
		public boolean hasLimits() { return limits != null; }
		public Limits getLimits(int iteration) {
			return limits.apply(iteration);
		}
	}
	
	/**
	 * Class for configuring the setup of algorithms to experiments for comparing them.
	 */
	public static class AlgoSetup {
		final Map<String, AlgoInfo> map;
		public AlgoSetup() {
			map = new HashMap<>();
		}
		public void include(String name, int iterations, BiFunction<ArtificialGamePosition,Integer,SearchAlgorithm> algo_getter, Function<Integer, Limits> limit_func) {
			map.put(name, new AlgoInfo(name, algo_getter, iterations, limit_func));
		}
		public void include(String name, int iterations, BiFunction<ArtificialGamePosition,Integer,SearchAlgorithm> algo_getter) {
			map.put(name, new AlgoInfo(name, algo_getter, iterations, null));
		}
		public void include(String name, BiFunction<ArtificialGamePosition,Integer,SearchAlgorithm> algo_getter, Function<Integer, Limits> limit_func) {
			include(name, 1, algo_getter, limit_func);
		}
		public void include(String name, BiFunction<ArtificialGamePosition,Integer,SearchAlgorithm> algo_getter) {
			include(name, 1, algo_getter);
		}
		public void include(String name, int iterations, Function<ArtificialGamePosition, SearchAlgorithm> algo_getter, Function<Integer, Limits> limit_func) {
			include(name, iterations, (p,i) -> algo_getter.apply(p), limit_func);
		}
		public void include(String name, int iterations, Function<ArtificialGamePosition, SearchAlgorithm> algo_getter) {
			include(name, iterations, algo_getter, null);
		}
		public void include(String name, Function<ArtificialGamePosition, SearchAlgorithm> algo_getter) {
			include(name, 1, algo_getter);
		}
		public void include(String name, int iterations, Supplier<SearchAlgorithm> algo_getter, Function<Integer, Limits> limit_func) {
			include(name, iterations, p -> algo_getter.get(), limit_func);
		}
		public void include(String name, int iterations, Supplier<SearchAlgorithm> algo_getter) {
			include(name, iterations, algo_getter, null);
		}
		public void include(String name, Supplier<SearchAlgorithm> algo_getter) {
			include(name, 1, algo_getter);
		}
		public int size() {
			return size(0);
		}
		public int size(int initalIteration) {
			int size = 0;
			for (var name : map.keySet()) {
				int iters = map.get(name).iterations();
				size += iters > initalIteration? iters - initalIteration : 0;
			}
			return size;
		}
	}
	
	/**
	 * {@link #run(Stream, AlgoSetup, Duration, Limits, String, Optional, int, int, int, boolean, boolean, boolean, int)} with an infinite time limit and without any transposition tables.
	 * Do not use this if the algorithm setup contains algorithms which require a transposition table, this will result in a runtime error.
	 */
	public static void run(Stream<Supplier<ArtificialGamePosition>> trees, AlgoSetup algorithms, Limits limits, String write_to, Optional<String[]> read_from, int number_of_threads, boolean breakIterationOnSolve, boolean retry_unsolved, int initial_iteration) {
		run(trees, algorithms, Duration.ofSeconds(Long.MAX_VALUE), limits, write_to, read_from, number_of_threads, 0, 0, true, breakIterationOnSolve, retry_unsolved, initial_iteration);
	}
	/**
	 * {@link #run(Stream, AlgoSetup, Duration, Limits, String, Optional, int, int, int, boolean, boolean, boolean, int)} with an infinite time limit.
	 */
	public static void run(Stream<Supplier<ArtificialGamePosition>> trees, AlgoSetup algorithms, Limits limits, String write_to, Optional<String[]> read_from, int number_of_threads, int number_of_tables, int table_size, boolean breakIterationOnSolve, boolean retry_unsolved, int initial_iteration) {
		run(trees, algorithms, Duration.ofSeconds(Long.MAX_VALUE), limits, write_to, read_from, number_of_threads, number_of_tables, table_size, true, breakIterationOnSolve, retry_unsolved, initial_iteration);
	}
	/**
	 * Performs an algorithm comparison on the provided tree generator and algorithm set. This will keep performing runs for every tree in the generator.
	 * So provide a tree generator with a finite size or limit the overall runtime with the statically provided time limit with {@link #timeout_amount} and {@link #timeout_unit}.
	 * @param trees a finite stream of tree generators. Each tree supplier is called exactly once.
	 * @param algorithms The algorithm setup to apply.
	 * @param time_limit the time limit of each individual run, may be infinite.
	 * @param limits the spatial limits of each individual run, may be infinite, but not {@code null}.
	 * @param write_to the name of the file to write to.
	 * @param read_from the names of files to read from, may be empty.
	 * @param number_of_threads the number of computer threads to use for parallel computation of runs.
	 * @param number_of_tables the number of transposition tables to use for algorithms that use transposition tables, may be 0 if no algorithms use transposition tables.
	 * @param table_size the size of each transposition table used.
	 * @param saveTimeIntractable whether to save runs that exceeded the time limit.
	 * @param breakIterationOnSolve whether to stop iterating on runs of the same algorithm on the same tree when the first one of such iterations was solved.
	 * @param retry_unsolved whether to re-run unsolved runs with lower limits, as loaded from the provided files.
	 * @param initial_iteration the initial iteration number to apply to the algorithm setup. By default would be 0.
	 */
	public static void run(Stream<Supplier<ArtificialGamePosition>> trees, AlgoSetup algorithms, Duration time_limit, Limits limits, String write_to, Optional<String[]> read_from, int number_of_threads, int number_of_tables, int table_size, boolean saveTimeIntractable, boolean breakIterationOnSolve, boolean retry_unsolved, int initial_iteration) {
		long[] _skip_seeds = new long[0]; String[][] skip_algs = new String[0][];
		read: if (read_from.isPresent()) {
			boolean anyThere = false;
			for (String s : read_from.get())
				if (new File(s).exists()) anyThere = true;
			if (!anyThere) break read;
			long[][] __skip_seeds = new long[1][];
			String[][][] _skip_algs = new String[1][][];
			getAlreadyComputedRuns(read_from.get(), __skip_seeds, _skip_algs, breakIterationOnSolve, retry_unsolved, retry_unsolved);
			_skip_seeds = __skip_seeds[0];
			skip_algs = _skip_algs[0];
		}
		final long[] skip_seeds = _skip_seeds;
		final String[][] algs_skip = skip_algs;
		final String t_write_to = write_to.matches("[^\\.]*\\..*") ?
				write_to.replaceAll("(\\..*)", "\\.table$1")
				: write_to + ".table";
		final boolean append = new File(write_to).exists();
		final boolean t_append = new File(t_write_to).exists();
		
		if (!append)
			new File(write_to).getParentFile().mkdirs();

		final boolean hasTableSharing = number_of_tables > 0;
		final Table[] shared_tables = new Table[number_of_tables];
		final boolean[] table_occupied = new boolean[number_of_tables];
		final int[] num_waiting_for_table = new int[] {0};
		
		ExecutorService exec;
		if (number_of_threads <= 0) {
			throw new RuntimeException("Has to contain at least 1 thread");
		}
		else {
			writefln("Using fixed thread pool (%d)", number_of_threads);
			exec = Executors.newFixedThreadPool(number_of_threads);
		}
		flush();
		
		if (expansion_rate_printer) startExpansionRatePrinter(exec);
		
		try (
				var bw = new BufferedWriter(new FileWriter(write_to, append));
				var tw = hasTableSharing ? new BufferedWriter(new FileWriter(t_write_to, t_append)) : null)
		{
			if (!append) {
				bw.append("seed,adversarial,range,width,num_alts,growth_factor,force_relevance,algorithm,iteration,seconds,evaluated,expanded,nodes,intractable,expLimit,nodeLimit,solution,depth1Pes,depth2Opt,bestPes,altValue,sepValue,maxValue,remainingEffort\n");
				bw.flush();
			}
			if (!t_append && hasTableSharing) {
				tw.append("seed,algorithm,iteration,table_size,nodes_stored,retrievals,soft_collisions,hard_collisions\n");
				tw.flush();
			}
			trees.forEach(tree_getter -> {
				var tree = tree_getter.get();
				if (tree == null) return;
				final int skip_seed_index = Arrays.binarySearch(skip_seeds, tree.settings.initial_seed);
				algorithms.map.forEach((alg_name, alg_info) -> {
					final boolean[] wasSolved = {false};
					iter_loop: for (int iteration=initial_iteration; iteration < alg_info.iterations(); iteration++) {
						if (breakIterationOnSolve && wasSolved[0]) {
							recordProgress();
							continue iter_loop;
						}
						final int iter = iteration;
						Limits L = limits;
						if (alg_info.hasLimits()) L = alg_info.getLimits(iteration);
						final Limits _limits = L;
						if (skip_seed_index >= 0) {
							String[] skip_these = algs_skip[skip_seed_index];
							for (int J=0; J < skip_these.length; J++) {
								boolean[] isSolved = {false}, sameLimits = {false};
								boolean isSaved = checkIfSaved(skip_these[J],alg_info.name(),iter, isSolved, sameLimits, _limits);
								wasSolved[0] |= isSolved[0];
								if (
										(isSaved && !retry_unsolved) ||
										(isSaved && retry_unsolved && (sameLimits[0] || isSolved[0]))
									) {
									recordProgress();
									continue iter_loop;
								}
							}
						}
						Runnable task = () -> startRun(
								tree,alg_info,iter,hasTableSharing,table_size,shared_tables,table_occupied,
								num_waiting_for_table,time_limit,_limits,saveTimeIntractable,bw,tw,wasSolved);
						CompletableFuture.runAsync(() -> {
							try {
								if (breakIterationOnSolve && wasSolved[0]) {
									recordProgress();
									return;
								}
								task.run();
							} catch (Throwable e) {
								e.printStackTrace();
							}
						}, exec);
				}});
			});
			
			exec.shutdown();
			try {
				exec.awaitTermination(timeout_amount, timeout_unit);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (!exec.isTerminated()) exec.shutdownNow();
			bw.close();
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Execute a single run of one algorithm and one tree.
	 * @param tree the tree to search
	 * @param algorithm the algorithm to search with
	 * @param iteration the index of the iteration of this run, for the provided algorithm and tree pair
	 * @param hasTableSharing whether transposition tables are provided
	 * @param table_size the size of the provided transposition tables
	 * @param shared_tables the array containing all tables
	 * @param table_occupied the array holding information about which tables are occupied
	 * @param num_waiting_for_table a pointer-like value storing how many runs are awaiting a table
	 * @param time_limit the time limit of this run, may be infinite.
	 * @param limits the spatial limits of this run, may be infinite, may not be null.
	 * @param saveTimeIntractable whether to save this run if it exceeds the time limit.
	 * @param bw the buffered writer for writing to the output file for main results
	 * @param tw the buffered writer for writing to the output file for transposition table info results
	 * @param gotSolved a pointer-like value keeping track of whether this run has been solved, as is written to when this is the case. Existing {@code true} values do not get overridden, even if the run was unsolved.
	 */
	private static void startRun(
			ArtificialGamePosition tree, AlgoInfo algorithm, int iteration, boolean hasTableSharing,
			int table_size, Table[] shared_tables, boolean[] table_occupied, int[] num_waiting_for_table,
			Duration time_limit, Limits limits, boolean saveTimeIntractable, BufferedWriter bw, BufferedWriter tw,
			boolean[] gotSolved)
	{
		if (print_detailed_progress) {
			resetPrint();
			writeln("started on ("+tree.settings.initial_seed+", "+algorithm.name()+")");
			waitForFlush();
		}
		SearchAlgorithm algo = algorithm.get(tree, iteration);
		boolean useTable = algo instanceof SearchWithTable;
		SearchWithTable algoT = useTable ? (SearchWithTable) algo : null;
		SearchResult<?,ArtificialGamePosition> res = null;
		Table.Metrics table_metrics = null;
		MetricKeeper m = new MetricKeeper();
		Instant A, B;
		try {
			if (useTable) {
				// The following is preparation of the transposition table for algorithms that use one.
				// Setting up the table counts as prep-time, and is not counted for computation time.
				if (!hasTableSharing)
					throw new RuntimeException("The \""+algorithm.name()+"\" algorithm requires a table, but none were provided.");
				int[] tableID = new int[] {-1};
				Table table = waitForTable(table_size, shared_tables, table_occupied, num_waiting_for_table, tableID);
				A = Instant.now();
				res = algoT.search(tree, table, time_limit, limits, m);
				B = Instant.now();
				if (gotSolved.length > 0) gotSolved[0] |= res.complete();
				table_metrics = table.getMetrics();
				freeTable(table, shared_tables, table_occupied, num_waiting_for_table, tableID[0]);
			} else {
				A = Instant.now();
				res = algo.search(tree, time_limit, limits, m);
				B = Instant.now();
				if (gotSolved.length > 0) gotSolved[0] |= res.complete();
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
			recordSkip();
			return;
		}
		try {
			if (saveTimeIntractable || Duration.between(A, B).compareTo(time_limit) < 0 || res.complete()) {
				synchronized (bw) {
					var mostOpt2 = GameTreeNode.mostOptimistic2(res.root().children(), res.root().maximising());
					var leasPes2 = GameTreeNode.leastPessimistic2(res.root().children(),res.root().maximising());
					var s = tree.settings;
					if (print_detailed_progress) {
						resetPrint();
						write("Appended data for ("+s.initial_seed+", "+algorithm.name()+")");
						waitForFlush();
					}
					bw.append(String.format(Locale.CANADA,"%d,%s,%d,%d,%d,%f,%f,%s,%d,%f,%d,%d,%d,%s,%d,%d,%d,%d,%d,%f,%f,%f,%f,%f\n",
							s.initial_seed, s.adversarial ? "yes" : "no", s.initial_range, s.width, s.num_alts, s.growth_factor,
							s.force_relevance_chance,algorithm.name(),iteration,Duration.between(A, B).toNanos()/1_000_000_000d,
							m.evaluations(), m.expansions(), m.maxObservedNodes(), res.intractable() ? "yes" : "no",
							limits.maxExpansions(), limits.maxNodes(),
							res.bestMoveIndex()+1, mostOpt2.best().depthOfLower(), mostOpt2.secondbest().depthOfUpper(),
							leasPes2.best().lowerbound(), mostOpt2.secondbest().upperbound(), mostOpt2.best().lowerbound(),
							mostOpt2.best().upperbound(), res.root().remainingSolveEffort()));
					bw.flush();
					if (table_metrics != null) {
						tw.append(String.format("%d,%s,%d,%d,%d,%d,%d,%d\n",
								s.initial_seed, algorithm.name(), iteration, 1l << table_metrics.keySize(), table_metrics.numStored(),
								table_metrics.timesRetrieved(), table_metrics.softCollisions(), table_metrics.hardCollisions()));
						tw.flush();
					}
				}
				recordProgress();
			} else {
				recordSkip();
			}
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
			recordSkip();
		}
	}
	
	/**
	 * Runs wait for a table to become available in this method.
	 * @param table_size
	 * @param shared_tables
	 * @param table_occupied
	 * @param num_waiting_for_table
	 * @param tableID
	 * @return
	 */
	static Table waitForTable(int table_size, Table[] shared_tables, boolean[] table_occupied, int[] num_waiting_for_table, int[] tableID) {
		Table table = null;
		boolean isWaiting = false;
		waiting: while (table == null) {
			for (int k=0; k < shared_tables.length; k++) {
			synchronized (table_occupied) {
				// this is synchronized so there is zero ambiguity
				//  and each table is only assigned once at a time
				if (!table_occupied[k]) {
					table_occupied[k] = true;
					if (shared_tables[k] == null)
						shared_tables[k] = new Table(table_size);
					table = shared_tables[k];
					tableID[0] = k;
					break waiting;
				}
			} }
			if (!isWaiting) {
				// increment the wait counter so trees freed up will be reused.
				synchronized (num_waiting_for_table) {
					num_waiting_for_table[0]++;
				}
				isWaiting = true;
			}
			// wait until there is a table available
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		synchronized (num_waiting_for_table) { num_waiting_for_table[0]--; }
		return table;
	}
	
	/**
	 * Frees the provided table.
	 * @param table
	 * @param shared_tables
	 * @param table_occupied
	 * @param num_waiting_for_table
	 * @param table_ID
	 */
	static void freeTable(Table table, Table[] shared_tables, boolean[] table_occupied, int[] num_waiting_for_table, int table_ID) {
		// make the table free for the next:
		synchronized (table_occupied) {
			// if there are threads waiting for a table, reuse it
			if (num_waiting_for_table[0] > 0) table.clear();
			// if not, remove it
			else shared_tables[table_ID] = null;
			table_occupied[table_ID] = false;
		}
	}
	
	/**
	 * Concatenates the given streams a specified number of times.
	 * @param <T> the type of object the stream contains
	 * @param times the number of times to repeat the given stream
	 * @param streams a supplier of streams
	 * @return A stream containing all the elements from {@code times} number of calls to the {@code streams} stream supplier function.
	 */
	public static <T> Stream<T> repeat(int times, Supplier<Stream<T>> streams) {
		return IntStream.range(0, times).boxed().flatMap(i -> streams.get());
	}
	
	/**
     * Generates a stream of artificial game trees for all combinations of the provided parameters,
     * using a random seed for each tree generated from the given initial seed.
     *
     * @param adversarial      Array of booleans indicating whether the tree is adversarial ({@code true} or non-adversarial ({@code false}).
     * @param width            Array of branching factors (number of children per node).
     * @param range            Array of initial ranges for the root node.
     * @param alt_vals         Array of numbers of alternative values (distribution parameter).
     * @param growth_factor    Array of growth factors (distribution parameter).
     * @param force_relevance  Array of force relevance chances (probability children are relevant given a growth factor above 1).
     * @param initial_seed     Initial seed for the random number generator.
     * @return                 Stream of {@link ArtificialGamePosition} trees for all parameter combinations.
     */
	public static Stream<Supplier<ArtificialGamePosition>> getTrees(Boolean[] adversarial, Integer[] width, Long[] range, Integer[] alt_vals, Double[] growth_factor, Double[] force_relevance, long initial_seed) {
		Random r = new Random(initial_seed);
		return getTrees(adversarial, width, range, alt_vals, growth_factor, force_relevance, r::nextLong);
	}
	
	/**
     * Generates a stream of artificial game trees for all combinations of the provided parameters,
     * for each seed in the given array. For each seed, all parameter combinations are generated.
     *
     * @param adversarial      Array of booleans indicating whether the tree is adversarial ({@code true} or non-adversarial ({@code false}).
     * @param width            Array of branching factors (number of children per node).
     * @param range            Array of initial ranges for the root node.
     * @param alt_vals         Array of numbers of alternative values (distribution parameter).
     * @param growth_factor    Array of growth factors (distribution parameter).
     * @param force_relevance  Array of force relevance chances (probability children are relevant given a growth factor above 1).
     * @param seeds            Array of seeds; each seed is used for a full parameter grid.
     * @return                 Stream of {@link ArtificialGamePosition} trees for all seeds and parameter combinations.
     */
	public static Stream<Supplier<ArtificialGamePosition>> getTrees(Boolean[] adversarial, Integer[] width, Long[] range, Integer[] alt_vals, Double[] growth_factor, Double[] force_relevance, Long[] seeds) {
		return Stream.of(seeds).flatMap(seed -> getTrees(adversarial, width, range, alt_vals, growth_factor, force_relevance, () -> seed));
	}
	
	/**
     * Generates a stream of artificial game trees for all combinations of the provided parameters,
     * using a supplied seed for each tree. The seed supplier is called once for each tree generated.
     *
     * @param adversarial      Array of booleans indicating whether the tree is adversarial ({@code true} or non-adversarial ({@code false}).
     * @param width            Array of branching factors (number of children per node).
     * @param range            Array of initial ranges for the root node.
     * @param alt_vals         Array of numbers of alternative values (distribution parameter).
     * @param growth_factor    Array of growth factors (distribution parameter).
     * @param force_relevance  Array of force relevance chances (probability children are relevant given a growth factor above 1).
     * @param seeds            Supplier of seeds; called once per tree.
     * @return                 Stream of {@link ArtificialGamePosition} trees for all parameter combinations.
     */
	public static Stream<Supplier<ArtificialGamePosition>> getTrees(Boolean[] adversarial, Integer[] width, Long[] range, Integer[] alt_vals, Double[] growth_factor, Double[] force_relevance, LongSupplier seeds) {
		Object[][] groups = new Object[][]{adversarial, width, range, alt_vals, growth_factor, force_relevance};
		final int total = adversarial.length * width.length * range.length * alt_vals.length * growth_factor.length * force_relevance.length;
		int[] sizes = new int[groups.length];
	    for (int k = 0; k < groups.length; k++)
	    		sizes[k] = groups[k].length;
	    
		Supplier<Supplier<ArtificialGamePosition>> generator = new Supplier<Supplier<ArtificialGamePosition>>() {
		    int i = 0;
			@Override
			public Supplier<ArtificialGamePosition> get() {
				if (i < total) {
					int[] indices = new int[groups.length];
			        int remainder = i++;
			        for (int k = groups.length - 1; k >= 0; k--) {
			            indices[k] = remainder % sizes[k];
			            remainder /= sizes[k];
			        }
					return () -> new Settings(
							// adversarial (true) or non-adversarial (false)
							(Boolean) groups[0][indices[0]],
							// initial seed of the tree
							seeds.getAsLong(),
							// w = width; number of children per node; branching factor
							(Integer) groups[1][indices[1]],
							// R = range; initial range of root node (lowerbound is always 0)
							(Long)    groups[2][indices[2]],
							// k = number of alternative values; distribution parameter (higher means larger trees)
							(Integer) groups[3][indices[3]],
							// g = growth factor; distribution parameter (higher may mean larger trees, too high, smaller trees)
							(Double)  groups[4][indices[4]],
							// r = force relevance chance; chance that children are forced to be relevant (only used if g > 1)
							(Double)  groups[5][indices[5]]).getTree();
				}
				return () -> null;
			}
		};
		
		return Stream.generate(generator).limit(total);
	}
	
	/**
     * Generates a stream of variant artificial game trees for all combinations of the provided parameters,
     * using a supplied seed for each tree. The seed supplier is called once for each tree generated.
	 * 
	 * @param width a function to generate the number of children at each node
	 * @param distribution a function to generate node bounds at each node
	 * @return Stream of {@link VariantAGP} trees for all parameter combinations.
	 */
	public static Stream<Supplier<VariantAGP>> getTrees(Boolean[] adversarial, Integer[] maxWidth, Generator.Width[] width,
			Long[] range, Generator.Bounds[] distribution, Double[] growth_factor, Double[] force_relevance, LongSupplier seeds) {
		Object[][] groups = new Object[][]{adversarial, maxWidth, width, range, distribution, growth_factor, force_relevance};
		final int total = adversarial.length * maxWidth.length * width.length * range.length * distribution.length * growth_factor.length * force_relevance.length;
		int[] sizes = new int[groups.length];
	    for (int k = 0; k < groups.length; k++)
	    		sizes[k] = groups[k].length;
	    
		Supplier<Supplier<VariantAGP>> generator = new Supplier<Supplier<VariantAGP>>() {
		    int i = 0;
			@Override
			public Supplier<VariantAGP> get() {
				if (i++ < total) {
					int[] indices = new int[groups.length];
			        int remainder = i;
			        for (int k = groups.length - 1; k >= 0; k--) {
			            indices[k] = remainder % sizes[k];
			            remainder /= sizes[k];
			        }
					return () -> new VariantAGP.Settings(
							// adversarial (true) or non-adversarial (false)
							(Boolean) groups[0][indices[0]],
							// initial seed of the tree
							seeds.getAsLong(),
							// w = max-width; maximum number of children per node; maximum branching factor
							(Integer) groups[1][indices[1]],
							// W = width; generator for number of children per node; true branching factor
							(Generator.Width) groups[2][indices[2]],
							// R = range; initial range of root node (lowerbound is always 0)
							(Long)    groups[3][indices[3]],
							// D = distribution; function for generating node values
							(Generator.Bounds) groups[4][indices[4]],
							// g = growth factor; distribution parameter (higher may mean larger trees, too high, smaller trees)
							(Double)  groups[5][indices[5]],
							// r = force relevance chance; chance that children are forced to be relevant (only used if g > 1)
							(Double)  groups[6][indices[6]]).getTree();
				}
				return () -> null;
			}
		};
		
		return Stream.generate(generator).limit(total);
	}
	
	private static final String keep_delim = "((?<=%1$s)|(?=%1$s))";
	private static final String iterSep = "#", subIterSepUnsolved = "-", subIterSepSolved = "+";
	private static final String subIterSepAny = keep_delim.formatted("(\\+|-)");
	
	static void getAlreadyComputedRuns(long[][] _skip_seeds, String[][][] _skip_algs, boolean count_intractable_for_expansions_as_solved, boolean save_limits, boolean count_node_limit_exceeded_as_unsolved, String... files) {
		getAlreadyComputedRuns(files, _skip_seeds, _skip_algs, count_intractable_for_expansions_as_solved, save_limits, count_node_limit_exceeded_as_unsolved);
	}
	static void getAlreadyComputedRuns(String file1, String file2, String file3, long[][] _skip_seeds, String[][][] _skip_algs, boolean count_intractable_for_expansions_as_solved, boolean save_limits, boolean count_node_limit_exceeded_as_unsolved) {
		getAlreadyComputedRuns(new String[] {file1, file2, file3}, _skip_seeds, _skip_algs, count_intractable_for_expansions_as_solved, save_limits, count_node_limit_exceeded_as_unsolved);
	}
	static void getAlreadyComputedRuns(String file1, String file2, long[][] _skip_seeds, String[][][] _skip_algs, boolean count_intractable_for_expansions_as_solved, boolean save_limits, boolean count_node_limit_exceeded_as_unsolved) {
		getAlreadyComputedRuns(new String[] {file1, file2}, _skip_seeds, _skip_algs, count_intractable_for_expansions_as_solved, save_limits, count_node_limit_exceeded_as_unsolved);
	}
	static void getAlreadyComputedRuns(String previous_run_file, long[][] _skip_seeds, String[][][] _skip_algs, boolean count_intractable_for_expansions_as_solved, boolean save_limits, boolean count_node_limit_exceeded_as_unsolved) {
		getAlreadyComputedRuns(new String[] {previous_run_file}, _skip_seeds, _skip_algs, count_intractable_for_expansions_as_solved, save_limits, count_node_limit_exceeded_as_unsolved);
	}
	/**
	 * Reads from a file or set of files and recovers which runs have previously been completed so they can be skipped.
	 * @param files_of_previous_runs the file or files to read, may be empty.
	 * @param _skip_seeds pointer-like value, this contains part of the output of this method, contains the seeds which have runs that may be skipped.
	 * @param _skip_algs pointer-like value, this contains part of the output of this method, contains detailed information on which runs can be skipped.
	 * @param count_intractable_for_expansions_as_solved whether to record runs that were unsolved due to exceeding the limits as solved runs.
	 * @param save_limits whether to record the limits of a run in the output _skip_algs.
	 * @param count_node_limit_exceeded_as_unsolved whether to record solved runs exceeding the node limit as unsolved.
	 */
	static synchronized void getAlreadyComputedRuns(String[] files_of_previous_runs, long[][] _skip_seeds, String[][][] _skip_algs, boolean count_intractable_for_expansions_as_solved, boolean save_limits, boolean count_node_limit_exceeded_as_unsolved) {
		HashMap<Long, ArrayList<String>> runs = new HashMap<>();
		long total_runs = 0;
		
		for (String previous_run_file : files_of_previous_runs) {
			try (BufferedReader br = new BufferedReader(new FileReader(previous_run_file))) {
				String content = null;
				String[] data;
				
				int seed_column = -1, alg_column = -1, iter_column = -1, intrac_column = -1;
				int exp_column = -1, node_column = -1, expL_column = -1, nodeL_column = -1;
				data = (content = br.readLine()).split(",");
				for (int i=0; i < data.length; i++) {
					if (data[i].contains("seed")) seed_column = i;
					if (data[i].contains("algorithm")) alg_column = i;
					if (data[i].contains("iteration")) iter_column = i;
					if (data[i].contains("intractable")) intrac_column = i;
					if (data[i].contains("expanded")) exp_column = i;
					if (data[i].contains("nodes")) node_column = i;
					if (data[i].contains("expLimit")) expL_column = i;
					if (data[i].contains("nodeLimit")) nodeL_column = i;
				}
				if (seed_column < 0 || alg_column < 0)
					throw new IOException("Could not find 'seed' or 'algorithm' column in file "+previous_run_file+".");
				
				while((content = br.readLine()) != null) {
					data = content.split(",");
					if (!data[seed_column].matches("\\s*[\\d]+\\s*")) continue;
					long seed = Long.parseLong(data[seed_column]);
					ArrayList<String> algs = runs.get(seed);
					if (algs == null) runs.put(seed, algs = new ArrayList<>());
					if (iter_column >= 0) {
						String algline = data[alg_column];
						int iter = Integer.parseInt(data[iter_column]);
						ArrayList<Integer> iters = new ArrayList<>(iter);
						Map<Integer, Boolean> isSolved = new HashMap<>();
						int found = -1;
						for (int i=0; i < algs.size(); i++) {
							String[] alg_split = algs.get(i).split(iterSep);
							if (alg_split.length > 1 && alg_split[0].equals(algline)) {
								found = i;
								// parse the integer array stored inside the alg-string
								String[] arr = alg_split[1].split(subIterSepAny);
								for (int k=0; k < arr.length; k+=2) {
									int s_iter = Integer.parseInt(arr[k+1]);
									iters.add(s_iter);
									isSolved.put(s_iter, arr[k].contentEquals(subIterSepSolved));
								}
							}
						}
						boolean contains = iters.contains(iter);
						if (!contains || (iters.contains(iter) && !isSolved.get(iter))) {
							if (!contains) iters.add(iter);
							boolean solved = false;
							if (intrac_column >= 0)
								solved |= data[intrac_column].contains("no") || data[intrac_column].contains("0");
							if (count_intractable_for_expansions_as_solved && exp_column >= 0 && expL_column >= 0)
								solved |= Long.parseLong(data[exp_column]) >= Long.parseLong(data[expL_column]);
							if (count_node_limit_exceeded_as_unsolved && node_column >= 0 && nodeL_column >= 0)
								solved &= Long.parseLong(data[node_column]) < Long.parseLong(data[nodeL_column]);
							isSolved.put(iter, solved);
							total_runs += contains ? 0 : 1;
						}
						iters.sort(Comparator.naturalOrder());
						StringBuilder sb = new StringBuilder();
						for (int i=0; i < iters.size(); i++) {
							sb.append((isSolved.get(iters.get(i)) ? subIterSepSolved:subIterSepUnsolved)+iters.get(i));
						}
						if (save_limits && expL_column >= 0 && nodeL_column >= 0)
							sb.append(iterSep + data[expL_column] + iterSep + data[nodeL_column]);
						String res = String.format("%s%s%s",algline,iterSep,sb.toString());
						if (found < 0) algs.add(res);
						else algs.set(found, res);
					} else {
						algs.add(data[alg_column]);
						total_runs++;
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		long[] skip_seeds = _skip_seeds[0] = new long[runs.keySet().size()];
		String[][] skip_algs = _skip_algs[0] = new String[runs.keySet().size()][];
		int s_i = 0;
		for (Long seed : runs.keySet()) skip_seeds[s_i++] = seed;
		Arrays.sort(skip_seeds);
		for (int i=0; i < skip_seeds.length; i++) {
			ArrayList<String> algs = runs.get(skip_seeds[i]);
			skip_algs[i] = new String[algs.size()];
			int k_i = 0;
			for (String alg : algs) skip_algs[i][k_i++] = alg;
		}
		
		resetPrint();
		writefln("found %d seeds already (partially) completed, %d runs in total", runs.keySet().size(), total_runs);
	}
	
	/**
	 * Check if this algorithm with the specified iteration is stored in the previous file.
	 * @param skipAlgStr the array of algorithms to skip for this seed, generated by {@link #getAlreadyComputedRuns(String[], long[][], String[][][], boolean, boolean, boolean)}
	 * @param algorithm the algorithm being checked
	 * @param iteration the iteration being checked
	 * @return {@code true} if the provided string-line matches the provided algorithm 
	 * and already includes the provided iteration as saved. {@code false} otherwise.
	 */
	static boolean checkIfSaved(String skipAlgStr, String algorithm, int iteration, boolean[] wasSolved,
			boolean[] sameLimits, Limits limits) {
		String[] alg_split = skipAlgStr.split(iterSep);
		if (!alg_split[0].equals(algorithm)) return false;
		if (alg_split.length <= 1) return iteration == 0;
		// parse the integer array stored inside the alg-string
		String[] arr = alg_split[1].split(subIterSepAny);
		for (int k=0; k < arr.length; k+=2) {
			if (Integer.parseInt(arr[k+1]) == iteration) {
				wasSolved[0] = arr[k].contentEquals(subIterSepSolved);
				
				if (alg_split.length >= 4) {
					long expL = Long.parseLong(alg_split[2]);
					long nodL = Long.parseLong(alg_split[3]);
					if (expL == limits.maxExpansions() && nodL == limits.maxNodes())
						sameLimits[0] = true;
				}
				
				return true;
			}
		}
		return false;
	}
	
	//                                                    //
	// // // // // // // // // // // // // // // // // // //
	//   Methods for printing to the console efficiently  //
	// // // // // // // // // // // // // // // // // // //
	//                                                    //
	
	private static BufferedWriter buff_out = null;
	static void writeln(String s) {
		write(s+"\n");
	}
	static void writeln() {
		write("\n");
	}
	static void writef(String s, Object... os) {
		write(s.formatted(os));
	}
	static void writefln(String s, Object... os) {
		write(s.formatted(os)+"\n");
	}
	static void write(String s) {
		setBuffOut();
		try {
			buff_out.write(s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	static void flush() {
		if (buff_out == null) return;
		try {
			buff_out.flush();
			awaitsFlush = false;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static void waitForFlush() {
		Instant now = Instant.now();
		if (lastFlushTime == null || Duration.between(lastFlushTime, now).toMillis() > in_between_records_ms) {
			flush();
			lastFlushTime = now;
		} else {
			awaitsFlush = true;
			new Thread(() -> {
				try {
					Thread.sleep(in_between_records_ms);
					if (awaitsFlush) flush();
				} catch (InterruptedException e) {}
			}).start();
		}
	}
	static void closeAllPrints() {
		if (buff_out == null) return;
		try {
			buff_out.close();
			buff_out = null;
		} catch (IOException e) {
			System.err.println("already closed.");
		}
	}
	private static void setBuffOut() {
		if (buff_out == null)
			buff_out = new BufferedWriter(new OutputStreamWriter(System.out));
	}
	
	static boolean printReset = true;
	static void resetPrint() {
		if (!printReset) writeln();
		printReset = true;
	}
	public static void recordProgress(int set_total) {
		num_total = set_total;
		if (num_total < num_prints)
			num_prints = num_total;
		num_completed = 0l;
		num_skipped = 0l;
		lastFlushTime = null;
	}
	private static Instant lastFlushTime = null;
	private static boolean awaitsFlush = false;
	private final static long in_between_records_ms = 1000;
	synchronized static void recordProgress() {
		num_completed++;
		if ((num_completed+num_skipped) % (num_total / num_prints) == 0) {
			resetPrint();
			String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
			if (num_skipped <= 0)
				writefln("[%s] %4d/%4d : Completed runs of %5d trees out of %d",
						time, (num_prints * num_completed) / num_total, num_prints, num_completed, num_total);
			else
				writefln("[%s] %4d/%4d/%4d : Completed runs of %5d trees, and skipped %5d, out of %d",
						time, (num_prints * num_completed) / num_total, (num_prints * (num_completed+num_skipped)) / num_total,
						num_prints, num_completed, num_skipped, num_total);
			waitForFlush();
		}
	}
	synchronized static void recordSkip() {
		num_skipped++;
		if ((num_completed+num_skipped) % (num_total / num_prints) == 0) {
			resetPrint();
			String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
			writefln("[%s] %4d/%4d/%4d : Completed runs of %5d trees, and skipped %5d, out of %d",
					time, (num_prints * num_completed) / num_total, (num_prints * (num_completed+num_skipped)) / num_total,
					num_prints, num_completed, num_skipped, num_total);
			waitForFlush();
		}
	}
	
	/**
	 * Temporary method used to print the rate of expansion during runs. This causes a hit in performance, for debugging only.
	 * @param exec the thread pool executor being used for the experiments.
	 */
	public static void startExpansionRatePrinter(ExecutorService exec) {
		new Thread(() -> {
			int last_print_len = 0;
			while (!exec.isTerminated()) {
				long last_total = BstarBasic.num_expanded_total;
				if (last_total >= Long.MAX_VALUE/2) {
					last_total = BstarBasic.num_expanded_total = 0l;
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				String number = ""+(BstarBasic.num_expanded_total - last_total) / 1000;
				if (printReset) {
					write("per millisecond expansions: "+ number);
					printReset = false;
				}
				else {
					write("\b".repeat(last_print_len)+number);
				}
				flush();
				last_print_len = number.length();
			}
		}).start();
	}
	
}
