package ru.dvor85.widgetscripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ScriptActivity extends Activity {

	private final static int MSG_OUT = 1;
	private final static int MSG_TIME_CLOSE = 2;
	private final static int TIME_CLOSE_SECS = 10;
	private final static int MAX_OUT_SIZE = 5000;
	private final static String LOG_TAG = MainActivity.PAKAGE_NAME;

	private Button btn_stop;
	private TextView textout;
	private ScrollView scrollview;
	private ExecutorService es;

	private long id;
	private String cmd;
	private DB mDb;
	private Boolean need_root = false;
	// Для сохранения при пересоздании (перевороте, либо повторном создании)
	// Используем статическое поле, которое одинаково для всех инстанций
	// активити
	private SaveOnRotate save;
	private static Map<Long, SaveOnRotate> savemap;

	// Статический класс - это только вложенный класс, экземпляр которого можно
	// создать без создания родительского класса. Он не содержит ссылку на
	// внешний класс. Это не тоже самое, что статическое поле.
	// К статическим полям можно обращаться без создания экземпляра класса.
	// Статические методы работают только со статическими полями класса. К ним
	// также можно обратиться без создания экземпляра класса.
	// В данном случае используем статическую карту классов, в котором
	// сохраняются нужные переменные
	// Экземпляры этой активити различаются интентом, экстра данные которой
	// равны идентификатору скрипта в базе. Этот идентификатор - ключ для
	// значений карты.
	private final class SaveOnRotate {

		public MyHandler handler = null;
		public RunnableCmd cmdrun = null;
		public String out = "";
		public String commandName = "";

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			id = extras.getLong(DB.COLUMN_SCRIPTS_ID, 0);
			if (id == 0) {
				finish();
			}
		} else {
			finish();
		}

		setContentView(R.layout.script_activity);

		btn_stop = (Button) findViewById(R.id.stop_btn);
		textout = (TextView) findViewById(R.id.textout);
		scrollview = (ScrollView) findViewById(R.id.scrollview);

		// выполнить в одном потоке
		es = Executors.newFixedThreadPool(1);

		if (savemap == null) {
			savemap = new HashMap<Long, SaveOnRotate>();
		}

		if (savemap.containsKey(id)) {
			// Извлекаем объект класса с сохраненными параметрами
			save = savemap.get(id);
			// Из хэндлера извллекаем старое активити, связанное с ним
			ScriptActivity prevActivity = save.handler.getActivity();
			// И если это не смена ориентации закрываем его. Если нет оно само
			// закроется. Если не делать проверку, то закрыв предыдущее активити
			// - не откроется новое, при смене ориентации
			if ((prevActivity != null) && (!prevActivity.isChangingConfigurations()))
				prevActivity.finish();
			textout.setText(save.out);
			setTitle(getTitle().toString() + " RUN: " + save.commandName);
			// Привязываем к извлеченному хэндлеру текущее активити для вывода
			// данных
			save.handler.link(this);
		} else {
			save = new SaveOnRotate();
			save.handler = new MyHandler(this);
			savemap.put(id, save);

			mDb = new DB(this);
			mDb.open();
			try {
				Cursor c = mDb.getDB().rawQuery(
						"select " + DB.COLUMN_SCRIPTS_NAME + "," + DB.COLUMN_SCRIPTS_FILE + ","
								+ DB.COLUMN_SCRIPTS_ROOT + " from " + DB.DB_TABLE_SCRIPTS
								+ " where " + DB.COLUMN_SCRIPTS_ID + " = " + id, null);
				if (c.moveToFirst()) {
					cmd = c.getString(c.getColumnIndex(DB.COLUMN_SCRIPTS_FILE));
					need_root = c.getInt(c.getColumnIndex(DB.COLUMN_SCRIPTS_ROOT)) == 1;
					save.commandName = c.getString(c.getColumnIndex(DB.COLUMN_SCRIPTS_NAME));
					setTitle(getTitle().toString() + " RUN: " + save.commandName);
					try {
						if (save.cmdrun == null) {
							save.cmdrun = new RunnableCmd(cmd.trim());
							Log.d(LOG_TAG, "Run " + cmd.trim());
							es.execute(save.cmdrun);
						}
					} catch (Throwable e) {
						close();
						finish();
					}
				} else {
					close();
					finish();
				}
			} finally {
				mDb.close();
			}
		}

	}

	@Override
	public void onBackPressed() {
		close();
		super.onBackPressed();
	}

	public void OnClickStop(View v) {
		close();
		finish();
	}

	private void close() {
		// закрытие перед закрытием активити
		// удалить все сообщения хэндлера и отвязать от активити
		save.handler.removeMessages(MSG_TIME_CLOSE);
		save.handler.removeMessages(MSG_OUT);
		save.handler.unlink();
		// если процесс запущен, то закрыть его
		if ((save.cmdrun != null) && (save.cmdrun.running)) {
			save.cmdrun.stop();
			Toast.makeText(this, "Stop \"" + save.cmdrun.cmd + "\"", Toast.LENGTH_LONG).show();
			Log.d(LOG_TAG, "Stop \"" + save.cmdrun.cmd + "\"");
		}
		// Удаляем из карты данные для закрытого активити
		savemap.remove(id);
	}

	public class RunnableCmd implements Runnable {

		private String cmd;
		private boolean running = false;
		private Process process;
		private String SU;

		public RunnableCmd(String cmd) {
			this.cmd = cmd;
			running = false;
		}

		public String getChilds(String ppid) throws IOException {
			// рекурсивный поиск всех дочерних процессов
			BufferedReader stdout;
			BufferedWriter stdin;
			Process ps;
			String[] psinfo;
			StringBuilder sb = new StringBuilder();

			ps = new ProcessBuilder(SU).redirectErrorStream(true).start();
			stdin = new BufferedWriter(new OutputStreamWriter(ps.getOutputStream()));
			try {
				stdin.append("toolbox ps").append('\n');
				stdin.flush();
			} finally {
				try {
					stdin.close();
				} catch (Exception e) {
				}
			}
			stdout = new BufferedReader(new InputStreamReader(ps.getInputStream()));
			try {
				String line = stdout.readLine();
				while (line != null) {
					psinfo = line.split(" +");
					if (psinfo[2].equals(ppid)) {
						sb.append(psinfo[1]).append(' ');
						sb.append(getChilds(psinfo[1]));
					}
					line = stdout.readLine();
				}
			} finally {
				try {
					stdout.close();
				} catch (Exception e) {
				}
			}
			return sb.toString();
		}

		public void killproc(String pid) throws IOException {
			// убить процесс и все дочерние процессы
			// делать это с правами запущенного процесса. Process.destroy
			// убивает только с пользовательскими правами
			BufferedWriter stdin;
			Process kp;
			String cpid;

			kp = new ProcessBuilder(SU).redirectErrorStream(true).start();
			stdin = new BufferedWriter(new OutputStreamWriter(kp.getOutputStream()));
			try {
				cpid = getChilds(pid);
				stdin.append("kill -9 " + pid + " " + cpid).append('\n');
				stdin.flush();
			} finally {
				try {
					stdin.close();
				} catch (Exception e) {
				}
			}
		}

		public void run() {
			Message msg;
			String line;
			BufferedReader stdout;
			BufferedWriter stdin;
			char[] buffer = new char[1024 * 4];
			StringBuilder sb = new StringBuilder();

			try {
				SU = need_root ? "su" : "sh";

				process = new ProcessBuilder(SU).redirectErrorStream(true).start();
				stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
				try {
					if (cmd.contains(".sh")) {
						String fn = cmd.substring(0, cmd.lastIndexOf(".sh") + 3)
								.replace(" ", "\\ ");
						String param = cmd.substring(cmd.lastIndexOf(".sh") + 3).trim();
						sb.append("sh").append(' ');
						cmd = fn + ' ' + param;
					}
					stdin.append(sb.append(cmd).append('\n').toString());
					stdin.flush();
				} finally {
					try {
						stdin.close();
					} catch (Exception e) {
					}
				}

				running = true;

				stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
				try {
					while (running && (-1 != stdout.read(buffer))) {
						line = new String(buffer).replace("\0", "");
						msg = save.handler.obtainMessage(MSG_OUT, 0, 0, line);
						save.handler.sendMessage(msg);
						Arrays.fill(buffer, '\0');
					}
				} finally {
					try {
						stdout.close();
					} catch (Exception e) {
					}

				}
				running = false;

				// запустить процесс автозакрытия
				if (process.waitFor() == 0) {
					msg = save.handler.obtainMessage(MSG_TIME_CLOSE, TIME_CLOSE_SECS, 0, null);
					save.handler.sendMessage(msg);
				}
			} catch (Exception e) {
			} finally {
				this.stop();
			}
		}

		public void stop() {
			// убить процессы в отдельном потоке, чтобы разгрузить основной
			// поток

			running = false;

			new Thread(new Runnable() {
				@Override
				public void run() {
					if (process != null) {
						String pr = process.toString();
						try {
							killproc(pr.substring(pr.indexOf('=') + 1, pr.indexOf(']')));
						} catch (IOException e) {
						}
					}
				}
			}).start();
		}
	}

	static class MyHandler extends Handler {
		// мягкая ссылка для предотвращения утечки памяти
		private WeakReference<ScriptActivity> wrActivity;
		private static final String delim = "\n\n";

		public ScriptActivity getActivity() {
			return wrActivity.get();
		}

		public MyHandler(ScriptActivity activity) {
			link(activity);
		}

		public void link(ScriptActivity activity) {
			wrActivity = new WeakReference<ScriptActivity>(activity);
		}

		public void unlink() {
			wrActivity.clear();
		}

		private void setText(final ScriptActivity activity) {
			activity.textout.setText(activity.save.out);
			activity.scrollview.post(new Runnable() {
				@Override
				public void run() {
					activity.scrollview.fullScroll(ScrollView.FOCUS_DOWN);
				}
			});
		}

		@Override
		public void handleMessage(Message msg) {

			super.handleMessage(msg);

			final ScriptActivity activity = getActivity();

			if (activity != null)
				switch (msg.what) {
				case MSG_OUT:
					if (msg.obj != null) {
						activity.save.out += msg.obj.toString().replace("\n", delim);
					}
					// Обрезка вывода
					if (activity.save.out.length() > MAX_OUT_SIZE) {
						activity.save.out = activity.save.out.substring(activity.save.out.length()
								- MAX_OUT_SIZE);
						activity.save.out = activity.save.out.substring(activity.save.out
								.indexOf(delim) + delim.length());
					}
					setText(activity);

					break;
				case MSG_TIME_CLOSE:
					activity.btn_stop.setText(String.format(
							activity.getResources().getString(R.string.auto_close), msg.arg1));
					if (msg.arg1 <= 0) {
						activity.close();
						activity.finish();
					} else {
						sendMessageDelayed(obtainMessage(MSG_TIME_CLOSE, msg.arg1 - 1, 0, null),
								1000);
					}
					break;
				default:
					break;
				}
		}
	}
}
