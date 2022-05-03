/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   21.02.2008 (Fabian Dill): created
 */
package org.knime.workbench.editor2.commands;

import static org.knime.workbench.ui.async.AsyncUtil.waitForTerminationAndOpenDialogWhenFailed;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.draw2d.geometry.Rectangle;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.Annotation;
import org.knime.core.node.workflow.WorkflowAnnotation;
import org.knime.core.node.workflow.WorkflowAnnotationID;
import org.knime.core.ui.node.workflow.WorkflowManagerUI;
import org.knime.core.ui.node.workflow.async.AsyncWorkflowAnnotationUI;
import org.knime.core.ui.node.workflow.async.AsyncWorkflowManagerUI;
import org.knime.workbench.editor2.editparts.AnnotationEditPart;

/**
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
public class ChangeAnnotationBoundsCommand extends AbstractKNIMECommand implements AsyncCommand {

    private final Rectangle m_oldBounds;

    private final Rectangle m_newBounds;

    private final WorkflowAnnotationID m_annotationID;

    /**
     * @param hostWFM The host WFM
     * @param portBar The workflow port bar to change
     * @param newBounds The new bounds
     */
    public ChangeAnnotationBoundsCommand(final WorkflowManagerUI hostWFM, final AnnotationEditPart portBar,
        final Rectangle newBounds) {
        super(hostWFM);
        CheckUtils.checkArgument(portBar.getModel() instanceof WorkflowAnnotation,
            "Argument must represent Workflow Annotation");
        var anno = (WorkflowAnnotation)portBar.getModel();
        m_oldBounds = new Rectangle(anno.getX(), anno.getY(), anno.getWidth(),  anno.getHeight());
        m_newBounds = newBounds;
        m_annotationID = anno.getID();
    }

    /** Resolves the actual annotation object (this command only remembers the annotation ID).
     * @return The annotation object to the annotation ID or an empty optional if not available
     * (The eclipse command stack should forbid this to be empty but possible no longer available
     * when deleted via another UI (AP.next?))
     */
    private Optional<WorkflowAnnotation> getWorkflowAnnotation() {
        return getHostWFMUI().getWorkflowAnnotations().stream() //
                .filter(a -> Objects.equals(a.getID(), m_annotationID)) //
                .findFirst();
    }

    private WorkflowAnnotation getWorkflowAnnotationOrThrow() {
        return getWorkflowAnnotation().orElseThrow(
            () -> new IllegalStateException("WorkflowAnnotation no longer present"));
    }

    @Override
    public boolean shallExecuteAsync() {
        return getWorkflowAnnotation().map(AsyncWorkflowAnnotationUI.class::isInstance).orElse(Boolean.FALSE);
    }

    @Override
    public AsyncWorkflowManagerUI getAsyncHostWFM() {
        if (getHostWFMUI() instanceof AsyncWorkflowManagerUI) {
            return (AsyncWorkflowManagerUI)getHostWFMUI();
        } else {
            return null;
        }
    }

    @Override
    public boolean canExecute() {
        // not calling getWorkflowAnnotation().isPresent() as that can be expensive (rather let fail)
        return super.canExecute();
    }

    /**
     * Sets the new bounds.
     *
     * @see org.eclipse.gef.commands.Command#execute()
     */
    @Override
    public void execute() {
        if (shallExecuteAsync()) {
            waitForTerminationAndOpenDialogWhenFailed(
                executeAsync().thenCompose(f -> getAsyncHostWFM().refreshAsync(false)),
                "Changing annotation bounds ...");
        } else {
            var annotation = getWorkflowAnnotationOrThrow();
            annotation.setDimension(m_newBounds.x, m_newBounds.y, m_newBounds.width, m_newBounds.height);
        }
    }

    @Override
    public CompletableFuture<Void> executeAsync() {
        Annotation annotation = getWorkflowAnnotationOrThrow();
        assert annotation instanceof AsyncWorkflowAnnotationUI;
        AsyncWorkflowAnnotationUI asyncAnno = (AsyncWorkflowAnnotationUI)annotation;
        return asyncAnno.setDimensionAsync(m_newBounds.x, m_newBounds.y, m_newBounds.width, m_newBounds.height);
    }

    /**
     * Sets the old bounds.
     *
     * @see org.eclipse.gef.commands.Command#execute()
     */
    @Override
    public void undo() {
        if (shallExecuteAsync()) {
            waitForTerminationAndOpenDialogWhenFailed(
                undoAsync().thenCompose(f -> getAsyncHostWFM().refreshAsync(false)),
                "Undo change of annotations bounds ...");
        } else {
            Annotation annotation = getWorkflowAnnotationOrThrow();
            annotation.setDimension(m_oldBounds.x, m_oldBounds.y, m_oldBounds.width, m_oldBounds.height);
            // must set explicitly so that event is fired by container
//            m_annotationEditPart.getFigure().setBounds(m_oldBounds);
//            m_annotationEditPart.getFigure().getLayoutManager().layout(m_annotationEditPart.getFigure());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> undoAsync() {
        Annotation annotation = getWorkflowAnnotationOrThrow();
        assert annotation instanceof AsyncWorkflowAnnotationUI;
        AsyncWorkflowAnnotationUI asyncAnno = (AsyncWorkflowAnnotationUI)annotation;
        return asyncAnno.setDimensionAsync(m_oldBounds.x, m_oldBounds.y, m_oldBounds.width, m_oldBounds.height);
    }
}
