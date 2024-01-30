package io.github.imaron85.wakamine.wakatime;


import io.github.imaron85.wakamine.WakaMine;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.PasswordAuthentication;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class Response {
	public int statusCode;
	public String body;
	public String lastModified;

	public Response(int statusCode, String body, @Nullable String lastModified) {
		this.statusCode = statusCode;
		this.body = body;
		this.lastModified = lastModified;
	}
}

public class Dependencies {

	private static String resourcesLocation = null;
	private static String originalProxyHost = null;
	private static String originalProxyPort = null;
	private static String githubReleasesUrl = "https://api.github.com/repos/wakatime/wakatime-cli/releases/latest";
	private static String githubDownloadUrl = "https://github.com/wakatime/wakatime-cli/releases/latest/download";

	public static String getResourcesLocation() {
		if (Dependencies.resourcesLocation != null) return Dependencies.resourcesLocation;

		if (System.getenv("WAKATIME_HOME") != null && !System.getenv("WAKATIME_HOME").trim().isEmpty()) {
			File resourcesFolder = new File(System.getenv("WAKATIME_HOME"));
			if (resourcesFolder.exists()) {
				Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
				WakaMine.LOGGER.debug("Using $WAKATIME_HOME for resources folder: " + Dependencies.resourcesLocation);
				return Dependencies.resourcesLocation;
			}
		}

		if (isWindows()) {
			File windowsHome = new File(System.getenv("USERPROFILE"));
			File resourcesFolder = new File(windowsHome, ".wakatime");
			Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
			return Dependencies.resourcesLocation;
		}

		File userHomeDir = new File(System.getProperty("user.home"));
		File resourcesFolder = new File(userHomeDir, ".wakatime");
		Dependencies.resourcesLocation = resourcesFolder.getAbsolutePath();
		return Dependencies.resourcesLocation;
	}

	public static boolean isCLIInstalled() {
		File cli = new File(Dependencies.getCLILocation());
		return cli.exists();
	}

	public static boolean isCLIOld() {
		if (!Dependencies.isCLIInstalled()) {
			return false;
		}
		ArrayList<String> cmds = new ArrayList<String>();
		cmds.add(Dependencies.getCLILocation());
		cmds.add("--version");
		try {
			Process p = Runtime.getRuntime().exec(cmds.toArray(new String[cmds.size()]));
			BufferedReader stdInput = new BufferedReader(new
				InputStreamReader(p.getInputStream()));
			BufferedReader stdError = new BufferedReader(new
				InputStreamReader(p.getErrorStream()));
			p.waitFor();
			String output = "";
			String s;
			while ((s = stdInput.readLine()) != null) {
				output += s;
			}
			while ((s = stdError.readLine()) != null) {
				output += s;
			}
			WakaMine.LOGGER.debug("wakatime-cli local version output: \"" + output + "\"");
			WakaMine.LOGGER.debug("wakatime-cli local version exit code: " + p.exitValue());

			if (p.exitValue() != 0) return true;

			String accessed = ConfigFile.get("internal", "cli_version_last_accessed", true);
			BigInteger now = Util.getCurrentTimestamp().toBigInteger();
			if (accessed != null && accessed.trim().equals("true")) {
				try {
					BigInteger lastAccessed = new BigInteger(accessed.trim());
					BigInteger fourHours = BigInteger.valueOf(4 * 3600);
					if (lastAccessed != null && lastAccessed.add(fourHours).compareTo(now) > 0) {
						WakaMine.LOGGER.debug("Skip checking for wakatime-cli updates because recently checked "+ (now.subtract(lastAccessed).toString()) +" seconds ago");
						return false;
					}
				} catch (NumberFormatException e2) {
					WakaMine.LOGGER.warn(e2.toString());
				}
			}

			String cliVersion = latestCliVersion();
			WakaMine.LOGGER.debug("Latest wakatime-cli version: " + cliVersion);
			if (output.trim().equals(cliVersion)) return false;
		} catch (Exception e) {
			WakaMine.LOGGER.warn(e.toString());
		}
		return true;
	}

	public static String latestCliVersion() {
		try {
			Response resp = getUrlAsString(githubReleasesUrl, ConfigFile.get("internal", "cli_version_last_modified", true), true);
			if (resp == null) return "Unknown";
			Pattern p = Pattern.compile(".*\"tag_name\":\\s*\"([^\"]+)\",.*");
			Matcher m = p.matcher(resp.body);
			if (m.find()) {
				String cliVersion = m.group(1);
				if (resp.lastModified != null) {
					ConfigFile.set("internal", "cli_version_last_modified", true, resp.lastModified);
					ConfigFile.set("internal", "cli_version", true, cliVersion);
				}
				BigInteger now = Util.getCurrentTimestamp().toBigInteger();
				ConfigFile.set("internal", "cli_version_last_accessed", true, now.toString());
				return cliVersion;
			}
		} catch (Exception e) {
			WakaMine.LOGGER.warn(e.toString());
		}
		return "Unknown";
	}

