package test;

import static algorithm.StrategyFunction.*;

import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import algorithm.BstarBasicDB;
import algorithm.SearchAlgorithm.Limits;
import experiment.CompareAlgorithms;
import experiment.CompareAlgorithms.AlgoSetup;
import gametree.ArtificialGamePosition;

import static experiment.CompareAlgorithms.*;

public class Test_BstarBasicDB {
	
	public static void main(String[] args) {
		long initial_seed = 8000000l;
		int nr_of_trees_per_pair = 100;
		Boolean[] adversarial = {true};
		Double[] growthFactors = {1.5};
		Double[] force_chances = {0.5};
		Integer[] num_alts = {4};
		Long[] ranges = {100l};
		Integer[] branching_factors = {6};
		
		int num_threads = Runtime.getRuntime().availableProcessors();
		
		Limits limits = new Limits(
				// Limit Evaluations:
				false, 0,
				// Limit Expansions:
				true, 10_000_000,
				// Limit Nodes:
				true, 750_000);
		
		String location = "data/testing/_trash_results.csv"; //"data/testing/experimental_results_9.csv";
		String[] read_from = null;//location;
		String write_to = location;
		
		int number_of_trees = nr_of_trees_per_pair * growthFactors.length * force_chances.length * num_alts.length * ranges.length * branching_factors.length * adversarial.length;
		Supplier<Stream<Supplier<ArtificialGamePosition>>> treesGen = () -> {
			var seed_gen = new LongSupplier(){ long seed = initial_seed; public synchronized long getAsLong() { return seed++;}};
			return repeat(nr_of_trees_per_pair, () -> getTrees(adversarial, branching_factors, ranges, num_alts, growthFactors, force_chances, seed_gen));
		};
		
		AlgoSetup algos = new AlgoSetup();
		
		algos.include("regularDB", t -> new BstarBasicDB(B_AR, 2./t.settings.width));
		
		System.out.println("Starting progress on "+number_of_trees+" trees with "+algos.size()+" algorithms");
		recordProgress(number_of_trees*algos.size());
		
		CompareAlgorithms.num_prints = Math.min(1000, number_of_trees * algos.size());
		run(treesGen.get(), algos, limits, write_to, Optional.ofNullable(read_from), num_threads, false, false, 0);
	}
	
}
