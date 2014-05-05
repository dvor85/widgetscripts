package ru.dvor85.widgetscripts;

import java.util.Arrays;

import ru.dvor85.widgetscripts.R;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

public class ScriptWidget extends AppWidgetProvider {

	private final static String LOG_TAG = MainActivity.PAKAGE_NAME;
	private static DB mDb;

	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);
		Log.d(LOG_TAG, "onEnabled");
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		for (int widgetID : appWidgetIds) {
			updateWidget(context, appWidgetManager, widgetID);
		}
		Log.d(LOG_TAG, "onUpdate " + Arrays.toString(appWidgetIds));
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		mDb = new DB(context);
		mDb.open();
		try {
			for (int widgetID : appWidgetIds) {
				mDb.delWidget(widgetID);
			}
		} finally {
			mDb.close();
		}

		Log.d(LOG_TAG, "onDeleted " + Arrays.toString(appWidgetIds));
	}

	@Override
	public void onDisabled(Context context) {
		super.onDisabled(context);
		Log.d(LOG_TAG, "onDisabled");
	}

	public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetID) {
		Log.d(LOG_TAG, "updateWidget " + widgetID);

		mDb = new DB(context);
		mDb.open();
		try {
			Cursor c = mDb.getDB().rawQuery(
					"SELECT " + DB.COLUMN_SCRIPTS_NAME + ", " + DB.COLUMN_SCRIPTS_ID + ", "
							+ DB.COLUMN_SCRIPTS_FILE + " FROM " + DB.DB_TABLE_SCRIPTS
							+ " INNER JOIN " + DB.DB_TABLE_WIDGETS + " ON "
							+ DB.COLUMN_WIDGET_SCRIPT_ID + " = " + DB.COLUMN_SCRIPTS_ID + " WHERE "
							+ DB.COLUMN_WIDGET_ID + " = " + widgetID, null);
			if (c.moveToFirst()) {
				String widgetName = c.getString(c.getColumnIndex(DB.COLUMN_SCRIPTS_NAME));
				long scriptId = c.getLong(c.getColumnIndex(DB.COLUMN_SCRIPTS_ID));

				RemoteViews widgetView = new RemoteViews(context.getPackageName(), R.layout.widget);
				// Настраиваем внешний вид виджета

				widgetView.setTextViewText(R.id.widget_name, widgetName);
				
				Intent intent = new Intent(context, ScriptActivity.class);
				intent.putExtra(DB.COLUMN_SCRIPTS_ID, scriptId);				
				Uri data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME));
				intent.setData(data);
				PendingIntent pIntent = PendingIntent.getActivities(
						context,
						widgetID,
						new Intent[] {
								new Intent(context, MainActivity.class).putExtra(
										AppWidgetManager.EXTRA_APPWIDGET_ID,
										AppWidgetManager.INVALID_APPWIDGET_ID), intent }, 0);

				widgetView.setOnClickPendingIntent(R.id.buttonimg, pIntent);
				widgetView.setOnClickPendingIntent(R.id.widget_name, pIntent);

				// Обновляем виджет
				appWidgetManager.updateAppWidget(widgetID, widgetView);

			}
		} finally {
			mDb.close();
		}
	}

}
