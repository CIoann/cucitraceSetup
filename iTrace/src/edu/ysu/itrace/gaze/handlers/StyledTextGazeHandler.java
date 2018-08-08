package edu.ysu.itrace.gaze.handlers;

import org.eclipse.jface.bindings.SchemeEvent;
//import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;

import edu.ysu.itrace.AstFtrManager;
import edu.ysu.itrace.AstManager;
import edu.ysu.itrace.ControlView;
import edu.ysu.itrace.Gaze;
import edu.ysu.itrace.QAManager;
import edu.ysu.itrace.ScEntity;
import edu.ysu.itrace.gaze.IGazeHandler;
import edu.ysu.itrace.gaze.IStyledTextGazeResponse;

/**
 * Implements the gaze handler interface for a StyledText widget.
 */
public class StyledTextGazeHandler implements IGazeHandler {
	private StyledText targetStyledText;
	// private ProjectionViewer projectionViewer;
	private AstManager astManager;
	private AstFtrManager astFtrManager;

	private QAManager qaManager;
	private String filename;

	/**
	 * Constructs a new gaze handler for the target StyledText object
	 */
	public StyledTextGazeHandler(Object target, String filename) {
		this.targetStyledText = (StyledText) target;
		this.filename = filename;

	}

	private boolean isFeature() {
		if (filename.contains(".feature")) {
			// This is a feature file in editor should be handled.
		//	System.out.println("Step 4: " + astFtrManager.getPath().toString() + filename);
			// System.out.println(""+
			// targetStyledText.getText());
			return true;
		}
		return false;

	}

	private boolean isREADME() {
		if (filename.contains(".md")) {
			// This is a feature file in editor should be handled.
		//	System.out.println("Step 4: " + qaManager.getPath().toString() + filename);
			// System.out.println(""+
			// targetStyledText.getText());
			return true;
		}
		return false;
	}

