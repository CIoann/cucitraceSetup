package edu.ysu.itrace;


import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.LinkedList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.progress.UIJob;

import cucumber.eclipse.editor.editors.GherkinModel;
import cucumber.eclipse.editor.editors.PositionedElement;
import edu.ysu.itrace.ScEntity.SCEType;


public class AstFtrManager {


	public class ScEntityFTR extends ScEntity {

		public final int KEYWORD_SPACE = 1;
		public final int offeset = 4;

		private int startModelOffset;
	}

	/**
	 * Task ran on UI thread to reload the AST. Like any UIJob, it can be
	 * scheduled to occur later and can be canceled.
	 */
	private class ReloadAstFtrJob extends UIJob {
		private AstFtrManager ftrManager;

		public ReloadAstFtrJob(String name, AstFtrManager ftrManager) {
			super(name);
			this.ftrManager = ftrManager;
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			ftrManager.reload();
			return Status.OK_STATUS;
		}
	}

	private IEditorPart editor;
	private StyledText styledText;
	private ProjectionViewer projectionViewer;
	private ReloadAstFtrJob reloadFtrJob;
	private LinkedList<ScEntityFTR> sourceCodeEntities;
	private String editorPath;

	final private int AFTER_KEYPRESS_RELOAD_THRESHOLD_MILLIS = 1000;

	public AstFtrManager(IEditorPart editor, StyledText styledText) {
		try {
			editorPath = ((IFileEditorInput) editor.getEditorInput()).getFile().getFullPath().toFile()
					.getCanonicalPath();
		} catch (IOException e) {
			// ignore IOErrors while constructing path
			editorPath = "?";
		}
		this.editor = editor;
		this.styledText = styledText;
		// This is the only why I know to get the ProjectionViewer. Perhaps
		// there is better way. ~Ben
		ITextOperationTarget t = (ITextOperationTarget) editor.getAdapter(ITextOperationTarget.class);

		System.out.println("Step 1: AstFtrManager " + editorPath);
		if (t instanceof ProjectionViewer)
			projectionViewer = (ProjectionViewer) t;
		hookupAutoReload();
		reload();

		System.out.println("Step 2: AstFtrManager " + editorPath);

	}

	/**
	 * Returns a string representation of the path to the file associated with
	 * the current editor.
	 */
	public String getPath() {
		return editorPath;
	}

	public ProjectionViewer getProjectionViewer() {
		return projectionViewer;
	}

	public ScEntityFTR[] getSCEs(int lineNumber, int colNumber) {
		LinkedList<ScEntityFTR> entities = new LinkedList<ScEntityFTR>();
		for (ScEntityFTR sce : sourceCodeEntities) {
			boolean found = true;
			if (lineNumber < sce.startLine || lineNumber > sce.endLine)
				found = false;
			if (lineNumber == sce.startLine && colNumber < sce.startCol)
				found = false;
			if (lineNumber == sce.endLine && colNumber > sce.endCol)
				found = false;
			if (found)
				entities.add(sce);
		}
		return entities.toArray(new ScEntityFTR[0]);
	}

	public void reload() {

		System.out.println("======");
		System.out.println("RELOAD");
		System.out.println("======");
		
		
		// Reset source code entities list.
		sourceCodeEntities = new LinkedList<ScEntityFTR>();
	//	System.out.println("text is available already here" + styledText.getText());
		String source = styledText.getText().toString();
		Document document = new Document(source);
		GherkinModel gm = new GherkinModel();

		gm.updateFromDocument(document);

		System.out.println("This is gherkin model the document " + gm.getFeatureElement().getStatement().getKeyword());

		PositionedElement feature = gm.getFeatureElement();
		System.out.println();

		System.out.println("feature begins" + gm.getFeatureElement().getStatement().getName());

		System.out.println();
//note check when the source is created 
		for (PositionedElement pe : feature.getChildren()) {
			sourceCodeEntities.add(createElem(pe));
			 System.out.println("PE type" +pe.getStatement().getKeyword() +
			 "Scenario Name: " + pe.getStatement().getName().toString());
			// System.out.println();
			//

			for (PositionedElement pe1 : pe.getChildren()) {
				sourceCodeEntities.add(createElem(pe1));
				// System.out.println("PE1 type"
				// +pe1.getStatement().getKeyword() + "Line Name: " +
				// pe1.getStatement().getName() + pe1.getStatement().getLine());
				// System.out.println();
			}

		}

	}

