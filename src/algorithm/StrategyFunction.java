package algorithm;

import static gametree.GameTreeNode.getComparator;

import java.util.Random;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import gametree.GameTreeNode;
import gametree.MetricKeeper;

/**
 * The heuristic function for determining the strategy at the root of any B* search.
 * This class contains several defaults for strategy functions, but you can also construct 
 * your own using this interface and several methods for constructing new strategy functions.
 */
public interface StrategyFunction {
	
	boolean useProveBest(GameTreeNode<?,?> root, MetricKeeper... metrics);
	
	boolean isDeterministic();
	
	@FunctionalInterface
	public static interface Deterministic extends StrategyFunction {
		public default boolean isDeterministic() {
			return true;
		}
	}
	
	@FunctionalInterface
	public static interface NonDeterministic extends StrategyFunction {
		public default boolean isDeterministic() {
			return false;
		}
	}
	
	//%% STRATEGY FUNCTIONS %%//
	// Naive Static Approaches:
	
	/** will ALWAYS use PROVEBEST */
	public static Deterministic PROVEBEST = (n, m) -> true;
	/** will use DISPROVEREST where possible */
	public static Deterministic DISPROVEREST = (n, m) -> false;
	
	// Naive Dynamic Approaches:
	
	/** will alternate between PROVEBEST and DISPROVEREST, starting at PROVEBEST;
	 * 	unless DISPROVEREST is impossible, which will suspend the alternation count */
	public static NonDeterministic ALTERNATE() {
		return new NonDeterministic() {
			int iter = 0;
			public boolean useProveBest(GameTreeNode<?,?> n, MetricKeeper... m) {
				return iter++%2==0;
			}
		};
	}
	
	/** will select randomly between PROVEBEST and DISPROVEREST when both are applicable */
	public static NonDeterministic RANDOM() { return RANDOM(System.currentTimeMillis()); }
	/** will select randomly between PROVEBEST and DISPROVEREST when both are applicable */
	public static NonDeterministic RANDOM(long seed) {
		return new NonDeterministic() {
			Random rand = new Random(seed);
			public boolean useProveBest(GameTreeNode<?,?> n, MetricKeeper... m) {
				return rand.nextBoolean();
			}
		};
	}
	
	// Berliner's Heuristic Approaches:
	
	/** Berliner's {@link #CRITERIUM_D(int)}, considering 1 alternative*/
	public static Deterministic B_2D = CRITERIUM_D_or_R(2, false);
	/** Berliner's {@link #CRITERIUM_D(int)}, considering 2 alternatives*/
	public static Deterministic B_3D = CRITERIUM_D_or_R(3, false);
	/** Berliner's {@link #CRITERIUM_D(int)}, considering all alternatives*/
	public static Deterministic B_AD = CRITERIUM_D_or_R(0, false);
	/** Berliner's {@link #CRITERIUM_R(int)}, considering 1 alternative*/
	public static Deterministic B_2R = CRITERIUM_D_or_R(2, true);
	/** Berliner's {@link #CRITERIUM_R(int)}, considering 2 alternatives*/
	public static Deterministic B_3R = CRITERIUM_D_or_R(3, true);
	/** Berliner's {@link #CRITERIUM_R(int)}, considering all alternatives */
	public static Deterministic B_AR = CRITERIUM_D_or_R(0, true);

	public static Deterministic P_2M1 = BY_SUBTREE_MAXSIZE(2, true, 1);
	public static Deterministic P_2S1 = BY_SUBTREE_SIZE(2, true, 1);
	public static Deterministic P_2X1 = BY_SUBTREE_EXPANSIONS(2, true, 1);
	public static Deterministic P_2M2 = BY_SUBTREE_MAXSIZE(2, true, 0.5);
	public static Deterministic P_2S2 = BY_SUBTREE_SIZE(2, true, 0.5);
	public static Deterministic P_2X2 = BY_SUBTREE_EXPANSIONS(2, true, 0.5);
	
	public static Deterministic P_3M1 = BY_SUBTREE_MAXSIZE(3, true, 1);
	public static Deterministic P_3S1 = BY_SUBTREE_SIZE(3, true, 1);
	public static Deterministic P_3X1 = BY_SUBTREE_EXPANSIONS(3, true, 1);
	public static Deterministic P_3M2 = BY_SUBTREE_MAXSIZE(3, true, 0.5);
	public static Deterministic P_3S2 = BY_SUBTREE_SIZE(3, true, 0.5);
	public static Deterministic P_3X2 = BY_SUBTREE_EXPANSIONS(3, true, 0.5);
	
	public static Deterministic P_AM1 = BY_SUBTREE_MAXSIZE(0, true, 1);
	public static Deterministic P_AS1 = BY_SUBTREE_SIZE(0, true, 1);
	public static Deterministic P_AX1 = BY_SUBTREE_EXPANSIONS(0, true, 1);
	public static Deterministic P_AM2 = BY_SUBTREE_MAXSIZE(0, true, 0.5);
	public static Deterministic P_AS2 = BY_SUBTREE_SIZE(0, true, 0.5);
	public static Deterministic P_AX2 = BY_SUBTREE_EXPANSIONS(0, true, 0.5);
	
