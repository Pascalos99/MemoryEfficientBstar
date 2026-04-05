package algorithm;

import static gametree.GameTreeNode.leastPessimistic2;
import static gametree.GameTreeNode.mostOptimistic2;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import algorithm.SearchAlgorithm.SearchWithTable;
import gametree.DepthFirstNode;
import gametree.GameTreeNode;
import gametree.IGamePosition;
import gametree.MetricKeeper;

/**
 * This is the purest df-B* implementation, developed as described in the thesis. However, this variant
 * had severe issues such as frequent stack overflows and behaviour of getting stuck at very deep sub-trees.
 * This is addressed in the DfBstar2.java implementation of df-B*.
 * <br><br>
 * Important to note that DfBstar is very experimental. Giving the search more stack-size to work with may 
 * be the difference between completion and failure, but not always in the same direction. When searching 
 * at depths where ranges do not tighten any further (e.g.: [59,61] with discrete node values), the search 
 * will forever attempt to raise or lower the same node by 1 point, which is often less useful to the search 
 * than switching to a different node. 
 * <br><br>
 * This is a fundamental issue with df-Bstar, as the only information we have to set thresholds with is 
 * not effort information, the same additional effort does not lead to the same increase in bounds. 
 * Probability based search might help this, but it is unclear.
 */
@Deprecated
public class DfBstar implements SearchWithTable {

	private final double sigma, epsilon;
	private StrategyFunction strategyFunction;
	private StopCondition stopCondition;
	
	public final DebugPrinter DB;
	