	@Override
	public IStyledTextGazeResponse handleGaze(int absoluteX, int absoluteY, int relativeX, int relativeY,
			final Gaze gaze) {
		final int lineIndex;
		final int col;
		final Point absoluteLineAnchorPosition;
		final String name;
		final int lineHeight;
		final int fontHeight;
		final ScEntity[] entities;
		// final SceEntities[] enty;

		final String path;

		try {

			int lineOffset;
			int offset;
			Point relativeRoot;
			Point lineAnchorPosition;
			int splitLength;

			if (targetStyledText.getData(ControlView.KEY_AST) == null
					&& (targetStyledText.getData(ControlView.KEY_FTR) == null)
					&& (targetStyledText.getData(ControlView.KEY_QA) == null)) {
				System.out.print("No AST");

				return null;
			}

			if ((targetStyledText.getData(ControlView.KEY_QA) != null) && isREADME()) {
				System.out.println("QA!!!!");
				qaManager = (QAManager) targetStyledText.getData(ControlView.KEY_QA);
				lineOffset = 0;
				offset = 0;
				relativeRoot = new Point(0, 0);
				lineAnchorPosition = new Point(0, 0);
				lineIndex = 0;
				col = 0;
				absoluteLineAnchorPosition = new Point(0, 0);

				lineHeight = 0;
				fontHeight = 0;

				entities = qaManager.getSCEs();
//
//				for (ScEntity e1 : entities) {
//					System.out.println("Entity test " + e1.getName());
//				}
				path = qaManager.getPath();
				splitLength = path.split("\\\\").length;
				name = path.split("\\\\")[splitLength - 1];
				
				System.out.println("done qa");

			}

			else if (targetStyledText.getData(ControlView.KEY_AST) != null && !isFeature()) {

				System.out.println("File Access");
				lineIndex = targetStyledText.getLineIndex(relativeY);
				lineOffset = targetStyledText.getOffsetAtLine(lineIndex);

				astManager = (AstManager) targetStyledText.getData(ControlView.KEY_AST);
				// System.out.println("Step 3: Visibility to text editor's
				// text");//) + targetStyledText.getText());
				// projectionViewer = astManager.getProjectionViewer();
				try {
					offset = targetStyledText.getOffsetAtLocation(new Point(relativeX, relativeY));
				} catch (IllegalArgumentException ex) {
					return null;
				}
				col = offset - lineOffset;

				// (0, 0) relative to the control in absolute screen
				// coordinates.
				relativeRoot = new Point(absoluteX - relativeX, absoluteY - relativeY);
				// Top-left position of the first character on the line in
				// relative coordinates.
				lineAnchorPosition = targetStyledText.getLocationAtOffset(lineOffset);
				// To absolute.
				absoluteLineAnchorPosition = new Point(lineAnchorPosition.x + relativeRoot.x,
						lineAnchorPosition.y + relativeRoot.y);

				lineHeight = targetStyledText.getLineHeight();
				fontHeight = targetStyledText.getFont().getFontData()[0].getHeight();
				entities = astManager.getSCEs(lineIndex + 1, col);
				//
				// for (ScEntity e1 : entities) {
				// System.out.println("Entity test " + e1.getName());
				// }
				path = astManager.getPath();
				splitLength = path.split("\\\\").length;
				name = path.split("\\\\")[splitLength - 1];

				System.out.println("done ast");
			} else {
				System.out.println("Feature file");
				astFtrManager = (AstFtrManager) targetStyledText.getData(ControlView.KEY_FTR);
				lineIndex = targetStyledText.getLineIndex(relativeY);
				lineOffset = targetStyledText.getOffsetAtLine(lineIndex);
				try {
					offset = targetStyledText.getOffsetAtLocation(new Point(relativeX, relativeY));
				} catch (IllegalArgumentException ex) {
					return null;
				}
				col = offset - lineOffset;
				relativeRoot = new Point(absoluteX - relativeX, absoluteY - relativeY);
				lineAnchorPosition = targetStyledText.getLocationAtOffset(lineOffset);
				absoluteLineAnchorPosition = new Point(lineAnchorPosition.x + relativeRoot.x,
						lineAnchorPosition.y + relativeRoot.y);
				lineHeight = targetStyledText.getLineHeight();
				fontHeight = targetStyledText.getFont().getFontData()[0].getHeight();

				entities = astFtrManager.getSCEs(lineIndex + 1, col);
				// System.out.println("Entities retrieved" + entities.length);
				// for (ScEntity f : entities) {
				// System.out.println("Entity " + f.type);
				// System.out.println("Entity " + f.name);
				// System.out.println();
				//
				// }
				path = astFtrManager.getPath();

				splitLength = path.split("\\\\").length;
				name = path.split("\\\\")[splitLength - 1];
				// return null;

				System.out.println("done feature");
			}

		} catch (IllegalArgumentException e) {
			/*
			 * An IllegalArgumentException SHOULD mean that the gaze fell
			 * outside the valid text area, so just drop this one.
			 */
			e.printStackTrace();
			return null;
		}

		/*
		 * This anonymous class just grabs the variables marked final in the
		 * enclosing method and returns them.
		 */
		return new IStyledTextGazeResponse() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public String getGazeType() {
				/*
				 * String[] splitPath = path.split("\\."); String type =
				 * splitPath[splitPath.length-1];
				 */
				String type = path;
				int dotIndex;
				for (dotIndex = 0; dotIndex < type.length(); dotIndex++)
					if (path.charAt(dotIndex) == '.')
						break;
				if (dotIndex + 1 == type.length())
					return "text";
				type = type.substring(dotIndex + 1);

				// System.out.print("gaze type" + type);
				return type;
			}

			@Override
			public int getLineHeight() {
				return lineHeight;
			}

			@Override
			public int getFontHeight() {
				return fontHeight;
			}

			@Override
			public Gaze getGaze() {
				return gaze;
			}

			public IGazeHandler getGazeHandler() {
				return StyledTextGazeHandler.this;
			}

			@Override
			public int getLine() {
				return lineIndex + 1;
			}

			@Override
			public int getCol() {
				return col;
			}

			// Write out the position at the top-left of the first
			// character in absolute screen coordinates.
			@Override
			public int getLineBaseX() {
				return absoluteLineAnchorPosition.x;
			}

			@Override
			public int getLineBaseY() {
				return absoluteLineAnchorPosition.y;
			}

			@Override
			public ScEntity[] getSCEs() {

				return entities;
			}

			@Override
			public String getPath() {
				return path;
			}

		};
	}

}
