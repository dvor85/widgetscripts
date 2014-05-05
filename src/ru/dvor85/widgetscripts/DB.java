package ru.dvor85.widgetscripts;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class DB {

	private static final String DB_NAME = "ws.db";
	private static final int DB_VERSION = 2;

	public static final String DB_TABLE_SCRIPTS = "scripts";
	public static final String DB_TABLE_WIDGETS = "widgets";
	public static final String COLUMN_SCRIPTS_ID = "_id";
	public static final String COLUMN_SCRIPTS_NAME = "name";
	public static final String COLUMN_SCRIPTS_FILE = "file";
	public static final String COLUMN_SCRIPTS_ROOT = "root";
	public static final String COLUMN_WIDGET_ID = "widget_id";
	public static final String COLUMN_WIDGET_SCRIPT_ID = "script_id";

	private static final String DB_CREATE_SCRIPTS = "create table " + DB_TABLE_SCRIPTS + "("
			+ COLUMN_SCRIPTS_ID + " integer primary key autoincrement, " + COLUMN_SCRIPTS_NAME
			+ " text, " + COLUMN_SCRIPTS_FILE + " text, " + COLUMN_SCRIPTS_ROOT + " short" + ");";

	private static final String DB_CREATE_WIDGETS = "create table " + DB_TABLE_WIDGETS + "("
			+ COLUMN_WIDGET_ID + " integer primary key, " + COLUMN_WIDGET_SCRIPT_ID + " integer "
			+ ");";

	private final Context mCtx;

	private DBHelper mDBHelper;
	private SQLiteDatabase mDB;

	public SQLiteDatabase getDB() {
		return mDB;
	}

	public DB(Context ctx) {
		mCtx = ctx;
	}

	// открыть подключение
	public void open() {
		mDBHelper = new DBHelper(mCtx, DB_NAME, null, DB_VERSION);
		mDB = mDBHelper.getWritableDatabase();
	}

	// закрыть подключение
	public void close() {
		try {
			if (mDBHelper != null)
				mDBHelper.close();
		} catch (Exception e) {
		}
	}

	public Cursor getScripts() {
		return mDB.query(DB_TABLE_SCRIPTS, null, null, null, null, null, null);
	}

	public void addScript(String name, String file, Boolean root) {
		ContentValues cv = new ContentValues();
		cv.put(COLUMN_SCRIPTS_NAME, name);
		cv.put(COLUMN_SCRIPTS_FILE, file);
		cv.put(COLUMN_SCRIPTS_ROOT, root ? 1 : 0);
		mDB.insert(DB_TABLE_SCRIPTS, null, cv);
	}

	public void updateScript(long id, String name, String file, Boolean root) {
		ContentValues cv = new ContentValues();
		cv.put(COLUMN_SCRIPTS_NAME, name);
		cv.put(COLUMN_SCRIPTS_FILE, file);
		cv.put(COLUMN_SCRIPTS_ROOT, root ? 1 : 0);
		mDB.update(DB_TABLE_SCRIPTS, cv, COLUMN_SCRIPTS_ID + " = " + id, null);
	}

	public void delScript(long id) {
		mDB.delete(DB_TABLE_SCRIPTS, COLUMN_SCRIPTS_ID + " = " + id, null);
	}

	public void addWidget(long widget_id, long script_id) {
		ContentValues cv = new ContentValues();
		cv.put(COLUMN_WIDGET_ID, widget_id);
		cv.put(COLUMN_WIDGET_SCRIPT_ID, script_id);
		mDB.insert(DB_TABLE_WIDGETS, null, cv);
	}

	public void updateWidget(long widget_id, long script_id) {
		ContentValues cv = new ContentValues();
		cv.put(COLUMN_WIDGET_SCRIPT_ID, script_id);
		mDB.update(DB_TABLE_WIDGETS, cv, COLUMN_WIDGET_ID + " = " + widget_id, null);
	}

	public void delWidget(long widget_id) {
		mDB.delete(DB_TABLE_WIDGETS, COLUMN_WIDGET_ID + " = " + widget_id, null);
	}

	// класс по созданию и управлению БД
	private class DBHelper extends SQLiteOpenHelper {

		public DBHelper(Context context, String name, CursorFactory factory, int version) {
			super(context, name, factory, version);
		}

		// создаем и заполняем БД
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.beginTransaction();
			db.execSQL(DB_CREATE_SCRIPTS);
			db.execSQL(DB_CREATE_WIDGETS);
			db.setTransactionSuccessful();
			db.endTransaction();

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion == 1 && newVersion == 2) {
				db.execSQL(DB_CREATE_WIDGETS);
			}
		}
	}
}
