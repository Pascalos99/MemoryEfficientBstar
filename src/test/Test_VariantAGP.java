package test;

import org.junit.jupiter.api.Test;

import algorithm.BstarBasic;
import algorithm.StrategyFunction;
import gametree.Generator;
import gametree.Generator.Width;
import gametree.MetricKeeper;
import gametree.SearchTreeNode;
import gametree.VariantAGP;
import visualizer.TextExplorer;

public class Test_VariantAGP {

	@Test
	public void test() {
		boolean adversarial = true;
		long seed = System.currentTimeMillis();
		int maxWidth = 10;
		Width width = (r, min, max, d) -> Math.max(Math.min(max, max-(int) d), min);
		long range = 100;
		Generator.Bounds bounds = (r, n) -> {
			if (n.depth() >= 5 && r.nextDouble() < 1. / (20 - n.depth())) {
				if (r.nextDouble() < 0.5) return new long[] {n.upperbound()-1};
				return new long[] {n.lowerbound()};
			}
			long[] res = new long[10 - (int)(n.depth() / 4)];
			for (int i=0; i < res.length; i++)
				res[i] = r.nextLong(n.lowerbound(), n.upperbound());
			return res;
		};
		double growth = 1;
		double force_relevance = 0;
		var game = new VariantAGP(adversarial, seed, maxWidth, width, range, bounds, growth, force_relevance);
		var tree = SearchTreeNode.getTree(game, false, 0, true, 0, true);
		var bstar = new BstarBasic(StrategyFunction.B_AR);
		var m = new MetricKeeper("bstar", 0, 0, 1);
		var res = bstar.searchWithTree(tree, m);
		System.out.println(res.describe());
		var exp = new TextExplorer(tree, 3);
		exp.start();
	}

}
