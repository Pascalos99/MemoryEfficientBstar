package test;

import org.junit.jupiter.api.Test;

import algorithm.BstarBasic;
import algorithm.BstarSquaredSimple;
import algorithm.SearchAlgorithm.Limits;
import gametree.ArtificialGamePosition;
import gametree.SearchTreeNode;
import gametree.ArtificialGamePosition.Settings;
import gametree.MetricKeeper;

import static org.junit.jupiter.api.Assertions.*;
import static algorithm.StrategyFunction.*;

import java.time.Duration;
import java.time.Instant;

@Deprecated
public class Test_SearchAlgorithm {
	
	@Test
	public void test() {
		assertTrue(true);
		
		long seed = System.currentTimeMillis();
//		seed = 1751061383223l;
//		seed = 1751633251998l;
		seed = 1752150134576l;
		System.out.println("seed = "+seed);
		
		Settings settings = new Settings(
				// adversarial (true) or non-adversarial (false)
				true,
				// initial seed of the tree
				seed,
				// w = width; number of children per node; branching factor
				6,
				// R = range; initial range of root node (lowerbound is always 0)
				100,
				// k = number of alternative values; distribution parameter (higher means larger trees)
				4,
				// g = growth factor; distribution parameter (higher may mean larger trees, too high, smaller trees)
				1.5,
				// r = force relevance chance; chance that children are forced to be relevant (only used if g > 1)
				0.5);
		
		var root = new ArtificialGamePosition(settings);
//		var tree = SearchTreeNode.getTree(root, 3, 2);
		Limits LIMIT = Limits.memoryLimit(20000000);
		
		System.out.println("BstarSquared AR-PB:");
		Instant start1 = Instant.now();
		var res1 = new BstarSquaredSimple(B_AR).search(root, LIMIT, new MetricKeeper("Total"));
		System.out.println(Duration.between(start1, Instant.now()).toMillis()+" milliSeconds");
		System.out.println(res1.describe());
		System.out.println(res1.root().printTree(1, true));
		
		System.out.println("\n\nBstarSquared AR-ALT:");
		Instant start2 = Instant.now();
		var res2 = new BstarSquaredSimple(B_AR, ALTERNATE()).search(root, LIMIT, new MetricKeeper("Total"));
		System.out.println(Duration.between(start2, Instant.now()).toMillis()+" milliSeconds");
		System.out.println(res2.describe());
		System.out.println(res2.root().printTree(1, true));
		
		// TODO why does this work sometimes??
		
		System.out.println("\n\nBstarSquared AR-PB-PB:");
		Instant start4 = Instant.now();
		var res4 = new BstarSquaredSimple(B_AR, PROVEBEST, PROVEBEST).search(root, LIMIT, new MetricKeeper("Total"));
		System.out.println(Duration.between(start4, Instant.now()).toMillis()+" milliSeconds");
		System.out.println(res4.describe());
		System.out.println(res4.root().printTree(1, true));
		
		// TODO and is there ever a time this works??
		
		System.out.println("\n\nBstarSquared AR-PB-PB-PB:");
		Instant start5 = Instant.now();
		var res5 = new BstarSquaredSimple(B_AR, PROVEBEST, PROVEBEST, PROVEBEST).search(root, LIMIT, new MetricKeeper("Total"));
		System.out.println(Duration.between(start5, Instant.now()).toMillis()+" milliSeconds");
		System.out.println(res5.describe());
		System.out.println(res5.root().printTree(1, true));
		
		System.out.println("\n\nBstar AR:");
		var m = new MetricKeeper("Total");
		Instant start3 = Instant.now();
		var res3 = new BstarBasic(B_AR).searchWithTree(SearchTreeNode.getTree(root, 3, 2), LIMIT, m);
		System.out.println(Duration.between(start3, Instant.now()).toMillis()+" milliSeconds");
		System.out.println(res3.describe());
		System.out.println(res3.root().printTree(1, true));
	}

}
