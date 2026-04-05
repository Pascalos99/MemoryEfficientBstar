package experiment;

import static algorithm.StrategyFunction.*;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import algorithm.BstarBasic;
import algorithm.SearchAlgorithm.Limits;
import gametree.ArtificialGamePosition;

import static experiment.CompareAlgorithms.*;

/**
 * This is part of the preliminary experiments conducted to determine the value for
 *  P_{alt} as chosen in the thesis, which was found to at best be P_{alt}=0.3
 */
public class Experiment3 {

	public static void main(String[] args) {
		experiment3();
	}
	
	public static void experiment3() {
		long initial_seed = 6623708332940258034l; //new Random().nextLong();
		writeln("seed = "+initial_seed);
		int nr_of_trees_per_pair = 40;
		Boolean[] adversarial = {true};
		Double[] growthFactors = {1., 1.1, 1.2, 1.3, 1.4, 1.5};
		Double[] force_chances = {0., 0.2, 0.3, 0.4, 0.5, 0.6};
		Integer[] num_alts = {4,5,6};
		Long[] ranges = {10_000_000l};
		Integer[] branching_factors = {2,3,5,10,15,30};
		// 6 * 3 * 6 * 6 * 5 = 3240 trees -> *6 = 19440 trees
		
		// Table sharing settings:
		int numTables = 0; // only needed if df-B* is being used
		int tableSize = 20; // 1 << 20 = 2^20 = 1_048_576
		
		boolean filter_fast_runs = false;
		Duration fast_run_cutoff = Duration.ofSeconds(30);
		int num_threads_fast = Runtime.getRuntime().availableProcessors() - 8;
		
		int num_threads = Runtime.getRuntime().availableProcessors() / 2;
		Limits limits = new Limits(
				// Limit Evaluations:
				false, 0,
				// Limit Expansions:
				true, 5_000_000,
				// Limit Nodes:
				true, 1l << 20); // 1 << 20 = 2^20 = 1_048_576

		String location = "data/experiments/core_3_trial_2.csv";
		String read_from[] = {location};
		String write_to = location;
		
		int number_of_trees = nr_of_trees_per_pair * growthFactors.length * force_chances.length * num_alts.length * ranges.length * branching_factors.length * adversarial.length;
		Supplier<Stream<Supplier<ArtificialGamePosition>>> treesGen = () -> {
			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed++;}};
//			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed;}};
			return repeat(nr_of_trees_per_pair, () -> getTrees(adversarial, branching_factors, ranges, num_alts, growthFactors, force_chances, seed_gen));
		};
		
		AlgoSetup algos = new AlgoSetup();
		
		// name is based on the parameter index:
		double[] 	param1 = {0., 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7};
		for (int i=0; i < param1.length; i++) { final int I = i;
			algos.include(String.format("BAR_0%d",I), () -> {
				var alg = new BstarBasic(B_AR);
				alg.setRandomChance(param1[I]);
				return alg;
			});
		} // 10368 *5 = 51840
		for (int i=0; i < param1.length; i++) { final int I = i;
			algos.include(String.format("ALT_0%d",I), () -> {
				var alg = new BstarBasic(ALTERNATE());
				alg.setRandomChance(param1[I]);
				return alg;
			});
		}

		writefln("Starting progress on %d trees with %d algorithms", number_of_trees, algos.size());
		
		if (filter_fast_runs) {
			recordProgress(number_of_trees*algos.size());
			writeln("start time-limited run"); flush();
			run(treesGen.get(), algos, fast_run_cutoff, limits, write_to, Optional.ofNullable(read_from), num_threads_fast, numTables, tableSize, false, false, false, 0);
		}
		recordProgress(number_of_trees*algos.size());
		writeln("start unlimited run"); flush();
		run(treesGen.get(), algos, limits, write_to, Optional.ofNullable(read_from), num_threads, numTables, tableSize, false, false, 0);
		closeAllPrints();
	}
	
}
