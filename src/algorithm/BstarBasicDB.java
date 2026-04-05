package algorithm;

import static gametree.GameTreeNode.leastPessimistic2;
import static gametree.GameTreeNode.mostOptimistic2;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Random;

import algorithm.SearchAlgorithm.SearchWithTree;
import gametree.GameTreeNode;
import gametree.GameTreeNode.Result;
import gametree.IGamePosition;
import gametree.MetricKeeper;
import gametree.SearchTreeNode;

/**
 * Disprove-Best B*, as described in the report https://github.com/Pascalos99/ReportNewAdvancesInBstar/blob/main/Anema2025.pdf
 */
public class BstarBasicDB implements SearchWithTree {
	
	public BstarBasicDB(StopCondition additional_stopcondition, StrategyFunction strategyFunction, double effort_ratio) {
		if (strategyFunction == null) throw new NullPointerException("strategy function may not be `null`");
		this.strategyFunction = strategyFunction;
		max_rootless_updates = 1000;
		this.effort_ratio = effort_ratio;
		
		filterTerminalNodes = true;
		randomChance = 0.35;

		if (additional_stopcondition == null) stopCondition = StopCondition.SEPARATION;
		else stopCondition = StopCondition.SEPARATION.or(additional_stopcondition);
	}
	public BstarBasicDB(StrategyFunction strategyFunction, double effort_ratio) {
		this(null, strategyFunction, effort_ratio);
	}

	private final StrategyFunction strategyFunction;
	private final StopCondition stopCondition;
	private final double effort_ratio;
	
	private boolean expectIncorrectBounds = true;
	
	// (1) max-rootless updates
	// (2) filter terminal nodes
	// (3) select random (35%)
	private long max_rootless_updates;
	private boolean filterTerminalNodes;
	private double randomChance;
	
	public void setMaxRootlessUpdates(long maxRootless) {
		max_rootless_updates = maxRootless;
	}
	public void disableMaxRootlessUpdates() {
		max_rootless_updates = Long.MAX_VALUE;
	}
	public void setFilterTerminalNodes(boolean set) {
		filterTerminalNodes = set;
	}
	public void setRandomChance(double probability) {
		randomChance = probability;
	}
	public void disableRandomChance() {
		randomChance = 0;
	}

	private volatile long rootless_updates;
	
	/**
	 * defaults to {@code true}
	 * @param set
	 */
	public void expectIncorrectBounds(boolean set) {
		expectIncorrectBounds = set;
	}
	
	@Override
	public <P extends IGamePosition<P>> SearchResult<?, P>
			search(P root, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
		SearchTreeNode<P> tree;
		if (expectIncorrectBounds)
			tree = SearchTreeNode.getTree(root, false, 2, true, 2, true);
		else tree = SearchTreeNode.getTree(root, true, 2, true, 2, false);
		return searchWithTree(tree, time_limit, space_limit, metrics);
	}
	
	@Override
	public <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> SearchResult<N, P> 
			searchWithTree(N root, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
		Instant start = Instant.now();
		rootless_updates = 0l;
		// this is the "main" metricKeeper for the algorithm, and thus the 'first' entry in the returned metrics array:
		MetricKeeper accounter = new MetricKeeper("internal-BstarDB");
		MetricKeeper[] M = MetricKeeper.combineArrays(new MetricKeeper[] {accounter}, metrics);
		boolean[] provebest = {true, false};
		double[] lowhigh = {Double.NaN, Double.NaN};
		N current = updateBounds(root, true, space_limit, M);
		while (
				// check time and space conditions first:
				Duration.between(start, Instant.now()).compareTo(time_limit) < 0 
				&& !space_limit.reached(accounter)
				// no need to check for separation if the current node is not root (thanks to back-propagaion)
				&& !stopCondition.stopSearching(current, M))
		{
			selectStrategy(current, provebest, lowhigh, M);
			N next;
			if (provebest[1])
				next = selectNextDB(current, lowhigh[0], lowhigh[1], M);
			else
				next = selectNext(current, provebest[0], M);

			boolean newExpansion = next != null && next.savedChildren().isEmpty();
			current = updateBounds(next == null ? current : next, next == null, space_limit, M);
			if (newExpansion) BstarBasic.num_expanded_total++;
		}
		if (current != root) updateBounds(current, true, space_limit, M);
		// insert "M" as the array for the metrics, such that the 'main' metrics are given first:
		return new SearchResult<N, P>(root, M);
	}
	
	private <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> N updateBounds(N current, boolean backup_to_root, Limits space_limit, MetricKeeper... m) {
		boolean updated_bounds = current.adjustBounds(m);
		while (
				(current.depth() > 0)
						&&
				(
						   backup_to_root 
						|| updated_bounds
						|| (backup_to_root |= rootless_updates > max_rootless_updates)
						|| (backup_to_root |= space_limit.reached(m[0]))
//						|| (backup_to_root |= stopCondition.stopSearching(current, m))
				)
		){
			current = current.parent();
			updated_bounds = current.adjustBounds(m);
		}
		if (current.depth() > 0) {
			rootless_updates++;
		} else {
			rootless_updates = 0l;
		}
		return current;
	}
	
	private <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> void selectStrategy(N node, boolean[] strategy, double[] relevant_range, MetricKeeper... metrics) {
		if (!node.isRoot()) return;
		Result<N, P> res1 = mostOptimistic2(node.children(metrics), node.maximising());
		Result<N, P> res2 = leastPessimistic2(node.children(metrics), node.maximising());
		relevant_range[0] = res2.best().lowerbound(metrics);
		relevant_range[1] = res1.secondbest().upperbound(metrics);
		if (res1.best() != res2.best()) {
			strategy[0] = true;
			strategy[1] = true; // this indicates that the choice of strategy was forced
		} else {
			strategy[0] = strategyFunction.useProveBest(node, metrics);
			strategy[1] = false; // the choice of strategy was not forced
		}
	}
	
