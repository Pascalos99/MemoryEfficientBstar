package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import gametree.ArtificialGamePosition;
import gametree.ArtificialGamePosition.Settings;
import gametree.MetricKeeper;
import gametree.SearchTreeNode;

class Test_ArtificialGamePosition {

    static Stream<Long> randomSeeds(int count, String seed_name) {
    	final long seed = System.currentTimeMillis();
    	System.out.println("Main seed \""+seed_name+"\": "+seed);
        Random mainRandom = new Random(seed);
        return Stream.generate(mainRandom::nextLong).limit(count).parallel();
    }
    
    static Stream<Long> randomSeeds1() { return randomSeeds(100, "seed 1"); }
    
    static Stream<Long> randomSeeds2() { return randomSeeds(10, "seed 2"); }
	
    @Test
    void testSpecificTrees() {
    	testTrees1(-2430065191421133220L);
    }
    
    @ParameterizedTest
	@MethodSource("randomSeeds1")
	void testTrees1(long seed) {
		Random r = new Random(seed);
		Settings settings = new Settings(
				// adversarial (true) or non-adversarial (false)
				r.nextBoolean(),
				// initial seed of the tree
				r.nextLong(),
				// w = width; number of children per node; branching factor
				r.nextInt(2, 10),
				// R = range; initial range of root node (lowerbound is always 0)
				r.nextLong(50,10000),
				// k = number of alternative values; distribution parameter (higher means larger trees)
				r.nextInt(2,20),
				// g = growth factor; distribution parameter (higher may mean larger trees, too high, smaller trees)
				r.nextDouble(0.5,3),
				// r = force relevance chance; chance that children are forced to be relevant (only used if g > 1)
				r.nextDouble());
		testTreeBehaviour(settings, 5, "Tree (1) test failed with seed: " + seed);
	}
    
    @ParameterizedTest
	@MethodSource("randomSeeds2")
	void testTrees2(long seed) {
		Random r = new Random(seed);
		Settings settings = new Settings(
				// adversarial (true) or non-adversarial (false)
				r.nextBoolean(),
				// initial seed of the tree
				r.nextLong(),
				// w = width; number of children per node; branching factor
				r.nextInt(2, 10),
				// R = range; initial range of root node (lowerbound is always 0)
				Long.MAX_VALUE,
				// k = number of alternative values; distribution parameter (higher means larger trees)
				r.nextInt(2,20),
				// g = growth factor; distribution parameter (higher may mean larger trees, too high, smaller trees)
				r.nextDouble(0.5,3),
				// r = force relevance chance; chance that children are forced to be relevant (only used if g > 1)
				r.nextDouble());
		testTreeBehaviour(settings, 5, "Tree (2) test failed with seed: " + seed);
	}
	
	private void testTreeBehaviour(Settings settings, int depth, String error_msg) {
    	var root = new ArtificialGamePosition(settings);
		var tree = SearchTreeNode.getTree(root);
		try {
			testBoundsGetTighter(tree, depth);
		} catch (Exception e) {
	        System.err.println(error_msg);
	        System.err.println("Settings that caused failure: " + settings);
			throw e;
		}
    }
	
	private void testBoundsGetTighter(SearchTreeNode<ArtificialGamePosition> node, int depth, MetricKeeper... metrics) {
		if (depth <= 0) return;
		for (var child : node.children(metrics)) {
			checkChild(child, metrics);
			if (depth >= 1) {
				testBoundsGetTighter(child, depth-1, metrics);
				checkChild(child, metrics);
			}
		}
	}
	
	private void checkChild(SearchTreeNode<ArtificialGamePosition> child, MetricKeeper... metrics) {
		double lb = child.lowerbound(metrics);
		double ub = child.upperbound(metrics);
		if (child.adjustBounds(metrics)) {
			double LB = child.lowerbound(metrics);
			double UB = child.upperbound(metrics);
			assertTrue(lb != LB || ub != UB, "error in SearchTreeNode.adjustBounds");
			assertTrue(LB >= lb, "ArtificialGamePosition children bounds not tighter (["+lb+","+ub+"] -> ["+LB+","+UB+"])");
			assertTrue(UB <= ub, "ArtificialGamePosition children bounds not tighter (["+lb+","+ub+"] -> ["+LB+","+UB+"])");
		} else {
			assertEquals(lb, child.lowerbound(metrics), "error in SearchTreeNode.adjustBounds");
			assertEquals(ub, child.upperbound(metrics), "error in SearchTreeNode.adjustBounds");
		}
	}

}