	public static String getCLILocation() {
		if (System.getenv("WAKATIME_CLI_LOCATION") != null && !System.getenv("WAKATIME_CLI_LOCATION").trim().isEmpty()) {
			File wakatimeCLI = new File(System.getenv("WAKATIME_CLI_LOCATION"));
			if (wakatimeCLI.exists()) {
				WakaMine.LOGGER.debug("Using $WAKATIME_CLI_LOCATION as CLI Executable: " + wakatimeCLI);
				return System.getenv("WAKATIME_CLI_LOCATION");
			}
		}

		String ext = isWindows() ? ".exe" : "";
		return combinePaths(getResourcesLocation(), "wakatime-cli-" + osname() + "-" + architecture() + ext);
	}

	public static void installCLI() {
		File resourceDir = new File(getResourcesLocation());
		if (!resourceDir.exists()) resourceDir.mkdirs();

		checkMissingPlatformSupport();

		String url = getCLIDownloadUrl();
		String zipFile = combinePaths(getResourcesLocation(), "wakatime-cli.zip");

		if (downloadFile(url, zipFile)) {

			// Delete old wakatime-cli if it exists
			File file = new File(getCLILocation());
			recursiveDelete(file);

			File outputDir = new File(getResourcesLocation());
			try {
				unzip(zipFile, outputDir);
				if (!isWindows()) {
					makeExecutable(getCLILocation());
				}
				File oldZipFile = new File(zipFile);
				oldZipFile.delete();
			} catch (IOException e) {
				WakaMine.LOGGER.warn(e.toString());
			}
		}
	}

	private static void checkMissingPlatformSupport() {
		String osname = osname();
		String arch = architecture();

		String[] validCombinations = {
			"darwin-amd64",
			"darwin-arm64",
			"freebsd-386",
			"freebsd-amd64",
			"freebsd-arm",
			"linux-386",
			"linux-amd64",
			"linux-arm",
			"linux-arm64",
			"netbsd-386",
			"netbsd-amd64",
			"netbsd-arm",
			"openbsd-386",
			"openbsd-amd64",
			"openbsd-arm",
			"openbsd-arm64",
			"windows-386",
			"windows-amd64",
			"windows-arm64",
		};
		if (!Arrays.asList(validCombinations).contains(osname + "-" + arch)) reportMissingPlatformSupport(osname, arch);
	}

	private static void reportMissingPlatformSupport(String osname, String architecture) {
		String url = "https://api.wakatime.com/api/v1/cli-missing?osname=" + osname + "&architecture=" + architecture + "&plugin=WakaMine";
		try {
			getUrlAsString(url, null, false);
		} catch (Exception e) {
			WakaMine.LOGGER.warn(e.toString());
		}
	}

	private static String getCLIDownloadUrl() {
		return githubDownloadUrl + "/wakatime-cli-" + osname() + "-" + architecture() + ".zip";
	}

