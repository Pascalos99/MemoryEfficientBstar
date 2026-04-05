package visualizer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import gametree.GameTreeNode;

public class TreeToXML {
	
	public TreeToXML(float boxWidth, float boxHeight, float lineWidth, String boxParamsMax, String boxParamsMin, String colorboxParamsMax, String colorboxParamsMin, 
					String lineParamsMax, String lineParamsMin, String arrowParamsMax, String arrowParamsMin, String arrowParamsPointMax, String arrowParamsPointMin) {
		super();
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
		this.lineWidth = lineWidth;
		this.boxParamsMin = boxParamsMin;
		this.boxParamsMax = boxParamsMax;
		this.colorboxParamsMin = colorboxParamsMin;
		this.colorboxParamsMax = colorboxParamsMax;
		this.lineParamsMin = lineParamsMin;
		this.lineParamsMax = lineParamsMax;
		this.arrowParamsMin = arrowParamsMin;
		this.arrowParamsMax = arrowParamsMax;
		this.arrowParamsPointMax = arrowParamsPointMax;
		this.arrowParamsPointMin = arrowParamsPointMin;
		
		irrelevantBoxParamsMin = boxParamsMin + "dashed=1;dashPattern=5 5;";
		irrelevantBoxParamsMax = boxParamsMax + "dashed=1;dashPattern=5 5;";
		irrelevantColorboxParamsMin = addTransparency(colorboxParamsMin);
		irrelevantColorboxParamsMax = addTransparency(colorboxParamsMax);
		irrelevantLineParamsMin = lineParamsMin + "dashed=1;dashPattern=5 5;";
		irrelevantLineParamsMax = lineParamsMax + "dashed=1;dashPattern=5 5;";
		irrelevantArrowParamsMin = arrowParamsMin;
		irrelevantArrowParamsMax = arrowParamsMax;
		
		boxSpacingX = 10;
		boxSpacingY = 40;
	}
	
	public TreeToXML(float boxWidth, float boxHeight, float lineWidth) {
		this(boxWidth, boxHeight, lineWidth,
			// default maximising BOX params:
			"rounded=0;whiteSpace=wrap;fillColor=none;",
			// default minimising BOX params:
			"rounded=0;whiteSpace=wrap;fillColor=none;",
			
			// default maximising COLORBOX params:
			"rounded=0;whiteSpace=wrap;fillColor=light-dark(#078C12,#006F09);strokeColor=none;",
			// default minimising COLORBOX params:
			"rounded=0;whiteSpace=wrap;fillColor=light-dark(#FF2424,#B11616);strokeColor=none;",
			
			// default maximising LINE params:
			"endArrow=none;exitX=0.5;exitY=1;entryX=0.5;entryY=0;",
			// default minimising LINE params:
			"endArrow=none;exitX=0.5;exitY=1;entryX=0.5;entryY=0;",
			
			// default maximising ARROW params:
			"endArrow=blockThin;endFill=1;exitX=0;exitY=0.5;entryX=1;entryY=0.5;",
			// default minimising ARROW params:
			"endArrow=blockThin;endFill=1;exitX=1;exitY=0.5;entryX=0;entryY=0.5;",
			// default maximising point-ARROW params:
			"endArrow=oval;endFill=0;exitX=0;exitY=0.5;entryX=1;entryY=0.5;",
			// default minimising point-ARROW params:
			"endArrow=oval;endFill=0;exitX=1;exitY=0.5;entryX=0;entryY=0.5;"
		);
		setLinesToElbow();
	}
	
	public void setColorsGray() {
		// minimising color:
		// 		light-dark(#ADADAD,#585858)
		// maximising color:
		// 		light-dark(#888888,#787878)
		colorboxParamsMin = setColor(colorboxParamsMin, "light-dark(#ADADAD,#585858)");
		colorboxParamsMax = setColor(colorboxParamsMax, "light-dark(#888888,#787878)");
		irrelevantColorboxParamsMin = addTransparency(setColor(irrelevantColorboxParamsMin, "light-dark(#ADADAD,#585858)"));
		irrelevantColorboxParamsMax = addTransparency(setColor(irrelevantColorboxParamsMax, "light-dark(#888888,#787878)"));
	}
	
