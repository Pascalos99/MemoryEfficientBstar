package visualizer;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;

import gametree.GameTreeNode;
import gametree.MetricKeeper;
import gametree.SearchTreeNode;
import gametree.ArtificialGamePosition.Settings;
import static gametree.GameTreeNode.*;

/**
 * This class allows the manual exploration of trees. Either change the main method to select a different tree,
 *  or use a different file or jshell to access this class for the exploration of trees in the console.
 */
public class TextExplorer {

	public static void main(String[] args) {
		long seed = 1767968549960l;//System.currentTimeMillis(); //1764682964160l
		// mooi voorbeeld: 1764941539407l
		System.out.println("seed: "+seed);
		var root = new Settings(
				// adversarial:
				true,
				// seed:
				seed,
				// branching factor:
				5,
				// initial range:
				100,
				// distribution factor:
				3,
				// growth factor:
				1.5,
				// relevance chance:
				0.2
			).getTree();
		var tree = SearchTreeNode.getTree(root, false, 2, false, 2, false);
		var te = new TextExplorer(tree, 5);
		te.systemExitOnClose = true;
		te.start();
	}
	
	private GameTreeNode<?,?> root;
	private GameTreeNode<?,?> current;
	private int depth;
	private MetricKeeper metrics;
	private MetricKeeper[] extra_metrics = null;
	private boolean systemExitOnClose = false;
	private boolean stop = false;
	
	public TextExplorer(GameTreeNode<?,?> root, int depth) {
		setTree(root);
		setDepth(depth);
	}
	public void setMetrics(MetricKeeper m) {
		metrics = m;
	}
	public MetricKeeper getMetrics() {
		return metrics;
	}
	
	public void addMetrics(MetricKeeper... metrics) {
		extra_metrics = MetricKeeper.combineArrays(extra_metrics, metrics);
	}
	
	public void setTree(GameTreeNode<?,?> root) {
		this.root = root;
		current = root;
		metrics = new MetricKeeper("TextExplorer",0,0,1);
	}
	public void setDepth(int depth) {
		this.depth = depth;
	}
	
	public GameTreeNode<?,?> getRoot() {
		return root;
	}
	public GameTreeNode<?,?> getCurrent() {
		return current;
	}
	
