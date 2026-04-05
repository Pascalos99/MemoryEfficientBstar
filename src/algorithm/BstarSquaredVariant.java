package algorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import gametree.GameTreeNode;
import gametree.IGamePosition;
import gametree.MetricKeeper;
import gametree.ResultTreeNode;
import gametree.SearchTreeNode;

import static gametree.MetricKeeper.*;

@Deprecated
public class BstarSquaredVariant implements SearchAlgorithm {

	public BstarSquaredVariant(StrategyFunction... strategyFunctions) {
		for (var s : strategyFunctions)
			if (s == null) throw new NullPointerException("Strategy function may not be `null`");
		if (strategyFunctions.length <= 0) throw new IllegalArgumentException("Must have at least one strategy function");
		if (strategyFunctions.length == 1)
			this.strategyFunctions = new StrategyFunction[] {strategyFunctions[0], StrategyFunction.PROVEBEST};
		else
			this.strategyFunctions = Arrays.copyOf(strategyFunctions, strategyFunctions.length);
	}

	/**
	 * Uses the default {@link BstarBasic#PROVEBEST} strategy function for L2 search.
	 * @param L1_strategyFunction The strategy function to be used for L1 search.
	 */
	public BstarSquaredVariant(StrategyFunction L1_strategyFunction) {
		this(L1_strategyFunction, StrategyFunction.PROVEBEST);
	}

	private final StrategyFunction[] strategyFunctions;
	
	private boolean expectIncorrectBounds = false;
	
	/**
	 * defaults to {@code false}
	 * @param set
	 */
	public void expectIncorrectBounds(boolean set) {
		expectIncorrectBounds = set;
	}

	private int level = 1;
	private void setLevel(int newLevel) { level = newLevel; }

	@Override
	public <P extends IGamePosition<P>> SearchResult<?, P>
			search(P root, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
		Instant start = Instant.now();

		MetricKeeper L1_metrics = new MetricKeeper("L"+level+" only");
		MetricKeeper L2_metrics = new MetricKeeper("L"+(level+1)+" total");
		MetricKeeper true_metrics = new MetricKeeper("L"+level+" true");

		var settings = new L1Position.Settings(level, start, space_limit, time_limit, L1_metrics, L2_metrics, true_metrics, metrics, strategyFunctions);
		settings.expectIncorrectBounds = expectIncorrectBounds;
		L1Position<P> L1_root = new L1Position<P>(settings, root, root.lowerbound(), root.upperbound());
		incrementEvaluations(2, combineArrays(metrics, L1_metrics));
		
		class SearchTreeNodeModified extends SearchTreeNode<L1Position<P>> {
			/**
			 * Change whenever major changes to class structure are made.
			 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
			 */
			private static final long serialVersionUID = 1L;
			
			public SearchTreeNodeModified(Settings settings, SearchTreeNode<L1Position<P>> parent,
					L1Position<P> position, MetricKeeper... metrics) {
				super(settings, parent, position, metrics);
			}
			public SearchTreeNodeModified(Settings settings, L1Position<P> position, MetricKeeper... metrics) {
				this(settings, null, position, metrics);
			}
			@Override
			public long _depthOfUpper(MetricKeeper... metrics) {
				return Math.max(super._depthOfUpper(metrics), position().depthOfUpper());
			}
			@Override
			public long _depthOfLower(MetricKeeper... metrics) {
				return Math.max(super._depthOfLower(metrics), position().depthOfLower());
			}
			@Override
			protected SearchTreeNode<L1Position<P>> createChild(SearchTreeNode<L1Position<P>> parent, L1Position<P> position) {
				var result = new SearchTreeNodeModified(parent.settings(), parent, position, parent.getAttachedMetrics());
				position.setTree(result);
				return result;
			}
		}
		// at first a tree with irrelevance pruning and position pruning to depth 2 was used.
		//  but if the node bounds are *not* 100% accurate (as is possible with B*-squared), then 
		//  irrelevance pruning destroys the structure of the tree in a way that solving the tree may 
		//  become impossible
		SearchTreeNode<L1Position<P>> L1_tree = new SearchTreeNodeModified(new SearchTreeNode.Settings(false,2,true,2,true), L1_root);
		L1_tree.setAllowModifications(true);
		L1_root.setTree(L1_tree);

		MetricKeeper[] l1_list = combineArrays(new MetricKeeper[] {L1_metrics}, metrics);
		adjustNodeCount(1, l1_list);
		StopCondition total_limit = (n, m) -> space_limit.reachedSum(L1_metrics, L2_metrics);
		BstarBasic b1 = new BstarBasic(total_limit, strategyFunctions[0]);
		b1.expectIncorrectBounds(true);
		var L1_result = b1.searchWithTree(L1_tree, time_limit, space_limit, l1_list);
		var result = new ResultTreeNode<>(L1_result.root(), l1 -> l1.original);
		true_metrics.setNodeCount(L1_metrics.nodes());
		true_metrics.incrementExpansions(L1_metrics.expansions());
		return new SearchResult<>(result, combineArrays(new MetricKeeper[] {true_metrics}, combineArrays(new MetricKeeper[] {L1_metrics, L2_metrics}, metrics)));
	}