	public void setColorsDefault() {
		// minimising color:
		// 		light-dark(#FF2424,#B11616)
		// maximising color:
		// 		light-dark(#078C12,#006F09)
		colorboxParamsMin = setColor(colorboxParamsMin, "light-dark(#FF2424,#B11616)");
		colorboxParamsMax = setColor(colorboxParamsMax, "light-dark(#078C12,#006F09)");
		irrelevantColorboxParamsMin = addTransparency(setColor(irrelevantColorboxParamsMin, "light-dark(#FF2424,#B11616)"));
		irrelevantColorboxParamsMax = addTransparency(setColor(irrelevantColorboxParamsMax, "light-dark(#078C12,#006F09)"));
	}
	
	private static String addTransparency(String param) {
		return param.replaceAll(
				"(fillColor=[^#]*)(#[\\dABCDEF]{6})[\\dABCDEF]*,(#[\\dABCDEF]{6})[\\dABCDEF]*([^;]*)",
				// this adds a 63% transparency to the colorbox of irrelevant nodes
				"$1$2\\A0,$3\\A0$4");
	}
	
	private String setColor(String param, String color) {
		return param.replaceAll("fillColor=[^;]*","fillColor="+color);
	}
	
	public void setLinesToCurves() {
		lineParamsMax = setLinesToCurves(lineParamsMax);
		lineParamsMin = setLinesToCurves(lineParamsMin);
		irrelevantLineParamsMax = setLinesToCurves(irrelevantLineParamsMax);
		irrelevantLineParamsMin = setLinesToCurves(irrelevantLineParamsMin);
	}
	private static String setLinesToCurves(String param) {
		if (!param.contains("curved=[^;]*;")) param += "curved=1;";
		else param = param.replaceAll("curved=[^;]*;", "curved=1;");
		if (!param.contains("edgeStyle=[^;]*;")) param += "edgeStyle=orthogonalEdgeStyle;";
		else param = param.replaceAll("edgeStyle=[^;]*;", "edgeStyle=orthogonalEdgeStyle;");
		return param;
	}
	
	public void setLinesToElbow() {
		lineParamsMax = setLinesToElbow(lineParamsMax);
		lineParamsMin = setLinesToElbow(lineParamsMin);
		irrelevantLineParamsMax = setLinesToElbow(irrelevantLineParamsMax);
		irrelevantLineParamsMin = setLinesToElbow(irrelevantLineParamsMin);
	}
	private static String setLinesToElbow(String param) {
		param = param.replaceAll("curved=1;", "");
		if (!param.contains("elbow=[^;]*;")) param += "elbow=vertical;";
		else param = param.replaceAll("elbow=[^;]*;", "elbow=vertical;");
		if (!param.contains("edgeStyle=[^;]*;")) param += "edgeStyle=elbowEdgeStyle;";
		else param = param.replaceAll("edgeStyle=[^;]*;", "edgeStyle=elbowEdgeStyle;");
		return param;
	}
	
	public void setLinesToStraight() {
		lineParamsMax = setLinesToStraight(lineParamsMax);
		lineParamsMin = setLinesToStraight(lineParamsMin);
		irrelevantLineParamsMax = setLinesToStraight(irrelevantLineParamsMax);
		irrelevantLineParamsMin = setLinesToStraight(irrelevantLineParamsMin);
	}
	private static String setLinesToStraight(String param) {
		param = param.replaceAll("curved=1;","");
		param = param.replaceAll("elbow=vertical;","");
		param = param.replaceAll("edgeStyle=[^;]*;","");
		return param;
	}
	
