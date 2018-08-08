package edu.ysu.itrace;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.projection.ProjectionViewer;

/**
 * Keeps updated information about the source code viewed in one StyledText.
 */
public class AstManager {
    /**
     * Types of source code entities.
     */
    
    /**
     * Information extracted about a source code entity.
     */
    public class ScEntityAST extends ScEntity {

        private int startModelOffset;
        @Override
        public String createName(){
        	if( type != ScEntity.SCEType.COMMENT )
        		return name;
        	else{
        		int widgetOffsetStart = projectionViewer.modelOffset2WidgetOffset(startModelOffset);
                int widgetOffsetEnd = widgetOffsetStart+totalLength;
                if( widgetOffsetStart >= 0 && widgetOffsetEnd >= 0)
                	return styledText.getText(widgetOffsetStart,widgetOffsetEnd);
                else{
                	int offsetStart = startModelOffset;
                	while(widgetOffsetStart < 0){
                		offsetStart++;
                		widgetOffsetStart = projectionViewer.modelOffset2WidgetOffset(offsetStart);
                	}
                	int shownLineIndex = styledText.getLineAtOffset(offsetStart);
                	return styledText.getLine(shownLineIndex);
                }
        	}
        		
        }
    }

    /**
     * Task ran on UI thread to reload the AST. Like any UIJob, it can be
     * scheduled to occur later and can be canceled.
     */
    private class ReloadAstJob extends UIJob {
        private AstManager astManager;

        public ReloadAstJob(String name, AstManager astManager) {
            super(name);
            this.astManager = astManager;
        }

        @Override
        public IStatus runInUIThread(IProgressMonitor monitor) {
            astManager.reload();
            return Status.OK_STATUS;
        }
    }

    final private int AFTER_KEYPRESS_RELOAD_THRESHOLD_MILLIS = 1000;

    private IEditorPart editor;
    private StyledText styledText;
    private ProjectionViewer projectionViewer;
    private ReloadAstJob reloadAstJob;
    private LinkedList<ScEntityAST> sourceCodeEntities;
    private String editorPath;

    /**
     * Constructor. Loads the AST and sets up the StyledText to automatically
     * reload after certain events.
     * @param editor IEditorPart which owns the following StyledText.
     * @param styledText StyledText to which this AST pertains.
     */
    public AstManager(IEditorPart editor, StyledText styledText) {
        try {
            editorPath = ((IFileEditorInput) editor.getEditorInput()).getFile()
                    .getFullPath().toFile().getCanonicalPath();
        } catch (IOException e) {
            // ignore IOErrors while constructing path
            editorPath = "?";
        }
        this.editor = editor;
        this.styledText = styledText;
        //This is the only why I know to get the ProjectionViewer. Perhaps there is better way. ~Ben
        ITextOperationTarget t = (ITextOperationTarget) editor.getAdapter(ITextOperationTarget.class);
        if(t instanceof ProjectionViewer) projectionViewer = (ProjectionViewer)t;
        hookupAutoReload();
        reload();
    }

    /**
     * Returns a string representation of the path to the
     * file associated with the current editor.
     */
    public String getPath() {
        return editorPath;
    }
    public ProjectionViewer getProjectionViewer(){
    	return projectionViewer;
    }