	public static class L1Position<P extends IGamePosition<P>> implements IGamePosition<L1Position<P>> {

		public static class Settings {

			public final int level;
			public final Instant start;
			public final Limits space_limit;
			public final Duration time_limit;
			public final MetricKeeper L1_metrics;
			public final MetricKeeper L2_metrics;
			public final MetricKeeper true_metrics;
			public final MetricKeeper[] other_metrics;
			public final StrategyFunction[] strategyFunctions;
			
			public boolean expectIncorrectBounds = false;

			public Settings(int level, Instant start, Limits space_limit, Duration time_limit, MetricKeeper L1_metrics, MetricKeeper L2_metrics, MetricKeeper true_metrics, MetricKeeper[] other_metrics, StrategyFunction[] strategyFunctions) {
				this.level = level;
				this.start = start;
				this.space_limit = space_limit;
				this.time_limit = time_limit;
				this.L1_metrics = L1_metrics;
				this.L2_metrics = L2_metrics;
				this.true_metrics = true_metrics;
				this.other_metrics = other_metrics;
				this.strategyFunctions = strategyFunctions;
			}
		}

		private L1Position(Settings settings, P original, double lowerbound, double upperbound) {
			this(settings, original);
			this.lowerbound = lowerbound;
			this.upperbound = upperbound;
			evaluated = true;
			expanded = false;
			children = null;
		}
		public L1Position(Settings settings, P original) {
			s = settings;
			this.original = original;
			evaluated = false;
			expanded = false;
			children = null;
		}

		private static double param_a = 500_000;
		private static double param_b = 50_000;
		
		private static double param_c = 250_000;
		private static double param_d = 25_000;
		
		long getLimit() {
			long N = s.L1_metrics.maxObservedNodes();
//			long M = Math.round(4.5 * Math.log(N));
//			long M = 50;//N;
//			double param_a = 450_000;
//			double param_b = param_a * 0.1;
//			long M = Math.round(N / (1. + Math.exp((param_a - N) / param_b)));
			
			long M = N;
			if (s.level <= 1)
				M = Math.round(N / (1. + Math.exp((param_a - N) / param_b)));
			else
//				M = 20;
				M = Math.round(N / (1. + Math.exp((param_c - N) / param_d)));
			
			if (!s.space_limit.limitMaxNodes()) return M;
			long res = Math.min(M, s.space_limit.maxNodes() - s.L1_metrics.nodes());
			return res;
		}

		final P original;
		final Settings s;
		private SearchTreeNode<L1Position<P>> L0_tree = null;

		public void setTree(SearchTreeNode<L1Position<P>> tree) {
			L0_tree = tree;
		}

		private boolean evaluated, expanded, evaluating;
		private double lowerbound, upperbound;
		private long depthOfLower, depthOfUpper;
		private L1Position<?>[] children;
		
