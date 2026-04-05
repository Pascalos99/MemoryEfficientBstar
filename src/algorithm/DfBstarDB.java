package algorithm;

import static gametree.GameTreeNode.leastPessimistic2;
import static gametree.GameTreeNode.mostOptimistic2;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;

import algorithm.SearchAlgorithm.SearchWithTable;
import gametree.DepthFirstNode;
import gametree.GameTreeNode;
import gametree.IGamePosition;
import gametree.MetricKeeper;

/**
 * This implementation of df-B* applies concepts from B* Disprove-Best into the 
 * internal node selection procedure.
 * <br><br>
 * It is important to note that DfBstar is very experimental. Giving the search more stack-size to work with may 
 * be the difference between completion and failure, but not always in the same direction. When searching 
 * at depths where ranges do not tighten any further (e.g.: [59,61] with discrete node values), the search 
 * will forever attempt to raise or lower the same node by 1 point, which is often less useful to the search 
 * than switching to a different node. 
 * <br><br>
 * This is a fundamental issue with df-Bstar, as the only information we have to set thresholds with is 
 * not effort information, the same additional effort does not lead to the same increase in bounds. 
 * Probability based search might help this, but it is unclear.
 */
public class DfBstarDB implements SearchWithTable {

	private final double sigma, epsilon, chance;
	private StrategyFunction strategyFunction;
	private StopCondition stopCondition;
	
	/** default value {@code 10} */
	private long initialMaxDepth;
	/** default value {@code 1} */
	private long depthIncrement;
	/** default value {@code 50000} */
	private long initialStackSize;
	
	public final DebugPrinter DB;
	
	public DfBstarDB(StopCondition extraStopCondition, StrategyFunction strategyFunction, double sigma, double epsilon, double selectAltChance) {
		if (strategyFunction == null) throw new NullPointerException("strategy function may not be `null`");
		if (sigma <= 0 || sigma > 0.5) throw new IllegalArgumentException(
				String.format("Sigma should be greater than 0 and less than or equal to 0.5 (got %.2f)",sigma));
		if (epsilon <= 0 || epsilon >= 1) throw new IllegalArgumentException(
				String.format("Epsilon should be between 0 and 1 exclusive (got %.2f)",sigma));
		if (selectAltChance < 0 || selectAltChance > 1) throw new IllegalArgumentException(
				String.format("SelectAltChance should be a valid probability between 0 and 1 inclusive (got %.2f)",selectAltChance));
		this.strategyFunction = strategyFunction;
		if (extraStopCondition == null) stopCondition = StopCondition.NONE;
		else stopCondition = extraStopCondition;
		this.sigma = sigma;
		this.epsilon = epsilon;
		chance = selectAltChance;
		DB = new DebugPrinter();
		
		initialMaxDepth = 10l;
		depthIncrement = 1l;
		initialStackSize = 50_000l;
	}
	public DfBstarDB(StrategyFunction strategyFunction, double sigma, double epsilon, double selectAltChance) {
		this(null, strategyFunction, sigma, epsilon, selectAltChance);
	}
	
	/**
	 * Whenever the search reaches a depth beyond the maximum depth, it returns to the root
	 * and increases the search depth by {@link #depthIncrement} before continuing the search.
	 * @param maxDepth The initial maximum depth of the search, defaults to {@code 10}
	 */
	public void setInitialMaxDepth(long maxDepth) {
		initialMaxDepth = maxDepth;
	}
	/**
	 * Whenever the search reaches a depth beyond the maximum depth, it returns to the root
	 * and increases the search depth by {@link #depthIncrement} before continuing the search.
	 * @param depthIncrement The depth increment of the search, defaults to {@code 1}
	 */
	public void setDepthIncrement(long depthIncrement) {
		this.depthIncrement = depthIncrement;
	}
	
