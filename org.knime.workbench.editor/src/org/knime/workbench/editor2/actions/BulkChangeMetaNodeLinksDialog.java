/*
 * ------------------------------------------------------------------------
 *
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
 *   17 Aug 2022 (leon.wenzler): created
 */
package org.knime.workbench.editor2.actions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.workflow.MetaNodeTemplateInformation.TemplateType;
import org.knime.core.node.workflow.NodeContainerTemplate;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.node.workflow.SubNodeContainer;
import org.knime.core.node.workflow.TemplateUpdateUtil;
import org.knime.core.node.workflow.UnsupportedWorkflowVersionException;
import org.knime.core.node.workflow.WorkflowLoadHelper;
import org.knime.core.node.workflow.WorkflowManager;
import org.knime.core.node.workflow.WorkflowPersistor.LoadResult;
import org.knime.core.ui.util.SWTUtilities;
import org.knime.core.util.pathresolve.ResolverUtil;
import org.knime.core.util.pathresolve.URIToFileResolve.KNIMEURIDescription;
import org.knime.workbench.core.imports.ImportForbiddenException;
import org.knime.workbench.core.imports.RepoObjectImport;
import org.knime.workbench.core.imports.URIImporterFinder;
import org.knime.workbench.editor2.actions.BulkChangeMetaNodeLinksAction.LinkChangeAction;
import org.knime.workbench.editor2.commands.BulkChangeMetaNodeLinksCommand;
import org.knime.workbench.explorer.ExplorerMountTable;
import org.knime.workbench.explorer.dialogs.MessageJobFilter;
import org.knime.workbench.explorer.dialogs.SpaceResourceSelectionDialog;
import org.knime.workbench.explorer.dialogs.Validator;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileInfo;
import org.knime.workbench.explorer.filesystem.AbstractExplorerFileStore;
import org.knime.workbench.explorer.filesystem.ExplorerFileSystem;
import org.knime.workbench.explorer.view.AbstractContentProvider;
import org.knime.workbench.explorer.view.AbstractContentProvider.LinkType;
import org.knime.workbench.explorer.view.ContentObject;
import org.knime.workbench.ui.KNIMEUIPlugin;

/**
 * JFace implementation of a dialog for changing link properties of multiple components or meta nodes at once. Only
 * allows for changing for distinct component/metanode and only allows for either chaning the link type or the link uri.
 *
 * @author Leon Wenzler, KNIME AG, Konstanz, Germany
 */
public class BulkChangeMetaNodeLinksDialog extends Dialog {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(BulkChangeMetaNodeLinksDialog.class);

    private final Map<URI, List<NodeContainerTemplate>> m_metaNodeGroups;

    private Map<String, URI> m_displayNamesToGroupKeys = new HashMap<>();

    private LinkChangeAction m_linkChangeAction = LinkChangeAction.NO_CHANGE;

    private LinkType m_oldLinkType = LinkType.None;

    private LinkType m_selectedLinkType = LinkType.None;

    private URI m_oldLinkURI = null;

    private URI m_selectedLinkURI = null;

    private boolean m_uriInputViaText = false;

    // instance attributes for global access, mainly enabling/disabling
    private Label m_oldLinkLabel;

    private Text m_uriTextField;

    private Button m_linkChangeButton;

    private final WorkflowManager m_manager;

    /**
     * Creates a dialog.
     *
     * @param parent the parent shell
     * @param metaNodes
     * @param manager The manager (used for resolution of 'knime://' URLs)
     */
    BulkChangeMetaNodeLinksDialog(final Shell parent, final List<NodeContainerTemplate> metaNodes,
        final WorkflowManager manager) {
        super(parent);
        m_metaNodeGroups = divideInDistinctGroups(metaNodes);
        m_manager = manager;
    }

