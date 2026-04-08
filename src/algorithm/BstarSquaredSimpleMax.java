package algorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import gametree.GameTreeNode;
import gametree.IGamePosition;
import gametree.MetricKeeper;
import gametree.ResultTreeNode;
import gametree.SearchTreeNode;

import static gametree.MetricKeeper.*;

/**
 * This implements B*²-max with the `dispose all' bounds preservation technique.
 * <br><br>
 * This variant, on the 'extra' branch, implements shallow and deep irrelevance pruning as an additional option
 * when initiating the SearchAlgorithm. The methods for this are {@link #useIrrelevanceStopping(boolean)} and {@link #useDeepIrrelevance(boolean)}.
 * Additionally, it is possible to let irrelevance stopping apply to all levels of search for the generalised multi-level B*^k with 
 * {@link #useIrrelevanceStoppingAtAllLevels(boolean)}. Finally, the {@link #useBonusEvaluations(boolean)} method toggles whether to use
 * additional function evaluations to provide additional information to irrelevance stopping. This option is not thoroughly described in the thesis,
 * but allows B*-squared with 'dispose all' to use irrelevance stopping more effectively, at a slight cost of increased function evaluations.
 * This is mostly untested, but can lead to fewer expansions and evaluations overall, but may also slightly decrease the efficiency of B*-squared.
 */
public class BstarSquaredSimpleMax implements SearchAlgorithm {

	/**
	 * @param L1_strategyFunction The strategy function to be used for L1 search.
	 * @param L2_strategyFunction The strategy function to be used for L2 search.
	 */
	public BstarSquaredSimpleMax(StopCondition extraStopCondition, StrategyFunction... strategyFunctions) {
		for (var s : strategyFunctions)
			if (s == null) throw new NullPointerException("Strategy function may not be `null`");
		if (strategyFunctions.length <= 0) throw new IllegalArgumentException("Must have at least one strategy function");
		if (strategyFunctions.length == 1)
			this.strategyFunctions = new StrategyFunction[] {strategyFunctions[0], StrategyFunction.PROVEBEST};
		else
			this.strategyFunctions = Arrays.copyOf(strategyFunctions, strategyFunctions.length);
		
		stopCondition = extraStopCondition == null ? StopCondition.NONE : extraStopCondition;
		variant = new VariantSetting();
	}
	/**
	 * @param L1_strategyFunction The strategy function to be used for L1 search.
	 * @param L2_strategyFunction The strategy function to be used for L2 search.
	 */
	public BstarSquaredSimpleMax(StrategyFunction... strategyFunctions) {
		this(StopCondition.NONE, strategyFunctions);
	}

	/**
	 * Uses the default {@link BstarBasic#PROVEBEST} strategy function for L2 search.
	 * @param L1_strategyFunction The strategy function to be used for L1 search.
	 */
	public BstarSquaredSimpleMax(StopCondition extraStopCondition, StrategyFunction L1_strategyFunction) {
		this(extraStopCondition, L1_strategyFunction, StrategyFunction.PROVEBEST);
	}
	/**
	 * Uses the default {@link BstarBasic#PROVEBEST} strategy function for L2 search.
	 * @param L1_strategyFunction The strategy function to be used for L1 search.
	 */
	public BstarSquaredSimpleMax(StrategyFunction L1_strategyFunction) {
		this(StopCondition.NONE, L1_strategyFunction, StrategyFunction.PROVEBEST);
	}

	private final StrategyFunction[] strategyFunctions;
	private final StopCondition stopCondition;
	
	private boolean expectIncorrectBounds = false;
	private VariantSetting variant;
	
	/**
	 * defaults to {@code false}
	 * @param set
	 */
	public void expectIncorrectBounds(boolean set) {
		expectIncorrectBounds = set;
	}
	public void useIrrelevanceStopping(boolean set) {
		variant.irrelevanceStopping = set;
	}
	public void useDeepIrrelevance(boolean set) {
		variant.deepIrrelevance = set;
		variant.irrelevanceStopping |= set;
	}
	public void useIrrelevanceStoppingAtAllLevels(boolean set) {
		variant.applyIrrelevanceStoppingToL3 = set;
		variant.irrelevanceStopping |= set;
	}
	/** This setting, if {@code true}, makes B* evaluate nodes with the evaluation
	 * function before they are explored with a second-level search.
	 * This allows pruning like shallow and deep irrelevance to work more consistently 
	 * at the cost of some additional evaluations. */
	public void useBonusEvaluations(boolean set) {
		variant.bonusEvalsOnCreation = set;
	}

