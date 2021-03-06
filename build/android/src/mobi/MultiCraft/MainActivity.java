package mobi.MultiCraft;

import static mobi.MultiCraft.PreferencesHelper.TAG_BUILD_NUMBER;
import static mobi.MultiCraft.PreferencesHelper.TAG_SHORTCUT_CREATED;
import static mobi.MultiCraft.PreferencesHelper.getBuildNumber;
import static mobi.MultiCraft.PreferencesHelper.isCreateShortcut;
import static mobi.MultiCraft.PreferencesHelper.loadSettings;
import static mobi.MultiCraft.PreferencesHelper.saveSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;
import net.MultiCraft.Official.R;

public class MainActivity extends Activity {
	public final static String TAG = "Error";
	public final static String CREATE_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";
	public final static String FILES = Environment.getExternalStorageDirectory() + "/Files.zip";
	public final static String WORLDS = Environment.getExternalStorageDirectory() + "/worlds.zip";
	public final static String GAMES = Environment.getExternalStorageDirectory() + "/games.zip";
	public final static String NOMEDIA = ".nomedia";
	private final static int REQUEST_STORAGE = 0;
	private ProgressDialog mProgressDialog;
	private String dataFolder = "/Android/data/net.MultiCraft.Official/files/";
	private String unzipLocation = Environment.getExternalStorageDirectory() + dataFolder;
	private ProgressBar mProgressBar;
	private Utilities util;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		loadSettings(this);
		IntentFilter filter = new IntentFilter(UnzipService.ACTION_UPDATE);
		registerReceiver(myReceiver, filter);
		if (!isTaskRoot()) {
			finish();
			return;
		}
		if (ActivityCompat.checkSelfPermission(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			requestStoragePermission();
		} else {
			init();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		dismissProgressDialog();
		unregisterReceiver(myReceiver);
	}

	private void addShortcut() {
		saveSettings(TAG_SHORTCUT_CREATED, false);
		Intent shortcutIntent = new Intent(getApplicationContext(), MainActivity.class);
		shortcutIntent.setAction(Intent.ACTION_MAIN);
		Intent addIntent = new Intent();
		addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
		addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.drawable.ic_launcher));
		addIntent.setAction(CREATE_SHORTCUT);
		getApplicationContext().sendBroadcast(addIntent);
	}

	@SuppressWarnings("deprecation")
	public void init() {
		if (isCreateShortcut())
			addShortcut();
		mProgressBar = (ProgressBar) findViewById(R.id.PB1);
		Drawable draw;
		draw = getResources().getDrawable(R.drawable.custom_progress_bar);
		mProgressBar.setProgressDrawable(draw);
		util = new Utilities();
		util.createDirAndFiles();
		if (getBuildNumber().equals("0")) {
			saveSettings(TAG_BUILD_NUMBER, getString(R.string.ver));
			util.startDeletion(true);
		} else {
			util.checkVersion();
		}
	}

	private void requestPermissionAfterExplain() {
		Toast.makeText(this, R.string.explain, Toast.LENGTH_LONG).show();
		ActivityCompat.requestPermissions(MainActivity.this,
				new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_STORAGE);
	}

	private void requestStoragePermission() {
		if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			requestPermissionAfterExplain();
		} else {
			ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
					REQUEST_STORAGE);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
			@NonNull int[] grantResults) {

		// Check if the only required permission has been granted
		if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			init();
		} else {
			requestStoragePermission();
		}
	}

	private void showSpinnerDialog(int message) {
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(MainActivity.this);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			mProgressDialog.setCancelable(false);
		}
		mProgressDialog.setMessage(getString(message));
		mProgressDialog.show();
	}

	private void dismissProgressDialog() {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
	}

	public void runGame() {
		Intent intent = new Intent(MainActivity.this, MCNativeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		if (isFinishing())
			return;
		dismissProgressDialog();
		util.deleteZip(FILES);
		util.deleteZip(WORLDS);
		util.deleteZip(GAMES);
	}

	private void startUnzipService(String[] file) throws IOException {
		// Start MyIntentService
		Intent intentMyIntentService = new Intent(this, UnzipService.class);
		intentMyIntentService.putExtra(UnzipService.EXTRA_KEY_IN_FILE, file);
		intentMyIntentService.putExtra(UnzipService.EXTRA_KEY_IN_LOCATION, unzipLocation);
		startService(intentMyIntentService);

	}

	private BroadcastReceiver myReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int progress = intent.getIntExtra(UnzipService.ACTION_PROGRESS, 0);
			if (progress >= 0) {
				mProgressBar.setVisibility(View.VISIBLE);
				mProgressBar.setProgress(progress);
			} else {
				runGame();
			}
		}
	};

	private class DeleteTask extends AsyncTask<String, Void, Void> {
		String location;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showSpinnerDialog(R.string.rm_old);
		}

		@Override
		protected Void doInBackground(String... params) {
			location = params[0];
			for (String p : params) {
				deleteFiles(p);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			if (isFinishing())
				return;
			dismissProgressDialog();
			if (unzipLocation.equals(location)) {
				new CopyZip().execute(FILES, WORLDS, GAMES);
			} else {
				new CopyZip().execute(FILES, GAMES);
			}
		}

		private void deleteFiles(String path) {
			File file = new File(path);
			if (file.exists()) {
				String deleteCmd = "rm -r " + path;
				Runtime runtime = Runtime.getRuntime();
				try {
					runtime.exec(deleteCmd);
				} catch (IOException e) {
					Log.e(TAG, e.getLocalizedMessage());
				}
			}
		}
	}

	private class CopyZip extends AsyncTask<String, Void, String> {
		String[] zips;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String... params) {
			zips = params;
			for (String s : zips) {
				copyAssets(s);
			}
			return "Done";

		}

		@Override
		protected void onPostExecute(String result) {
			if (util.getAvailableSpaceInMB() > 25) {
				try {
					startUnzipService(zips);
				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
				}
			} else
				Toast.makeText(MainActivity.this, R.string.not_enough_space, Toast.LENGTH_LONG).show();
		}

		private void copyAssets(String zipName) {
			String filename = zipName.substring(zipName.lastIndexOf("/") + 1);
			InputStream in;
			OutputStream out;
			try {
				in = getAssets().open(filename);
				out = new FileOutputStream(zipName);
				copyFile(in, out);
				in.close();
				out.flush();
				out.close();
			} catch (IOException e) {
				Log.e(TAG, "Failed to copy asset file: " + e.getMessage());
			}
		}

		private void copyFile(InputStream in, OutputStream out) throws IOException {
			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
	}

	private class Utilities {

		/*
		 * private void createLangFile() { PrintWriter writer; try { writer =
		 * new PrintWriter(unzipLocation + "lang.txt", "UTF-8"); if
		 * ("Russian".equals(Locale.getDefault().getDisplayLanguage())) {
		 * writer.println("ru"); } else { writer.println("en"); }
		 * writer.close(); } catch (Exception e) { Log.e(TAG,
		 * e.getLocalizedMessage()); }
		 * 
		 * }
		 */

		private void createDirAndFiles() {
			try {
				File folder = new File(unzipLocation);
				if (!(folder.exists()))
					folder.mkdirs();
				File myFile = new File(unzipLocation, NOMEDIA);
				if (!myFile.exists())
					myFile.createNewFile();
				// createLangFile();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}
		}

		private void deleteZip(String fileName) {
			File file = new File(fileName);
			if (file.exists())
				file.delete();
		}

		private void startDeletion(boolean isAll) {
			if (isAll) {
				new DeleteTask().execute(unzipLocation);
			} else {
				new DeleteTask().execute(unzipLocation + "textures", unzipLocation + "games", unzipLocation + "builtin",
						unzipLocation + "fonts", unzipLocation + "debug.txt");
			}

		}

		@SuppressWarnings("deprecation")
		@SuppressLint("NewApi")
		private long getAvailableSpaceInMB() {
			final long SIZE_KB = 1024L;
			final long SIZE_MB = SIZE_KB * SIZE_KB;
			long availableSpace;
			StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
			if (Build.VERSION.SDK_INT > 17) {
				availableSpace = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
			} else {
				availableSpace = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
			}
			return availableSpace / SIZE_MB;
		}

		public void checkVersion() {
			File location = new File(unzipLocation);
			File[] contents = location.listFiles();
			if (contents == null) {
				Log.e(TAG, "Wrong path to unzip");
			} else if (contents.length == 1) {
				saveSettings(TAG_BUILD_NUMBER, getString(R.string.ver));
				startDeletion(true);
			} else if (getBuildNumber().equals(getString(R.string.ver))) {
				runGame();
			} else {
				saveSettings(TAG_BUILD_NUMBER, getString(R.string.ver));
				startDeletion(false);
			}
		}
	}
}