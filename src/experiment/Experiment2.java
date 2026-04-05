package experiment;

import static algorithm.StrategyFunction.*;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import algorithm.*;
import algorithm.SearchAlgorithm.Limits;
import experiment.CompareAlgorithms.AlgoSetup;
import gametree.ArtificialGamePosition;

import static experiment.CompareAlgorithms.*;

/**
 * The second core experiment, with the goal of determining the exact memory footprint of B*-squared compared to B*
 */
public class Experiment2 {
	
	public static void main(String[] args) {
		writeln("Perform Experiment 2:");
		experiment2(0, 25, true, true, true);
		closeAllPrints();
	}

	public static void experiment2(int initialIter, int limitExtent, boolean doBstarRuns, boolean doSigmoidRuns, boolean doMaxRuns) {
		long initial_seed = 762391543368789717l;
		writeln("seed = "+initial_seed);
		int nr_of_trees_per_pair = 500;
		Boolean[] adversarial = {true};
		Double[] growthFactors = {1.2, 1.3, 1.4, 1.5};
		Double[] force_chances = {0.4};
		Integer[] num_alts = {5,6};
		Long[] ranges = {10_000_000l};
		Integer[] branching_factors = {5,10,15};
		// 4 * 1 * 2 * 3 * 5 = 120 trees
		
		// Table sharing settings:
		int numTables = 0; // 32 tables, each of 24MB is approx 0.77GB
		int tableSize = 20; // 1 << 20 = 2^20 = 1_048_576
		
		int num_threads1 = 5; //Runtime.getRuntime().availableProcessors() / 2;
		int num_threads2 = Runtime.getRuntime().availableProcessors();
		
		boolean filter_fast_runs = false;
		Duration fast_run_cutoff = Duration.ofMillis(50);
		
		Limits limits = new Limits(
				// Limit Evaluations:
				false, 0,
				// Limit Expansions:
				true, 10_000_000,
				// Limit Nodes:
				true, 15_000_000);
		
		boolean only_iterate_until_one_solved = false;
		boolean retry_unsolved_regular = true;
		boolean retry_unsolved_squared = false;
		String[] read_from = {"data/experiments/core_2_trial_1a.csv", "data/experiments/core_2_trial_1b.csv"};
		String write_to = "data/experiments/core_2_trial_1b.csv";
		
		int number_of_trees = nr_of_trees_per_pair * growthFactors.length * force_chances.length * num_alts.length * ranges.length * branching_factors.length * adversarial.length;
		Supplier<Stream<Supplier<ArtificialGamePosition>>> treesGen = () -> {
			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed++;}};
//			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed;}};
			return repeat(nr_of_trees_per_pair, () -> getTrees(adversarial, branching_factors, ranges, num_alts, growthFactors, force_chances, seed_gen));
		};

		AlgoSetup algos2 = new AlgoSetup();
		
		long[] nodeLims = {5,10,25,50,100,200,500,1000,2000,5000,10_000,15_000,25_000,50_000,75_000,115_000,150_000,200_000,275_000,350_000,450_000,600_000,700_000,850_000,1_000_000};
		final Limits[] sq_limits = new Limits[limitExtent]; //new Limits[nodeLims.length];
		for (int i=0; i < sq_limits.length; i++) {
			sq_limits[i] = new Limits(
					limits.limitEvaluations(), limits.maxEvaluations(),
					limits.limitExpansions(), limits.maxExpansions(),
					true, nodeLims[i]); }
		// TODO make a final selection
		if (doMaxRuns)
			algos2.include("squared_B_AR_ALT_max", sq_limits.length, 	() -> new BstarSquaredSimpleMax(B_AR, ALTERNATE()), i -> sq_limits[i]);
		if (doSigmoidRuns)
			algos2.include("squared_B_AR_ALT", sq_limits.length, 	() -> new BstarSquaredSimple(B_AR, ALTERNATE()), i -> sq_limits[i]);
//		algos2.include("df_B_AR", 							() -> new DfBstar2(B_AR, 0.1, 0.3));
		
//		algos2.include("squared_ALT_PB",   sq_limits.length, 	() -> new BstarSquaredSimple(ALTERNATE(), PROVEBEST), i -> sq_limits[i]);
//		algos2.include("df_ALT", 							() -> new DfBstar2(ALTERNATE(), 0.1, 0.3));
		// TODO implement custom tables for df-B* iterations
		
		if (doBstarRuns) {
			AlgoSetup algos1 = new AlgoSetup();
			algos1.include("regular_B_AR", 10, () -> new BstarBasic(B_AR));
			
			writefln("Starting progress on %d trees with %d algorithms", number_of_trees, algos1.size());
			recordProgress(number_of_trees * algos1.size());
			
			writeln("start Bstar-only run"); flush();
			run(treesGen.get(), algos1, limits, write_to, Optional.ofNullable(read_from), num_threads1, 0, 0, false, retry_unsolved_regular, 0);
		}
		
		if (doMaxRuns || doSigmoidRuns) {
			writefln("Starting progress on %d trees with %d algorithms", number_of_trees, algos2.size());
			
			if (filter_fast_runs) {
				recordProgress(number_of_trees * algos2.size(initialIter));
				writeln("start time-limited run"); flush();
				run(treesGen.get(), algos2, fast_run_cutoff, limits, write_to, Optional.ofNullable(read_from), num_threads2, numTables, tableSize, false, only_iterate_until_one_solved, retry_unsolved_squared, initialIter);
			}
			
			recordProgress(number_of_trees * algos2.size(initialIter));
			writeln("start varying limits run"); flush();
			run(treesGen.get(), algos2, limits, write_to, Optional.ofNullable(read_from), num_threads2, numTables, tableSize, only_iterate_until_one_solved, retry_unsolved_squared, initialIter);
		}
	}
}