	private int level = 1;
	void setLevel(int newLevel) { level = newLevel; }

	@Override
	public <P extends IGamePosition<P>> SearchResult<?, P>
			search(P root, Duration time_limit, Limits space_limit, MetricKeeper... metrics) {
		Instant start = Instant.now();
		
		MetricKeeper L1_metrics = new MetricKeeper("L"+level+" only");
		MetricKeeper L2_metrics = new MetricKeeper("L"+(level+1)+" total");
		MetricKeeper true_metrics = new MetricKeeper("L"+level+" true");

		var settings = new L1Position.Settings(level, start, space_limit, time_limit, L1_metrics, L2_metrics, true_metrics, metrics, strategyFunctions, variant);
		settings.expectIncorrectBounds = expectIncorrectBounds;
		L1Position<P> L1_root = new L1Position<P>(settings, root, root.lowerbound(), root.upperbound());
		incrementEvaluations(2, combineArrays(metrics, L1_metrics));
		
		// at first a tree with irrelevance pruning and position pruning to depth 2 was used.
		//  but if the node bounds are *not* 100% accurate (as is possible with B*-squared), then 
		//  irrelevance pruning destroys the structure of the tree in a way that solving the tree may 
		//  become impossible
		SearchTreeNodeModified<P> L1_tree = new SearchTreeNodeModified<P>(new SearchTreeNode.Settings(false,2,true,2,true), L1_root);
		L1_tree.setInplaceUpdates(settings.variant.requireModifications());
		L1_tree.setAllowModifications(settings.variant.requireModifications());
		L1_root.setTree(L1_tree);

		MetricKeeper[] l1_list = combineArrays(new MetricKeeper[] {L1_metrics}, metrics);
		adjustNodeCount(1, l1_list);
		StopCondition total_limit = (n, m) -> space_limit.reachedSum(L1_metrics, L2_metrics);
		BstarBasic b1 = new BstarBasic(total_limit.or(stopCondition), strategyFunctions[0]);
		b1.expectIncorrectBounds(true);
		var L1_result = b1.searchWithTree(L1_tree, time_limit, space_limit, l1_list);
		var result = new ResultTreeNode<>(L1_result.root(), l1 -> l1.original);
		true_metrics.setNodeCount(L1_metrics.nodes());
		true_metrics.incrementExpansions(L1_metrics.expansions());
		return new SearchResult<>(result, combineArrays(new MetricKeeper[] {true_metrics}, combineArrays(new MetricKeeper[] {L1_metrics, L2_metrics}, metrics)));
	}
	
	public static class SearchTreeNodeModified<P extends IGamePosition<P>> extends SearchTreeNode<L1Position<P>> {
		/**
		 * Change whenever major changes to class structure are made.
		 * @see 5.6.1 from <a href=https://docs.oracle.com/javase/6/docs/platform/serialization/spec/version.html#6678>docs.oracle.com</a>
		 */
		private static final long serialVersionUID = 1L;
		
		boolean marker = false;
		
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
		protected SearchTreeNodeModified<P> createChild(SearchTreeNode<L1Position<P>> parent, L1Position<P> position) {
			var result = new SearchTreeNodeModified<P>(parent.settings(), parent, position, parent.getAttachedMetrics());
			position.setTree(result);
			return result;
		}
		@Override
		public SearchTreeNodeModified<P> parent() {
			return (SearchTreeNodeModified<P>) super.parent();
		}
	}

	public static class L1Position<P extends IGamePosition<P>> implements IGamePosition<L1Position<P>> {

		final P original;
		final Settings s;
		private SearchTreeNodeModified<P> L0_tree = null;
		
		private boolean evaluated;
		private double lowerbound, upperbound;
		private long depthOfLower, depthOfUpper;
		
		private StopCondition stopping;
		
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
			
			public VariantSetting variant;

			public Settings(int level, Instant start, Limits space_limit, Duration time_limit, MetricKeeper L1_metrics, MetricKeeper L2_metrics, MetricKeeper true_metrics, MetricKeeper[] other_metrics, StrategyFunction[] strategyFunctions, VariantSetting variant) {
				this.level = level;
				this.start = start;
				this.space_limit = space_limit;
				this.time_limit = time_limit;
				this.L1_metrics = L1_metrics;
				this.L2_metrics = L2_metrics;
				this.true_metrics = true_metrics;
				this.other_metrics = other_metrics;
				this.strategyFunctions = strategyFunctions;
				this.variant = variant;
			}
		}

