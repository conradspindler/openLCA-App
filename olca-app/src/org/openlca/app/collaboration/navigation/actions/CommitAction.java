package org.openlca.app.collaboration.navigation.actions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.openlca.app.M;
import org.openlca.app.collaboration.dialogs.CommitDialog;
import org.openlca.app.collaboration.dialogs.HistoryDialog;
import org.openlca.app.collaboration.navigation.RepositoryLabel;
import org.openlca.app.db.Database;
import org.openlca.app.db.Repository;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.navigation.elements.INavigationElement;
import org.openlca.app.rcp.images.Icon;
import org.openlca.app.util.MsgBox;
import org.openlca.git.actions.GitCommit;
import org.openlca.git.actions.GitPush;
import org.openlca.git.model.Change;

public class CommitAction extends Action implements INavigationAction {

	private List<INavigationElement<?>> selection;

	@Override
	public String getText() {
		return M.Commit + "...";
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return Icon.COMMIT.descriptor();
	}

	@Override
	public boolean isEnabled() {
		return RepositoryLabel.hasChanged(Navigator.findElement(Database.getActiveConfiguration()));
	}

	@Override
	public void run() {
		doRun(true);
	}

	boolean doRun(boolean canPush) {
		try {
			var repo = Repository.get();
			var input = Datasets.select(selection, canPush, true);
			if (input == null)
				return false;
			var committer = repo.promptCommitter();
			if (committer == null)
				return false;
			var credentials = Actions.credentialsProvider();
			Actions.run(GitCommit.from(Database.get())
					.to(repo.git)
					.changes(input.datasets().stream().map(d -> new Change(d.leftDiffType, d))
							.collect(Collectors.toList()))
					.withMessage(input.message())
					.as(committer)
					.update(repo.workspaceIds));
			if (input.action() != CommitDialog.COMMIT_AND_PUSH)
				return true;
			var result = Actions.run(GitPush
					.from(Repository.get().git)
					.authorizeWith(credentials));
			if (result.status() == Status.REJECTED_NONFASTFORWARD) {
				MsgBox.error("Rejected - Not up to date - Please merge remote changes to continue");
				return false;
			} else {
				Collections.reverse(result.newCommits());
				new HistoryDialog("Pushed commits", result.newCommits()).open();
				return true;
			}
		} catch (IOException | GitAPIException | InvocationTargetException | InterruptedException e) {
			Actions.handleException("Error during commit", e);
			return false;
		} finally {
			Actions.refresh();
		}
	}

	@Override
	public boolean accept(List<INavigationElement<?>> selection) {
		if (!Repository.isConnected())
			return false;
		this.selection = selection;
		return true;
	}

}