	public DfBstar(StopCondition extraStopCondition, StrategyFunction strategyFunction, double sigma, double epsilon) {
		if (strategyFunction == null) throw new NullPointerException("strategy function may not be `null`");
		if (sigma <= 0 || sigma > 0.5) throw new IllegalArgumentException(
				String.format("Sigma should be greater than 0 and less than or equal to 0.5 (got %.2f)",sigma));
		if (epsilon <= 0 || epsilon >= 1) throw new IllegalArgumentException(
				String.format("Epsilon should be between 0 and 1 exclusive (got %.2f)",sigma));
		this.strategyFunction = strategyFunction;
		if (extraStopCondition == null) stopCondition = StopCondition.NONE;
		else stopCondition = extraStopCondition;
		this.sigma = sigma;
		this.epsilon = epsilon;
		DB = new DebugPrinter();
	}
	public DfBstar(StrategyFunction strategyFunction, double sigma, double epsilon) {
		this(null, strategyFunction, sigma, epsilon);
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
		boolean[] foundError = {false};
		dfBstar(root, TT, time_limit, space_limit, foundError, M);
	}
	private <P extends IGamePosition<P>> void dfBstar(DepthFirstNode<P> root, Table TT, Duration time_limit, Limits space_limit, boolean[] foundError, MetricKeeper[] M) {
		Instant start = Instant.now();
		Duration elapsed_time = Duration.ZERO;
		boolean maximising = root.maximising();
		boolean lastError = false;
		boolean lastPB = false;
		// retrieve information from a previous search if available:
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
			boolean usePB = (mostOpt2.best() != leastPes2.best()) || strategyFunction.useProveBest(root, M);
			// if the last search lead to a stack overflow, switch to the other strategy if possible:
			if (lastError && lastPB==usePB && (mostOpt2.best() == leastPes2.best()))
				usePB = !lastPB;
			lastPB = usePB;
			// next node selected depends on the strategy:
			var next = usePB ? mostOpt2.best() : mostOpt2.secondbest();
			// calculate the thresholds for the next search:
			double U2 = mostOpt2.secondbest().upperbound(M), L_ = leastPes2.best().lowerbound(M);
			double Ls = next.lowerbound(M), Us = next.upperbound(M);
			double tee = Math.min(U2, Ls + sigma * (Us - Ls));
			double tau = Math.max(L_, Us - sigma * (Us - Ls));
			// perform a multiple iterative deepening search on the next node:
			DB.debugL("searching (% 6.1f,% 6.1f) with {% 8.3f,% 8.3f}\n", Ls, Us, tee, tau);
			DB.debugS("searching (% 5.1f,% 6.1f) with {% 6.2f,% 6.2f}", Ls, Us, tee, tau);
			MID(next, tee, tau, TT, time_limit.minus(elapsed_time), space_limit, foundError, 1l, M);
			Ls = next.lowerbound(M); Us = next.upperbound(M);
			DB.debugL(" ==> gets (% 6.1f,% 6.1f)\n", Ls, Us);
			DB.debugS(false, " ==> (% 5.1f,% 5.1f)\n", Ls, Us);
			
			/* this allows the search to back up to the root and continue the search when 
			 *  overflow occurs. But only once in a row. If it happens twice in a row, 
			 *  then the search terminates. */
			if (lastError && !foundError[0]) {
				lastError = false;
			} else if (foundError[0] && !lastError) {
				foundError[0] = false;
				lastError = true;
			}
		}
	}
	
	private <P extends IGamePosition<P>> void MID(DepthFirstNode<P> n, double tee, double tau, Table TT, Duration time_limit, Limits space_limit, boolean[] foundError, long depth, MetricKeeper[] M) {
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
			DB.debugL("returned early at depth %d\n", depth);
			return;
		}
		// Stop if this is a terminal node:
		var children = new ArrayList<>(n.children(M));
		if (children.size() <= 0) {
			DB.debugL("returned for lack of children at depth %d\n", depth);
			return;
		}
		MetricKeeper.adjustNodeCount(children.size(), M);
		// This should prevent cycles:
		TT.putInTable(hash, L, U); // (but it likely does nothing in its current state)
		
		// Multiple Iterative Deepening:
		while (!foundError[0]
				&& !space_limit.reached(M[0]) // M[0] is the MetricKeeper named 'accounter' from the search(...) method above
				&& (elapsed_time = Duration.between(start, Instant.now())).compareTo(time_limit) < 0) {
			// Stop Condition:
			if (L >= tee || U <= tau || L == U) {
				TT.putInTable(hash, L, U);
				DB.debugL("returned for reaching thresholds after iteration at depth %d\n", depth);
				break;
			}
			// Compute children bounds:
			boolean updateBounds = false;
			for (var child : children) {
				boolean newValTT = child.readFromTT(TT);
				updateBounds |= newValTT || !child.hasSavedBounds();
			}
			if (updateBounds) {
				n.adjustBounds(children, M);
				L = n.lowerbound(M); U = n.upperbound(M);
				if (L >= tee || U <= tau || L == U) {
					TT.putInTable(hash, L, U);
					DB.debugL("returned for reaching thresholds after update at depth %d\n", depth);
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
			var next = mostOpt2.best();
			// Determine thresholds:
			double n_tee = tee, n_tau = tau;
			// If only one child is relevant, then tee and tau do not change
			if (children.size() > 1) {
				double L1 = next.lowerbound(M), U1 = next.upperbound(M);
				if (n.maximising())
					n_tau = Math.max(tau, mostOpt2.secondbest().upperbound(M) - epsilon * (U1 - L1));
				else
					n_tee = Math.min(tee, mostOpt2.secondbest().lowerbound(M) + epsilon * (U1 - L1));
			}
			// Recursive step:
			try {
				MID(next, n_tee, n_tau, TT, time_limit.minus(elapsed_time), space_limit, foundError, depth-1, M);
			} catch (StackOverflowError e) {
				DB.debug("overflow at depth %d\n", depth+1);
				foundError[0] = true;
				DB.debugL("returned for overflow at depth %d\n", depth);
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
		if (foundError[0]) {
			DB.debugL("returned for error at depth %d\n", depth);
		}
		MetricKeeper.adjustNodeCount(-children.size(), M);
	}

}