		private L1Position(Settings settings, P original, double lowerbound, double upperbound) {
			this(settings, original);
			this.lowerbound = lowerbound;
			this.upperbound = upperbound;
			evaluated = true;
		}
		public L1Position(Settings settings, P original) {
			s = settings;
			this.original = original;
			evaluated = false;
			
			stopping = StopCondition.NONE;
			if (s.variant.irrelevanceStopping && (s.level != 1 || s.variant.applyIrrelevanceStoppingToL3)) {
				stopping = (n, m) -> {
					if (!n.isRoot()) return false;
					SearchTreeNodeModified<P> p = L0_tree.parent();
					if (p == null) return false;
					double[] cBounds = (n instanceof SearchTreeNode) && ((SearchTreeNode<?>) n).allowModifications() ?
							((SearchTreeNode<?>) n).getBoundsPointer()
							: new double[] {n.lowerbound(m), n.upperbound(m)};
					double[] l0b = L0_tree.getBoundsPointer();
					if (l0b[0] == cBounds[0] && l0b[1] == cBounds[1]) return false;
					l0b[0] = cBounds[0];
					l0b[1] = cBounds[1];
					boolean stopSearching = irrelevanceCheck(p, cBounds);
					return stopSearching;
				};
			}
		}
		
		long getLimit() {
			long N = s.L1_metrics.maxObservedNodes();
			
			long M = N;
			
			if (!s.space_limit.limitMaxNodes()) return M;
			long res = Math.min(M, s.space_limit.maxNodes() - s.L1_metrics.nodes());
			return res;
		}
		
		public void setTree(SearchTreeNodeModified<P> tree) {
			L0_tree = tree;
		}

		/**
		 * 1. select node
		 * 2. expand node: creates children n1, n2, n3...
		 * 3. adjust bounds, this also (a) evaluates all children, and (b) may override previously computed bounds
		 * On evaluation, start a second-level search centred at that child.
		 * This can be very costly. How can we improve this?
		 * if we initialise L2 at evaluation time, and then save the bounds of the L2 root children, 
		 *   then can we reduce the number of expansions coming next?
		 */
		
		private boolean irrelevanceCheck(SearchTreeNodeModified<P> parent, double[] cBounds) {
			double[] pBounds = parent.getBoundsPointer();
			if (s.variant.bonusEvalsOnCreation && !parent.marker) {
				// this marker is only used here, to mark whether we need to shadow-update this parent's bounds
				parent.marker = true;
				
				// shadow update of parent's bounds:
				for (var child : parent.children()) {
					if (child.position() == this) {
						if (parent.maximising())
							pBounds[0] = Math.max(pBounds[0], cBounds[0]);
						else
							pBounds[1] = Math.min(pBounds[1], cBounds[1]);
						continue;
					} else {
						if (parent.maximising() && cBounds[1] <= pBounds[0])
							return true;
						else if (!parent.maximising() && cBounds[0] >= pBounds[1])
							return true;
					}
					double[] lowUpp = child.getBoundsPointer();
					// we do a sort of "half" shadow-update of the parent's bounds based 
					//  only on the 'original' evaluation function.
					if (parent.maximising())
						pBounds[0] = Math.max(pBounds[0], lowUpp[0] = child.position().originalLowerbound());
					else
						pBounds[1] = Math.min(pBounds[1], lowUpp[1] = child.position().originalUpperbound());
				}
			}
			// shallow irrelevance:
			if (!GameTreeNode.quickRelevant(parent.maximising(), pBounds[0], pBounds[1], cBounds[0], cBounds[1]))
				return true;
			// proof completion checking is now included in shallow irrelevance as well:
			//    might want to change that later (!)
			if (parent.isRoot()) {
				// check for proof completion:
				//  -- because we already know that this node is relevant (due to the shallow irrelevance check),
				//     we only need to check whether any other node is relevant, in which case the tree is not solved.
				var pchildren = parent.children();
				if (parent.maximising()) {
					for (var child : pchildren)
						if (child.getBoundsPointer()[1] > cBounds[0]) return false;
				} else {
					for (var child : pchildren)
						if (child.getBoundsPointer()[0] < cBounds[1]) return false;
				}
				return true;
			}
			if (!s.variant.deepIrrelevance) return false;
			// deep irrelevance:
			
			var pchildren = parent.children();
			
			if (parent.maximising()) {
				// lower bound can be raised
				pBounds[0] = Math.max(pBounds[0], cBounds[0]);
				// upper bound can be lowered
				pBounds[1] = cBounds[1];
				// but only as much as the siblings allow
				for (var child : pchildren)
					pBounds[1] = Math.max(pBounds[1], child.getBoundsPointer()[1]);
			} else {
				// upper bound can be lowered
				pBounds[1] = Math.min(pBounds[1], cBounds[1]);
				// lower bound can be raised
				pBounds[0] = cBounds[0];
				// same as above: only as siblings allow
				for (var child : pchildren)
					pBounds[0] = Math.min(pBounds[0], child.getBoundsPointer()[0]);
			}
			return irrelevanceCheck(parent.parent(), pBounds);
		}
		
