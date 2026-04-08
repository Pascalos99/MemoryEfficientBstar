package algorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import gametree.*;

import static gametree.MetricKeeper.*;
import static gametree.GameTreeNode.*;

/**
 * This implements B*²-logistic with one of four available bounds preservation techniques.
 * <br><br>
 * As with the {@link BstarSquaredSimple} implementation on this branch, this implementation allows
 * for the optional use of shallow and deep irrelevance stopping.
 * <br><br>
 * Additionally, this implementation allows for the option of other bounds preservation techniques.
 * This is selected through the {@link #setAsDisposeAll(boolean, boolean, boolean)},
 * {@link #setAsKeepIgnore(boolean, boolean, boolean)}, {@link #setAsKeepLazy(boolean, boolean, boolean, boolean)},
 * and {@link #setAsKeepWindowed(boolean, boolean, boolean)} methods. Note that the variant should be selected and set
 * before any search is initiated, as this order results in the expected behavior.
 */
public class BstarSquaredKeep implements SearchAlgorithm {

	/**
	 * The first evaluation function provided corresponds to the function used by the first-level search.
	 * The number of strategy functions provided corresponds to the number of levels of search, with a minimum of two.
	 * @param extraStopCondition An additional stop condition for the search
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 */
	public BstarSquaredKeep(StopCondition extraStopCondition, StrategyFunction... strategyFunctions) {
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
	 * The first evaluation function provided corresponds to the function used by the first-level search.
	 * The number of strategy functions provided corresponds to the number of levels of search, with a minimum of two.
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 */
	public BstarSquaredKeep(StrategyFunction... strategyFunctions) {
		this(StopCondition.NONE, strategyFunctions);
	}

	/**
	 * Uses the default {@link BstarBasic#PROVEBEST} strategy function for the second-level search.
	 * @param extraStopCondition An additional stop condition for the search
	 * @param L1_strategyFunction The strategy function to be used for the first-level search.
	 */
	public BstarSquaredKeep(StopCondition extraStopCondition, StrategyFunction L1_strategyFunction) {
		this(extraStopCondition, L1_strategyFunction, StrategyFunction.PROVEBEST);
	}
	/**
	 * Uses the default {@link BstarBasic#PROVEBEST} strategy function for the second-level search.
	 * @param L1_strategyFunction The strategy function to be used for the first-level search.
	 */
	public BstarSquaredKeep(StrategyFunction L1_strategyFunction) {
		this(StopCondition.NONE, L1_strategyFunction, StrategyFunction.PROVEBEST);
	}

	private final StrategyFunction[] strategyFunctions;
	private final StopCondition stopCondition;
	
	private boolean expectIncorrectBounds = false;
	private VariantSetting variant;
	
	/**
	 * Whether or not to expect the evaluation function to provide incorrect bounds. Defaults to {@code false}.
	 * This increases the memory usage of the search if set to {@code true}.
	 * @param set
	 */
	public void expectIncorrectBounds(boolean set) {
		expectIncorrectBounds = set;
	}
	/**
	 * @param set if {@code true}, applies shallow irrelevance stopping to the search.
	 */
	public void useIrrelevanceStopping(boolean set) {
		variant.irrelevanceStopping = set;
	}
	/**
	 * @param set if {@code true}, applies shallow and deep irrelevance stopping to the search.
	 */
	public void useDeepIrrelevance(boolean set) {
		variant.deepIrrelevance = set;
		variant.irrelevanceStopping |= set;
	}
	/**
	 * Irrelevance stopping, if applied to all levels of the search, will also apply 
	 * deep irrelevance stopping (if this has been enabled by another method) to all
	 * levels of the search.
	 * @param set if {@code true}, applies shallow irrelevance stopping to the search for all levels
	 * of the search. This is specifically for the use of higher level search like B*-cubed or above.
	 */
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
	/**
	 * This sets the search to apply the 'full disposal' approach to bounds preservation. This means that the children of 
	 * the second-level root are never preserved and always revisited upon further investigation of that node as added
	 * to the first-level tree.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if any of the other two settings are also enabled,
	 * as they require shallow irrelevance to function.
	 * @param bonusEvals set to {@code true} to perform additional evaluations to provide extra information to irrelevance stopping.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 */
	public void setAsDisposeAll(boolean bonusEvals, boolean shallowIrrelevance, boolean deepIrrelevance) {
		variant.keepChildrenAfterL2		= false;
		variant.windowedPruning			= false;
		variant.bonusEvalsOnCreation	= bonusEvals;
		variant.irrelevanceStopping		= shallowIrrelevance || deepIrrelevance || bonusEvals;
		variant.deepIrrelevance			= deepIrrelevance;
	}
	/**
	 * This sets the search to apply the 're-search' approach to bounds preservation. This means that the children of the
	 * second-level root are preserved after the second-level search is terminated. These children are kept for if the node
	 * is later expanded, and also for shallow and deep irrelevance stopping, if enabled. They could also aim next node selection
	 * and move ordering at second-level searches, but this has not been implemented here.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if deep irrelevance is enabled.
	 * <br><br>
	 * The 'never discard' option defaults to {@code true} in the overloaded variant of this method: {@link #setAsKeepIgnore(boolean, boolean)}.
	 * If set to {@code false}, the second-level search may at times be re-executed to expand a node, which appears to have a major impact on performance.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param neverDiscard set to {@code true} to keep second-level root children in memory indefinitely, even after they have been requested through a node expansion.
	 * This could be set to {@code false} to save on some memory space, but could drastically decrease performance if the search often re-expands nodes.
	 */
	public void setAsKeepIgnore(boolean shallowIrrelevance, boolean deepIrrelevance, boolean neverDiscard) {
		variant.keepChildrenAfterL2		= true;
		variant.windowedPruning			= false;
		variant.keepChildrenAfterQuery	= neverDiscard;
		variant.keepLazyExpansions		= false;
		variant.bonusEvalsOnCreation	= false;
		variant.irrelevanceStopping		= shallowIrrelevance || deepIrrelevance;
		variant.deepIrrelevance			= deepIrrelevance;
	}
	/**
	 * This sets the search to apply the 'windowed' approach to bounds preservation. This means that the children of the
	 * second-level root are preserved after the second-level search is terminated. These children are kept for if the node
	 * is later expanded, and also for shallow and deep irrelevance stopping, if enabled. The bounds are also used for windowed
	 * pruning during second-level searches.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if deep irrelevance is enabled.
	 * <br><br>
	 * The 'never discard' option defaults to {@code true} in the overloaded variant of this method: {@link #setAsKeepWindowed(boolean, boolean)}.
	 * If set to {@code false}, the second-level search may at times be re-executed to expand a node, which appears to have a major impact on performance.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param neverDiscard set to {@code true} to keep second-level root children in memory indefinitely, even after they have been requested through a node expansion.
	 * This could be set to {@code false} to save on some memory space, but could drastically decrease performance if the search often re-expands nodes.
	 */
	public void setAsKeepWindowed(boolean shallowIrrelevance, boolean deepIrrelevance, boolean neverDiscard) {
		variant.keepChildrenAfterL2		= true;
		variant.windowedPruning			= true;
		variant.keepChildrenAfterQuery	= neverDiscard;
		variant.keepLazyExpansions		= false;	
		variant.bonusEvalsOnCreation	= false;
		variant.irrelevanceStopping		= shallowIrrelevance || deepIrrelevance;
		variant.deepIrrelevance			= deepIrrelevance;
	}
	/**
	 * This sets the search to apply the 'keep bounds' approach to bounds preservation. This means that the children of 
	 * the second-level root are preserved after the second-level search is terminated. These children are not used for irrelevance
	 * stopping or node selection, but rather determine the values of new nodes when generated. This reduces the reliance on
	 * second-level search and thus is somewhat closer to regular B* search.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if any of shallow irrelevance or bonus evaluations are also enabled,
	 * as they require shallow irrelevance to function.
	 * <br><br>
	 * The 'never discard' option defaults to {@code true} in the overloaded variant of this method: {@link #setAsKeepLazy(boolean, boolean)}.
	 * If set to {@code false}, the second-level search may at times be re-executed to expand a node, which appears to have a major impact on performance.
	 * @param bonusEvals set to {@code true} to perform additional evaluations to provide extra information to irrelevance stopping.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param neverDiscard set to {@code true} to keep second-level root children in memory indefinitely, even after they have been requested through a node expansion.
	 * This could be set to {@code false} to save on some memory space, but could drastically decrease performance if the search often re-expands nodes.
	 */
	public void setAsKeepLazy(boolean bonusEvals, boolean shallowIrrelevance, boolean deepIrrelevance, boolean neverDiscard) {
		variant.keepChildrenAfterL2		= true;
		variant.windowedPruning			= false;
		variant.keepChildrenAfterQuery	= neverDiscard;
		variant.keepLazyExpansions		= true;	
		variant.bonusEvalsOnCreation	= bonusEvals;
		variant.irrelevanceStopping		= shallowIrrelevance || deepIrrelevance || bonusEvals;
		variant.deepIrrelevance			= deepIrrelevance;
	}
	/**
	 * This sets the search to apply the 're-search' approach to bounds preservation. This means that the children of the
	 * second-level root are preserved after the second-level search is terminated. These children are kept for if the node
	 * is later expanded, and also for shallow and deep irrelevance stopping, if enabled. They could also aim next node selection
	 * and move ordering at second-level searches, but this has not been implemented here.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if deep irrelevance is enabled.
	 * <br><br>
	 * This method overloads {@link #setAsKeepIgnore(boolean, boolean, boolean)} with a default value of {@code true} for the
	 * 'never discard' variable. This generally improves the performance of B*-squared variants greatly.
	 * 
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 */
	public void setAsKeepIgnore(boolean shallowIrrelevance, boolean deepIrrelevance) {
		setAsKeepIgnore(shallowIrrelevance, deepIrrelevance, true); }
	/**
	 * This sets the search to apply the 'windowed' approach to bounds preservation. This means that the children of the
	 * second-level root are preserved after the second-level search is terminated. These children are kept for if the node
	 * is later expanded, and also for shallow and deep irrelevance stopping, if enabled. The bounds are also used for windowed
	 * pruning during second-level searches.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if deep irrelevance is enabled.
	 * <br><br>
	 * This method overloads {@link #setAsKeepWindowed(boolean, boolean, boolean)} with a default value of {@code true} for the
	 * 'never discard' variable. This generally improves the performance of B*-squared variants greatly.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 */
	public void setAsKeepWindowed(boolean shallowIrrelevance, boolean deepIrrelevance) {
		setAsKeepWindowed(shallowIrrelevance, deepIrrelevance, true); }
	/**
	 * This sets the search to apply the 'keep bounds' approach to bounds preservation. This means that the children of 
	 * the second-level root are preserved after the second-level search is terminated. These children are not used for irrelevance
	 * stopping or node selection, but rather determine the values of new nodes when generated. This reduces the reliance on
	 * second-level search and thus is somewhat closer to regular B* search.
	 * <br><br>
	 * Note that shallow irrelevance is automatically set to {@code true} if any of shallow irrelevance or bonus evaluations are also enabled,
	 * as they require shallow irrelevance to function.
	 * <br><br>
	 * This method overloads {@link #setAsKeepLazy(boolean, boolean, boolean, boolean)} with a default value of {@code true} for the
	 * 'never discard' variable. This generally improves the performance of B*-squared variants greatly.
	 * @param bonusEvals set to {@code true} to perform additional evaluations to provide extra information to irrelevance stopping.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 */
	public void setAsKeepLazy(boolean bonusEvals, boolean shallowIrrelevance, boolean deepIrrelevance) {
		setAsKeepLazy(bonusEvals, shallowIrrelevance, deepIrrelevance, true); }

	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsDisposeAll(boolean, boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param bonusEvals set to {@code true} to perform additional evaluations to provide extra information to irrelevance stopping.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsDisposeAll(boolean bonusEvals, boolean shallowIrrelevance, boolean deepIrrelevance, StrategyFunction... strategyFunctions) {
		var res = new BstarSquaredKeep(strategyFunctions);
		res.setAsDisposeAll(bonusEvals, shallowIrrelevance, deepIrrelevance);
		return res;
	}
	
	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsKeepIgnore(boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsKeepIgnore(boolean shallowIrrelevance, boolean deepIrrelevance, StrategyFunction... strategyFunctions) {
		return getAsKeepIgnore(shallowIrrelevance, deepIrrelevance, true, strategyFunctions);
	}
	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsKeepIgnore(boolean, boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param neverDiscard set to {@code true} to keep second-level root children in memory indefinitely, even after they have been requested through a node expansion.
	 * This could be set to {@code false} to save on some memory space, but could drastically decrease performance if the search often re-expands nodes.
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsKeepIgnore(boolean shallowIrrelevance, boolean deepIrrelevance, boolean neverDiscard, StrategyFunction... strategyFunctions) {
		var res = new BstarSquaredKeep(strategyFunctions);
		res.setAsKeepIgnore(shallowIrrelevance, deepIrrelevance, neverDiscard);
		return res;
	}
	
	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsKeepWindowed(boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsKeepWindowed(boolean shallowIrrelevance, boolean deepIrrelevance, StrategyFunction... strategyFunctions) {
		return getAsKeepWindowed(shallowIrrelevance, deepIrrelevance, true, strategyFunctions);
	}
	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsKeepWindowed(boolean, boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param neverDiscard set to {@code true} to keep second-level root children in memory indefinitely, even after they have been requested through a node expansion.
	 * This could be set to {@code false} to save on some memory space, but could drastically decrease performance if the search often re-expands nodes.
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsKeepWindowed(boolean shallowIrrelevance, boolean deepIrrelevance, boolean neverDiscard, StrategyFunction... strategyFunctions) {
		var res = new BstarSquaredKeep(strategyFunctions);
		res.setAsKeepWindowed(shallowIrrelevance, deepIrrelevance, neverDiscard);
		return res;
	}
	
	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsKeepLazy(boolean, boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param bonusEvals set to {@code true} to perform additional evaluations to provide extra information to irrelevance stopping.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsKeepLazy(boolean bonusEvals, boolean shallowIrrelevance, boolean deepIrrelevance, StrategyFunction... strategyFunctions) {
		return getAsKeepLazy(bonusEvals, shallowIrrelevance, deepIrrelevance, true, strategyFunctions);
	}
	/**
	 * Initialises a B*-squared search engine with the settings as described by {@link #setAsKeepLazy(boolean, boolean, boolean, boolean)}.
	 * The provided strategy functions are interpreted as by {@link #BstarSquaredKeep(StrategyFunction...)}.
	 * @param bonusEvals set to {@code true} to perform additional evaluations to provide extra information to irrelevance stopping.
	 * @param shallowIrrelevance set to {@code true} to apply shallow irrelevance
	 * @param deepIrrelevance set to {@code true} to apply deep irrelevance
	 * @param neverDiscard set to {@code true} to keep second-level root children in memory indefinitely, even after they have been requested through a node expansion.
	 * This could be set to {@code false} to save on some memory space, but could drastically decrease performance if the search often re-expands nodes.
	 * @param strategyFunctions The list of strategy functions to apply at the root of a leveled search, including the first-level search.
	 * @return The initialised B*-squared engine
	 */
	public static BstarSquaredKeep getAsKeepLazy(boolean bonusEvals, boolean shallowIrrelevance, boolean deepIrrelevance, boolean neverDiscard, StrategyFunction... strategyFunctions) {
		var res = new BstarSquaredKeep(strategyFunctions);
		res.setAsKeepLazy(bonusEvals, shallowIrrelevance, deepIrrelevance, neverDiscard);
		return res;
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
		L1Position<P> L1_root;
		if (variant.keepChildrenAfterL2)
			L1_root = new L1PositionKeep<P>(settings, root, root.lowerbound(), root.upperbound());
		else
			L1_root = new L1Position<P>(settings, root, root.lowerbound(), root.upperbound());
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
		SearchWithTree b1;
		if (settings.variant.windowedPruning && settings.level != 1)
			 b1 = new BstarWindowed(total_limit.or(stopCondition), strategyFunctions[0]);
		else b1 = new    BstarBasic(total_limit.or(stopCondition), strategyFunctions[0]);
		
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
		boolean ofKeepL2 = false;
		
		public SearchTreeNodeModified(Settings settings, SearchTreeNode<L1Position<P>> parent,
				L1Position<P> position, MetricKeeper... metrics) {
			super(settings, parent, position, metrics);
			ofKeepL2 = position instanceof L1PositionKeep;
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
			if (result.ofKeepL2 && position.evaluated) {
				double[] bounds = result.getBoundsPointer();
				bounds[0] = position.lowerbound;
				bounds[1] = position.upperbound;
			}
			return result;
		}
		@Override
		public SearchTreeNodeModified<P> parent() {
			return (SearchTreeNodeModified<P>) super.parent();
		}
		@Override
		public long nodeSize() {
			// this ensures that the node-count of L1Position.children is respected when these nodes are pruned:
			if (ofKeepL2 && position() != null) return ((L1PositionKeep<?>) position()).childrenSize() + 1l;
			return 1l;
		}
	}
	
	public static class L1PositionKeep<P extends IGamePosition<P>> extends L1Position<P> {
		public L1PositionKeep(Settings settings, P original) {
			super(settings, original);
			children = null;
			expanded = false;
		}
		public L1PositionKeep(Settings settings, P original, double lowerbound, double upperbound) {
			super(settings, original, lowerbound, upperbound);
			children = null;
			expanded = false;
		}
		L1PositionKeep<?>[] children;
		boolean expanded;
		
		public long childrenSize() {
			if (children != null) return children.length;
			return 0l;
		}
		
		@Override
		public void evaluate() {
			if (evaluated && (expanded || s.variant.keepLazyExpansions)) return;
			MetricKeeper[] metrics = combineArrays(s.other_metrics, s.L2_metrics);
			
			var L2_result = performLeveledSearch(metrics);
			// this call also sets "evaluated" to 'true':
			recordResults(L2_result);
			var L2_root = L2_result.root();
			var L2_children = L2_root.children(metrics);
			if (L0_tree != null && L0_tree.depth() > 0) {
				// this removes children that are irrelevant to the search
				//  from the L1 tree before they even reach it, reducing the number of nodes
				L2_children.removeIf(c -> !quickRelevant(
					L2_root.maximising(),lowerbound,upperbound,c.lowerbound(metrics),c.upperbound(metrics)));
			}
			children = new L1PositionKeep<?>[L2_children.size()];
			for (int i=0; i < children.length; i++) {
				var L2_child = L2_children.get(i);
				L1PositionKeep<P> child = new L1PositionKeep<P>(s, L2_child.position(), L2_child.lowerbound(metrics), L2_child.upperbound(metrics));
				child.setTree(L0_tree);
				children[i] = child;
			}
			expanded = true;
			
			adjustNodeCount(-s.L2_metrics.nodes(), metrics);
			adjustNodeCount(children.length, s.L1_metrics);
			s.true_metrics.setNodeCount(s.L1_metrics.nodes());
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public Collection<L1Position<P>> next() {
			if (s.variant.keepLazyExpansions && !expanded) {
				return original.next().stream().map(n -> {
					var res = (L1Position<P>) new L1PositionKeep<P>(s, n);
					res.setTree(L0_tree);
					return res;
				}).toList();
			}
			evaluate();
			List<L1Position<P>> result = new ArrayList<L1Position<P>>(children.length);
			for (var child : children) result.add((L1PositionKeep<P>) child);
			if (!s.variant.keepChildrenAfterQuery) {
				adjustNodeCount(-children.length, s.L1_metrics);
				expanded = false;
				children = null;
			}
			return result;
		}
	}

	public static class L1Position<P extends IGamePosition<P>> implements IGamePosition<L1Position<P>> {

		final P original;
		final Settings s;
		SearchTreeNodeModified<P> L0_tree = null;
		
		boolean evaluated;
		double lowerbound, upperbound;
		long depthOfLower, depthOfUpper;
		
		StopCondition stopping;
		
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
			if (s.variant.irrelevanceStopping && (s.level == 1 || s.variant.applyIrrelevanceStoppingToL3)) {
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
					boolean stopSearching = irrelevanceCheck(p, cBounds, true);
					return stopSearching;
				};
			}
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
		
		private boolean irrelevanceCheck(SearchTreeNodeModified<P> parent, double[] cBounds, boolean first_call) {
			double[] pBounds = parent.getBoundsPointer();
			if (s.variant.bonusEvalsOnCreation && first_call && !parent.marker) {
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
			
			// experiments have shown that editing the L1 tree
			//  in-place during L2 search results in huge stalling
			// the reason is yet unknown
			pBounds = new double[] {pBounds[0], pBounds[1]};
			
			if (parent.maximising()) {
				// lower bound can be raised
				pBounds[0] = Math.max(pBounds[0], cBounds[0]);
				// upper bound can be lowered
				pBounds[1] = cBounds[1];
				// but only as much as the siblings allow
				for (var child : pchildren) {
					var point = child.getBoundsPointer();
					if (point == cBounds) continue;
					pBounds[1] = Math.max(pBounds[1], point[1]);
				}
			} else {
				// upper bound can be lowered
				pBounds[1] = Math.min(pBounds[1], cBounds[1]);
				// lower bound can be raised
				pBounds[0] = cBounds[0];
				// same as above: only as siblings allow
				for (var child : pchildren) {
					var point = child.getBoundsPointer();
					if (point == cBounds) continue;
					pBounds[0] = Math.min(pBounds[0], point[0]);
				}
			}
			return irrelevanceCheck(parent.parent(), pBounds, false);
		}
		
		SearchResult<?,P> performLeveledSearch(MetricKeeper[] m) {
			SearchAlgorithm b2;
			if (s.strategyFunctions.length == 2) {
				if (s.variant.windowedPruning) {
					b2 = new BstarWindowed(stopping, s.strategyFunctions[1]);
					var w2 = (BstarWindowed) b2;
					if (evaluated) w2.setRelevantWindow(lowerbound, upperbound);
					else {
						double[] bounds = L0_tree.getBoundsPointer();
						w2.setRelevantWindow(bounds[0], bounds[1]);
					}
					w2.expectIncorrectBounds(s.expectIncorrectBounds);
				} else {
					b2 = new BstarBasic(stopping, s.strategyFunctions[1]);
					((BstarBasic) b2).expectIncorrectBounds(s.expectIncorrectBounds);
				}
			} else {
				b2 = new BstarSquaredKeep(stopping, Arrays.copyOfRange(s.strategyFunctions, 1, s.strategyFunctions.length));
				((BstarSquaredKeep) b2).setLevel(s.level+1);
				if (s.variant.applyIrrelevanceStoppingToL3) {
					((BstarSquaredKeep) b2).useIrrelevanceStoppingAtAllLevels(true);
					((BstarSquaredKeep) b2).useDeepIrrelevance(s.variant.deepIrrelevance);
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
			adjustNodeCount(1, m);
			return b2.search(original, time_remaining, L2_limit, m);
		}
		
		void recordResults(SearchResult<?,P> L2_result) {
			var L2_root = L2_result.root();
			lowerbound = L2_root.lowerbound();
			upperbound = L2_root.upperbound();
			depthOfLower = L2_root.depthOfLower();
			depthOfUpper = L2_root.depthOfUpper();
			evaluated = true;
			
			var result_metrics = L2_result.mainMetrics();
			s.true_metrics.incrementExpansions(result_metrics.expansions());
			s.true_metrics.incrementEvaluations(result_metrics.evaluations());
			s.true_metrics.setNodeCount(result_metrics.maxObservedNodes() + s.L1_metrics.nodes());
		}
		
		public void evaluate() {
			if (evaluated) return;
			MetricKeeper[] metrics = combineArrays(s.other_metrics, s.L2_metrics);
			
			var L2_result = performLeveledSearch(metrics);
			recordResults(L2_result);
			
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
		/** Modification to expansions such that children in L1 start with 
		  * evaluation function values from the original evaluation function.
		  * This allows the irrelevance stop conditions to trigger more consistently. */
		boolean bonusEvalsOnCreation;
		
		// Save Bounds Settings:
		// 1. Ignore: save the children after L2 search
		//            > positions of depth=1 children are only generated once, and not again on re-search
		//            > saved bounds improve order of evaluation (?)
		// 2. Window: same as above, plus:
		//            > relevant zone pruning based on saved L2 bounds
		// 3. Keep: keep children after L2 search, do not re-compute their bounds
		//            > positions of depth=1 children are only generated once, and not again on re-search
		//            > L2 search is only initiated if the node has no saved bounds
		
		boolean keepChildrenAfterL2, keepChildrenAfterQuery;
		boolean keepLazyExpansions, windowedPruning;
		
		boolean requireModifications() {
			return irrelevanceStopping || bonusEvalsOnCreation || keepChildrenAfterL2 || windowedPruning;
		}
		
		public VariantSetting() {
			irrelevanceStopping			= false;
			deepIrrelevance				= false;
			applyIrrelevanceStoppingToL3	= false;
			bonusEvalsOnCreation			= false;
			keepChildrenAfterL2			= false;
			keepChildrenAfterQuery		=  true;
			keepLazyExpansions			= false;
			windowedPruning				= false;
		}
	}

}
