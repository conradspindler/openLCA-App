package org.openlca.app.collaboration.dialogs;

import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.FormDialog;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.openlca.app.M;
import org.openlca.app.collaboration.preferences.CollaborationPreference;
import org.openlca.app.collaboration.viewers.diff.CommitViewer;
import org.openlca.app.collaboration.viewers.diff.DiffNode;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.Question;
import org.openlca.app.util.UI;
import org.openlca.app.viewers.trees.CheckboxTreeViewers;

public class CommitDialog extends FormDialog {

	public static final int COMMIT_AND_PUSH = 2;
	private final boolean canPush;
	private final DiffNode node;
	private final boolean isStashCommit;
	private String message;
	private CommitViewer viewer;
	private Set<String> initialSelection;

	public CommitDialog(DiffNode node, boolean canPush, boolean isStashCommit) {
		super(UI.shell());
		this.node = node;
		this.canPush = canPush;
		this.isStashCommit = isStashCommit;
		setBlockOnOpen(true);
	}

	public void setInitialSelection(Set<String> initialSelection) {
		this.initialSelection = initialSelection;
	}

	@Override
	protected Point getInitialSize() {
		return new Point(600, 600);
	}

	@Override
	protected void createFormContent(IManagedForm mform) {
		var form = UI.header(mform, M.CommitChangesToRepository);
		var toolkit = mform.getToolkit();
		var body = UI.body(form, toolkit);
		if (!isStashCommit) {
			createCommitMessage(body, toolkit);
		}
		createModelViewer(body, toolkit);
		form.reflow(true);
	}

	private void createCommitMessage(Composite parent, FormToolkit toolkit) {
		var section = UI.section(parent, toolkit, M.CommitMessage + "*");
		var client = toolkit.createComposite(section);
		client.setLayout(new GridLayout());
		section.setClient(client);
		Text commitText = toolkit.createText(client, null, SWT.BORDER
				| SWT.V_SCROLL | SWT.WRAP | SWT.MULTI);
		var gd = UI.gridData(commitText, true, false);
		gd.heightHint = 150;
		commitText.addModifyListener((event) -> {
			message = commitText.getText();
			updateButtons();
		});
		commitText.setFocus();
	}

	private void createModelViewer(Composite parent, FormToolkit toolkit) {
		var section = UI.section(parent, toolkit, M.Files + "*");
		UI.gridData(section, true, true);
		var comp = toolkit.createComposite(section);
		UI.gridData(comp, true, true);
		UI.gridLayout(comp, 1);
		section.setClient(comp);
		viewer = new CommitViewer(comp, this::updateButtons);
		viewer.setSelection(initialSelection, node);
		CheckboxTreeViewers.setInput(comp, viewer.getViewer(), node, () -> {
			CheckboxTreeViewers.expandGrayed(viewer.getViewer());
			this.updateButtons();
		});
		if (CollaborationPreference.onlyFullCommits()) {
			viewer.getViewer().getTree().addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					if (Question.ask(M.Configuration, M.AlwaysCommitAllChangesQuestion)) {
						CollaborationPreference.allowDatasetSelection();
						viewer.getViewer().getTree().removeMouseListener(this);
					}
				}
			});
		}
	}

	private void updateButtons() {
		var enabled = viewer.hasChecked();
		if (!isStashCommit && (message == null || message.isEmpty())) {
			enabled = false;
		}
		if (canPush) {
			getButton(COMMIT_AND_PUSH).setEnabled(enabled);
		}
		getButton(IDialogConstants.OK_ID).setEnabled(enabled);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		if (canPush) {
			var commitAndPush = createButton(parent, COMMIT_AND_PUSH, M.CommitAndPush, false);
			commitAndPush.setEnabled(false);
			commitAndPush.setImage(Icon.PUSH.get());
			setButtonLayoutData(commitAndPush);
		}
		var commit = createButton(parent, IDialogConstants.OK_ID, isStashCommit ? M.Stash : M.Commit, true);
		commit.setEnabled(false);
		commit.setImage(Icon.COMMIT.get());
		setButtonLayoutData(commit);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == COMMIT_AND_PUSH) {
			setReturnCode(COMMIT_AND_PUSH);
			close();
		} else {
			super.buttonPressed(buttonId);
		}
	}

	public String getMessage() {
		return message;
	}

	public Set<DiffNode> getSelected() {
		return viewer.getChecked();
	}

}
