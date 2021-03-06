package net.osmand.plus.download;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.widget.Toast;

import net.osmand.access.AccessibleToast;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.WorldRegion;
import net.osmand.plus.activities.ActionBarProgressActivity;
import net.osmand.plus.base.BasicProgressAsyncTask;
import net.osmand.plus.download.items.ItemsListBuilder;

import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseDownloadActivity extends ActionBarProgressActivity {
	protected DownloadActivityType type = DownloadActivityType.NORMAL_FILE;
	protected OsmandSettings settings;
	public static DownloadIndexesThread downloadListIndexThread;
	protected Set<WeakReference<Fragment>> fragSet = new HashSet<>();
	protected List<IndexItem> downloadQueue = new ArrayList<>();

	public static final int MAXIMUM_AVAILABLE_FREE_DOWNLOADS = 10;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		settings = ((OsmandApplication) getApplication()).getSettings();
		if (downloadListIndexThread == null) {
			downloadListIndexThread = new DownloadIndexesThread(this);
		}
		super.onCreate(savedInstanceState);
		// Having the next line here causes bug AND-197: The storage folder dialogue popped up upon EVERY app startup, because the map list is not indexed yet.
		// Hence line moved to updateDownloads() now.
		// prepareDownloadDirectory();
	}

	public void updateDownloads() {
		if (downloadListIndexThread.getCachedIndexFiles() != null && downloadListIndexThread.isDownloadedFromInternet()) {
			downloadListIndexThread.runCategorization(DownloadActivityType.NORMAL_FILE);
		} else {
			downloadListIndexThread.runReloadIndexFiles();
		}
		prepareDownloadDirectory();
	}


	@Override
	protected void onResume() {
		super.onResume();
		downloadListIndexThread.setUiActivity(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		downloadListIndexThread.setUiActivity(null);
	}


	public void updateDownloadList(List<IndexItem> list) {

	}

	public void updateProgress(boolean updateOnlyProgress, Object tag) {

	}

	public DownloadActivityType getDownloadType() {
		return type;
	}

	public Map<IndexItem, List<DownloadEntry>> getEntriesToDownload() {
		if (downloadListIndexThread == null) {
			return new LinkedHashMap<>();
		}
		return downloadListIndexThread.getEntriesToDownload();
	}

	public void downloadedIndexes() {

	}

	public void updateFragments() {

	}

	public void downloadListUpdated() {

	}

	public OsmandApplication getMyApplication() {
		return (OsmandApplication) getApplication();
	}

	public void categorizationFinished(List<IndexItem> filtered, List<IndexItemCategory> cats) {

	}

	public void onCategorizationFinished() {

	}

	public ItemsListBuilder getItemsBuilder() {
		return getItemsBuilder("");
	}

	public ItemsListBuilder getItemsBuilder(String regionId) {
		if (downloadListIndexThread.isDataPrepared()) {
			return new ItemsListBuilder(getMyApplication(), regionId, downloadListIndexThread.getResourcesByRegions(),
					downloadListIndexThread.getVoiceRecItems(), downloadListIndexThread.getVoiceTTSItems());
		} else {
			return null;
		}
	}

	public Map<String, IndexItem> getIndexItemsByRegion(WorldRegion region) {
		if (downloadListIndexThread.isDataPrepared()) {
			return downloadListIndexThread.getResourcesByRegions().get(region);
		} else {
			return null;
		}
	}

	public boolean startDownload(IndexItem item) {
		addToDownload(item);
		if (downloadListIndexThread.getCurrentRunningTask() != null && getEntriesToDownload().get(item) == null) {
			return false;
		}
		downloadFilesCheckFreeVersion();
		return true;
	}

	protected void addToDownload(IndexItem item) {
		List<DownloadEntry> download = item.createDownloadEntry(getMyApplication(), item.getType(), new ArrayList<DownloadEntry>());
		getEntriesToDownload().put(item, download);
	}

	public void downloadFilesPreCheckSpace() {
		double sz = 0;
		List<DownloadEntry> list = downloadListIndexThread.flattenDownloadEntries();
		for (DownloadEntry es : list) {
			sz += es.sizeMB;
		}
		// get availabile space
		double asz = downloadListIndexThread.getAvailableSpace();
		if (asz != -1 && asz > 0 && sz / asz > 0.4) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(MessageFormat.format(getString(R.string.download_files_question_space), list.size(), sz, asz));
			builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					downloadListIndexThread.runDownloadFiles();
				}
			});
			builder.setNegativeButton(R.string.shared_string_no, null);
			builder.show();
		} else {
			downloadListIndexThread.runDownloadFiles();
		}
	}

	protected void downloadFilesCheckFreeVersion() {
		if (Version.isFreeVersion(getMyApplication())) {
			int total = settings.NUMBER_OF_FREE_DOWNLOADS.get();
			if (total > MAXIMUM_AVAILABLE_FREE_DOWNLOADS) {
				new InstallPaidVersionDialogFragment()
						.show(getSupportFragmentManager(), InstallPaidVersionDialogFragment.TAG);
			} else {
				downloadFilesCheckInternet();
			}
		} else {
			downloadFilesCheckInternet();
		}
	}

	protected void downloadFilesCheckInternet() {
		if (!getMyApplication().getSettings().isWifiConnected()) {
			if (getMyApplication().getSettings().isInternetConnectionAvailable()) {
				new ConfirmDownloadDialogFragment().show(getSupportFragmentManager(),
						ConfirmDownloadDialogFragment.TAG);
			} else {
				AccessibleToast.makeText(this, R.string.no_index_file_to_download, Toast.LENGTH_LONG).show();
			}
		} else {
			downloadFilesPreCheckSpace();
		}
	}

	@Override
	public void onAttachFragment(Fragment fragment) {
		fragSet.add(new WeakReference<Fragment>(fragment));
	}

	public void makeSureUserCancelDownload() {
		AlertDialog.Builder bld = new AlertDialog.Builder(this);
		bld.setTitle(getString(R.string.shared_string_cancel));
		bld.setMessage(R.string.confirm_interrupt_download);
		bld.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				cancelDownload();
			}
		});
		bld.setNegativeButton(R.string.shared_string_no, null);
		bld.show();
	}

	public void cancelDownload() {
		BasicProgressAsyncTask<?, ?, ?> t = DownloadActivity.downloadListIndexThread.getCurrentRunningTask();
		if (t != null) {
			t.setInterrupted(true);
		}
		// list of items to download need to be cleared in case of dashboard activity
//		if (this instanceof MainMenuActivity) {
//			getEntriesToDownload().clear();
//		}
	}

	private void prepareDownloadDirectory() {
		if (!getMyApplication().getResourceManager().getIndexFileNames().isEmpty()) {
			showDialogOfFreeDownloadsIfNeeded();
		}
	}

	private void showDialogOfFreeDownloadsIfNeeded() {
		if (Version.isFreeVersion(getMyApplication())) {
			AlertDialog.Builder msg = new AlertDialog.Builder(this);
			msg.setTitle(R.string.free_version_title);
			String m = getString(R.string.free_version_message, MAXIMUM_AVAILABLE_FREE_DOWNLOADS + "", "") + "\n";
			m += getString(R.string.available_downloads_left, MAXIMUM_AVAILABLE_FREE_DOWNLOADS - settings.NUMBER_OF_FREE_DOWNLOADS.get());
			msg.setMessage(m);
			if (Version.isMarketEnabled(getMyApplication())) {
				msg.setPositiveButton(R.string.install_paid, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Version.marketPrefix(getMyApplication()) + "net.osmand.plus"));
						try {
							startActivity(intent);
						} catch (ActivityNotFoundException e) {
						}
					}
				});
				msg.setNegativeButton(R.string.shared_string_cancel, null);
			} else {
				msg.setNeutralButton(R.string.shared_string_ok, null);
			}

			msg.show();
		}
	}


	public boolean isInQueue(IndexItem item) {
		return downloadQueue.contains(item);
	}

	public void removeFromQueue(IndexItem item) {
		downloadQueue.remove(item);
	}

	public static class InstallPaidVersionDialogFragment extends DialogFragment {
		public static final String TAG = "InstallPaidVersionDialogFragment";
		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			String msgTx = getString(R.string.free_version_message, MAXIMUM_AVAILABLE_FREE_DOWNLOADS + "");
			AlertDialog.Builder msg = new AlertDialog.Builder(getActivity());
			msg.setTitle(R.string.free_version_title);
			msg.setMessage(msgTx);
			if (Version.isMarketEnabled(getMyApplication())) {
				msg.setPositiveButton(R.string.install_paid, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(Intent.ACTION_VIEW,
								Uri.parse(Version.marketPrefix(getMyApplication())
										+ "net.osmand.plus"));
						try {
							startActivity(intent);
						} catch (ActivityNotFoundException e) {
						}
					}
				});
				msg.setNegativeButton(R.string.shared_string_cancel, null);
			} else {
				msg.setNeutralButton(R.string.shared_string_ok, null);
			}
			return msg.create();
		}

		private OsmandApplication getMyApplication() {
			return (OsmandApplication) getActivity().getApplication();
		}
	}

	public static class ConfirmDownloadDialogFragment extends DialogFragment {
		public static final String TAG = "ConfirmDownloadDialogFragment";
		@NonNull
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage(getString(R.string.download_using_mobile_internet));
			builder.setPositiveButton(R.string.shared_string_yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					((BaseDownloadActivity) getActivity()).downloadFilesPreCheckSpace();
				}
			});
			builder.setNegativeButton(R.string.shared_string_no, null);
			return builder.create();
		}
	}
}