	public TreeToXML(String boxParamsMax, String boxParamsMin, String colorboxParamsMax, String colorboxParamsMin, String lineParamsMax, 
			String lineParamsMin, String arrowParamsMax, String arrowParamsMin, String arrowParamsPointMax, String arrowParamsPointMin) {
		this(80, 20, 1, boxParamsMax, boxParamsMin, colorboxParamsMax, colorboxParamsMin, lineParamsMax, lineParamsMin, arrowParamsMax, arrowParamsMin, arrowParamsPointMax, arrowParamsPointMin);
	}
	
	public TreeToXML() {
		this(80, 20, 1);
	}

	public float boxWidth, boxHeight, lineWidth, boxSpacingX, boxSpacingY;
	
	public String boxParamsMax, colorboxParamsMax, lineParamsMax, arrowParamsMax, arrowParamsPointMax;
	public String boxParamsMin, colorboxParamsMin, lineParamsMin, arrowParamsMin, arrowParamsPointMin;
	public String irrelevantBoxParamsMax, irrelevantColorboxParamsMax, irrelevantLineParamsMax, irrelevantArrowParamsMax;
	public String irrelevantBoxParamsMin, irrelevantColorboxParamsMin, irrelevantLineParamsMin, irrelevantArrowParamsMin;
	
	private String[] boxParams() {
		return new String[]{boxParamsMax, boxParamsMin, irrelevantBoxParamsMax, irrelevantBoxParamsMin}; }
	private String[] colorboxParams() {
		return new String[]{colorboxParamsMax, colorboxParamsMin, irrelevantColorboxParamsMax, irrelevantColorboxParamsMin}; }
	private String[] lineParams() {
		return new String[]{lineParamsMax, lineParamsMin, irrelevantLineParamsMax, irrelevantLineParamsMin}; }
	private String[] arrowParams() {
		return new String[]{arrowParamsMax, arrowParamsMin, irrelevantArrowParamsMax, irrelevantArrowParamsMin}; }
	
	public int fontSize = 9;
	public String textParams = "strokeColor=none;fillColor=none;align=center;verticalAlign=middle;rounded=0;";
	public boolean addText = true;
	
	public String subComponentLockState = "movable=0;resizable=0;rotatable=0;deletable=0;editable=0;locked=1;connectable=0;";
	public String mainLockState = "movable=1;resizable=0;rotatable=1;deletable=1;editable=1;locked=0;connectable=0;";
	
	private static final String HEADER = ""
			+ "<mxfile host=\"app.diagrams.net\" agent=\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0\" version=\"29.1.1\">"
			+ "\n\t<diagram name=\"Page-1\" id=\"OUCnc_cJ2f6Imd4TqsKn\">"
			+ "\n\t\t<mxGraphModel dx=\"335\" dy=\"337\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"827\" pageHeight=\"1169\" math=\"0\" shadow=\"0\">"
			+ "\n\t\t\t<root>"
			+ "\n\t\t\t\t<mxCell id=\"0\" />"
			+ "\n\t\t\t\t<mxCell id=\"1\" parent=\"0\" />";
	private static final String TAIL = ""
			+ "\n\t\t\t</root>"
			+ "\n\t\t</mxGraphModel>"
			+ "\n\t</diagram>"
			+ "\n</mxfile>";
	private static final String PREFIX = "\n\t\t\t\t";
	