	public static boolean downloadFile(String url, String saveAs) {
		File outFile = new File(saveAs);

		// create output directory if does not exist
		File outDir = outFile.getParentFile();
		if (!outDir.exists())
			outDir.mkdirs();

		URL downloadUrl = null;
		try {
			downloadUrl = new URL(url);
		} catch (MalformedURLException e) {
			WakaMine.LOGGER.error("DownloadFile(" + url + ") failed to init new URL");
			WakaMine.LOGGER.error(e.toString());
			return false;
		}

		WakaMine.LOGGER.debug("DownloadFile(" + downloadUrl.toString() + ")");

		setupProxy();

		ReadableByteChannel rbc = null;
		FileOutputStream fos = null;
		try {
			rbc = Channels.newChannel(downloadUrl.openStream());
			fos = new FileOutputStream(saveAs);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
			teardownProxy();
			return true;
		} catch (RuntimeException e) {
			WakaMine.LOGGER.warn(e.toString());
			try {
				// try downloading without verifying SSL cert (https://github.com/wakatime/jetbrains-wakatime/issues/46)
				SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
				SSL_CONTEXT.init(null, new TrustManager[] { new LocalSSLTrustManager() }, null);
				HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
				HttpsURLConnection conn = (HttpsURLConnection)downloadUrl.openConnection();
				conn.setRequestProperty("User-Agent", "github.com/wakatime/jetbrains-wakatime");
				InputStream inputStream = conn.getInputStream();
				fos = new FileOutputStream(saveAs);
				int bytesRead = -1;
				byte[] buffer = new byte[4096];
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					fos.write(buffer, 0, bytesRead);
				}
				inputStream.close();
				fos.close();
				teardownProxy();
				return true;
			} catch (NoSuchAlgorithmException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (KeyManagementException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (IOException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (IllegalArgumentException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (Exception e1) {
				WakaMine.LOGGER.warn(e1.toString());
			}
		} catch (IOException e) {
			WakaMine.LOGGER.warn(e.toString());
		}

		teardownProxy();
		return false;
	}

	public static Response getUrlAsString(String url, @Nullable String lastModified, boolean updateLastModified) {
		StringBuilder text = new StringBuilder();

		URL downloadUrl = null;
		try {
			downloadUrl = new URL(url);
		} catch (MalformedURLException e) {
			WakaMine.LOGGER.error("getUrlAsString(" + url + ") failed to init new URL");
			WakaMine.LOGGER.error(e.toString());
			return null;
		}

		WakaMine.LOGGER.debug("getUrlAsString(" + downloadUrl.toString() + ")");

		setupProxy();

		String responseLastModified = null;
		int statusCode = -1;
		try {
			HttpsURLConnection conn = (HttpsURLConnection) downloadUrl.openConnection();
			conn.setRequestProperty("User-Agent", "github.com/wakatime/jetbrains-wakatime");
			if (lastModified != null && !lastModified.trim().equals("")) {
				conn.setRequestProperty("If-Modified-Since", lastModified.trim());
			}
			statusCode = conn.getResponseCode();
			if (statusCode == 304) {
				teardownProxy();
				return null;
			}
			InputStream inputStream = downloadUrl.openStream();
			byte[] buffer = new byte[4096];
			while (inputStream.read(buffer) != -1) {
				text.append(new String(buffer, "UTF-8"));
			}
			inputStream.close();
			if (updateLastModified && conn.getResponseCode() == 200) responseLastModified = conn.getHeaderField("Last-Modified");
		} catch (RuntimeException e) {
			WakaMine.LOGGER.warn(e.toString());
			try {
				// try downloading without verifying SSL cert (https://github.com/wakatime/jetbrains-wakatime/issues/46)
				SSLContext SSL_CONTEXT = SSLContext.getInstance("SSL");
				SSL_CONTEXT.init(null, new TrustManager[]{new LocalSSLTrustManager()}, null);
				HttpsURLConnection.setDefaultSSLSocketFactory(SSL_CONTEXT.getSocketFactory());
				HttpsURLConnection conn = (HttpsURLConnection) downloadUrl.openConnection();
				conn.setRequestProperty("User-Agent", "github.com/wakatime/jetbrains-wakatime");
				if (lastModified != null && !lastModified.trim().equals("")) {
					conn.setRequestProperty("If-Modified-Since", lastModified.trim());
				}
				statusCode = conn.getResponseCode();
				if (statusCode == 304) {
					teardownProxy();
					return null;
				}
				InputStream inputStream = conn.getInputStream();
				byte[] buffer = new byte[4096];
				while (inputStream.read(buffer) != -1) {
					text.append(new String(buffer, "UTF-8"));
				}
				inputStream.close();
				if (updateLastModified && conn.getResponseCode() == 200) responseLastModified = conn.getHeaderField("Last-Modified");
			} catch (NoSuchAlgorithmException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (KeyManagementException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (UnknownHostException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (IOException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (IllegalArgumentException e1) {
				WakaMine.LOGGER.warn(e1.toString());
			} catch (Exception e1) {
				WakaMine.LOGGER.warn(e1.toString());
			}
		} catch (UnknownHostException e) {
			WakaMine.LOGGER.warn(e.toString());
		} catch (Exception e) {
			WakaMine.LOGGER.warn(e.toString());
		}

		teardownProxy();
		return new Response(statusCode, text.toString(), responseLastModified);
	}

	/**
	 * Configures a proxy if one is set in ~/.wakatime.cfg.
	 */
	private static void setupProxy() {
		String proxyConfig = ConfigFile.get("settings", "proxy", false);
		if (proxyConfig != null && !proxyConfig.trim().equals("")) {
			originalProxyHost = System.getProperty("https.proxyHost");
			originalProxyPort = System.getProperty("https.proxyPort");
			try {
				URI proxyUrl = new URI(proxyConfig);
				String userInfo = proxyUrl.getUserInfo();
				if (userInfo != null) {
					final String user = userInfo.split(":")[0];
					final String pass = userInfo.split(":")[1];
					Authenticator authenticator = new Authenticator() {
						public PasswordAuthentication getPasswordAuthentication() {
							return (new PasswordAuthentication(user, pass.toCharArray()));
						}
					};
					Authenticator.setDefault(authenticator);
				}

				if (!proxyUrl.getHost().trim().isEmpty()) {
					System.setProperty("https.proxyHost", proxyUrl.getHost());
					System.setProperty("https.proxyPort", Integer.toString(proxyUrl.getPort()));
				}

			} catch (URISyntaxException e) {
				WakaMine.LOGGER.error("Proxy string must follow https://user:pass@host:port format: " + proxyConfig);
				WakaMine.LOGGER.error(e.toString());
			}
		}
	}

	private static void teardownProxy() {
		if (originalProxyHost != null) {
			System.setProperty("https.proxyHost", originalProxyHost);
		} else {
			System.clearProperty("https.proxyHost");
		}
		if (originalProxyPort != null) {
			System.setProperty("https.proxyPort", originalProxyPort);
		} else {
			System.clearProperty("https.proxyPort");
		}
		Authenticator.setDefault(null);
	}

	private static void unzip(String zipFile, File outputDir) throws IOException {
		if(!outputDir.exists())
			outputDir.mkdirs();

		byte[] buffer = new byte[1024];
		ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
		ZipEntry ze = zis.getNextEntry();

		while (ze != null) {
			String fileName = ze.getName();
			File newFile = new File(outputDir, fileName);

			if (ze.isDirectory()) {
				newFile.mkdirs();
			} else {
				FileOutputStream fos = new FileOutputStream(newFile.getAbsolutePath());
				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
				fos.close();
			}

			ze = zis.getNextEntry();
		}

		zis.closeEntry();
		zis.close();
	}

	private static void recursiveDelete(File path) {
		if(path.exists()) {
			if (isDirectory(path)) {
				File[] files = path.listFiles();
				for (int i = 0; i < files.length; i++) {
					if (isDirectory(files[i])) {
						recursiveDelete(files[i]);
					} else {
						files[i].delete();
					}
				}
			}
			path.delete();
		}
	}

	public static boolean is64bit() {
		return System.getProperty("os.arch").indexOf("64") != -1;
	}

	public static boolean isWindows() {
		return System.getProperty("os.name").contains("Windows");
	}

	public static String osname() {
		if (isWindows()) return "windows";
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("mac") || os.contains("darwin")) return "darwin";
		if (os.contains("linux")) return "linux";
		return os;
	}

	public static String architecture() {
		String arch = System.getProperty("os.arch");
		if (arch.contains("386") || arch.contains("32")) return "386";
		if (arch.equals("aarch64")) return "arm64";
		if (osname().equals("darwin") && arch.contains("arm")) return "arm64";
		if (arch.contains("64")) return "amd64";
		return arch;
	}

	public static String combinePaths(String... args) {
		File path = null;
		for (String arg : args) {
			if (arg != null) {
				if (path == null)
					path = new File(arg);
				else
					path = new File(path, arg);
			}
		}
		if (path == null)
			return null;
		return path.toString();
	}

	private static void makeExecutable(String filePath) throws IOException {
		File file = new File(filePath);
		try {
			file.setExecutable(true);
		} catch(SecurityException e) {
			WakaMine.LOGGER.warn(e.toString());
		}
	}

	private static boolean isSymLink(File filepath) {
		try {
			return Files.isSymbolicLink(filepath.toPath());
		} catch(SecurityException e) {
			WakaMine.LOGGER.warn(e.toString());
			return false;
		}
	}

	private static boolean isDirectory(File filepath) {
		try {
			return filepath.isDirectory();
		} catch(SecurityException e) {
			WakaMine.LOGGER.warn(e.toString());
			return false;
		}
	}

	public static void createSymlink(String source, String destination) {
		File sourceLink = new File(source);
		if (isDirectory(sourceLink)) recursiveDelete(sourceLink);
		if (!isWindows()) {
			if (!isSymLink(sourceLink)) {
				recursiveDelete(sourceLink);
				try {
					Files.createSymbolicLink(sourceLink.toPath(), new File(destination).toPath());
				} catch (Exception e) {
					WakaMine.LOGGER.warn(e.toString());
					try {
						Files.copy(new File(destination).toPath(), sourceLink.toPath(), StandardCopyOption.REPLACE_EXISTING);
					} catch (Exception ex) {
						WakaMine.LOGGER.warn(ex.toString());
					}
				}
			}
		} else {
			try {
				Files.copy(new File(destination).toPath(), sourceLink.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				WakaMine.LOGGER.warn(e.toString());
			}
		}
	}
}