		public void expand() {
			if (expanded && evaluated) return;
			if (evaluating) throw new RuntimeException("Already Evaluating");
			evaluating = true;
			MetricKeeper[] metrics = combineArrays(s.other_metrics, s.L2_metrics);
			{
				adjustNodeCount(1, metrics);
				SearchAlgorithm b2;
				
				StopCondition stop = (n, m) -> {
					if (!n.isRoot()) return false;
//					assert (this == L0_tree.position()); // this is true
					if (!L0_tree.setBounds(n.lowerbound(metrics), n.upperbound(metrics))) return false;
					if (!L0_tree.isRelevant(metrics)) return true;
					
					// this may not be worth it, the overhead should be evaluated properly:
//					/*
					var p = L0_tree.parent();
					while (p != null) {
						var children = p.savedChildren();
						// this line was necessary to prevent a scenario where search shoots off to places it shouldn't:
						if (children.isEmpty() || children.get().stream().anyMatch(c -> !c.hasSavedBounds() && c != L0_tree))
							// whenever an ancestor has not yet evaluated all its children, we consider the current sub-tree relevant,
							//  - otherwise search gets stuck on trying to evaluate all the un-evaluated children.
							break;
						if (!p.adjustBounds(metrics)) break; // do not back up when bounds don't change
						if (!p.isRelevant() || (p.isRoot() && GameTreeNode.separation(p, metrics))) return true;
						p = p.parent();
					}
//					*/
					return false;
				};
				// on more than 2 levels, so far, the stop-condition seems to slow down search, 
				//  no matter whether it is on L2, L3 or both.
				if (s.level != 1 || s.strategyFunctions.length > 2) stop = null;
				
				if (s.strategyFunctions.length == 2) {
					b2 = new BstarBasic(stop, s.strategyFunctions[1]);
					((BstarBasic) b2).expectIncorrectBounds(s.expectIncorrectBounds);
				} else {
					b2 = new BstarSquaredVariant(Arrays.copyOfRange(s.strategyFunctions, 1, s.strategyFunctions.length));
					((BstarSquaredVariant) b2).setLevel(s.level+1);
				}
				Duration elapsed_time = Duration.between(s.start, Instant.now());
				Duration time_remaining = s.time_limit.minus(elapsed_time);
				// limitEvaluations, maxEvaluations, limitExpansions, maxExpansions, limitMaxNodes, maxNodes
				Limits L2_limit = new Limits(
						s.space_limit.limitEvaluations(), s.space_limit.maxEvaluations() - s.true_metrics.evaluations(),
						s.space_limit.limitExpansions(), s.space_limit.maxExpansions() - s.true_metrics.expansions(),
						true, getLimit());
				var L2_result = b2.search(original, time_remaining, L2_limit, metrics);
				
				lowerbound = L2_result.root().lowerbound();
				upperbound = L2_result.root().upperbound();
				depthOfLower = L2_result.root().depthOfLower();
				depthOfUpper = L2_result.root().depthOfUpper();
				evaluated = true;

				var L2_children = L2_result.root().children(metrics);
				children = new L1Position<?>[L2_children.size()];
				for (int i=0; i < children.length; i++) {
					var L2_child = L2_children.get(i);
					L1Position<P> child = new L1Position<P>(s, L2_child.position(), L2_child.lowerbound(metrics), L2_child.upperbound(metrics));
					child.setTree(L0_tree);
					children[i] = child;
				}
				expanded = true;
				
				var result_metrics = L2_result.mainMetrics();
				s.true_metrics.incrementExpansions(result_metrics.expansions());
				s.true_metrics.incrementEvaluations(result_metrics.evaluations());
				s.true_metrics.setNodeCount(result_metrics.maxObservedNodes() + s.L1_metrics.nodes());
			}
			adjustNodeCount(-s.L2_metrics.nodes(), metrics);
			s.true_metrics.setNodeCount(s.L1_metrics.nodes());
			evaluating = false;
		}
		
		@SuppressWarnings("unchecked")
		public Collection<L1Position<P>> next() {
			expand();
			List<L1Position<P>> result = new ArrayList<L1Position<P>>(children.length);
			for (var child : children) result.add((L1Position<P>) child);
			expanded = false;
			children = null;
			return result;
		}
		public double upperbound() {
			expand();
			return upperbound;
		}
		public double lowerbound() {
			expand();
			return lowerbound;
		}
		public long depthOfUpper() {
			if (!evaluating) expand();
			return depthOfUpper;
		}
		public long depthOfLower() {
			if (!evaluating) expand();
			return depthOfLower;
		}
		public boolean maximising() {
			return original.maximising();
		}
		public long hash() {
			return original.hash();
		}
		public String toString() {
			return String.format(Locale.CANADA, "%s%.1f-%.1f%s<-{%s}", maximising()?"[":"(",lowerbound,upperbound,maximising()?"]":")",original);
		}
	}

}