	private String getNodeString(NodeInfo n, double minV, double maxV, String[] boxParams, String[] colorboxParams, String[] lineParams, String[] arrowParams) {
		StringBuilder sb = new StringBuilder();
		boolean isRelevant = n.ID == 0 || n.original.isRelevant(); // if root node is irrelevant, draw it as relevant
		float x = n.horizontal_placement * (boxWidth + boxSpacingX);
		float y = n.vertical_placement * (boxHeight + boxSpacingY);
		float lower = Math.max(boxWidth * (float) ((n.lower - minV) / (maxV - minV)), lineWidth * 0.5f);
		float range = Math.min(boxWidth * (float) ((n.upper - n.lower) / (maxV - minV)), boxWidth - lower - lineWidth*0.5f);
		boolean pointValue = false;
		if (range <= 0) {
			lower -= lineWidth*0.5f;
			range = lineWidth;
			pointValue = true;
		}
		int paramIndex = (isRelevant ? 0 : 2) + (n.maximising ? 0 : 1);
		// BOX
		sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"box%d\" parent=\"1\" style=\"%s%sstrokeWidth=%f;\" value=\"\" vertex=\"1\">",
				PREFIX, n.ID, boxParams[paramIndex], mainLockState, lineWidth));
		sb.append(String.format(Locale.CANADA, "%s\t<mxGeometry height=\"%f\" width=\"%f\" x=\"%f\" y=\"%f\" as=\"geometry\" />%s</mxCell>",
				PREFIX, boxHeight, boxWidth, x, y, PREFIX));
		// COLORBOX
		sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"colorbox%d\" parent=\"box%d\" style=\"%s%sstrokeWidth=%f;\" value=\"\" vertex=\"1\">",
				PREFIX, n.ID, n.ID, colorboxParams[paramIndex], subComponentLockState, lineWidth*0.5f));
		sb.append(String.format(Locale.CANADA, "%s\t<mxGeometry height=\"%f\" width=\"%f\" x=\"%f\" y=\"%f\" as=\"geometry\" />%s</mxCell>",
				PREFIX, boxHeight-lineWidth, range, lower, lineWidth*0.5f, PREFIX));
		// ARROW
		if (!pointValue) {
			sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"arrow%d\" edge=\"1\" parent=\"box%d\" source=\"box%d\" target=\"colorbox%d\" style=\"%s%sstrokeWidth=%f;\">",
					PREFIX, n.ID, n.ID, n.ID, n.ID, arrowParams[paramIndex], subComponentLockState, lineWidth*0.75f));
		} else {
			sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"vert%d\" edge=\"1\" parent=\"colorbox%d\" source=\"colorbox%d\" target=\"colorbox%d\" style=\"%s%s\">",
					PREFIX, n.ID, n.ID, n.ID, n.ID, "endArrow=none;exitX=0.5;exitY=1;entryX=0.5;entryY=0;strokeColor=none", subComponentLockState));
			sb.append("%s\t<mxGeometry relative=\"1\" as=\"geometry\" />%s</mxCell>".formatted(PREFIX, PREFIX));
			sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"arrow%d\" edge=\"1\" parent=\"box%d\" source=\"box%d\" target=\"vert%d\" style=\"%s%sstrokeWidth=%f;\">",
					PREFIX, n.ID, n.ID, n.ID, n.ID, n.maximising ? arrowParamsPointMax : arrowParamsPointMin, subComponentLockState, lineWidth*0.75f));
		}
		sb.append("%s\t<mxGeometry relative=\"1\" as=\"geometry\" />%s</mxCell>".formatted(PREFIX, PREFIX));
		// LINES
		if (n.parent != null) {
			sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"line%d\" edge=\"1\" parent=\"1\" source=\"box%d\" target=\"box%d\" style=\"%s%sstrokeWidth=%f;\">",
					PREFIX, n.ID, n.parent.ID, n.ID, lineParams[(isRelevant ? 0 : 2) + (n.parent.maximising ? 0 : 1)], subComponentLockState, lineWidth));
			sb.append("%s\t<mxGeometry relative=\"1\" as=\"geometry\" />%s</mxCell>".formatted(PREFIX, PREFIX));
		}
		if (addText) {
			sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"textLeft%d\" parent=\"box%d\" style=\"text;html=1;%s%s\" value=\"&lt;font style=&quot;font-size: %dpx;&quot;&gt;%.0f&lt;/font&gt;\" vertex=\"1\">",
					PREFIX, n.ID, n.ID, textParams, subComponentLockState, fontSize, n.lower));
			sb.append(String.format(Locale.CANADA, "%s\t<mxGeometry height=\"%d\" width=\"%f\" x=\"0\" y=\"%f\" as=\"geometry\" />%s</mxCell>",
					PREFIX, fontSize, boxWidth*0.5f, boxHeight, PREFIX));
			sb.append(String.format(Locale.CANADA, "%s<mxCell id=\"textRight%d\" parent=\"box%d\" style=\"text;html=1;%s%s\" value=\"&lt;font style=&quot;font-size: %dpx;&quot;&gt;%.0f&lt;/font&gt;\" vertex=\"1\">",
					PREFIX, n.ID, n.ID, textParams, subComponentLockState, fontSize, n.upper));
			sb.append(String.format(Locale.CANADA, "%s\t<mxGeometry height=\"%d\" width=\"%f\" x=\"%f\" y=\"%f\" as=\"geometry\" />%s</mxCell>",
					PREFIX, fontSize, boxWidth*0.5f, boxWidth*0.5f, boxHeight, PREFIX));
		}
		return sb.toString();
	}
	
	protected String transform(List<NodeInfo> nodes, double minValue, double maxValue) {
		StringBuilder sb = new StringBuilder();
		sb.append(HEADER);
		for (var n : nodes) {
			sb.append(getNodeString(n, minValue, maxValue, boxParams(), colorboxParams(), lineParams(), arrowParams()));
		}
		sb.append(TAIL);
		return sb.toString();
	}
	
	public String transform(GameTreeNode<?,?> tree, int maxDepth, double minValue, double maxValue) {
		List<NodeInfo> nodes = NodeInfo.processTree(tree, maxDepth, new double[2]);
		return transform(nodes, minValue, maxValue);
	}
	
	public String transform(GameTreeNode<?,?> tree, int maxDepth) {
		double[] minAndMaxValues = new double[2];
		List<NodeInfo> nodes = NodeInfo.processTree(tree, maxDepth, minAndMaxValues);
		return transform(nodes, minAndMaxValues[0], minAndMaxValues[1]);
	}
	
	private static class NodeInfo {
		private long ID;
		private NodeInfo parent;
		private int vertical_placement;
		private float horizontal_placement;
		private double lower, upper;
		private boolean maximising;
		private GameTreeNode<?,?> original;
		
		private NodeInfo(long ID, GameTreeNode<?,?> node, NodeInfo parent, int vertical, float horizontal) {
			this.ID = ID;
			this.parent = parent;
			vertical_placement = vertical;
			horizontal_placement = horizontal;
			lower = node.lowerbound();
			upper = node.upperbound();
			maximising = node.maximising();
			original = node;
		}
		
		private static List<NodeInfo> processTree(GameTreeNode<?,?> root, int maxDepth, double[] minAndMaxValues) {
			ArrayList<NodeInfo> result = new ArrayList<>();
			Queue<NodeInfo> queue = new LinkedList<>();
			int lastVertical = 0;
			int horizontal = 0;
			
			long running_ID = 0;
			var rootinfo = new NodeInfo(running_ID++, root, null, 0, 0);
			queue.add(rootinfo);
			
			minAndMaxValues[0] = rootinfo.lower;
			minAndMaxValues[1] = rootinfo.upper;
			
			while (!queue.isEmpty()) {
				NodeInfo current = queue.poll();
				result.add(current);
				if (current.vertical_placement != lastVertical) {
					lastVertical = current.vertical_placement;
					horizontal = 0;
				}
				var hasChildren = current.original.savedChildren();
				if (lastVertical < maxDepth && hasChildren.isPresent()) {
					var children = hasChildren.get();
					for (int i=0; i < children.size(); i++) {
						var childinfo = new NodeInfo(running_ID++,children.get(i), current, current.vertical_placement+1, horizontal++);
						if (childinfo.lower < minAndMaxValues[0])
							minAndMaxValues[0] = childinfo.lower;
						if (childinfo.upper > minAndMaxValues[1])
							minAndMaxValues[1] = childinfo.upper;
						queue.add(childinfo);
					}
				}
			}
			return result;
		}
	}
	
}