	/**
	 * If the sum of the squares of the depths from which the optimistic
	 * bounds of the alternatives has been backed up is <b>less than</b> the square
	 * of the depth from which the value of the best arc has been backed up,
	 * then use DISPROVEREST; otherwise use PROVEBEST.
	 *
	 * @param num_alternatives number of alternatives (including best arc) to consider.
	 * 	Or any value {@code <1} to consider ALL. 
	 * @param divide_by_range toggle the use of the "R" criterium, where the square of the depth value is divided 
	 * by the node's range before summing and comparing them.
	 * @return a function which follows the described behavior for the
	 *   selected number of alternatives considered to determine the strategy.
	 *   This function will return {@code true} when PROVEBEST should be used, and {@code false} otherwise.
	 */
	public static Deterministic CRITERIUM_D_or_R(int num_alternatives, boolean divide_by_range) {
		// this function computes the D or R metric (depending on the `divide_by_range` tag
		HeuristicFunction D_or_R = (x, maximising, m) -> {
			double value = maximising ? x.depthOfUpper(m) : x.depthOfLower(m);
			value *= value;
			if (divide_by_range) return value / (x.upperbound(m) - x.lowerbound(m));
			return value;
		};
		return getHeuristicStrategyFunction(num_alternatives, true, D_or_R, D_or_R);
	}
	
	public static Deterministic BY_SUBTREE_MAXSIZE(int num_alternatives, boolean by_optimistic, double best_node_weight) {
		return BY_SUBTREE_PARAMS(num_alternatives, by_optimistic, met -> best_node_weight * met.maxObservedNodes(), met -> met.maxObservedNodes());
	}
	public static Deterministic BY_SUBTREE_SIZE(int num_alternatives, boolean by_optimistic, double best_node_weight) {
		return BY_SUBTREE_PARAMS(num_alternatives, by_optimistic, met -> best_node_weight * met.nodes(), met -> met.nodes());
	}
	public static Deterministic BY_SUBTREE_EXPANSIONS(int num_alternatives, boolean by_optimistic, double best_node_weight) {
		return BY_SUBTREE_PARAMS(num_alternatives, by_optimistic, met -> best_node_weight * met.expansions(), met -> met.expansions());
	}
	
	public static Deterministic BY_SUBTREE_PARAMS(int num_alternatives, boolean by_optimistic, ToDoubleFunction<MetricKeeper> bestNodeEvaluator, ToDoubleFunction<MetricKeeper> restNodesEvaluator) {
		final String metric_name = "SUBTREE_SIZE_METER";
		Function<ToDoubleFunction<MetricKeeper>,HeuristicFunction> func_getter = evaluator -> ((x, maximising, m) -> {
			MetricKeeper subtree_meter = null;
			boolean has_metrics = false;
			if (x.hasAttachedMetrics())
				for (var metric : x.getAttachedMetrics())
					if (metric.name.equals(metric_name)) {
						has_metrics = true;
						subtree_meter = metric;
						break;
					}
			if (!has_metrics) {
				subtree_meter = new MetricKeeper(metric_name,0,0,x.countSavedSubTree(true));
				x.attachMetrics(subtree_meter);
				x.updateMetrics();
			}
			return evaluator.applyAsDouble(subtree_meter);
		});
		return getHeuristicStrategyFunction(num_alternatives, by_optimistic, func_getter.apply(bestNodeEvaluator), func_getter.apply(restNodesEvaluator));
	}
	
	/**
	 * If the sum of the {@code restNodesFunction} values for the alternatives is <b>less than</b> the 
	 * {@code bestNodeFunction} value for the best node, 
	 * then use DISPROVEREST; otherwise use PROVEBEST.
	 *
	 * @param num_alternatives number of alternatives (including best node) to consider.
	 * 	Or any value {@code <1} to consider ALL. 
	 * @param by_optimistic if {@code true}, consider the nodes with better optimistic values to be 'better'. 
	 * If {@code false}, instead consider the nodes with better pessimistic values to be 'better'. This 
	 * ordering of nodes affects which node is selected as 'best' node and which nodes are selected as 
	 * alternative nodes if {@code num_alternatives} is not considering ALL alternative nodes.
	 * @param bestNodeFunction function to apply to the best node
	 * @param restNodesFunction function to apply to the alternative nodes
	 * @return a function which follows the described behavior for the
	 *   selected number of alternatives considered to determine the strategy.
	 *   This function will return {@code true} when PROVEBEST should be used, and {@code false} otherwise.
	 */
	public static Deterministic getHeuristicStrategyFunction(int num_alternatives, boolean by_optimistic, HeuristicFunction bestNodeFunction, HeuristicFunction restNodesFunction) {
		return (n, m) -> {
			if (num_alternatives == 1) return true;
			var children = n.children(m);
			// if there are 1 or fewer children, default to prove-best (return `true`):
			if (children == null || children.size() <= 1) return true;
			boolean maximising = n.maximising();
			// here we filter out all irrelevant children:
			children = children.stream().filter(c -> c.isRelevant(m)
					// this sorts the children by their best value first (depends on maximising and by_optimistic tags):
					).sorted(getComparator(maximising,by_optimistic,m)).toList();
			// if there is only 1 relevant node, default to prove-best (return `true`):
			if (children.size() <= 1) return true;
			double best_node_value = bestNodeFunction.evaluate(children.get(0), maximising, m);
			double sum_of_alts;
			if (num_alternatives > 1)
				sum_of_alts = children.stream().skip(1).limit(num_alternatives-1).mapToDouble(x -> restNodesFunction.evaluate(x, maximising, m)).sum();
			else
				sum_of_alts = children.stream().skip(1).mapToDouble(x -> restNodesFunction.evaluate(x, maximising, m)).sum();
			// compare the sums of squares:
			if (sum_of_alts < best_node_value)
				// `false` means to select disprove-rest, so we select the best alternative node
				return false;
			// `true` means to select prove-best, so we select the best node
			return true;
		};
	}
	
	@FunctionalInterface
	public static interface HeuristicFunction {
		public double evaluate(GameTreeNode<?,?> node, boolean maximising, MetricKeeper... metrics);
	}
	
}