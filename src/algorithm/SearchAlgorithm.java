package algorithm;

import java.time.Duration;

import gametree.GameTreeNode;
import gametree.IGamePosition;
import gametree.MetricKeeper;

/**
 * General interface for all search algorithms.
 */
public interface SearchAlgorithm {
	
	/**
	 * Initiates the search of the provided game tree with a given time limit and spatial limit.
	 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
	 * @param root root of the tree to be searched
	 * @param time_limit maximum time spent in the algorithm
	 * @param space_limit maximum node expansions, evaluations, and nodes saved in memory used by the algorithm
	 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
	 * @return result from the search
	 */
	<P extends IGamePosition<P>> SearchResult<?,P> search(P root, Duration time_limit, Limits space_limit, MetricKeeper... metrics);
	/**
	 * Initiates the search of the provided game tree without time limits.
	 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
	 * @param root root of the tree to be searched
	 * @param space_limit the spatial limits on this search
	 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
	 * @return result from the search
	 */
	default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, Limits space_limit, MetricKeeper... metrics) {
		// for all intents and purposes, this is no time limit (will continue searching until the end of the universe)
		return search(root, Duration.ofSeconds(Long.MAX_VALUE), space_limit, metrics);
	}
	/**
	 * Initiates the search of the provided game tree without spatial limits.
	 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
	 * @param root root of the tree to be searched
	 * @param time_limit maximum time spent in the algorithm
	 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
	 * @return result from the search
	 */
	default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, Duration time_limit, MetricKeeper... metrics) {
		return search(root, time_limit, Limits.NONE, metrics);
	}
	/**
	 * Initiates the search of the provided game tree without time or spatial limits.
	 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
	 * @param root root of the tree to be searched
	 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
	 * @return result from the search
	 */
	default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, MetricKeeper... metrics) {
		// for all intents and purposes, this is no time limit (will continue searching until the end of the universe)
		return search(root, Limits.NONE, metrics);
	}
	
	/**
	 * This represents a search algorithm which can search a given tree and modify it during search.
	 * Methods under this category can perform a search given an arbitrary tree, without making their
	 * own tree on the side internally.
	 */
	public static interface SearchWithTree extends SearchAlgorithm {
		/**
		 * Initiates the search of the provided game tree with a given time limit and spatial limit.
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param <N> The type of {@link GameTreeNode} which is used for executing the search
		 * @param root root of the tree to be searched
		 * @param time_limit maximum time spent in the algorithm
		 * @param space_limit maximum node expansions, evaluations, and nodes saved in memory used by the algorithm
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		<N extends GameTreeNode<N, P>, P extends IGamePosition<P>> SearchResult<N,P> searchWithTree(N root, Duration time_limit, Limits space_limit, MetricKeeper... metrics);
		/**
		 * Initiates the search of the provided game tree without time limits.
		 * @param <N> The type of {@link GameTreeNode} which is used for executing the search
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param space_limit the spatial limits on this search
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		default <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> SearchResult<N,P> searchWithTree(N root, Limits space_limit, MetricKeeper... metrics) {
			// for all intents and purposes, this is no time limit (will continue searching until the end of the universe)
			return searchWithTree(root, Duration.ofSeconds(Long.MAX_VALUE), space_limit, metrics);
		}
		/**
		 * Initiates the search of the provided game tree without spatial limits.
		 * @param <N> The type of {@link GameTreeNode} which is used for executing the search
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param time_limit maximum time spent in the algorithm
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		default <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> SearchResult<N,P> searchWithTree(N root, Duration time_limit, MetricKeeper... metrics) {
			return searchWithTree(root, time_limit, Limits.NONE, metrics);
		}
		/**
		 * Initiates the search of the provided game tree without time or spatial limits.
		 * @param <N> The type of {@link GameTreeNode} which is used for executing the search
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		default <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> SearchResult<N,P> searchWithTree(N root, MetricKeeper... metrics) {
			// for all intents and purposes, this is no time limit (will continue searching until the end of the universe)
			return searchWithTree(root, Limits.NONE, metrics);
		}
	}
	
	/**
	 * This represents a search algorithm which primarily stores search results in a transposition table.
	 * However, this also works for algorithms which are only supplemented by transposition tables.
	 */
	public static interface SearchWithTable extends SearchAlgorithm {
		/**
		 * Initiates the search of the provided game tree with a given time limit and spatial limit.
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param table table to use during search, can be non-empty
		 * @param time_limit maximum time spent in the algorithm
		 * @param space_limit maximum node expansions, evaluations, and nodes saved in memory used by the algorithm
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		<P extends IGamePosition<P>> SearchResult<?,P> search(P root, Table table, Duration time_limit, Limits space_limit, MetricKeeper... metrics);
		/**
		 * Initiates the search of the provided game tree without time limits.
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param table table to use during search, can be non-empty
		 * @param space_limit the spatial limits on this search
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, Table table, Limits space_limit, MetricKeeper... metrics) {
			// for all intents and purposes, this is no time limit (will continue searching until the end of the universe)
			return search(root, table, Duration.ofSeconds(Long.MAX_VALUE), space_limit, metrics);
		}
		/**
		 * Initiates the search of the provided game tree without spatial limits.
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param table table to use during search, can be non-empty
		 * @param time_limit maximum time spent in the algorithm
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, Table table, Duration time_limit, MetricKeeper... metrics) {
			return search(root, table, time_limit, Limits.NONE, metrics);
		}
		/**
		 * Initiates the search of the provided game tree without time or spatial limits.
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param table table to use during search, can be non-empty
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, Table table, MetricKeeper... metrics) {
			// for all intents and purposes, this is no time limit (will continue searching until the end of the universe)
			return search(root, table, Limits.NONE, metrics);
		}
		
		/**
		 * Initiates the search of the provided game tree with a given time limit and spatial limit.
		 * Also creates a table to search with, with the maximum possible size according to the spatial limit and available memory.
		 * @param <P> The type of {@link IGamePosition} which represents the type of game to be searched
		 * @param root root of the tree to be searched
		 * @param time_limit maximum time spent in the algorithm
		 * @param space_limit maximum node expansions, evaluations, and nodes saved in memory used by the algorithm
		 * @param metrics an array of {@link MetricKeeper} objects to keep track of evaluations, expansions, and node storage performed during the search, can be empty
		 * @return result from the search
		 */
		@Override
		default <P extends IGamePosition<P>> SearchResult<?,P> search(P root, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
			return search(root, Table.getTable(space_limit), time_limit, space_limit, metrics);
		}
	}
	
	/**
	 * Encapsulates the results and metrics captured from an adversarial tree search.
	 * @param root a game-tree node at the root position of the original tree. This node should at least
	 *        contain the updated optimistic and pessimistic values of the root's children nodes,
	 *        reflecting the updated values as a result of the search.
	 * @param metrics a list of metrics to be stored as part of this result. The first {@linkplain MetricKeeper} in the list
	 *  is considered the "main" metrics representing the result described by this object.
	 */
	public static record SearchResult<N extends GameTreeNode<N, P>, P extends IGamePosition<P>>(N root, MetricKeeper... metrics) {
		
		/**
		 * @return {@code true} if the tree resulting from search was terminated for intractability 
		 * (tree grew too large) rather than for completion of the proof. This is when there is no 
		 * separation at the root node after search has terminated. {@code false} is returned if 
		 * search terminated normally.
		 */
		public boolean intractable() {
			return !complete();
		}
		/**
		 * @return {@code true} if the tree resulting from search was terminated through separation. 
		 * This signifies that the proof has been <b>completed</b> and is therefore valid given the 
		 * assumption of a valid evaluation function and algorithm used to generate this result.
		 * {@code false} is returned if the resulting tree has not achieved separation and the proof 
		 * is likely not (yet) completed.
		 */
		public boolean complete() {
			return GameTreeNode.separation(root);
		}
		public GameTreeNode.Result<N, P> mostOptimisticNodes() {
			return GameTreeNode.mostOptimistic2(root.children(), root.maximising());
		}
		public GameTreeNode.Result<N, P> leastPessimisticNodes() {
			return GameTreeNode.leastPessimistic2(root.children(), root.maximising());
		}
		/**
		 * This is the position with the most optimistic value. 
		 * However, this may not be the proven best value (if the search did not reach separation) 
		 * as some other node may have the least pessimistic value (which may guarantee a better 
		 * value, but may have a lower optimistic value).
		 * @return the position with proven most optimistic value.
		 */
		public N mostOptimisticNode() {
			return mostOptimisticNodes().best();
		}
		/**
		 * @return the index of the move returned by {@link #mostOptimisticNode()}
		 */
		public int mostOptimisticNodeIndex() {
			return mostOptimisticNodes().bestindex();
		}
		/**
		 * This is the position with the least pessimistic value. This node guarantees the best 
		 * minimum return as proven by the search. Some other node may have a higher most optimistic 
		 * value (if the search did not reach separation), but may not guarantee the same pessimistic value.
		 * @return the position with the proven least pessimistic value.
		 */
		public N leastPessimisticNode() {
			return leastPessimisticNodes().best();
		}
		/**
		 * @return the index of the move returned by {@link #leastPessimisticNode()}
		 */
		public int leastPessimisticNodeIndex() {
			return leastPessimisticNodes().bestindex();
		}
		/**
		 * Alias for {@link #leastPessimisticNode()} followed by {@link GameTreeNode#position()}.<p> the tree is intractable, 
		 * the best position is the least pessimistic position. If the tree is not 
		 * intractable, {@link #leastPessimisticNode()} and {@link #mostOptimisticNode()} 
		 * return the same value: the best position.
		 * @return the position with the proven least pessimistic value (always) and most optimistic value (if 
		 * separation was reached during the search).
		 */
		public P bestMove() {
			return leastPessimisticNode().position();
		}
		/**
		 * @return the index of the best move as returned by {@link #bestMove()}
		 */
		public int bestMoveIndex() {
			return leastPessimisticNodeIndex();
		}
		
		public MetricKeeper mainMetrics() {
			if (metrics.length <= 0) return new MetricKeeper("data not present");
			return metrics[0];
		}
		
		/**
		 * @return A description of the results described by this object. Best used for printing or writing to a file read with a standard text editor.
		 */
		public String describe() {
	    	StringBuilder sb = new StringBuilder();
	    	sb.append(String.format("Result:\n\tseparation: %s\n\tmost optimistic: %d (%s)\n\tleast pessimistic: %d (%s)\n",
	    			complete()?"reached":"failed", mostOptimisticNodeIndex(), mostOptimisticNode().position(), leastPessimisticNodeIndex(), bestMove()));
	    	for (var metric : metrics) {
	    		sb.append(metric.describe());
	    	}
	    	return sb.toString();
	    }
	}
	
	/**
	 * The limits imposed on the search. This is used by B*-squared and by all algorithm variants 
	 *  for the implementation of the core experiments and its computational and spatial limits.
	 *  
	 *  @param limitEvaluations whether to limit the number of evaluations of the run.
	 */
	public static record Limits(boolean limitEvaluations, long maxEvaluations, boolean limitExpansions, long maxExpansions, boolean limitMaxNodes, long maxNodes) {
		
		public static final Limits NONE = new Limits(false,0,false,0,false,0);
		
		public boolean reached(MetricKeeper metrics) {
			return reachedEvaluations(metrics) || reachedExpansions(metrics) || reachedNodes(metrics);
		}
		
		public boolean reachedEvaluations(MetricKeeper metrics) {
			return limitEvaluations && metrics.evaluations() >= maxEvaluations;
		}
		public boolean reachedExpansions(MetricKeeper metrics) {
			return limitExpansions && metrics.expansions() >= maxExpansions;
		}
		public boolean reachedNodes(MetricKeeper metrics) {
			return limitMaxNodes && metrics.nodes() >= maxNodes;
		}
		
		public boolean reachedAny(MetricKeeper... metrics) {
			for (var m : metrics) if (reached(m)) return true;
			return false;
		}
		
		public boolean reachedAll(MetricKeeper... metrics) {
			if (metrics.length == 0) return false;
			for (var m : metrics) if (!reached(m)) return false;
			return true;
		}
		
		public boolean reachedSum(MetricKeeper... metrics) {
			long totalExpansions = 0l, totalEvaluations = 0l, totalNodes = 0l;
			for (var m : metrics) {
				totalExpansions += m.expansions();
				totalEvaluations += m.evaluations();
				totalNodes += m.nodes();
				if (limitExpansions && totalExpansions > maxExpansions) return true;
				if (limitEvaluations && totalEvaluations > maxEvaluations) return true;
				if (limitMaxNodes && totalNodes > maxNodes) return true;
			}
			return false;
		}
		
		public static Limits of(long maxEvaluations, long maxExpansions, long maxNodes) {
			return new Limits(true, maxEvaluations, true, maxExpansions, true, maxNodes);
		}
		
		public static Limits memoryLimit(long maxNodes) {
			return new Limits(false,0,false,0,true,maxNodes);
		}
		
		public Limits combine(Limits other) {
		    return new Limits(
		        this.limitEvaluations || other.limitEvaluations,
		        combineValue(this.limitEvaluations, this.maxEvaluations, other.limitEvaluations, other.maxEvaluations),
		        this.limitExpansions || other.limitExpansions,
		        combineValue(this.limitExpansions, this.maxExpansions, other.limitExpansions, other.maxExpansions),
		        this.limitMaxNodes || other.limitMaxNodes,
		        combineValue(this.limitMaxNodes, this.maxNodes, other.limitMaxNodes, other.maxNodes)
		    );
		}

		private static long combineValue(boolean aFlag, long aVal, boolean bFlag, long bVal) {
		    if (aFlag && bFlag) return Math.min(aVal, bVal);
		    if (aFlag) return aVal;
		    if (bFlag) return bVal;
		    return 0;
		}
	}
	
	/**
	 * Expands the search tree without stopping condition. This method most efficiently expands the tree to the 
	 * specified depth by only evaluating the nodes at that depth and back-propagating the resulting bounds changes. 
	 * This method does not stop if separation is achieved and will continue until all children at the specified depth 
	 * have been evaluated.<p>
	 * This method is preferably used scarcely, as the irrelevance of nodes is also not considered when expanding the tree. 
	 * This method works in a depth-first fashion without pruning.
	 * @param <N> Type of {@link GameTreeNode} used for this search
	 * @param <P> Type of {@link IGamePosition} which represents the tree to be searched
	 * @param node some initial node from which to expand the tree
	 * @param depth the maximum depth, as counted from the given {@code node}, to expand the tree to
	 * @param metrics {@link MetricKeeper} objects to keep track of expansions and evaluations made during this search
	 */
	public static <N extends GameTreeNode<N, P>, P extends IGamePosition<P>> void expand_tree_simple(N node, int depth, MetricKeeper... metrics) {
		if (depth <= 0) return;
		if (depth > 1)
			for (N child : node.children(metrics))
				expand_tree_simple(child, depth-1, metrics);
		node.adjustBounds(metrics);
	}
	
}