		public void evaluate() {
			if (evaluated) return;
			MetricKeeper[] metrics = combineArrays(s.other_metrics, s.L2_metrics);
			
			{
				SearchAlgorithm b2;
				if (s.strategyFunctions.length == 2) {
					b2 = new BstarBasic(stopping, s.strategyFunctions[1]);
					((BstarBasic) b2).expectIncorrectBounds(s.expectIncorrectBounds);
				} else {
					b2 = new BstarSquaredSimpleMax(stopping, Arrays.copyOfRange(s.strategyFunctions, 1, s.strategyFunctions.length));
					((BstarSquaredSimpleMax) b2).setLevel(s.level+1);
					if (s.variant.applyIrrelevanceStoppingToL3) {
						((BstarSquaredSimpleMax) b2).useIrrelevanceStoppingAtAllLevels(true);
						((BstarSquaredSimpleMax) b2).useDeepIrrelevance(s.variant.deepIrrelevance);
					}
				}
				Duration elapsed_time = Duration.between(s.start, Instant.now());
				Duration time_remaining = s.time_limit.minus(elapsed_time);
				// limitEvaluations, maxEvaluations, limitExpansions, maxExpansions, limitMaxNodes, maxNodes
				Limits L2_limit = new Limits(
						s.space_limit.limitEvaluations(), s.space_limit.maxEvaluations() - s.true_metrics.evaluations(),
						s.space_limit.limitExpansions(), s.space_limit.maxExpansions() - s.L1_metrics.expansions() - s.true_metrics.expansions(),
						true, getLimit());
				
				// "metrics" includes L2_metrics, so this node count value of 1 is included in L2_metrics.nodes()
				adjustNodeCount(1, metrics);
				var L2_result = b2.search(original, time_remaining, L2_limit, metrics);
				lowerbound = L2_result.root().lowerbound();
				upperbound = L2_result.root().upperbound();
				depthOfLower = L2_result.root().depthOfLower();
				depthOfUpper = L2_result.root().depthOfUpper();
				evaluated = true;
				
				var result_metrics = L2_result.mainMetrics();
				s.true_metrics.incrementExpansions(result_metrics.expansions());
				s.true_metrics.incrementEvaluations(result_metrics.evaluations());
				s.true_metrics.setNodeCount(result_metrics.maxObservedNodes() + s.L1_metrics.nodes());
			}
			// all nodes stored in the L2 tree are removed, so we update the metric keepers accordingly
			adjustNodeCount(-s.L2_metrics.nodes(), metrics);
			s.true_metrics.setNodeCount(s.L1_metrics.nodes());
		}
		
		public Collection<L1Position<P>> next() {
			return original.next().stream().map(n -> {
				var res = new L1Position<P>(s, n);
				res.setTree(L0_tree);
				return res;
			}).toList();
		}
		public double upperbound() {
			evaluate();
			return upperbound;
		}
		public double lowerbound() {
			evaluate();
			return lowerbound;
		}
		public double originalUpperbound() {
			if (original instanceof L1Position<?>) return ((L1Position<?>) original).originalUpperbound();
			s.true_metrics.incrementEvaluations();
			return original.upperbound();
		}
		public double originalLowerbound() {
			if (original instanceof L1Position<?>) return ((L1Position<?>) original).originalLowerbound();
			s.true_metrics.incrementEvaluations();
			return original.lowerbound();
		}
		public long depthOfUpper() {
			evaluate();
			return depthOfUpper;
		}
		public long depthOfLower() {
			evaluate();
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
	
	public static class VariantSetting {
		// shallow and deep irrelevance stop-conditions:
		boolean irrelevanceStopping, deepIrrelevance, applyIrrelevanceStoppingToL3;
		// modification to expansions such that children in L1 start with 
		//  evaluation function values from the original evaluation function
		//  this helps with allowing the irrelevance stop conditions to work better:
		boolean bonusEvalsOnCreation;
		
		boolean requireModifications() {
			return irrelevanceStopping || bonusEvalsOnCreation;
		}
		
		public VariantSetting() {
			irrelevanceStopping = false;
			deepIrrelevance = false;
			applyIrrelevanceStoppingToL3 = false;
			bonusEvalsOnCreation = false;
		}
	}

}