	private ScEntityFTR createElem(PositionedElement pe) {
		ScEntityFTR sce = new ScEntityFTR();
		System.out.println("Keyword, " +pe.getStatement().getKeyword().toUpperCase() );
		//a.replaceAll("\\s+","");
		String keyword = pe.getStatement().getKeyword().replaceAll("\\s+", "").toUpperCase();
		sce.type = configureType(keyword);
		sce.how = ScEntity.SCEHow.DECLARE;
		
		String name = pe.getStatement().getName().toString();
		name = name.replaceAll("'", "");
		name = name.replaceAll(" ", "_");
		sce.name = name;
		
		determineSCEPosition(pe, sce);
		return sce;
	}
	
	private SCEType configureType(String keyword){
		SCEType kokos=null;
		System.out.println("entiity keyword" + keyword);
			switch (keyword){
			case "SCENARIO" : 
				kokos = ScEntity.SCEType.SCENARIO;
				break;
			case "FEATURE" : 
				kokos = ScEntity.SCEType.FEATURE;
				break;
			case "GIVEN" : 
				kokos = ScEntity.SCEType.GIVEN;
				break;
			case "AND" : 
				kokos = ScEntity.SCEType.AND;
				break;
			case "WHEN" : 
				kokos = ScEntity.SCEType.WHEN;
				break;
			case "SCENARIOOUTLINE" : 
				kokos = ScEntity.SCEType.SCENARIO_OUTLINE;
				break;
			case "BUT" : 
				kokos = ScEntity.SCEType.BUT;
				break;
			case "THEN" : 
				kokos = ScEntity.SCEType.THEN;
				break;
			case "BACKGROUNDS" : 
				kokos = ScEntity.SCEType.BACKGROUNDS;
				break;
			case "TAGS" : 
				kokos = ScEntity.SCEType.TAGS;
				break;
				default: 
					break;
			}
			System.out.println("kokos is " + kokos);
		return kokos;

			
			
	}

	private static void determineSCEPosition(PositionedElement pe, ScEntityFTR sce) {
		int offeset = sce.offeset + sce.KEYWORD_SPACE + pe.getStatement().getKeyword().length();
		// System.out.println("ELEMENT" + pe.toString());

		// System.out.println("Line Range " +
		// pe.getStatement().getLineRange().getFirst());
		sce.totalLength = pe.getStatement().getName().toString().length();
		sce.startLine = pe.getStatement().getLine();
		sce.endLine = pe.getStatement().getLine();
		// System.out.println("NAME OF STATETMENT "+
		// pe.getStatement().toString());
		sce.startCol = offeset;
		// getLineRange().getFirst();
		sce.endCol = pe.getStatement().getName().length() - 1;

		// System.out.println("SCE unit: " + "\n=========\n" + "Name :" +
		// sce.getName() +
		// "\n=========\n" + "Keyword " + sce.type + "\n=========\n"+ "Start and
		// End line : " +
		// sce.startLine + " " +sce.endLine + "\n=========\n" + "Start and End
		// col" + sce.startCol + " " +sce.endCol +
		// "\n=========\n" + "Total length : "+ sce.totalLength

		// );

	}

	private void hookupAutoReload() {
		final AstFtrManager ftrManager = this;

		KeyListener kl = new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void keyPressed(KeyEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void keyReleased(KeyEvent e) {
				// TODO Auto-generated method stub
				if (reloadFtrJob != null) {
					reloadFtrJob.cancel();
				}
				
				System.out.println("RELOADING");
				reloadFtrJob = new ReloadAstFtrJob("reloadFtrJob", ftrManager);
				reloadFtrJob.schedule(AFTER_KEYPRESS_RELOAD_THRESHOLD_MILLIS);
			}

		};
	}

}