	public void start() {
		stop = false;
		initializeTTX();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while (!stop) {
			System.out.println("current sub-tree is: (depth = "+current.depth()+")");
			System.out.println(currentToString());
			System.out.print("INPUT: ");
			try {
				String input = br.readLine();
				String[] inputs = input.split(";");
				for (String in : inputs)
					processCommand(in);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public String currentToString() {
		String[] lines = current.printTree(depth, false, metrics).split("\n");
		StringBuilder sb = new StringBuilder();
		int exp = (int) Math.ceil(Math.log10(lines.length));
		for (int i=0; i < lines.length-1; i++)
			sb.append(String.format("%"+exp+"d: %s\n", i, lines[i]));
		return sb.toString();
	}
	
	public void processCommand(String command) {
		if (hasAny(command,"exit","stop","end","terminate"))
			exitCommand();
		else if (hasAny(command, "elbow"))
			ttx.setLinesToElbow();
		else if (hasAny(command, "curve"))
			ttx.setLinesToCurves();
		else if (hasAny(command, "straight"))
			ttx.setLinesToStraight();
		else if (hasAny(command, "text"))
			ttx.addText ^= true;
		else if (hasAny(command, "gray"))
			ttx.setColorsGray();
		else if (hasAny(command, "color", "colour", "rgb"))
			ttx.setColorsDefault();
		else if (hasAny(command,"export","exp"))
			try {
				exportCommand(command);
			} catch (IOException e) {
				e.printStackTrace();
			}
		else if (hasAny(command,"back","parent","up"))
			upCommand();
		else if (hasAny(command,"expand","explore","adjust","update","eval"))
			expandCommand(command);
		else if (hasAny(command,"depth","deepness"))
			depthCommand(command);
		else if (hasAny(command,"root"))
			rootCommand();
		else if (hasAny(command,"metric"))
			metricCommand();
		else if (hasAny(command,"show","view","open","display","dive"))
			diveCommand(command);
		else if (hasAny(command,"second","2nd"))
			opt2Command();
		else if (hasAny(command,"opt","1st"))
			optCommand();
		else if (hasAny(command,"pes"))
			pesCommand();
		else if (hasAny(command,"x"))
			expandCommand(command);
		else if (hasAny(command,"d"))
			depthCommand(command);
		else if (hasAny(command, "r"))
			rootCommand();
		else if (hasAny(command,"m"))
			metricCommand();
		else if (hasAny(command,"u","p"))
			upCommand();
		else if (hasAny(command,"v","s","o") || command.matches("[\\d]+"))
			diveCommand(command);
	}
	
	public void exitCommand() {
		stop = true;
		if (systemExitOnClose) System.exit(0);
	}
	
	private TreeToXML ttx;
	
	public void initializeTTX() {
		ttx = new TreeToXML(60, 20, 1);
		ttx.boxSpacingY = 30;
	}
	
	public void exportCommand(String command) throws IOException {
		String[] str = command.split("[\s]+");
		String name = "output";
		boolean useMinMaxValues = false;
		double minValue = 0, maxValue = 0;
		if (str.length >= 2) name = str[1];
		if (str.length >= 4) {
			String str1 = str[2], str2 = str[3];
			String regex = "-?([\\d]+\\.?[\\d]*|\\.[\\d]+)";
			if (str1.matches(regex) && str2.matches(regex)) {
				useMinMaxValues = true;
				minValue = Double.parseDouble(str1);
				maxValue = Double.parseDouble(str2);
				if (minValue > maxValue) {
					double t = minValue;
					minValue = maxValue;
					maxValue = t;
				}
			}
		}
		FileWriter fw = new FileWriter(name+".drawio");
		if (useMinMaxValues)
			fw.append(ttx.transform(current, depth, minValue, maxValue));
		else
			fw.append(ttx.transform(current, depth));
		fw.close();
	}
	
	public void upCommand() {
//		current.adjustBounds(metrics);
		current = current.parent();
		if (current == null) {
			current = root;
			System.out.println("already at root");
		} else  {
			System.out.println("backing-up to parent node");
			current.adjustBounds(metrics);
		}
	}
	
	public void rootCommand() {
		while (current != root) {
			current = current.parent();
			current.adjustBounds(metrics);
		}
//		current.adjustBounds(metrics);
	}
	
	public void optCommand() {
		current.adjustBounds(metrics);
		var res = mostOptimistic2(current.children(metrics), current.maximising(), metrics);
		current = res.best();
	}
	
	public void opt2Command() {
		current.adjustBounds(metrics);
		var res = mostOptimistic2(current.children(metrics), current.maximising(), metrics);
		current = res.secondbest();
	}
	
	public void pesCommand() {
		current.adjustBounds(metrics);
		var res = leastPessimistic2(current.children(metrics), current.maximising(), metrics);
		current = res.best();
	}
	
	public void metricCommand() {
		System.out.println(metrics.describe());
		if (extra_metrics != null)
			for (var m : extra_metrics)
				System.out.println(m.describe());
	}
	
	public void expandCommand(String command) {
		int index = getIntFromSplit(command.split("[^\\d]+x"));
		if (index == -1) index = 0;
		
		var node = selectNode(index);
		if (node == null) System.out.println("node "+index+" does not exist in view");
		else {
			node.adjustBounds(metrics);
			while (node != root && node != current) {
				node = node.parent();
				node.adjustBounds(metrics);
			}
			System.out.println("expanded node "+index);
		}
	}
	
	public void depthCommand(String command) {
		int d = getIntFromSplit(command.split("[^\\d]+"));
		if (d != -1) depth = d;
		System.out.println("current display depth is "+depth);
	}
	
	public void diveCommand(String command) {
		int sel = getIntFromSplit(command.split("[^\\d]+"));
		var selected = selectNode(sel);
		if (selected != null) current = selected;
		else System.out.println("node "+sel+" does not exist in view");
	}
	
	public int getIntFromSplit(String[] split) {
		int res = -1;
		if (split.length == 1 && split[0].matches("[\\d]+"))
			res = Integer.parseInt(split[0]);
		else if (split.length > 1)
			for (int i=0; i < split.length; i++)
				if (split[i].matches("[\\d]+"))
					res = Integer.parseInt(split[i]);
		return res;
	}
	
	public GameTreeNode<?,?> selectNode(int index) {
		int c_index = 0;
		
		int sdepth = (int) current.depth();
		ArrayList<GameTreeNode<?,?>> stack = new ArrayList<>();
		HashSet<GameTreeNode<?,?>> visited = new HashSet<>();
		stack.addLast(current);
		visited.add(current);
		
		while (!stack.isEmpty()) {
			var c = stack.removeLast();
			if (c_index++ == index) return c;
			var hasChildren = c.savedChildren();
			if (hasChildren.isEmpty()) continue;
			var children = hasChildren.get();
			for (int i=children.size()-1; i >= 0; i--) {
				var child = children.get(i);
				if (child.depth() - sdepth <= depth && !visited.contains(child)) {
					stack.addLast(child);
					visited.add(child);
				}
			}
		}
		return null;
	}
	
	public boolean hasAny(String main, String item1, String... items) {
		if (main.contains(item1)) return true;
		for (String item : items) if (main.contains(item)) return true;
		return false;
	}
	
}
