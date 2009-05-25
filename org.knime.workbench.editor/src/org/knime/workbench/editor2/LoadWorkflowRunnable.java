/*
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2009
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 *
 * History
 *   11.01.2007 (sieb): created
 */
package org.knime.workbench.editor2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.swing.UIManager;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Display;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.DefaultNodeProgressMonitor;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResultEntry;
import org.knime.core.node.workflow.WorkflowPersistor.WorkflowLoadResult;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResultEntry.LoadResultEntryType;
import org.knime.workbench.KNIMEEditorPlugin;

/**
 * A runnable which is used by the {@link WorkflowEditor} to load a workflow
 * with a progress bar. NOTE: As the {@link UIManager} holds a reference to this
 * runnable an own class file is necessary sucht that all references to the
 * created workflow manager can be deleted, otherwise the manager can not be
 * deleted later and the memeory can not be freed.
 *
 * @author Christoph Sieb, University of Konstanz
 * @author Fabian Dill, University of Konstanz
 */
class LoadWorkflowRunnable extends PersistWorflowRunnable {

    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(LoadWorkflowRunnable.class);

    private WorkflowEditor m_editor;

    private File m_workflowFile;
    
    private Throwable m_throwable = null;

    /**
     * 
     * @param editor the {@link WorkflowEditor} for which the workflow should
     * be loaded
     * @param workflowFile the workflow file from which the workflow should be 
     * loaded (or created = empty workflow file)
     */
    public LoadWorkflowRunnable(final WorkflowEditor editor, 
            final File workflowFile) {
        m_editor = editor;
        m_workflowFile = workflowFile;
    }
    
    /**
     * 
     * @return the throwable which was thrown during the loading of the workflow
     * or null, if no throwable was thrown
     */
    Throwable getThrowable() {
        return m_throwable;
    }
    
