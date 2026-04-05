package experiment;

import static algorithm.StrategyFunction.*;

import java.time.Duration;

import algorithm.BstarBasic;
import gametree.MetricKeeper;
import gametree.SearchTreeNode;
import gametree.ArtificialGamePosition.Settings;

@Deprecated
public class ExamineBstarBehavior {

	public static void main(String[] args) {
		var game = new Settings(true,8000997,6,100,4,1.5,0.5).getTree();
		var algo = new BstarBasic(B_AR);
		MetricKeeper m = new MetricKeeper("Total");
		var tree = SearchTreeNode.getTree(game,2,2);
		for (var child : tree.children(m))
			child.attachMetrics(new MetricKeeper("sub-tree"));
		var res = algo.searchWithTree(tree, Duration.ofSeconds(30), m);
		for (var child : tree.children(m)) {
			System.out.println("\n"+child);
			System.out.println(child.getAttachedMetrics()[0].describe());
		}
		System.out.println(res.describe());
//		System.out.println(m.describe());
//		System.out.println(res.root().printTree(5));
	}
	
}
