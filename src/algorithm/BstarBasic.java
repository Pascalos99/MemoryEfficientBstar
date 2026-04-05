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
 * This class implements B* with a few additional modifications:
 * <ol>
 * <li>if the number of back-ups without leading back to the root reaches 1000, the algorithm is forced to back-up to the root</li>
 * <li>if a terminal node is selected, the algorithm is forced to back-up to the root</li>
 * <li>if the current node's best child is not its least pessimistic child, there is a 30% chance that the least pessimistic child is selected,
 *  this will not occur if this would select a terminal node</li>
 * </ol>
 */
public class BstarBasic implements SearchWithTree {
	
	public BstarBasic(StopCondition extraStopCondition, StrategyFunction strategyFunction) {
		if (strategyFunction == null) throw new NullPointerException("strategy function may not be `null`");
		this.strategyFunction = strategyFunction;
		max_rootless_updates = 1000;
		
		filterTerminalNodes = true;
		randomChance = 0.3;

		if (extraStopCondition == null) stopCondition = StopCondition.SEPARATION;
		else stopCondition = StopCondition.SEPARATION.or(extraStopCondition);
	}
	public BstarBasic(StrategyFunction strategyFunction) {
		this(null, strategyFunction);
	}

	private final StrategyFunction strategyFunction;
	private final StopCondition stopCondition;
	
	private boolean expectIncorrectBounds = false;
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
	 * defaults to {@code false}
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
	
	public static volatile long num_expanded_total = 0l;
	
	@Override
	public <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> SearchResult<N, P> 
			searchWithTree(N root, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
		Instant start = Instant.now();
		rootless_updates = 0l;
		// this is the "main" metricKeeper for the algorithm, and thus the 'first' entry in the returned metrics array:
		MetricKeeper accounter = new MetricKeeper("internal-Bstar");
		MetricKeeper[] M = MetricKeeper.combineArrays(new MetricKeeper[] {accounter}, metrics);
		boolean[] provebest = {true};
		N current = updateBounds(root, true, space_limit, M);
		while (
				// check time and space conditions first:
				Duration.between(start, Instant.now()).compareTo(time_limit) < 0 
				&& !space_limit.reached(accounter)
				// no need to check for separation if the current node is not root (thanks to back-propagaion)
				&& !stopCondition.stopSearching(current, M))
		{
			selectStrategy(current, provebest, M);
			
			N next = selectNext(current, provebest[0], M);

			boolean newExpansion = next != null && next.savedChildren().isEmpty();
			current = updateBounds(next == null ? current : next, next == null, space_limit, M);
			if (newExpansion) num_expanded_total++;
			
		}
		if (current != root) updateBounds(current, true, space_limit, M);
		// insert "M" as the array for the metrics, such that the 'main' metrics are given first:
		return new SearchResult<N, P>(root, M);
	}
	
	private <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> N updateBounds(N current, boolean backup_to_root, Limits space_limit, MetricKeeper... m) {
		boolean updated_bounds = current.adjustBounds(m) || isTerminal(current, m);
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
			updated_bounds = current.adjustBounds(m) || isTerminal(current, m);
		}
		if (current.depth() > 0) {
			rootless_updates++;
		} else {
			rootless_updates = 0l;
		}
		return current;
	}
	
	private <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> void selectStrategy(N node, boolean[] strategy, MetricKeeper... metrics) {
		if (!node.isRoot()) return;
		Result<N, P> res1 = mostOptimistic2(node.children(metrics), node.maximising());
		Result<N, P> res2 = leastPessimistic2(node.children(metrics), node.maximising());
		if (res1.best() != res2.best())
			strategy[0] = true;
		else
			strategy[0] = strategyFunction.useProveBest(node, metrics);
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
		
		if (!current.isRoot()) {
			
			if (randomChance > 0) {
				Result<N, P> resPes = leastPessimistic2(children, current.maximising(), metrics);
				if (resPes.best() == resOpt.best()) return filterTerminal(resOpt.best(), metrics);
				
				// only improves stand-alone B*:
	//			if (isTerminal(resPes.best()) && r.nextFloat() < 0.2) return filterTerminal(resPes.secondbest(), metrics);
				// TODO
				// did I try simply using P(L' >= t) and P(U' <= T)?
				// using thresholds, there will be a plain and simple check based on bounds alone
				// we can still get stuck on nodes that do not like to converge, as could happen in a real game
				// but that is a problem for another time... could include a forced mostOpt-first if that is detected.
	
				// TODO figure out why this works:
				if (!isTerminal(resPes.best(), metrics) && (randomChance >= 1 || randomizer.nextFloat() < randomChance))
					return resPes.best();
			}
			
			return filterTerminal(resOpt.best(), metrics);
		}
		
		return filterTerminal(provebest ? resOpt.best() : resOpt.secondbest(), metrics);
	}
}
