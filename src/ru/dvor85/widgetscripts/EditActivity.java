package ru.dvor85.widgetscripts;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class EditActivity extends Activity implements OnClickListener {

	private EditText script_name, script_file, script_text;
	private TextView title_edit;
	private CheckBox needs_root;
	private Button load_file, save, cancel;
	private FileDialog fileDialog;
	private long id = 0;
	private DB mDb;
	private String fn;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			id = extras.getLong(DB.COLUMN_SCRIPTS_ID, 0);
		}

		setContentView(R.layout.edit_script);

		script_file = (EditText) findViewById(R.id.script_file);
		script_name = (EditText) findViewById(R.id.script_name);
		script_text = (EditText) findViewById(R.id.script_text);
		title_edit = (TextView) findViewById(R.id.title_edit);
		needs_root = (CheckBox) findViewById(R.id.needs_root);

		load_file = (Button) findViewById(R.id.load_file);
		save = (Button) findViewById(R.id.save);
		cancel = (Button) findViewById(R.id.cancel);

		load_file.setOnClickListener(this);
		save.setOnClickListener(this);
		cancel.setOnClickListener(this);

		if (id != 0) {
			title_edit.setText(R.string.edit_script);
			loadData();
		} else {
			title_edit.setText(R.string.add_script);
		}

	}

	@Override
	public void onClick(View v) {
		String path;
		switch (v.getId()) {
		case R.id.load_file:
			path = script_file.getText().toString().trim();
			if ((path.contains(File.separator)) && (path.contains(".sh"))) {
				path = path.substring(0, path.lastIndexOf(File.separator)).replace(" ", "\\ ");
			} else {
				path = Environment.getExternalStorageDirectory().toString();
			}

			File mPath = new File(path);

			fileDialog = new FileDialog(this, mPath, ".sh");
			fileDialog.setSelectDirectoryOption(true);
			fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
				public void fileSelected(File file) {
					fn = file.toString();
					script_file.setText(fn);
					script_text.setText(readFile(fn));
					if (script_name.getText().toString().trim().equals("")) {
						script_name.setText(fn.substring(fn.lastIndexOf(File.separator) + 1,
								fn.lastIndexOf(".sh")));
					}
				}
			});
			fileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
				public void directorySelected(File directory) {
					fn = directory.getPath();
					if (!script_name.getText().toString().trim().equals("")) {
						script_file.setText(fn + File.separator
								+ script_name.getText().toString().trim() + ".sh");
					} else {
						script_file.setText(fn);
					}
				}
			});
			fileDialog.showDialog();
			break;
		case R.id.save:
			if (saveData()) {
				setResult(RESULT_OK);
				finish();
			}
			break;
		case R.id.cancel:
			finish();
			break;
		}

	}

	public String readFile(String filename) {
		String content = "";
		File file = new File(filename);
		if (file.canRead()) {
			try {
				FileReader reader = new FileReader(file);
				char[] chars = new char[(int) file.length()];
				reader.read(chars);
				content = new String(chars);
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return content;
	}

	public void writeFile(String filename, String content) {

		Writer writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename),
					"utf-8"));
			writer.write(content);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (Exception ex) {
			}
		}
	}

	private Boolean saveData() {

		if (script_name.getText().toString().trim().equals("")
				|| script_file.getText().toString().trim().equals("")) {
			Toast.makeText(this, "Script not saved", Toast.LENGTH_SHORT).show();
			return false;
		}
		mDb = new DB(this);
		mDb.open();
		try {
			if (id == 0) {
				mDb.addScript(script_name.getText().toString().trim(), script_file.getText()
						.toString().trim(), needs_root.isChecked());
			} else {
				mDb.updateScript(id, script_name.getText().toString().trim(), script_file.getText()
						.toString().trim(), needs_root.isChecked());
			}

			if (!script_text.getText().toString().trim().equals("")
					&& script_file.getText().toString().trim().contains(".sh")) {
				fn = script_file.getText().toString().trim();
				fn = fn.substring(0, fn.lastIndexOf(".sh") + 3);
				writeFile(fn, script_text.getText().toString().trim());
			}

		} finally {
			mDb.close();
		}
		Toast.makeText(this, "Script saved", Toast.LENGTH_SHORT).show();
		return true;
	}

	private void loadData() {
		mDb = new DB(this);
		mDb.open();
		try {
			Cursor c = mDb.getDB().rawQuery(
					"select * from " + DB.DB_TABLE_SCRIPTS + " where " + DB.COLUMN_SCRIPTS_ID
							+ " = " + id, null);
			if (c.moveToFirst()) {
				script_name.setText(c.getString(c.getColumnIndex(DB.COLUMN_SCRIPTS_NAME)));
				script_file.setText(c.getString(c.getColumnIndex(DB.COLUMN_SCRIPTS_FILE)));
				if (script_file.getText().toString().trim().contains(".sh")) {
					fn = script_file.getText().toString().trim();
					fn = fn.substring(0, fn.lastIndexOf(".sh") + 3);
					script_text.setText(readFile(fn));

				}
				needs_root.setChecked(c.getInt(c.getColumnIndex(DB.COLUMN_SCRIPTS_ROOT)) == 1);
			} else {
				title_edit.setText(R.string.add_script);
			}
		} finally {
			mDb.close();
		}
	}
}
