package experiment;

import static algorithm.StrategyFunction.*;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import algorithm.*;
import algorithm.SearchAlgorithm.Limits;
import gametree.ArtificialGamePosition;

import static experiment.CompareAlgorithms.*;

/**
 * The first core experiment, with the goal of performing a general comparison of algorithm variants.
 */
public class Experiment1 {
	
	enum Type {
		Regular, Squared, DepthFirst;
	}
	
	public static void main(String[] args) {
		writeln("Start B* runs:");
		experiment1(Type.Regular);
		writeln("Start B*-squared runs:");
		experiment1(Type.Squared);
		writeln("Start df-B* runs:");
		experiment1(Type.DepthFirst);
		closeAllPrints();
	}
	
	public static void experiment1(Type type) {
		long initial_seed = 6623708332940258033l; //new Random().nextLong();
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
		int numTables = type == Type.DepthFirst ? 32 : 0; // 32 tables, each of 24MB is approx 0.77GB
		int tableSize = 20; // 1 << 20 = 2^20 = 1_048_576
		
		boolean filter_fast_runs = false;
		Duration fast_run_cutoff = Duration.ofSeconds(30);
		int num_threads_fast = Runtime.getRuntime().availableProcessors() - 8;
		int num_threads = Runtime.getRuntime().availableProcessors();
		if (type == Type.Regular) num_threads = 8;
		
		Limits limits = new Limits(
				// Limit Evaluations:
				false, 0,
				// Limit Expansions:
				true, 5_000_000,
				// Limit Nodes:
				true, 1l << 20); // 1 << 20 = 2^20 = 1_048_576
		
		String write_to = null;
		String[] read_from = {};
		if (type == Type.Regular || type == Type.Squared) {
			write_to = "data/experiments/core_1_trial_2b.csv";
			read_from = new String[]{
					"data/experiments/core_1_trial_2a.csv",
					"data/experiments/core_1_trial_2b.csv"
			};
		} if (type == Type.DepthFirst) {
			write_to = "data/experiments/core_1_trial_3.csv";
			read_from = new String[] {
					"data/experiments/core_1_trial_1.csv",
					"data/experiments/core_1_trial_3.csv"
			};
		}
		
		int number_of_trees = nr_of_trees_per_pair * growthFactors.length * force_chances.length * num_alts.length * ranges.length * branching_factors.length * adversarial.length;
		Supplier<Stream<Supplier<ArtificialGamePosition>>> treesGen = () -> {
			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed++;}};
//			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed;}};
			return repeat(nr_of_trees_per_pair, () -> getTrees(adversarial, branching_factors, ranges, num_alts, growthFactors, force_chances, seed_gen));
		};
		
		AlgoSetup algos = new AlgoSetup();
		
		if (type == Type.Regular) {
			algos.include("regular_B_AR",		10, () -> new BstarBasic(B_AR));
			algos.include("regular_ALT",			10, () -> new BstarBasic(ALTERNATE()));
		}
		if (type == Type.Squared) {
			algos.include("squared_B_AR_ALT", 	10, () -> new BstarSquaredSimple(B_AR, ALTERNATE()));
			algos.include("squared_ALT_PB", 		10, () -> new BstarSquaredSimple(ALTERNATE(), PROVEBEST));
		}
		if (type == Type.DepthFirst) {
			algos.include("df_B_AR", 			3,	() -> new DfBstar2(B_AR, 0.1, 0.3));
			algos.include("df_ALT", 				3,	() -> new DfBstar2(ALTERNATE(), 0.1, 0.3));
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
	}
}