	/**
	 * Whenever the search runs out of stack-size, it returns to the root and increases 
	 * the stack-size limit by a factor of {@code 2} before continuing the search.
	 * @param stackSize The initial stack-size of the search, defaults to {@code 50000}. 
	 * The exact meaning of this number is platform dependent, but the cost of choosing a value that 
	 * is too small is negligiable for large searches, whereas a value that is too large may reduce 
	 * memory available to the JVM, depending on the platform specific implementation.
	 */
	public void setInitialStackSize(long stackSize) {
		initialStackSize = stackSize;
	}
	
	@Override
	public <P extends IGamePosition<P>> SearchResult<?, P> search(P root, Table table, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
		DB.reset();
		DepthFirstNode<P> rootNode = DepthFirstNode.getTree(root, true, metrics);
		DB.debug("table size maximum = %d\n", table.keySize());
		MetricKeeper accounter = new MetricKeeper("internal-dfBstar");
		MetricKeeper[] M = MetricKeeper.combineArrays(new MetricKeeper[] {accounter}, metrics);
		dfBstar(rootNode, table, time_limit, space_limit, M);
		rootNode.adjustBounds(M);
		return new SearchResult<DepthFirstNode<P>, P>(rootNode, M);
	}
	
	private <P extends IGamePosition<P>> void dfBstar(DepthFirstNode<P> root, Table TT, Duration time_limit, Limits space_limit, MetricKeeper... M) {
		boolean[] foundError = {false,false};
		dfBstar(root, TT, time_limit, space_limit, foundError, M);
	}
	private <P extends IGamePosition<P>> void dfBstar(DepthFirstNode<P> root, Table TT, Duration time_limit, Limits space_limit, boolean[] foundError, MetricKeeper[] M) {
		// Initialise variables:
		Instant start = Instant.now();
		Duration elapsed_time = Duration.ZERO;
		boolean maximising = root.maximising();
		long maxDepth = initialMaxDepth;
		long stacksize = initialStackSize;
		DB.debug("starting with stack-size limit of %d\n", stacksize);
		DB.debug("starting with maximum depth of %d\n", maxDepth);
		// retrieve information from a previous search if available:
		boolean[] strategy = {true};
		var children = root.children(M);
		for (var child : children) child.readFromTT(TT);
		// now iterate until separation is reached or we run out of resources:
		while (!foundError[0]
				&& !stopCondition.stopSearching(root, M)
				&& !space_limit.reached(M[0]) // M[0] is the MetricKeeper named 'accounter' from the search(...) method above
				&& ((elapsed_time = Duration.between(start, Instant.now())).compareTo(time_limit) < 0)) {
			// sort children:
			var mostOpt2 = mostOptimistic2(children, maximising, M);
			// check for separation and stop searching if it is reached:
			if (mostOpt2.separation(M)) return;
			// determine the strategy:
			var leastPes2 = leastPessimistic2(children, maximising, M);
			boolean forcedPB = mostOpt2.best() != leastPes2.best();
			boolean usePB = forcedPB || strategyFunction.useProveBest(root, M);
			// next node selected depends on the strategy:
			var next = usePB ? mostOpt2.best() : mostOpt2.secondbest();
			// calculate the thresholds for the next search:
			double U2 = mostOpt2.secondbest().upperbound(M), L_ = leastPes2.best().lowerbound(M);
			double Ls = next.lowerbound(M), Us = next.upperbound(M);
			double tee = Math.min(U2, Ls + sigma * (Us - Ls));
			double tau = Math.max(L_, Us - sigma * (Us - Ls));
			double TEE = U2;
			double TAU = L_;
			if (forcedPB) {
				double upwardSuccessProb = (Us - L_ - sigma * (U2 - L_)) / (Us - Ls);
				double downwardSuccessProb = (L_ - Ls) / (Us - Ls);
				strategy[0] = upwardSuccessProb >= downwardSuccessProb;
				DB.debug("Evaluating a possible false-best node:\n%s",root.printTree(1, M));
				DB.debug("With upward prob %.2f and downward prob %.2f, we choose %s\n",
						upwardSuccessProb,downwardSuccessProb,strategy[0]?"ProveBest":"DisproveBest");
			} else
				strategy[0] = usePB;
			// perform a multiple iterative deepening search on the next node:
			DB.debugL("searching (% 6.1f,% 6.1f) with {% 8.3f,% 8.3f}\n", Ls, Us, tee, tau);
			DB.debugS("searching (% 5.1f,% 6.1f) with {% 6.2f,% 6.2f}", Ls, Us, tee, tau);
			
			final Duration timeLimit = time_limit.minus(elapsed_time);
			final long depthLimit = maxDepth;
			Thread midThread = new Thread(null, ()->{
				MID(next, tee, TEE, tau, TAU, TT, timeLimit, space_limit, foundError, strategy, depthLimit, M);
			},"MID-dfbstar",stacksize);
			midThread.start();
			try { 
				// we do not use multi-threading, but the thread allows us
				//  to set a separate stack-size limit for just that thread.
				// -- this may not work on all platforms (!)
				midThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Ls = next.lowerbound(M); Us = next.upperbound(M);
			DB.debugL(" ==> gets (% 6.1f,% 6.1f)\n", Ls, Us);
			DB.debugS(false, " ==> (% 5.1f,% 5.1f)\n", Ls, Us);
			
			// If stack-size limits were exceeded, increase them:
			if (foundError[0] && stacksize < Long.MAX_VALUE/2) {
				stacksize *= 2;
				DB.debug("increasing stack-size limit to %d\n", stacksize);
				foundError[0] = false;
			}
			// If depth limits were exceeded, increase them:
			if (foundError[1]) {
				if (maxDepth >= Long.MAX_VALUE) foundError[0] = true;
				else {
					maxDepth += depthIncrement;
					DB.debug("increasing maximum depth to %d\n", maxDepth);
					foundError[1] = false;
				}
			}
		}
	}
	
	private static Random randomiser = new Random();
	
	private <P extends IGamePosition<P>> void MID(DepthFirstNode<P> n, double tee, double TEE, double tau, double TAU, Table TT, Duration time_limit, Limits space_limit, boolean[] foundError, boolean[] usePB, long depth, MetricKeeper[] M) {
		Instant start = Instant.now();
		Duration elapsed_time = Duration.ZERO;
		
		// Initialise parent bounds:
		double L = n.lowerbound(M), U = n.upperbound(M);
		long hash = n.position().hash();
		// Save in Table if spot available (this is a zero-search entry)
		if (!TT.hasEntry(TT.getKey(hash))) TT.putInTable(hash, L, U);
		// Stop early if possible:
		if (L >= tee || U <= tau || L == U) {
			TT.putInTable(hash, L, U);
			DB.debugL("returned early at depth %d\n", n.depth());
			return;
		}
		// Stop if maximum depth reached:
		if (depth <= 0) {
			foundError[1] = true;
			DB.debugL("returned for reaching maximum depth at depth %d\n", n.depth());
			return;
		}
		// Stop if this is a terminal node:
		var children = new ArrayList<>(n.children(M));
		if (children.size() <= 0) {
			DB.debugL("returned for lack of children at depth %d\n", n.depth());
			return;
		}
		MetricKeeper.adjustNodeCount(children.size(), M);
		// This should prevent cycles:
		TT.putInTable(hash, L, U); // (but it likely does nothing in its current state)
		
		// Multiple Iterative Deepening:
		while (!foundError[0] && !foundError[1]
				&& !space_limit.reached(M[0]) // M[0] is the MetricKeeper named 'accounter' from the search(...) method above
				&& (elapsed_time = Duration.between(start, Instant.now())).compareTo(time_limit) < 0) {
			// Stop Condition (usually at the end of the previous iteration):
			if (L >= tee || U <= tau || L == U) {
				TT.putInTable(hash, L, U);
				DB.debugL("returned for reaching thresholds after iteration at depth %d\n", n.depth());
				break;
			}
			// Compute children bounds:
			boolean updateBounds = false;
			for (var child : children) {
				boolean newValTT = child.readFromTT(TT);
				updateBounds |= newValTT || !child.hasSavedBounds();
			}
			// Check for stop condition one more time (usually in the first iteration):
			if (updateBounds) {
				n.adjustBounds(children, M);
				L = n.lowerbound(M); U = n.upperbound(M);
				if (L >= tee || U <= tau || L == U) {
					TT.putInTable(hash, L, U);
					DB.debugL("returned for reaching thresholds after update at depth %d\n", n.depth());
					break;
				}
			}
			// Prune irrelevant children:
			final double l = L, u = U;
			int prevSize = children.size();
			children.removeIf(c -> c.lowerbound(M) > u || c.upperbound(M) < l);
			if (children.size() < prevSize) MetricKeeper.adjustNodeCount(children.size() - prevSize, M);
			// Sort children:
			var mostOpt2 = GameTreeNode.mostOptimistic2(children, n.maximising(), M);
			var best = mostOpt2.best(); var next = best;
			// Determine thresholds:
			double n_tee = tee, n_tau = tau;
			// If only one child is relevant, then tee and tau do not change
			if (children.size() > 1) {
				double L1 = best.lowerbound(M), U1 = best.upperbound(M);
				// Only apply DisproveBest logic if the parent node's goal does not align with the global goal:
				if (usePB[0] == n.maximising()) {
					var leastPes2 = GameTreeNode.leastPessimistic2(children, n.maximising(), M);
					var bestpes = leastPes2.best();
					// Only select an alternative if the best optimistic node is not the best pessimistic node
					if (bestpes != best && (chance >= 1 || randomiser.nextFloat() < chance)) {
						double bestStr = Double.NEGATIVE_INFINITY;
						double bestRng = Double.NEGATIVE_INFINITY;
						for (var child : children) {
							// Select the node with the highest 'chance' of succeeding the threshold in the desired direction
							double strength = n.maximising() ? child.upperbound(M) - TEE : TAU - child.lowerbound(M);
							double range = child.upperbound(M) - child.lowerbound(M);
							if (strength > bestStr || (strength == bestStr && range > bestRng)) {
								bestStr = strength;
								bestRng = range;
								next = child;
							}
						}
						double Us = next.upperbound(M), Ls = next.lowerbound(M);
						if (n.maximising()) {
							n_tau = Math.max(tee, Math.min(Us - epsilon * (Us - Ls), tau));
						} else {
							n_tee = Math.min(tau, Math.max(Ls + epsilon * (Us - Ls), tee));
						}
					}
				}
				if ( n.maximising() && next == best)
					n_tau = Math.max(tau, mostOpt2.secondbest().upperbound(M) - epsilon * (U1 - L1));
				if (!n.maximising() && next == best)
					n_tee =Math.min(tee, mostOpt2.secondbest().lowerbound(M) + epsilon * (U1 - L1));
				if (next != best) {
					DB.debugL("selected sub-optimal node (%.0f,%.0f) with {%.1f,%.1f} instead of best node (%.0f,%.0f) at depth %d\n",
							next.lowerbound(M), next.upperbound(M), n_tee, n_tau,
							best.lowerbound(M), best.upperbound(M), n.depth());
				}
			}
			// Recursive step:
			try {
				MID(next, n_tee, TEE, n_tau, TAU, TT, time_limit.minus(elapsed_time), space_limit, foundError, usePB, depth-1, M);
			} catch (StackOverflowError e) {
				// If the search ran out of stack-size memory,
				//  we relay this information back to the root and save our current progress
				DB.debugS(false, " (overflow at d=%d)", n.depth());
				DB.debugL("overflow at depth %d\n", n.depth());
				foundError[0] = true;
				DB.debugL("returned for overflow at depth %d\n", n.depth());
				n.adjustBounds(M);
				L = n.lowerbound(M); U = n.upperbound(M);
				TT.putInTable(hash, L, U);
				MetricKeeper.adjustNodeCount(-children.size(), M);
				return;
			}
			// Update parent bounds:
			n.adjustBounds(children, M);
			L = n.lowerbound(M); U = n.upperbound(M);
		}
		if (foundError[0])
			DB.debugL("returned for error at depth %d\n", n.depth());
		MetricKeeper.adjustNodeCount(-children.size(), M);
	}

}