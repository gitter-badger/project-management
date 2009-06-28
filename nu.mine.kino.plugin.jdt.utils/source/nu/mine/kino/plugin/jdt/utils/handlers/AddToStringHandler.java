/******************************************************************************
 * Copyright (c) 2009 Masatomi KINO and others. 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *      Masatomi KINO - initial API and implementation
 * $Id$
 ******************************************************************************/
//作成日: 2009/06/27
package nu.mine.kino.plugin.jdt.utils.handlers;

import java.lang.reflect.InvocationTargetException;

import nu.mine.kino.plugin.jdt.utils.JDTUtils;
import nu.mine.kino.plugin.jdt.utils.JDTUtilsPlugin;
import nu.mine.kino.plugin.jdt.utils.WorkbenchRunnableAdapter;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * @author Masatomi KINO
 * @version $Revision$
 */
/**
 * @author Masatomi KINO
 * @version $Revision$
 */
public class AddToStringHandler extends AbstractHandler implements IHandler {

    public Object execute(ExecutionEvent event) throws ExecutionException {
        final IJavaElement element = JDTUtils.getJavaElement(event);
        IJavaElement[] targets = null;

        // ソース自体を選択した場合。
        if (element instanceof ICompilationUnit) {
            // eventからCompilationUnitを取得。
            final ICompilationUnit unit = (ICompilationUnit) element;
            // unitから、子要素たちをIJavaElementの配列として取得。
            targets = JDTUtils.unit2IJavaElements(unit);
        }
        // ソース下のクラス名を選択した場合。
        else if (element.getElementType() == IJavaElement.TYPE) {
            IType type = (IType) element;
            IJavaElement[] tmpTargets = new IJavaElement[] { type };
            targets = tmpTargets;
        }

        try {
            IWorkbenchSite site = HandlerUtil.getActiveSite(event);
            AddToStringThread op = new AddToStringThread(targets, site);
            PlatformUI.getWorkbench().getProgressService().runInUI(
                    PlatformUI.getWorkbench().getProgressService(),
                    new WorkbenchRunnableAdapter(op, op.getScheduleRule()),
                    op.getScheduleRule());
        } catch (InvocationTargetException e) {
            JDTUtilsPlugin.logException(e);
        } catch (InterruptedException e) {
            JDTUtilsPlugin.logException(e);
        }
        // ////////////////////////////////////////////////////////////////////////////
        return null;
    }

    class AddToStringThread implements IWorkspaceRunnable {

        private final IJavaElement[] javaElements;

        private final IWorkbenchSite site;

        public AddToStringThread(IJavaElement[] javaElements,
                IWorkbenchSite site) {
            this.javaElements = javaElements;
            this.site = site;
        }

        public ISchedulingRule getScheduleRule() {
            return ResourcesPlugin.getWorkspace().getRoot();
        }

        public void run(IProgressMonitor monitor) throws CoreException {
            addToString(javaElements, monitor);

            // IClasspathEntry[] rawClasspath = javaElements[0].getJavaProject()
            // .getRawClasspath();
            // for (IClasspathEntry classpathEntry : rawClasspath) {
            // System.out.println(classpathEntry);
            // }

            // FormatAllAction formatAllAction = new FormatAllAction(site);
            // IStructuredSelection selection = new StructuredSelection(unit);
            // formatAllAction.run(selection);
        }
    }

    /**
     * @param elements
     * @param monitor
     * @throws CoreException
     */
    private void addToString(IJavaElement[] elements, IProgressMonitor monitor)
            throws CoreException {
        if (elements == null || elements.length == 0) {
            return;
        }
        try {
            monitor.beginTask("toStringを追加します", 5);
            // ITextFileBufferManagerの取得。
            ITextFileBufferManager manager = FileBuffers
                    .getTextFileBufferManager();
            // IPath path = unit.getPath();
            IPath path = elements[0].getPath();
            // ファイルにconnect
            SubProgressMonitor subMonitor = new SubProgressMonitor(monitor, 4);
            subMonitor.beginTask("", elements.length);
            manager.connect(path, LocationKind.IFILE, subMonitor);
            try {
                // document取得。
                IDocument document = manager.getTextFileBuffer(path,
                        LocationKind.IFILE).getDocument();
                // IJavaProject project = unit.getJavaProject();
                IJavaProject project = elements[0].getJavaProject();

                // エディット用クラスを生成。
                MultiTextEdit edit = new MultiTextEdit();

                // 子要素は、パッケージ宣言だったり、クラスだったりする。一つのソースに複数クラスが書いてある場合もあるし。
                for (final IJavaElement javaElement : elements) {
                    // ↓型(クラス)だったらば、ITypeにキャストしていい。
                    if (javaElement.getElementType() == IJavaElement.TYPE) {
                        // ホントはココで、選択されたType側だけ実行って判断が必要。
                        // ハンドラからcuをもらった時点で、どっちのJavaElementかって情報を保持しておかないと難しいな。
                        IType type = (IType) javaElement;
                        IMethod lastMethod = JDTUtils
                                .getLastMethodFromType(type);
                        String code = JDTUtils
                                .createIndentedCode(JDTUtils.createToString(
                                        type, lastMethod, document, project),
                                        lastMethod, document, project);

                        // オフセット位置を計算する。
                        int endOffSet = JDTUtils.getMemberEndOffset(lastMethod,
                                document);

                        edit.addChild(new InsertEdit(endOffSet, code)); // オフセット位置に、挿入する。
                    }
                    subMonitor.worked(1);
                }
                edit.apply(document); // apply all edits
            } catch (BadLocationException e) {
                e.printStackTrace();
            } finally {
                manager.disconnect(path, LocationKind.IFILE, subMonitor);
                subMonitor.done();
            }

        } finally {
            monitor.worked(1);
            monitor.done();
        }
    }
}
