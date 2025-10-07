/**
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 **/
 
/**
 This file is part of Save For Offline, an Android app which saves / downloads complete webpages for offine reading.
**/
 
/**
 If you modify, redistribute, or write something based on this or parts of it, you MUST,
 I repeat, you MUST comply with the GPLv2+ license. This means that if you use or modify
 my code, you MUST release the source code of your modified version, if / when this is
 required under the terms of the license.
 
 If you cannot / do not want to do this, DO NOT USE MY CODE. Thanks.
 
 (I've added this message to to the source because it's been used in severeral proprietary
 closed source apps, which I don't want, and which is also a violation of the liense.)
**/

/**
 Written by Jonas Czech (JonasCz, stackoverflow.com/users/4428462/JonasCz and github.com/JonasCz) originally and partially based on https://github.com/PramodKhare/GetMeThatPage/
 with lots of improvements. (4428462jonascz/eafc4d1afq)
 **/

package jonas.tool.saveForOffline;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.l3s.boilerpipe.extractors.ArticleExtractor;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class PageSaver {
    private EventCallback eventCallback;

    private OkHttpClient client;
    private final String HTTP_REQUEST_TAG = "TAG";

    private boolean isCancelled = false;
    private Options options = new Options();

    // filesToGrab - maintains all the links to files (eg images, scripts) which we are going to grab/download
    private List<String> filesToGrab = new ArrayList<String>();
	
    private String title = "";
	private String pageIconUrl = "";

    private String indexFileName = "index.html";

    private final Pattern fileNameReplacementPattern = Pattern.compile("[^a-zA-Z0-9-_\\.]");

    public Options getOptions() {
        return this.options;
    }

    public String getPageTitle () {
        return this.title;
    }

    public PageSaver(EventCallback callback) {
        this.eventCallback = callback;
		
		client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public void cancel() {
        this.isCancelled = true;
        for (Call call : client.dispatcher().queuedCalls()) {
            if (HTTP_REQUEST_TAG.equals(call.request().tag())) {
                call.cancel();
            }
        }
        for (Call call : client.dispatcher().runningCalls()) {
            if (HTTP_REQUEST_TAG.equals(call.request().tag())) {
                call.cancel();
            }
        }
    }
	
	public void resetState () {
		filesToGrab.clear();
		
		title = "";
		pageIconUrl = "";
		isCancelled = false;
	}

    public boolean isCancelled () {
        return this.isCancelled;
    }

    public boolean getPage(String url, String outputDirPath, String indexFilename) {

        this.indexFileName = indexFilename;

        File outputDir = new File(outputDirPath);

        if (!outputDir.exists() && outputDir.mkdirs() == false) {
            eventCallback.onFatalError(new IOException("File " + outputDirPath + "could not be created"), url);
            return false;
        }

        //download main html and parse
        boolean success = downloadAndDistillPage(url, outputDirPath);
        if (isCancelled || !success) {
			return false;
		}
		
		ThreadPoolExecutor pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(), 60, TimeUnit.SECONDS, new BlockingDownloadTaskQueue<Runnable>());
		
		for (Iterator<String> i = filesToGrab.iterator(); i.hasNext();) {
			if (isCancelled) {
				eventCallback.onProgressMessage("Cancelling...");
				shutdownExecutor(pool, 10, TimeUnit.SECONDS);
				return success;
			}
			
			String urlToDownload = i.next();
			
            eventCallback.onProgressMessage("Saving file: " + getFileName(urlToDownload));
            eventCallback.onProgressChanged(filesToGrab.indexOf(urlToDownload), filesToGrab.size(), false);
			
			pool.submit(new DownloadTask(urlToDownload, outputDir));
		}
		pool.submit(new DownloadTask(pageIconUrl, outputDir, "saveForOffline_icon.png"));
		
		eventCallback.onProgressMessage("Finishing file downloads...");
		shutdownExecutor(pool, 60, TimeUnit.SECONDS);
		
		return success;
    }

    private boolean downloadAndDistillPage(final String url, final String outputDir) {
        String filename = indexFileName;
        String baseUrl = url;
        if (url.endsWith("/")) {
            baseUrl = url + filename;
        }

        try {
			eventCallback.onProgressMessage("Getting HTML file");
            String htmlContent = getStringFromUrl(url);

			eventCallback.onProgressMessage("Extracting content...");
            String extractedContent = ArticleExtractor.INSTANCE.getText(htmlContent);

			eventCallback.onProgressMessage("Processing extracted content...");
            String finalHtml = parseDistilledHtml(extractedContent, baseUrl);

			eventCallback.onProgressMessage("Saving main HTML file");
            File outputFile = new File(outputDir, filename);
            saveStringToFile(finalHtml, outputFile);
            return true;

        } catch (Exception e) {
			eventCallback.onFatalError(e, url);
			e.printStackTrace();
            return false;
        }
    }

    private class DownloadTask implements Runnable {

        private String url;
        private File outputDir;
		private String fileName;

        public DownloadTask(String url, File toPath) {
            this.url = url;
            this.outputDir = toPath;
        }
		
		public DownloadTask(String url, File toPath, String fileName) {
            this.url = url;
            this.outputDir = toPath;
			this.fileName = fileName;
        }

        @Override
        public void run() {
			if (fileName == null) {
				fileName = getFileName(url);
			}
			
            File outputFile = new File(outputDir, fileName);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", getOptions().getUserAgent())
                    .tag(HTTP_REQUEST_TAG)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                InputStream is = response.body().byteStream();
				
                FileOutputStream fos = new FileOutputStream(outputFile);
                final byte[] buffer = new byte[1024 * 32]; // read in batches of 32K
                int length;
                while ((length = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, length);
                }

                response.body().close();
                fos.flush();
                fos.close();
                is.close();

            } catch (IllegalArgumentException | IOException e) {
				IOException ex = new IOException("File download failed, URL: " + url + ", Output file path: " + outputFile.getPath());
				
				if (isCancelled) {
					ex.initCause(new IOException("Save was cancelled, isCancelled is true").initCause(e));
					eventCallback.onError(ex);
				} else {
					eventCallback.onError(ex.initCause(e));
				}
            }
        }
    }

    private String getStringFromUrl(String url) throws IOException, IllegalStateException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", getOptions().getUserAgent())
				.tag(HTTP_REQUEST_TAG)
                .build();
        Response response = client.newCall(request).execute();
        String out = response.body().string();
        response.body().close();
        return out;
    }

    private void saveStringToFile(String ToSave, File outputFile) throws IOException {

        if (outputFile.exists()) {
            return;
        }

        outputFile.createNewFile();

        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(ToSave.getBytes());

        fos.flush();
        fos.close();
    }

    private String parseDistilledHtml(String htmlToParse, String baseUrl) {
        Document document = Jsoup.parse(htmlToParse, baseUrl);
        document.outputSettings().escapeMode(Entities.EscapeMode.extended);
		
		if (title.isEmpty()) {
			title = document.title();
			eventCallback.onPageTitleAvailable(title);
		}
		
		if (pageIconUrl.isEmpty()) {
			eventCallback.onProgressMessage("Getting icon...");
			pageIconUrl = FaviconFetcher.getInstance().getFaviconUrl(document);
		}
		
		eventCallback.onProgressMessage("Processing HTML...");

        String urlToGrab;
        Elements links;

        if (getOptions().saveImages()) {
            links = document.select("img[src]");
            eventCallback.onLogMessage("Got " + links.size() + " image elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
				link.removeAttr("srcset");
            }
        }

        if (getOptions().makeLinksAbsolute()) {
            links = document.select("a[href]");
            eventCallback.onLogMessage("Making " + links.size() + " links absolute");
            for (Element link : links) {
                String absUrl = link.attr("abs:href");
                link.attr("href", absUrl);
            }
        }
        return document.outerHtml();
    }
	
	private boolean isLinkValid (String url) {
		if (url == null || url.length() == 0) {
			return false;
		} else if (!url.startsWith("http")) {
			return false;
		} else {
			return true;
		}
	}

    private void addLinkToList(String link, List<String> list) {
        if (!isLinkValid(link) || list.contains(link)) {
			return;
		} else {
			list.add(link);
		}
    }
	
	private void addLinkToList(String link, String baseUrl, List<String> list) {
		if (link.startsWith("data:image")) {
			return;
		}
		try {
			URL u = new URL(new URL(baseUrl), link);
			link = u.toString();
		} catch (MalformedURLException e) {
			return;
		}
		
		if (!isLinkValid(link) || list.contains(link)) {
			return;
		} else {
			list.add(link);
		}
    }

    private String getFileName(String url) {

        String filename = url.substring(url.lastIndexOf('/') + 1);
		
		if (filename.trim().length() == 0) {
			filename = String.valueOf(url.hashCode());
		}

        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf("?")) + filename.substring(filename.indexOf("?") + 1).hashCode();
        }

        filename = fileNameReplacementPattern.matcher(filename).replaceAll("_");
        filename = filename.substring(0, Math.min(200, filename.length()));

        return filename;
    }
	
	private void shutdownExecutor (ExecutorService e, int waitTime, TimeUnit waitTimeUnit) {
		e.shutdown();
		try {
            if (!e.awaitTermination(waitTime, waitTimeUnit)) {
				eventCallback.onError("Executor pool did not termimate after " + waitTime + " " + waitTimeUnit.toString() +", doing shutdownNow()");
				e.shutdownNow();
			}
        } catch (InterruptedException ie) {
            eventCallback.onError(ie);
        }
	}
	
	private class BlockingDownloadTaskQueue<E> extends SynchronousQueue<E> {
		public BlockingDownloadTaskQueue () {
			super();
		}
		
		@Override
		public boolean offer (E e) {
			try {
				put(e);
				return true;
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				eventCallback.onError(ie);
				
				return false;
			}
		}
	}
	
	class Options {
        private boolean makeLinksAbsolute = true;
        private boolean saveImages = true;
        private String userAgent = " ";

		public void setCache (File cacheDirectory, long maxCacheSize) {
			Cache cache = new Cache(cacheDirectory, maxCacheSize);
			client = client.newBuilder().cache(cache).build();
		}

		public void clearCache() throws IOException {
			if (client.cache() != null) {
				client.cache().evictAll();
			}
		}

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(final String userAgent) {
            this.userAgent = userAgent;
        }

        public boolean makeLinksAbsolute() {
            return makeLinksAbsolute;
        }

        public void makeLinksAbsolute(final boolean makeLinksAbsolute) {
            this.makeLinksAbsolute = makeLinksAbsolute;
        }

        public boolean saveImages() {
            return saveImages;
        }

        public void saveImages(final boolean saveImages) {
            this.saveImages = saveImages;
        }
    }
}

interface EventCallback {
    public void onProgressChanged(int progress, int maxProgress, boolean indeterminate);

    public void onProgressMessage(String fileName);
	
	public void onPageTitleAvailable (String pageTitle);

    public void onLogMessage (String message);

    public void onError(Throwable error);
	
	public void onError(String errorMessage);
	
	public void onFatalError (Throwable error, String pageUrl);
}