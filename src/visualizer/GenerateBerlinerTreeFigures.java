package visualizer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import gametree.GameTreeNode;
import gametree.SearchTreeNode;

/**
 * Generates the tree figures included in the thesis. The output files
 *  are xml format .drawio files, that can be edited at https://app.diagrams.net/<br>
 * The initial output of this method may need to be adjusted first, as the positions
 *  of nodes are not intelligently positioned by the generator. 
 */
public class GenerateBerlinerTreeFigures {

	public static void main(String[] args) {
		int depth = 5;
		long minVal = 8;
		long maxVal = 30;
		
		if (args.length < 1) {
			System.out.println("[FAILED]: Provide a valid filename as parameter.");
			return;
		}
		String[] filenames = null;
		try {
			String [] fn1 = generateBerlinerExamples(true, args[0]);
			String [] fn2 = generateBerlinerExamples(false, args[0]);
			filenames = new String[fn1.length + fn2.length];
			for (int i=0; i < fn1.length; i++)
				filenames[i] = fn1[i];
			for (int i=fn1.length; i < filenames.length; i++)
				filenames[i] = fn2[i - fn1.length];
		} catch (IOException e) {
			System.out.println("[FAILED]: Encountered an error.");
			e.printStackTrace();
			return;
		}
		var ttx = new TreeToXML(60, 20, 1);
		ttx.boxSpacingY = 30;
		if (args.length >= 2 && args[1].matches("(gr[a|e]y.*)|(mono-?(|chrome|colou?r))"))
			ttx.setColorsGray();
		for (String filename : filenames) {
			GameTreeNode<?,?> tree;
			try {
				tree = GameTreeNode.fromFile(filename);
			} catch (IOException | ClassNotFoundException e) {
				System.out.println("[FAILED]: Encountered an error.");
				e.printStackTrace();
				return;
			}
			try (FileWriter fw = new FileWriter(filename.replaceFirst("\\.gametree","")+".drawio")) {
				fw.append(ttx.transform(tree, depth, minVal, maxVal));
			} catch (IOException e) {
				System.out.println("[FAILED]: Encountered an error.");
				e.printStackTrace();
				return;
			}
		}
	}
	
	/**
	 * @param PB
	 * @param folder
	 * @return an array containing the names of the generated files
	 * @throws IOException
	 */
	public static String[] generateBerlinerExamples(boolean PB, String folder) throws IOException {
		{ // check if provided file is valid
			File ff = new File(folder);
			if ((!ff.exists() && !ff.mkdirs()) || (!ff.isDirectory()))
				throw new IOException("Specified folder \""+folder+"\" is not a folder or could not be created.");
		}
		String[] filenames = new String[PB ? 4 : 3];
		var game = ExampleTrees.berliner1979();
		var n0 = SearchTreeNode.getTree(game);
		// expand the root node: (step 1)
		var n0_children = n0.children();
		n0.adjustBounds();
		// save result to file:
		n0.toFile(filenames[0] = folder+File.separator+"berliner-step1.gametree");
		// select node at root, according to chosen strategy:
		var best2 = GameTreeNode.mostOptimistic2(n0_children, n0.maximising());
		var n1 = PB ? best2.best() : best2.secondbest();
		// expand N1: (step 2)
		var n1_children = n1.children();
		n1.adjustBounds();
		n0.adjustBounds();
		// save result to file:
		n0.toFile(filenames[1] = folder+File.separator+"berliner-"+(PB?"PB":"DR")+"-step2.gametree");
		if (PB) {
			// select most optimistic node:
			best2 = GameTreeNode.mostOptimistic2(n1_children, n1.maximising());
			var n2 = best2.best();
			// expand N2: (step 3)
			n2.children();
			n2.adjustBounds();
			n1.adjustBounds();
			n0.adjustBounds();
			// save result to file:
			n0.toFile(filenames[2] = folder+File.separator+"berliner-PB-step3.gametree");
			// select most optimistic node:
			best2 = GameTreeNode.mostOptimistic2(n1_children, n1.maximising());
			var n3 = best2.best();
			// expand N3: (step 4)
			n3.children();
			n3.adjustBounds();
			n1.adjustBounds();
			n0.adjustBounds();
			// save result to file:
			n0.toFile(filenames[3] = folder+File.separator+"berliner-PB-step4.gametree");
		} else {
			// select with disprove-rest:
			best2 = GameTreeNode.mostOptimistic2(n0_children, n0.maximising());
			var n2 = best2.secondbest();
			// expand N2: (step 3)
			n2.children();
			n2.adjustBounds();
			n0.adjustBounds();
			// save result to file:
			n0.toFile(filenames[2] = folder+File.separator+"berliner-DR-step3.gametree");
		}
		return filenames;
	}
	
}
