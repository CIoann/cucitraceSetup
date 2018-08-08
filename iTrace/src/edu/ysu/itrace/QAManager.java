package edu.ysu.itrace;

import java.io.IOException;
import java.util.LinkedList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.progress.UIJob;

import edu.ysu.itrace.AstFtrManager.ScEntityFTR;



public class QAManager {

    final private int AFTER_KEYPRESS_RELOAD_THRESHOLD_MILLIS = 1000;
	private String editorPath; 
    private IEditorPart editor;
    private StyledText styledText;

    private ReloadQAJob reloadQAJob;
    private ProjectionViewer projectionViewer;
    

    private LinkedList<ScEntity> sces;
	
    
    private class ReloadQAJob extends UIJob {
        private QAManager qaManager;

        public ReloadQAJob(String name, QAManager qaManager) {
            super(name);
            this.qaManager = qaManager;
        }

        @Override
        public IStatus runInUIThread(IProgressMonitor monitor) {
        	qaManager.reload();
            return Status.OK_STATUS;
        }
    }
    
	public QAManager(IEditorPart editor, StyledText styledText) {
		
		 try {
	            editorPath = ((IFileEditorInput) editor.getEditorInput()).getFile()
	                    .getFullPath().toFile().getCanonicalPath();
	        } catch (IOException e) {
	            // ignore IOErrors while constructing path
	            editorPath = "?";
	        }
	        this.editor = editor;
	        this.styledText = styledText;
	        ITextOperationTarget t = (ITextOperationTarget) editor.getAdapter(ITextOperationTarget.class);
	        if(t instanceof ProjectionViewer) projectionViewer = (ProjectionViewer)t;
	        hookupAutoReload();
	        reload();
		
		System.out.println("QA MANAGER SET ");

			
	}
    public String getPath() {
        return editorPath;
    }
    
    public void reload() {  

    	sces = new LinkedList<ScEntity>();
		ScEntity sqa = new ScEntity();
		sqa.type= ScEntity.SCEType.QA;
		sqa.how= ScEntity.SCEHow.QA;
		sqa.name =editor.getTitle().toString();
		sqa.totalLength =0;
		sqa.startLine=0;
		sqa.endLine=0;
		sqa.startCol=0;
		sqa.endCol=0;
		sces.add(sqa);
    
    }
    public ScEntity[] getSCEs() {

     
        return sces.toArray(new ScEntity[0]);
    }
    private void hookupAutoReload() {
        final QAManager qaManager = this;

        //Listen for key activity, then reload when inactivity follows.
        KeyListener keyListener = new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                //Do nothing.
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (reloadQAJob != null)
                	reloadQAJob.cancel();

                reloadQAJob = new ReloadQAJob("reloadQAJob", qaManager);
                reloadQAJob.schedule(AFTER_KEYPRESS_RELOAD_THRESHOLD_MILLIS);
            }
        };
        styledText.addKeyListener(keyListener);
    }
}