    /**
     * Gets the source code entities found at a location in source.
     * @param lineNumber 1-based line number.
     * @param colNumber 0-based column number.
     * @return Array of all source code entities found.
     */
    public ScEntityAST[] getSCEs(int lineNumber, int colNumber) {
        LinkedList<ScEntityAST> entities =
                new LinkedList<ScEntityAST>();

        //Look through source code entities to find all entities at the given
        //location. They are already sorted from most specific to least
        //specific.
        for (ScEntityAST sce : sourceCodeEntities) {
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
        return entities.toArray(new ScEntityAST[0]);
    }

    /**
     * Reloads the AST from the current contents of the StyledText.
     */
    public void reload() {  
        //Reset source code entities list.
        sourceCodeEntities = new LinkedList<ScEntityAST>();

        IFile file = ((IFileEditorInput) editor.getEditorInput()).getFile();
        IProject project = file.getProject();
        IJavaProject jProject = JavaCore.create(project);
        ICompilationUnit compUnit = (ICompilationUnit) JavaCore.create(file);

        //If the compilation unit is null, this is not a Java file.
        if (compUnit == null)
            return;

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setProject(jProject);
        parser.setSource(compUnit);
        parser.setResolveBindings(true);
        parser.setUnitName(file.getName());
        final CompilationUnit compileUnit =
                (CompilationUnit) parser.createAST(null);

        ASTVisitor visitor = new ASTVisitor() {
       
        	public boolean visit(TypeDeclaration node) {
        	      ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.TYPE;
                sce.how = ScEntity.SCEHow.DECLARE;
                ITypeBinding binding = node.resolveBinding();
                if (binding != null)
                    sce.name = binding.getQualifiedName();
                else
                    sce.name = "?." + node.getName().getFullyQualifiedName();
                determineSCEPosition(compileUnit, node, sce);
                sourceCodeEntities.add(sce);
                return true;
            }

            public boolean visit(MethodDeclaration node) {
                ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.METHOD;
                sce.how = ScEntity.SCEHow.DECLARE;
                sce.name = node.getName().getFullyQualifiedName();
                determineSCEPosition(compileUnit, node, sce);
                extractDataFromMethodBinding(node.resolveBinding(), sce);
                sourceCodeEntities.add(sce);
                return true;
            }

            public boolean visit(VariableDeclarationFragment node) {
                ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.VARIABLE;
                sce.how = ScEntity.SCEHow.DECLARE;
                sce.name = node.getName().getFullyQualifiedName();
                determineSCEPosition(compileUnit, node, sce);
                extractDataFromVariableBinding(node.resolveBinding(), sce);
                sourceCodeEntities.add(sce);
                return true;
            }
            
          
            public boolean visit(EnumDeclaration node) {
            	ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.ENUM;
                sce.how = ScEntity.SCEHow.DECLARE;
                sce.name = node.getName().getFullyQualifiedName();
                determineSCEPosition(compileUnit, node, sce);
                sourceCodeEntities.add(sce);
                return true;
            }
            

            public boolean visit(MethodInvocation node) {
                ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.METHOD;
                sce.how = ScEntity.SCEHow.USE;
                sce.name = node.getName().getFullyQualifiedName();
                determineSCEPosition(compileUnit, node, sce);
                extractDataFromMethodBinding(node.resolveMethodBinding(), sce);
                sourceCodeEntities.add(sce);
                return false;
            }

            public boolean visit(FieldAccess node) {
                ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.VARIABLE;
                sce.how = ScEntity.SCEHow.USE;
                sce.name = node.getName().getFullyQualifiedName();
                determineSCEPosition(compileUnit, node, sce);
                extractDataFromVariableBinding(node.resolveFieldBinding(), sce);
                sourceCodeEntities.add(sce);
                return false;
            }
            
            public boolean visit(ImportDeclaration node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.IMPORT;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	sce.name = node.getName().getFullyQualifiedName();
            	determineSCEPosition(compileUnit, node, sce);
            	sourceCodeEntities.add(sce);
            	return false;
            }
            
            public boolean visit(ForStatement node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.FORSTATEMENT;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	determineSCEPosition(compileUnit, node, sce);
            	sce.name = String.format("ForStatement-l%dc%d",
            		sce.startLine, sce.startCol);
            	sourceCodeEntities.add(sce);
            	return true;
            }
            
            public boolean visit(EnhancedForStatement node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.FORSTATEMENT;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	determineSCEPosition(compileUnit, node, sce);
            	sce.name = String.format("EnhancedForStatement-l%dc%d",
            		sce.startLine, sce.startCol);
            	sourceCodeEntities.add(sce);
            	return true;
            }
            
            public boolean visit(WhileStatement node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.WHILESTATEMENT;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	determineSCEPosition(compileUnit, node, sce);
            	sce.name = String.format("WhileStatement-l%dc%d",
            		sce.startLine, sce.startCol);
            	sourceCodeEntities.add(sce);
            	return true;
            }
            
            public boolean visit(SwitchStatement node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.SWITCHSTATEMENT;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	determineSCEPosition(compileUnit, node, sce);
            	sce.name = String.format("SwitchStatement-l%dc%d",
            		sce.startLine, sce.startCol);
            	sourceCodeEntities.add(sce);
            	return true;
            }
            
            public boolean visit(IfStatement node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.IFSTATEMENT;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	determineSCEPosition(compileUnit, node, sce);
            	sce.name = String.format("IfStatement-l%dc%d",
            		sce.startLine, sce.startCol);
            	sourceCodeEntities.add(sce);
            	return true;
            }

            public boolean visit(ConditionalExpression node) {
            	ScEntityAST sce = new ScEntityAST();
            	sce.type = ScEntity.SCEType.CONDITIONAL_EXPRESSION;
            	sce.how = ScEntity.SCEHow.DECLARE;
            	determineSCEPosition(compileUnit, node, sce);
            	sce.name = String.format("ConditionalExpression-l%dc%d",
            		sce.startLine, sce.startCol);
            	sourceCodeEntities.add(sce);
            	return true;
            }
            
        };
        compileUnit.accept(visitor);
        //Get comments separately.
        for (Object comment_obj : compileUnit.getCommentList()) {
            if (comment_obj instanceof Comment) {
                Comment comment = (Comment) comment_obj;
                ScEntityAST sce = new ScEntityAST();
                sce.type = ScEntity.SCEType.COMMENT;
                if(comment.isLineComment()) sce.how = ScEntity.SCEHow.LINE_COMMENT;
                else if(comment.isBlockComment()) sce.how = ScEntity.SCEHow.BLOCK_COMMENT;
                else sce.how = ScEntity.SCEHow.DOC_COMMENT;
                //The projectionViewer is used to convert the ASTNode's model offset to a Widget offset. ~Ben
                sce.startModelOffset = comment.getStartPosition();
                sce.totalLength = comment.getLength();
                int widgetOffsetStart = projectionViewer.modelOffset2WidgetOffset(comment.getStartPosition());
                int widgetOffsetEnd = widgetOffsetStart+comment.getLength();
                if( widgetOffsetStart >= 0 && widgetOffsetEnd >= 0)
                	sce.name = styledText.getText(widgetOffsetStart,widgetOffsetEnd);
                else{
                	int offsetStart = comment.getStartPosition();
                	while(widgetOffsetStart < 0){
                		offsetStart++;
                		widgetOffsetStart = projectionViewer.modelOffset2WidgetOffset(offsetStart);
                	}
                	int shownLineIndex = styledText.getLineAtOffset(offsetStart);
                	sce.name = styledText.getLine(shownLineIndex);
                }
                determineSCEPosition(compileUnit, comment, sce);
                sourceCodeEntities.add(sce);
            }
        }
       
        
        
        //Smaller entities take higher priority. If a method appears in a class,
        //for example, a query for the method should return the method instead
        //of the class.
        Collections.sort(sourceCodeEntities,
            new Comparator<ScEntityAST>() {
                @Override
                public int compare(ScEntityAST lhs, ScEntityAST rhs) {
                    return lhs.totalLength - rhs.totalLength;
                }
            });
    }
    
  
    
    /**
     * Get line/column start/end information about an ASTNode.
     * @param compileUnit Compilation unit to which the ASTNode belongs.
     * @param node The ASTNode.
     * @param sce The SourceCodeEntity in which to store the result.
     */
    private static void determineSCEPosition(CompilationUnit compileUnit,
            ASTNode node, ScEntityAST sce) {
        sce.totalLength = node.getLength();
        sce.startLine = compileUnit.getLineNumber(node.getStartPosition());
        sce.endLine = compileUnit.getLineNumber(node.getStartPosition() +
                                                node.getLength());
        sce.startCol = compileUnit.getColumnNumber(node.getStartPosition());
        sce.endCol = compileUnit.getColumnNumber(node.getStartPosition() +
                                                 node.getLength());
    }

    /**
     * Extracts the fully qualified name and parameters from a method binding
     * and applies them to the name in a SourceCodeEntity.
     * @param binding The method binding.
     * @param sce SourceCodeEntity to which to apply changes. Name must be set
     *            to the entity's unqualified name.
     */
    private void extractDataFromMethodBinding(IMethodBinding binding,
            ScEntityAST sce) {
        if (binding != null) {
            //Get package and type name within which this method is declared.
            ITypeBinding type = binding.getDeclaringClass();
            if (type != null)
                sce.name = type.getQualifiedName() + "." + sce.name;
            else
                sce.name = "?." + sce.name;
            //Get method parameter types
            String params = "";
            for (ITypeBinding paramType : binding.getParameterTypes()) {
                if (paramType != null)
                    params += paramType.getQualifiedName() + ",";
            }
            if (params.length() > 0) {
                sce.name += "("
                          + params.substring(0, params.length() - 1)
                          + ")";
            } else
                sce.name += "()";
        } else {
            //If binding fails, mark the qualification as "?" to show it could
            //not be determined.
            sce.name = "?." + sce.name + "(?)";
        }
    }

    /**
     * Extracts the fully qualified name from a variable binding and applies
     * them to the name in a ScEntityAST.
     * @param binding The variable binding.
     * @param sce ScEntityAST to which to apply changes. Name must be set
     *            to the entity's unqualified name.
     */
    private static void extractDataFromVariableBinding(
            IVariableBinding binding, ScEntityAST sce) {
        if (binding != null) {
            //Type member variable.
            ITypeBinding type = binding.getDeclaringClass();
            if (type != null)
                sce.name = type.getQualifiedName() + "." + sce.name;
            //Variable declared in method.
            else {
                IMethodBinding method = binding.getDeclaringMethod();
                if (method != null) {
                    type = method.getDeclaringClass();
                    if (type != null) {
                        sce.name = type.getQualifiedName() + "."
                                 + method.getName() + "." + sce.name;
                    } else
                        sce.name = "?." + method.getName() + "." + sce.name;
                } else
                    sce.name = "?." + sce.name;
            }
        } else {
            //If binding fails, mark the qualification as "?" to show it could
            //not be determined.
            sce.name = "?." + sce.name;
        }
    }

    /**
     * Called by constructor. Hooks up the StyledText with listeners to reload
     * the AST when it is likely to have changed.
     */
    private void hookupAutoReload() {
        final AstManager astManager = this;

        //Listen for key activity, then reload when inactivity follows.
        KeyListener keyListener = new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                //Do nothing.
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (reloadAstJob != null)
                    reloadAstJob.cancel();

                reloadAstJob = new ReloadAstJob("reloadAstJob", astManager);
                reloadAstJob.schedule(AFTER_KEYPRESS_RELOAD_THRESHOLD_MILLIS);
            }
        };
        styledText.addKeyListener(keyListener);
    }
}