    /**
     * 
     * {@inheritDoc}
     */
    public void run(final IProgressMonitor pm) {
        CheckThread checkThread = null;
        // indicates whether to create an empty workflow
        // this is done if the file is empty
        boolean createEmptyWorkflow = false;
        
        // name of the workflow in case a new one is created (not loaded)
        String name = null;
        if (m_workflowFile != null) {
            name = m_workflowFile.getParentFile().getName();
        }

        // set the loading canceled variable to false
        m_editor.setLoadingCanceled(false);
        m_throwable = null;

        try {
            // create progress monitor
            ProgressHandler progressHandler = new ProgressHandler(pm, 101,
                    "Loading workflow...");
            final DefaultNodeProgressMonitor progressMonitor 
                = new DefaultNodeProgressMonitor();
            progressMonitor.addProgressListener(progressHandler);

            checkThread = new CheckThread(pm, progressMonitor, true);

            checkThread.start();

            final WorkflowLoadResult result = WorkflowManager.loadProject(
                    m_workflowFile.getParentFile(), 
                    new ExecutionMonitor(progressMonitor));
            m_editor.setWorkflowManager(result.getWorkflowManager());
            pm.subTask("Finished.");
            pm.done();
            if (result.getWorkflowManager().isDirty()) {
                m_editor.markDirty();
            }
            
            final IStatus status = createStatus(result, 
                    !result.getGUIMustReportDataLoadErrors());
            final String message;
            switch (status.getSeverity()) {
            case IStatus.OK:
                message = "No problems during load.";
                break;
            case IStatus.WARNING:
                message = "Warnings during load";
                logPreseveLineBreaks("Warning during load: " 
                        + result.getFilteredError(
                                "", LoadResultEntryType.Warning), false);
                break;
            default:
                message = "Errors during load";
                logPreseveLineBreaks("Errors during load: " 
                        + result.getFilteredError(
                                "", LoadResultEntryType.Warning), true);
            }
            Display.getDefault().asyncExec(new Runnable() {
                public void run() {
                    // will not open if status is OK.
                    ErrorDialog.openError(
                            Display.getDefault().getActiveShell(), 
                            "Workflow Load", message, status);
                }
                
            });
        } catch (FileNotFoundException fnfe) {
            m_throwable = fnfe;
            LOGGER.fatal("File not found", fnfe);
        } catch (IOException ioe) {
            m_throwable = ioe;
            if (m_workflowFile.length() == 0) {
                LOGGER.info("New workflow created.");
                // this is the only place to set this flag to true: we have an 
                // empty workflow file, i.e. a new project was created
                // bugfix 1555: if an expection is thrown DO NOT create empty 
                // workflow 
                createEmptyWorkflow = true;
            } else {
                LOGGER.error("Could not load workflow from: "
                        + m_workflowFile.getName(), ioe);
            }
        } catch (InvalidSettingsException ise) {
            LOGGER.error("Could not load workflow from: "
                    + m_workflowFile.getName(), ise);
            m_throwable = ise;
        } catch (CanceledExecutionException cee) {
            LOGGER.info("Canceled loading worflow: " 
                    + m_workflowFile.getName());
            m_editor.setWorkflowManager(null);
            m_editor.setLoadingCanceled(true);
            m_editor.setLoadingCanceledMessage(cee.getMessage());
        } catch (Throwable e) {
            m_throwable = e;
            LOGGER.error("Workflow could not be loaded. " + e.getMessage(), e);
            m_editor.setWorkflowManager(null);
        } finally {
            // terminate the check thread
            checkThread.finished();
            // create empty WFM if a new workflow is created 
            // (empty workflow file)
            if (createEmptyWorkflow) {
                m_editor.setWorkflowManager(WorkflowManager.ROOT
                        .createAndAddProject(name));
                // save empty project immediately
                // bugfix 1341 -> see WorkflowEditor line 1294 
                // (resource delta visitor movedTo)
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {                        
                        m_editor.doSave(new NullProgressMonitor());
                    }
                });
                m_editor.setIsDirty(false);
                
            }
            // IMPORTANT: Remove the reference to the file and the
            // editor!!! Otherwise the memory can not be freed later
            m_editor = null;
            m_workflowFile = null;
        }
    }
    
    /** Logs the argument error to LOGGER, preserving line breaks.
     * This method will hopefully go into the NodeLogger facilities (and hence
     * be public API).
     * @param isError Whether to report to LOGGER.error (otherwise warn only).
     * @param error The error string to log.
     */
    private static final void logPreseveLineBreaks(
            final String error, final boolean isError) {
        StringTokenizer t = new StringTokenizer(error, "\n");
        while (t.hasMoreTokens()) {
            if (isError) {
                LOGGER.error(t.nextToken());
            } else {
                LOGGER.warn(t.nextToken());
            }
        }
    }
    
    private static IStatus createStatus(final LoadResultEntry loadResult, 
            final boolean treatDataLoadErrorsAsOK) {
        LoadResultEntry[] children = loadResult.getChildren();
        if (children.length == 0) {
            int severity;
            switch (loadResult.getType()) {
            case DataLoadError:
                severity = treatDataLoadErrorsAsOK 
                ? IStatus.OK : IStatus.ERROR;
                break;
            case Error:
                severity = IStatus.ERROR;
                break;
            case Warning:
                severity = IStatus.WARNING;
                break;
            default:
                severity = IStatus.OK;
            }
            return new Status(severity, KNIMEEditorPlugin.PLUGIN_ID, 
                    loadResult.getMessage(), null);
        }
        IStatus[] subStatus = new IStatus[children.length];
        for (int i = 0; i < children.length; i++) {
            subStatus[i] = createStatus(children[i], treatDataLoadErrorsAsOK);
        }
        return new MultiStatus(KNIMEEditorPlugin.PLUGIN_ID, Status.OK, 
                subStatus, loadResult.getMessage(), null);
    }
    
}