    /**
     * Uses the template link URI to divide the NodeContainerTemplates into distinct groups, i.e. the groups only share
     * a common link URI. The URI therefore acts as group identifier here. For the dropdown selection, better unique
     * group names are determined in {@link BulkChangeMetaNodeLinksDialog#getUniqueGroupNames()}.
     *
     * @param metaNodes
     * @return
     */
    private static Map<URI, List<NodeContainerTemplate>>
        divideInDistinctGroups(final List<NodeContainerTemplate> metaNodes) {
        Map<URI, List<NodeContainerTemplate>> metaNodeGroups = new HashMap<>();
        for (NodeContainerTemplate template : metaNodes) {
            var key = template.getTemplateInformation().getSourceURI();
            metaNodeGroups.putIfAbsent(key, new LinkedList<>());
            metaNodeGroups.get(key).add(template);
        }
        return metaNodeGroups;
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    /**
     * Tries to resolve an HTTP (http:// or https://) to a KNIME (knime://) URI.
     *
     * @param uri input URI, having HTTP protocol
     * @return Optional of output URI, having a KNIME protocol
     */
    private static Optional<URI> resolveToKnimeURI(final URI uri) {
        try {
            // TODO: should be changed, right now does an API call in the main thread on selecting the metanode group.
            // The KNIME URI resolving will should changed according to the outcome of AP-19371, so that the
            // returned URI here does not contain an ID but a path.
            var importObject = URIImporterFinder.getInstance().createEntityImportFor(uri);
            if (importObject.isPresent() && importObject.get() instanceof RepoObjectImport) {
                return Optional.of(((RepoObjectImport)importObject.get()).getKnimeURI());
            }
        } catch (ImportForbiddenException e) {
            LOGGER.debug("Could not resolve URI \"" + uri + "\" to KNIME URI", e);
        }
        return Optional.empty();
    }

    @Override
    protected void configureShell(final Shell shell) {
        super.configureShell(shell);
        shell.setSize(650, 550);
        shell.setText("Change Links");
        var img = KNIMEUIPlugin.getDefault().getImageRegistry().get("knime");
        shell.setImage(img);
    }

    @Override
    protected Control createDialogArea(final Composite parent) {
        var content = new Composite(parent, SWT.NONE);
        var fillBoth = new GridData(GridData.FILL_BOTH);
        content.setLayoutData(fillBoth);
        var gridLayout = new GridLayout();
        gridLayout.numColumns = 1;
        gridLayout.verticalSpacing = 8;
        gridLayout.marginWidth = 15;
        content.setLayout(gridLayout);

        // -- metanode group selection --

        var selectorLabel = new Label(content, SWT.LEFT);
        selectorLabel.setText("Change links of:");

        var metaNodeGroupSelector = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        metaNodeGroupSelector.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        metaNodeGroupSelector.setItems(getUniqueGroupNames());
        metaNodeGroupSelector.addSelectionListener(new SelectionAdapter() {
            /**
             * Updates all the attributes based on the selected metanode group. Updates selection (i.e. the URI), link
             * type and template type.
             *
             * @param event SelectionEvent
             */
            @Override
            public void widgetSelected(final SelectionEvent event) {
                m_oldLinkURI = m_displayNamesToGroupKeys.get(((Combo)event.getSource()).getText());
                m_oldLinkType = BulkChangeMetaNodeLinksCommand.resolveLinkType(m_oldLinkURI);
                // in the future, the hub will use IDs in the URI which is not human readable
                // this call to `ResolverUtil.toDescription` will still resolve the path for display purposes
                String desc;
                NodeContext.pushContext(m_manager);
                try {
                    desc = ResolverUtil.toDescription(m_oldLinkURI, new NullProgressMonitor())
                            .map(KNIMEURIDescription::toDisplayString).orElse(m_oldLinkURI.toString());
                } finally {
                    NodeContext.removeLastContext();
                }

                m_oldLinkLabel.setText(desc);
                m_oldLinkLabel.setToolTipText(desc);
                // selected properties can be changed multiple times, but will be compared to old ones for change detection
                m_selectedLinkURI = m_oldLinkURI;
                m_selectedLinkType = m_oldLinkType;
                // only if both group and action has been selected, the button is enabled
                m_linkChangeButton.setEnabled(m_linkChangeAction != LinkChangeAction.NO_CHANGE);
                m_uriTextField.setEditable(m_linkChangeAction == LinkChangeAction.URI_CHANGE);
                m_uriTextField.setToolTipText(m_oldLinkURI.toString());
                m_uriTextField.setText(m_oldLinkURI.toString());
            }
        });

        var oldLinkLabel = new Label(content, SWT.LEFT);
        oldLinkLabel.setText("The current link is:");
        m_oldLinkLabel = new Label(content, SWT.WRAP | SWT.BORDER);
        m_oldLinkLabel.setFont(JFaceResources.getFontRegistry().getItalic(JFaceResources.DEFAULT_FONT));
        m_oldLinkLabel.setText("<Select an item above>");
        m_oldLinkLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // vertical space
        new Label(content, SWT.NONE);
        new Label(content, SWT.NONE);

        // -- link properties changing --

        var propertiesLabel = new Label(content, SWT.LEFT);
        propertiesLabel.setText("Change:");

        var propertiesGroup = new Group(content, SWT.NONE);
        propertiesGroup.setLayout(new RowLayout(SWT.HORIZONTAL));

        // link type radio button
        var linkTypeButton = new Button(propertiesGroup, SWT.RADIO);
        linkTypeButton.setText("Link Type");
        Runnable onLinkTypeSelected = () -> {
            m_linkChangeAction = LinkChangeAction.TYPE_CHANGE;
            m_linkChangeButton.setText("Change...");
            // only if both the action and group has been selected, the button is enabled
            m_linkChangeButton.setEnabled(m_oldLinkURI != null);
            // resetting the URI change text field
            m_uriTextField.setEditable(false);
            m_uriTextField.setText(m_oldLinkURI == null ? "" : m_oldLinkURI.toString());
        };
        linkTypeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                if (((Button)e.getSource()).getSelection()) {
                    onLinkTypeSelected.run();
                }
            }
        });

        // link uri radio button
        var uriButton = new Button(propertiesGroup, SWT.RADIO);
        uriButton.setText("Link URL");
        uriButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                if (((Button)e.getSource()).getSelection()) {
                    m_linkChangeAction = LinkChangeAction.URI_CHANGE;
                    m_linkChangeButton.setText("Browse...");
                    // only if both the action and group has been selected, the button is enabled
                    var groupSelected = m_oldLinkURI != null;
                    m_linkChangeButton.setEnabled(groupSelected);
                    m_uriTextField.setEditable(groupSelected);
                }
            }
        });

        // text field for display and URI editing
        m_uriTextField = new Text(content, SWT.BORDER);
        var gridData = new GridData(GridData.FILL_HORIZONTAL);
        m_uriTextField.setLayoutData(gridData);
        m_uriTextField.setEditable(false);
        m_uriTextField.addModifyListener(event -> {
            try {
                m_selectedLinkURI = new URI(((Text)event.getSource()).getText());
                m_uriInputViaText = true;
            } catch (URISyntaxException e) {
                LOGGER.debug("The link in the textfield could not be interpreted as URI: " + e.getMessage(), e);
            }
        });

        // change properties push button
        m_linkChangeButton = new Button(content, SWT.PUSH);
        var buttonRightGridData = new GridData();
        buttonRightGridData.widthHint = 120;
        buttonRightGridData.horizontalAlignment = SWT.RIGHT;
        m_linkChangeButton.setLayoutData(buttonRightGridData);
        m_linkChangeButton.setText("...");
        m_linkChangeButton.setEnabled(false);
        m_linkChangeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                if (m_linkChangeAction == LinkChangeAction.TYPE_CHANGE) {
                    openTypeChangeDialog();
                } else if (m_linkChangeAction == LinkChangeAction.URI_CHANGE) {
                    openURIChangeDialog();
                }
            }
        });
        linkTypeButton.setSelection(true);
        onLinkTypeSelected.run();

        return content;
    }

    @Override
    protected void okPressed() {
        if (getLinkChangeAction() != LinkChangeAction.URI_CHANGE || verifyURI()) {
            super.okPressed();
        }
    }

    /**
     * Generates unique display names for the dropdown selection. Tries to use the filename of the link URI (last part)
     * as the display name. Avoids using the full URI unless it is necessary for uniqueness.
     *
     * @return String array of unique display names
     */
    private String[] getUniqueGroupNames() {
        var uriArray = m_metaNodeGroups.keySet().toArray(new URI[0]);
        var namesArray = new String[uriArray.length];
        var duplicateNames = new LinkedList<>();

        for (var i = 0; i < uriArray.length; i++) {
            // assigning the last URI part as name
            namesArray[i] = StringUtils.substringAfterLast(uriArray[i].toString(), "/");

            // making sure that names does not collide with others or is already a duplicate name
            for (var j = 0; j < i; j++) {
                if (namesArray[i].equals(namesArray[j])) {
                    duplicateNames.add(namesArray[i]);
                    namesArray[i] = uriArray[i].toString();
                    namesArray[j] = uriArray[j].toString();
                    m_displayNamesToGroupKeys.put(namesArray[j], uriArray[j]);
                }
            }
            if (duplicateNames.contains(namesArray[i])) {
                namesArray[i] = uriArray[i].toString();
            }
            // store the association between display name and group identifier
            m_displayNamesToGroupKeys.put(namesArray[i], uriArray[i]);
        }
        return namesArray;
    }

    /**
     * Opens the dialog for changing the link type.
     *
     * @param shell
     */
    private void openTypeChangeDialog() {
        final var shell = SWTUtilities.getActiveShell();
        var message =
            "Please select a new link type for the " + (isSelectedSubNode() ? "component" : "metanode") + "s.";
        if (isSelectedSubNode()) {
            var prompt = new ChangeSubNodeLinkAction.LinkPrompt(shell, message, m_selectedLinkType);
            if (prompt.open() != Window.OK) {
                return;
            }
            m_selectedLinkType = prompt.getLinkType();
        } else if (isSelectedMetaNode()) {
            var prompt = new ChangeMetaNodeLinkAction.LinkPrompt(shell, message, m_selectedLinkType);
            if (prompt.open() != Window.OK) {
                return;
            }
            m_selectedLinkType = prompt.getLinkType();
        }
    }

    /**
     * Opens the dialog for changing the link URI.
     *
     * @param shell
     */
    private void openURIChangeDialog() {
        final var shell = SWTUtilities.getActiveShell();
        List<String> validMountPointList = new ArrayList<>();
        for (Map.Entry<String, AbstractContentProvider> entry : ExplorerMountTable.getMountedContent().entrySet()) {
            AbstractContentProvider contentProvider = entry.getValue();
            if (contentProvider.isWritable() && contentProvider.canHostMetaNodeTemplates()) {
                validMountPointList.add(entry.getKey());
            }
        }
        ContentObject defaultSelection = null;
        if (ExplorerFileSystem.SCHEME.equals(m_selectedLinkURI.getScheme())) {
            var fileStore = ExplorerFileSystem.INSTANCE.getStore(m_selectedLinkURI);
           defaultSelection = ContentObject.forFile(fileStore == null ? null : fileStore.getParent());
        }
        var templateType = isSelectedSubNode() ? TemplateType.SubNode : TemplateType.MetaNode;
        var prompt = new DestinationSelectionDialog(shell, validMountPointList.toArray(new String[0]), defaultSelection,
            templateType);
        if (prompt.open() != Window.OK) {
            return;
        }
        m_selectedLinkURI = prompt.getSelection().toURI();
        m_uriTextField.setText(m_selectedLinkURI.toString());
        m_uriInputViaText = false;
    }

    /**
     * Upon pressing OK, the dialog verifies the input by resolving it to a KNIME URI and trying to load the template
     * located at the URI. The verification passes, if the loading completes with no errors and the template types
     * match. This procedure is only invoked if the URI was inputted via the textfield. Selecting the URI via the
     * "Browse Location dialog", we can be certain that the template exists.
     *
     * @return is the inputted URI valid?
     */
    private boolean verifyURI() {
        var repr = getRepresentativeFromSelected();
        if (repr.isPresent() && m_uriInputViaText) {
            m_selectedLinkURI = resolveToKnimeURI(m_selectedLinkURI).orElse(m_selectedLinkURI);
            var validURI = false;
            var errorMessage = "";
            var result = new LoadResult("Link Change Verification");
            NodeContext.pushContext(m_manager);
            try {
                var template = TemplateUpdateUtil.loadMetaNodeTemplate(m_selectedLinkURI, new WorkflowLoadHelper(true), result);
                if (result.hasErrors()) {
                    errorMessage = "Could not load the template at URI \"" + m_selectedLinkURI + "\":\n" + result.getMessage();
                } else if (isSelectedSubNode() && !(template instanceof SubNodeContainer)) {
                    errorMessage = "You selected a component but the URI does not point to one!";
                } else if (isSelectedMetaNode() && !(template instanceof WorkflowManager)) {
                    errorMessage = "You selected a metanode but the URI does not point to one!";
                } else {
                    // if we got to this point, the result has no errors and the template type matches
                    validURI = true;
                }
            } catch (IOException | UnsupportedWorkflowVersionException | CanceledExecutionException e) {
                LOGGER.debug("Could not load template at URI \"" + m_selectedLinkURI + "\"", e);
                errorMessage = "Could not load the template at URI \"" + m_selectedLinkURI + "\":\n" + e.getMessage();
            } finally {
                NodeContext.removeLastContext();
            }
            // opening a warning dialog of what went wrong
            if (!validURI) {
                MessageDialog.openError(SWTUtilities.getActiveShell(), "Change Links", errorMessage);
                return false;
            }
        }
        return true;
    }

    /**
     * Methods for retrieving the first node of the group of selected NodeContainerTemplates. Used for assessing common
     * attributes of the selected group.
     *
     * @return Optional of the representative NodeContainerTemplate
     */
    private Optional<NodeContainerTemplate> getRepresentativeFromSelected() {
        var group = m_metaNodeGroups.get(m_oldLinkURI);
        if (group != null && !group.isEmpty()) {
            return Optional.of(group.get(0));
        }
        return Optional.empty();
    }

    private boolean isSelectedSubNode() {
        return getRepresentativeFromSelected().map(SubNodeContainer.class::isInstance).orElse(false);
    }

    private boolean isSelectedMetaNode() {
        return getRepresentativeFromSelected().map(WorkflowManager.class::isInstance).orElse(false);
    }

    /**
     * Retrieves the list of NodeContainerTemplates to change from the selected display name of metanode groups.
     *
     * @return list of NodeContainerTemplates
     */
    public List<NodeContainerTemplate> getNCsToChange() {
        return m_metaNodeGroups.getOrDefault(m_oldLinkURI, Collections.emptyList());
    }

    /**
     * Returns the link change action, which is to be executed on the selected metanode group.
     *
     * @return link change action
     */
    public LinkChangeAction getLinkChangeAction() {
        if (m_linkChangeAction == LinkChangeAction.TYPE_CHANGE && m_selectedLinkType == m_oldLinkType) {
            return LinkChangeAction.NO_CHANGE;
        }
        if (m_linkChangeAction == LinkChangeAction.URI_CHANGE && m_selectedLinkURI.equals(m_oldLinkURI)) {
            return LinkChangeAction.NO_CHANGE;
        }
        return m_linkChangeAction;
    }

    /**
     * Retrieves the potentially newly set link type.
     *
     * @return String link type
     */
    public LinkType getLinkType() {
        return m_selectedLinkType;
    }

    /**
     * Retrieves the potentially newly set link URI.
     *
     * @return URI
     */
    public URI getURI() {
        return m_selectedLinkURI;
    }

    /**
     * Dialog for selecting the new destination for the selected group of either components or metanodes. This dialog
     * also validates the selected location, i.e. allows for choosing only component or only metanodes.
     */
    private static final class DestinationSelectionDialog extends SpaceResourceSelectionDialog {

        /**
         * @param parentShell
         * @param mountIDs
         * @param initialSelection
         * @param templateType
         */
        public DestinationSelectionDialog(final Shell parentShell, final String[] mountIDs,
            final ContentObject initialSelection, final TemplateType templateType) {
            super(parentShell, mountIDs, initialSelection);
            var templateName = templateType == TemplateType.SubNode ? "Component" : "Metanode";
            setTitle("Save As " + templateName + " Template");
            setHeader("Select destination workflow group for " + templateName.toLowerCase() + " template");
            setValidator(new Validator() {
                @Override
                public String validateSelectionValue(final AbstractExplorerFileStore selection, final String name) {
                    final AbstractExplorerFileInfo info = selection.fetchInfo();
                    if ((templateType == TemplateType.SubNode && info.isComponentTemplate())
                        || (templateType == TemplateType.MetaNode && info.isMetaNodeTemplate())) {
                        return null;
                    }
                    return "Only " + templateName.toLowerCase() + " templates can be selected as target.";
                }
            });
            setFilter(new MessageJobFilter());
        }
    }
}
