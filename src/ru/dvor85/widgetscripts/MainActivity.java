package ru.dvor85.widgetscripts;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class MainActivity extends ListActivity {

	public static String PAKAGE_NAME;

	private final static int ABOUT_DIALOG = 1;
	private Button btn_add;
	private SimpleCursorAdapter adapter;
	private Cursor mCursor;
	private int widgetID = AppWidgetManager.INVALID_APPWIDGET_ID;
	private Intent resultValue;
	private DB mDb;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PAKAGE_NAME = getApplicationContext().getPackageName();

		// извлекаем ID конфигурируемого виджета
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			// отрицательный ответ
			setResult(RESULT_CANCELED, resultValue);
			widgetID = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);

			// и проверяем его корректность
			if (widgetID == AppWidgetManager.INVALID_APPWIDGET_ID) {
				finish();
			}

			// формируем intent ответа
			resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetID);
		}

		setContentView(R.layout.activity_main);
		btn_add = (Button) findViewById(R.id.btn_add);

		mDb = new DB(this);
		mDb.open();

		mCursor = mDb.getScripts();
		startManagingCursor(mCursor);

		adapter = new SimpleCursorAdapter(this, R.layout.scripts_row, mCursor,
				new String[] { DB.COLUMN_SCRIPTS_NAME }, new int[] { R.id.textrow });
		setListAdapter(adapter);

		registerForContextMenu(getListView());
	}

	@Override
	@Deprecated
	protected Dialog onCreateDialog(int id) {
		if (id == ABOUT_DIALOG) {
			AlertDialog.Builder adb = new AlertDialog.Builder(this);
			PackageInfo pInfo;
			try {
				pInfo = getPackageManager().getPackageInfo(PAKAGE_NAME, 0);
			} catch (NameNotFoundException e) {
				pInfo = new PackageInfo();
				pInfo.versionName = "";
			}
			adb.setTitle(PAKAGE_NAME + " v." + pInfo.versionName);
			adb.setMessage(R.string.information);
			adb.setIcon(android.R.drawable.ic_dialog_info);
			adb.setPositiveButton(R.string.ok, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case Dialog.BUTTON_POSITIVE:
						break;
					default:
						break;
					}
				}
			});
			return adb.create();
		}
		return super.onCreateDialog(id);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		if (widgetID != AppWidgetManager.INVALID_APPWIDGET_ID) {
			mDb.addWidget(widgetID, id);
			AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
			ScriptWidget.updateWidget(this, appWidgetManager, widgetID);
			// положительный ответ
			setResult(RESULT_OK, resultValue);
			finish();
		} else {
			// запуск на исполнение
			Intent intent = new Intent(this, ScriptActivity.class);
			intent.putExtra(DB.COLUMN_SCRIPTS_ID, id);
			startActivity(intent);
		}
	}

	void StartEditActivity(long id) {
		// редактирование или добавление скрипта
		Intent intent = new Intent(this, EditActivity.class);
		intent.putExtra(DB.COLUMN_SCRIPTS_ID, id);
		startActivityForResult(intent, 0);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add_script:
			btn_addClick(btn_add);
			break;
		case R.id.about:
			showDialog(ABOUT_DIALOG);
			break;
		default:
			break;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			mCursor.requery();
		}
	}

	public void btn_addClick(View v) {
		StartEditActivity(0);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		long id = info.id;
		switch (item.getItemId()) {
		case R.id.delete:
			mDb.delScript(id);
			mCursor.requery();
			break;
		case R.id.edit:
			StartEditActivity(id);
		default:
			break;
		}

		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.list_context, menu);
	}

	protected void onDestroy() {
		super.onDestroy();
		// закрываем подключение при выходе
		mDb.close();
	}

}