	public static Random randomizer = new Random();
	
	private <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> N filterTerminal(N next, MetricKeeper... metrics) {
		return filterTerminalNodes? (isTerminal(next, metrics) ? null : next) : next;
	}
	
	private <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> boolean isTerminal(N node, MetricKeeper... metrics) {
		return node.lowerbound(metrics) == node.upperbound(metrics);
	}
	
	public <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> N selectNext(N current, boolean provebest, MetricKeeper... metrics) {
		Collection<N> children = current.children(metrics);
		if (children.size() <= 0) return null;
		if (children.size() == 1) return filterTerminal(children.iterator().next(), metrics);
		
		Result<N, P> resOpt = mostOptimistic2(children, current.maximising(), metrics);
		
//		/*
		if (!current.isRoot()) {
			
			if (randomChance > 0) {
				Result<N, P> resPes = leastPessimistic2(children, current.maximising(), metrics);
				if (resPes.best() == resOpt.best()) return filterTerminal(resOpt.best(), metrics);
				
				// only improves stand-alone B*:
	//			if (isTerminal(resPes.best()) && r.nextFloat() < 0.2) return filterTerminal(resPes.secondbest(), metrics);
	
				// TODO figure out why this works:
				if (!isTerminal(resPes.best(), metrics) && (randomChance >= 1 || randomizer.nextFloat() < randomChance))
					return filterTerminal(resPes.best(), metrics);
			}
			
			return filterTerminal(resOpt.best(), metrics);
		}
//		*/
//		if (!current.isRoot())
//			return filterTerminal(resOpt.best());
		
		return filterTerminal(provebest ? resOpt.best() : resOpt.secondbest(), metrics);
	}
	
	public static long hard_proof_counter = 0;
	public static long easy_proof_counter = 0;
	
	public <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> N selectNextDB(N current, double low, double high, MetricKeeper... metrics) {
		if (current.isRoot()) return selectNext(current, true, metrics);
		boolean max = current.maximising();
		var children = current.children(metrics);
		double summed_effort = 0, maximum_effort = 0;
		double minimum_effort = Double.POSITIVE_INFINITY;
		double range_easiest = -1, range_hardest = -1;
		N hardest = null, easiest = null;
		
		boolean hard_proof_impossible = false;
		int num_nodes_irrelevant = 0, num_nodes_easy_proof_impossible = 0;
		
		for (var child : children) {
			double lower = child.lowerbound(metrics);
			double upper = child.upperbound(metrics);
			double range = upper - lower;
			double effort_up = high - lower;
			double effort_down = upper - low;
			// if a node does not overlap with the range [low, high], it is irrelevant to the search
			// if we are maximising, the 'up' direction is the easiest proof: take the minimum effort
			// 					   , the 'down' direction is the harder proof: take the sum of the efforts
			// if we are minimising, the 'down' direction is the easiest proof: take the minimum effort
			// 					   , the 'up' direction is the harder proof: take the sum of the efforts
			double hard_effort = max ? effort_down : effort_up;
			double easy_effort = max ? effort_up : effort_down;
			boolean irrelevant = false;
			if (hard_effort > range) {
				hard_proof_impossible = true;
			}
			if (hard_effort <= 0) {
				irrelevant = true;
				num_nodes_irrelevant++;
			}
			if (easy_effort > range) {
				num_nodes_easy_proof_impossible++;
			}
			if (easy_effort <= 0) {
				// already solved?
				System.err.println("upwards solved from node "+current+"\n"+current.printTree(1, false, metrics));
				return null;
			}
			
			if (!irrelevant) {
				if (easy_effort <= range && (easy_effort < minimum_effort || (easy_effort == minimum_effort && range > range_easiest))) {
					minimum_effort = easy_effort;
					range_easiest = range;
					easiest = child;
				}
				if (hard_effort > maximum_effort || (hard_effort == maximum_effort && range < range_hardest)) {
					maximum_effort = hard_effort;
					range_hardest = range;
					hardest = child;
				}
				summed_effort += effort_ratio * hard_effort;
			}
		}
		
		if (num_nodes_irrelevant >= children.size()) {
			// all children are irrelevant - this means that the current node
			//  has achieved the downwards objective, further search in this sub-tree 
			//  is not adviced until bounds a and b are recomputed at root
			System.err.println("downwards solved from node "+current+"\n"+current.printTree(1, false, metrics));
			return null; // go back up to root
		}
		boolean easy_proof_impossible = num_nodes_easy_proof_impossible >= children.size();
		
		easy_proof_counter++;
		
		if (easy_proof_impossible || hard_proof_impossible) {
			// one of the two proofing directions, or both, are impossible in this node.
			// not clear if this should be able to occur.
			StringBuilder sb = new StringBuilder();
			if (easy_proof_impossible) sb.append("easy proof is impossible, ");
			if (hard_proof_impossible) sb.append("hard proof is impossible, ");
			sb.append("from node "+current+"\n");
			sb.append(current.printTree(1, false, metrics));
			System.err.println(sb);
			// In this case, default to returning `easiest` proof, as it is closest to the original B*
			return easiest;
		}
		
		if (minimum_effort <= summed_effort)
			return easiest;
		hard_proof_counter++;
		easy_proof_counter--;
		return hardest;
	}
}
