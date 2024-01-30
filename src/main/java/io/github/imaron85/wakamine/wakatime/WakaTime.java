package io.github.imaron85.wakamine.wakatime;

import io.github.imaron85.wakamine.WakaMine;
import io.github.imaron85.wakamine.config.config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.KeyboardFocusManager;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.*;

public class WakaTime {

	public static final BigDecimal FREQUENCY = new BigDecimal(2 * 60); // max secs between heartbeats for continuous
																		// coding

	public static String VERSION;
	public static String IDE_NAME;
	public static String IDE_VERSION;
	public static Boolean DEBUG = false;
	public static Boolean METRICS = false;
	public static Boolean DEBUG_CHECKED = false;
	public static Boolean STATUS_BAR = false;
	public static Boolean READY = false;
	public static String lastFile = null;
	public static BigDecimal lastTime = new BigDecimal(0);
	public static Boolean isBuilding = true;

	private static final int queueTimeoutSeconds = 30;
	private static ConcurrentLinkedQueue<Heartbeat> heartbeatsQueue = new ConcurrentLinkedQueue<Heartbeat>();
	private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static ScheduledFuture<?> scheduledFixture;

	private static final ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
		Thread t = Executors.defaultThreadFactory().newThread(r);
		t.setDaemon(true);
		return t;
	});

	public static void initComponent(String gameVersion) {
		IDE_NAME = "minecraft";
		IDE_VERSION = gameVersion;

		setupConfigs();
		checkCli();
		setupQueueProcessor();
	}

	private static void checkCli() {
		executor.submit(() -> {
			if (!Dependencies.isCLIInstalled()) {
				WakaMine.LOGGER.info("Downloading and installing wakatime-cli...");
				Dependencies.installCLI();
				WakaTime.READY = true;
				WakaMine.LOGGER.info("Finished downloading and installing wakatime-cli.");
			} else if (Dependencies.isCLIOld()) {
				if (System.getenv("WAKATIME_CLI_LOCATION") != null
						&& !System.getenv("WAKATIME_CLI_LOCATION").trim().isEmpty()) {
					File wakatimeCLI = new File(System.getenv("WAKATIME_CLI_LOCATION"));
					if (wakatimeCLI.exists()) {
						WakaMine.LOGGER.warn("$WAKATIME_CLI_LOCATION is out of date, please update it.");
					}
				} else {
					WakaMine.LOGGER.info("Upgrading wakatime-cli ...");
					Dependencies.installCLI();
					WakaTime.READY = true;
					WakaMine.LOGGER.info("Finished upgrading wakatime-cli.");
				}
			} else {
				WakaTime.READY = true;
				WakaMine.LOGGER.info("wakatime-cli is up to date.");
			}
			Dependencies.createSymlink(Dependencies.combinePaths(Dependencies.getResourcesLocation(), "wakatime-cli"),
					Dependencies.getCLILocation());
			WakaMine.LOGGER.debug("wakatime-cli location: " + Dependencies.getCLILocation());
		});
	}

	private static void setupQueueProcessor() {
		long delay = queueTimeoutSeconds;
		scheduledFixture = scheduler.scheduleAtFixedRate(() -> processHeartbeatQueue(), delay, delay, java.util.concurrent.TimeUnit.SECONDS);
	}


	public static void disposeComponent() {
		processHeartbeatQueue();
	}

	public static BigDecimal getCurrentTimestamp() {
		return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4,
				BigDecimal.ROUND_HALF_UP);
	}

	public static void appendHeartbeat(String status) {
		final BigDecimal time = WakaTime.getCurrentTimestamp();

		WakaTime.lastFile = status;
		WakaTime.lastTime = time;

		executor.submit(() -> {
				Heartbeat h = new Heartbeat();
				h.entity = status;
				h.timestamp = time;
				h.language = "Minecraft";
				h.isBuilding = WakaTime.isBuilding;

				heartbeatsQueue.add(h);
		});
	}

	private static void checkApiKey() {
		if (ConfigFile.getApiKey().equals("")){
			if(config.INSTANCE.api_token.equals("waka_xxx"))
				WakaMine.LOGGER.warn("No WakaTime Api-Key present");
			else
				ConfigFile.setApiKey(config.INSTANCE.api_token);
		}
	}

	private static void processHeartbeatQueue() {
		if (!WakaTime.READY)
			return;

		checkApiKey();

		// get single heartbeat from queue
		Heartbeat heartbeat = heartbeatsQueue.poll();
		if (heartbeat == null)
			return;

		// get all extra heartbeats from queue
		ArrayList<Heartbeat> extraHeartbeats = new ArrayList<>();
		while (true) {
			Heartbeat h = heartbeatsQueue.poll();
			if (h == null)
				break;
			extraHeartbeats.add(h);
		}

		sendHeartbeat(heartbeat, extraHeartbeats);
	}

	private static void sendHeartbeat(final Heartbeat heartbeat, final ArrayList<Heartbeat> extraHeartbeats) {
		WakaMine.LOGGER.info("SENDING HEARTBEAT");
		final String[] cmds = buildCliCommand(heartbeat, extraHeartbeats);
		if (cmds.length == 0) {
			return;
		}
		WakaMine.LOGGER.debug("Executing CLI: " + Arrays.toString(obfuscateKey(cmds)));
		try {
			Process proc = Runtime.getRuntime().exec(cmds);
			if (extraHeartbeats.size() > 0) {
				String json = toJSON(extraHeartbeats);
				WakaMine.LOGGER.debug(json);
				try {
					BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
					stdin.write(json);
					stdin.write("\n");
					try {
						stdin.flush();
						stdin.close();
					} catch (IOException e) {
						/* ignored because wakatime-cli closes pipe after receiving \n */ }
				} catch (IOException e) {
					WakaMine.LOGGER.warn(e.toString());
				}
			}
			if (WakaTime.DEBUG) {
				BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
				BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
				proc.waitFor();
				String s;
				while ((s = stdout.readLine()) != null) {
					WakaMine.LOGGER.debug(s);
				}
				while ((s = stderr.readLine()) != null) {
					WakaMine.LOGGER.debug(s);
				}
				WakaMine.LOGGER.debug("Command finished with return value: " + proc.exitValue());
			}
		} catch (Exception e) {
			WakaMine.LOGGER.warn(e.toString());
		}
	}

	private static String toJSON(ArrayList<Heartbeat> extraHeartbeats) {
		StringBuffer json = new StringBuffer();
		json.append("[");
		boolean first = true;
		for (Heartbeat heartbeat : extraHeartbeats) {
			StringBuffer h = new StringBuffer();
			h.append("{\"entity\":\"");
			h.append(jsonEscape(heartbeat.entity));
			h.append("\",\"timestamp\":");
			h.append(heartbeat.timestamp.toPlainString());
			h.append(",\"is_write\":");
			h.append(heartbeat.isWrite.toString());
			if (heartbeat.lineCount != null) {
				h.append(",\"lines\":");
				h.append(heartbeat.lineCount);
			}
			if (heartbeat.lineNumber != null) {
				h.append(",\"lineno\":");
				h.append(heartbeat.lineNumber);
			}
			if (heartbeat.cursorPosition != null) {
				h.append(",\"cursorpos\":");
				h.append(heartbeat.cursorPosition);
			}
			if (heartbeat.isUnsavedFile) {
				h.append(",\"is_unsaved_entity\":true");
			}
			if (heartbeat.isBuilding) {
				h.append(",\"category\":\"building\"");
			}
			if (heartbeat.project != null) {
				h.append(",\"alternate_project\":\"");
				h.append(jsonEscape(heartbeat.project));
				h.append("\"");
			}
			if (heartbeat.language != null) {
				h.append(",\"language\":\"");
				h.append(jsonEscape(heartbeat.language));
				h.append("\"");
			}
			h.append("}");
			if (!first)
				json.append(",");
			json.append(h);
			first = false;
		}
		json.append("]");
		return json.toString();
	}

	private static String jsonEscape(String s) {
		if (s == null)
			return null;
		StringBuffer escaped = new StringBuffer();
		final int len = s.length();
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\':
					escaped.append("\\\\");
					break;
				case '"':
					escaped.append("\\\"");
					break;
				case '\b':
					escaped.append("\\b");
					break;
				case '\f':
					escaped.append("\\f");
					break;
				case '\n':
					escaped.append("\\n");
					break;
				case '\r':
					escaped.append("\\r");
					break;
				case '\t':
					escaped.append("\\t");
					break;
				default:
					boolean isUnicode = (c >= '\u0000' && c <= '\u001F') || (c >= '\u007F' && c <= '\u009F')
							|| (c >= '\u2000' && c <= '\u20FF');
					if (isUnicode) {
						escaped.append("\\u");
						String hex = Integer.toHexString(c);
						for (int k = 0; k < 4 - hex.length(); k++) {
							escaped.append('0');
						}
						escaped.append(hex.toUpperCase());
					} else {
						escaped.append(c);
					}
			}
		}
		return escaped.toString();
	}

	private static String[] buildCliCommand(Heartbeat heartbeat, ArrayList<Heartbeat> extraHeartbeats) {
		ArrayList<String> cmds = new ArrayList<String>();
		cmds.add(Dependencies.getCLILocation());
		cmds.add("--plugin");
		String plugin = pluginString();
		if (plugin == null) {
			return new String[0];
		}
		cmds.add(pluginString());
		cmds.add("--entity");
		cmds.add(heartbeat.entity);
		cmds.add("--time");
		cmds.add(heartbeat.timestamp.toPlainString());
		String apiKey = ConfigFile.getApiKey();
		if (!apiKey.equals("")) {
			cmds.add("--key");
			cmds.add(apiKey);
		}
		if (heartbeat.lineCount != null) {
			cmds.add("--lines-in-file");
			cmds.add(heartbeat.lineCount.toString());
		}
		if (heartbeat.lineNumber != null && false) {
			cmds.add("--lineno");
			cmds.add(heartbeat.lineNumber.toString());
		}
		if (heartbeat.cursorPosition != null && false) {
			cmds.add("--cursorpos");
			cmds.add(heartbeat.cursorPosition.toString());
		}
		if (heartbeat.project != null) {
			cmds.add("--alternate-project");
			cmds.add(heartbeat.project);
		}
		if (heartbeat.language != null) {
			cmds.add("--alternate-language");
			cmds.add(heartbeat.language);
		}
		if (heartbeat.isWrite)
			cmds.add("--write");
		if (heartbeat.isUnsavedFile)
			cmds.add("--is-unsaved-entity");
		if (heartbeat.isBuilding) {
			cmds.add("--category");
			cmds.add("building");
		}
		if (WakaTime.METRICS)
			cmds.add("--metrics");

		if (extraHeartbeats.size() > 0)
			cmds.add("--extra-heartbeats");
		return cmds.toArray(new String[cmds.size()]);
	}

	private static String pluginString() {
		if (IDE_NAME == null || IDE_NAME.equals("")) {
			return null;
		}

		return IDE_NAME + "/" + IDE_VERSION + " " + IDE_NAME + "-wakatime/" + VERSION;
	}

	public static boolean enoughTimePassed(BigDecimal currentTime) {
		return WakaTime.lastTime.add(FREQUENCY).compareTo(currentTime) < 0;
	}

	public static boolean isAppActive() {
		return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;
	}

	public static void setupConfigs() {
		String debug = ConfigFile.get("settings", "debug", false);
		WakaTime.DEBUG = debug != null && debug.trim().equals("true");
		String metrics = ConfigFile.get("settings", "metrics", false);
		WakaTime.METRICS = metrics != null && metrics.trim().equals("true");
	}

	private static String todayText = "initialized";
	private static BigDecimal todayTextTime = new BigDecimal(0);

	private static String obfuscateKey(String key) {
		String newKey = null;
		if (key != null) {
			newKey = key;
			if (key.length() > 4)
				newKey = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXX" + key.substring(key.length() - 4);
		}
		return newKey;
	}

	private static String[] obfuscateKey(String[] cmds) {
		ArrayList<String> newCmds = new ArrayList<String>();
		String lastCmd = "";
		for (String cmd : cmds) {
			if (lastCmd == "--key")
				newCmds.add(obfuscateKey(cmd));
			else
				newCmds.add(cmd);
			lastCmd = cmd;
		}
		return newCmds.toArray(new String[newCmds.size()]);
	}
}
