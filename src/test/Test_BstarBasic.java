package test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import algorithm.BstarBasic;
import algorithm.SearchAlgorithm.SearchResult;
import algorithm.StrategyFunction;
import gametree.ArtificialGamePosition;
import gametree.ArtificialGamePosition.Settings;
import gametree.MetricKeeper;
import gametree.SearchTreeNode;

class Test_BstarBasic {

	@Test
	@SuppressWarnings("unused")
	void test() {
		var root = new SimplePosition(true, 0, 100);
		var c0 = root.addAdversarial(20, 90);
		var c1 = root.addAdversarial(10, 70);
		var c2 = c0.addAdversarial(50, 50);
		var c3 = c0.addAdversarial(30, 30);
		var c4 = c1.addAdversarial(30, 35);
		var c5 = c1.addAdversarial(40, 50);
		var tree = SearchTreeNode.getTree(root);
		BstarBasic bstar = new BstarBasic(StrategyFunction.ALTERNATE());
		SearchResult<?, SimplePosition> res = bstar.searchWithTree(tree);
		assertEquals(c1, res.bestMove());
		tree.adjustBounds();
		assertEquals(30, tree.lowerbound());
		assertEquals(35, tree.upperbound());
	}
	
//	@Test
	void test2() {
		boolean adversarial = true;
		long seed = System.currentTimeMillis();
		int width = 5;
		long initial_range = 100;
		int num_alts = 5;
		double growth_factor = 1.5;
		double relevance_chance = 0.3;
		
		System.out.println("seed = "+seed);
		
		Settings settings = new Settings(adversarial, seed, width, initial_range, num_alts, growth_factor, relevance_chance);
		var root = new ArtificialGamePosition(settings);
		var tree = SearchTreeNode.getTree(root, 2, 2);
		for (var child : tree.children()) child.attachMetrics(new MetricKeeper());
		BstarBasic bstar = new BstarBasic(StrategyFunction.B_AR);
		var res = bstar.searchWithTree(tree, Duration.ofSeconds(30));
		System.out.println(res.intractable());
		for (var child : tree.children()) {
			MetricKeeper m = child.getAttachedMetrics()[0];
			System.out.println(child + (child == res.mostOptimisticNode() ? " ***" : ""));
			System.out.println(" --- "+ m.evaluations()/2 + ", " + m.expansions());
		}
		System.out.println(res.metrics()[0].evaluations()/2+", "+(res.metrics()[0].expansions()+1));
	}
	
}
