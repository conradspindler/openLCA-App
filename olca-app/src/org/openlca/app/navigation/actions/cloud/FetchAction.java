package org.openlca.app.navigation.actions.cloud;

import org.openlca.app.M;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.openlca.app.App;
import org.openlca.app.cloud.CloudUtil;
import org.openlca.app.cloud.JsonLoader;
import org.openlca.app.cloud.index.Diff;
import org.openlca.app.cloud.index.DiffIndex;
import org.openlca.app.cloud.ui.DiffDialog;
import org.openlca.app.cloud.ui.commits.CommitEntryDialog;
import org.openlca.app.cloud.ui.commits.HistoryView;
import org.openlca.app.cloud.ui.diff.DiffNode;
import org.openlca.app.cloud.ui.diff.DiffNodeBuilder;
import org.openlca.app.cloud.ui.diff.DiffResult;
import org.openlca.app.db.Database;
import org.openlca.app.navigation.DatabaseElement;
import org.openlca.app.navigation.INavigationElement;
import org.openlca.app.navigation.Navigator;
import org.openlca.app.navigation.actions.INavigationAction;
import org.openlca.app.util.Error;
import org.openlca.app.util.Info;
import org.openlca.cloud.api.RepositoryClient;
import org.openlca.cloud.model.data.Commit;
import org.openlca.cloud.model.data.Dataset;
import org.openlca.cloud.model.data.FetchRequestData;
import org.openlca.cloud.util.WebRequests.WebRequestException;

import com.google.gson.JsonObject;

public class FetchAction extends Action implements INavigationAction {

	private RepositoryClient client;
	private DiffIndex index;

	public FetchAction() {
		setText(M.Fetch);
	}

	@Override
	public void run() {
		Runner runner = new Runner();
		runner.run();
		if (runner.error != null)
			Error.showBox(runner.error.getMessage());
		HistoryView.refresh();
	}

	private class Runner {

		private List<Commit> commits;
		private List<DiffResult> differences;
		private WebRequestException error;
		private DiffNode root;

		public void run() {
			App.runWithProgress(M.FetchingCommits, this::fetchCommits);
			boolean doContinue = showCommitEntries();
			if (!doContinue)
				return;
			App.runWithProgress(M.FetchingChanges, this::requestFetch);
			doContinue = showDifferences();
			if (!doContinue)
				return;
			App.runWithProgress(M.FetchingData, this::fetchData);
			if (error != null)
				return;
			Navigator.refresh(Navigator.getNavigationRoot());
		}

		private void fetchCommits() {
			try {
				commits = client.fetchNewCommitHistory();
			} catch (WebRequestException e) {
				error = e;
			}
		}

		private boolean showCommitEntries() {
			if (error != null)
				return false;
			if (commits.isEmpty()) {
				showNoChangesBox();
				return false;
			}
			CommitEntryDialog dialog = new CommitEntryDialog(commits, client);
			if (dialog.open() != IDialogConstants.OK_ID)
				return false;
			return true;
		}

		private void requestFetch() {
			try {
				List<FetchRequestData> descriptors = client.requestFetch();
				differences = createDifferences(descriptors);
				root = new DiffNodeBuilder(client.getConfig().getDatabase(),
						index).build(differences);
			} catch (WebRequestException e) {
				error = e;
			}
		}

		private boolean showDifferences() {
			if (error != null)
				return false;
			if (root != null) {
				JsonLoader loader = CloudUtil.getJsonLoader(client);
				DiffDialog dialog = new DiffDialog(root, loader);
				if (dialog.open() != IDialogConstants.OK_ID)
					return false;
			}
			return true;
		}

		private void fetchData() {
			List<Dataset> toFetch = new ArrayList<>();
			Map<Dataset, JsonObject> mergedData = new HashMap<>();
			for (DiffResult result : differences) {
				Dataset dataset = result.getDataset();
				switch (result.getType()) {
				case MODIFY_IN_LOCAL:
				case ADD_TO_LOCAL:
				case DELETE_FROM_LOCAL:
					toFetch.add(dataset);
					break;
				case CONFLICT:
					mergedData.put(dataset, result.getMergedData());
					break;
				default:
					break;
				}
			}
			try {
				Database.getIndexUpdater().disable();
				client.fetch(toFetch, mergedData);
				Database.getIndexUpdater().enable();
				FetchIndexHelper.index(differences, index);
			} catch (WebRequestException e) {
				error = e;
			}
		}

		private void showNoChangesBox() {
			Info.showBox(M.UpToDate);
		}

		private List<DiffResult> createDifferences(
				List<FetchRequestData> remotes) {
			List<DiffResult> differences = new ArrayList<>();
			for (FetchRequestData identifier : remotes) {
				Diff local = index.get(identifier.refId);
				differences.add(new DiffResult(identifier, local));
			}
			return differences;
		}

	}

	@Override
	public boolean accept(INavigationElement<?> element) {
		if (!(element instanceof DatabaseElement))
			return false;
		client = Database.getRepositoryClient();
		if (client == null)
			return false;
		index = Database.getDiffIndex();
		return true;
	}

	@Override
	public boolean accept(List<INavigationElement<?>> elements) {
		return false;
	}

}
