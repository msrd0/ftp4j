package it.sauronsoftware.ftp4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * FTP-related utilities and common routines.
 * 
 * @author Carlo Pelliccia
 * @since 1.7.3
 */
public class FTPUtils {

	/**
	 * Creates an &quot;always trusting&quot; SSLSocketFactory, which can be set
	 * on the client with the
	 * {@link FTPClient#setSSLSocketFactory(SSLSocketFactory)} method before
	 * connecting to the remote server using FTPS or FTPES.
	 * 
	 * By doing so, any SSL certificate supplied by the remote server will be
	 * automatically accepted, without any verification.
	 * 
	 * @return A {@link SSLSocketFactory} object trusting any certificate.
	 * @since 1.7.3
	 */
	public static SSLSocketFactory getAlwaysTrustingSSLSocketFactory() {
		TrustManager[] trustManager = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
		SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustManager, new SecureRandom());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
		return sslContext.getSocketFactory();
	}

	public static void transferFromServerToServer(final FTPClient sourceClient, final FTPClient targetClient, final String sourceFile, final String targetFile, final long restartAt, final FTPDataTransferListener listener) throws IllegalStateException, IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException {
		File tmpFile = File.createTempFile("ftp4j", ".tmp");
		tmpFile.createNewFile();
		final Object readLock = new Object();
		final Object operationLock = new Object();
		final _transferFromServerToServer_helper_struct h = new _transferFromServerToServer_helper_struct();
		final FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
		final FileInputStream fileInputStream = new FileInputStream(tmpFile);
		final OutputStream outputStream = new OutputStream() {
			public void write(int b) throws IOException {
				fileOutputStream.write(b);
				synchronized (readLock) {
					readLock.notify();
				}
			}
		};
		final InputStream inputStream = new InputStream() {
			public int read() throws IOException {
				do {
					int v = fileInputStream.read();
					if (v == -1) {
						if (h.downloadCompleted || h.downloadException != null) {
							return -1;
						}
						synchronized (readLock) {
							try {
								readLock.wait();
							} catch (InterruptedException e) {
							}
						}
					} else {
						return v;
					}
				} while (true);
			}
		};
		Thread t1 = new Thread() {
			public void run() {
				try {
					sourceClient.download(sourceFile, outputStream, restartAt, null);
					h.downloadCompleted = true;
				} catch (Exception e) {
					h.downloadException = e;
				}
				synchronized (readLock) {
					readLock.notify();
				}
				synchronized (operationLock) {
					operationLock.notify();
				}
			};
		};
		Thread t2 = new Thread() {
			public void run() {
				try {
					targetClient.upload(targetFile, inputStream, restartAt, restartAt, listener);
					h.uploadCompleted = true;
				} catch (Exception e) {
					h.uploadException = e;
				}
				synchronized (operationLock) {
					operationLock.notify();
				}
			};
		};
		t1.start();
		t2.start();
		try {
			synchronized (operationLock) {
				do {
					try {
						operationLock.wait();
					} catch (InterruptedException e) {
					}
					if (h.downloadException != null) {
						if (!h.uploadCompleted) {
							try {
								targetClient.abortCurrentDataTransfer(true);
							} catch (Throwable t) {
							}
						}
						throw h.downloadException;
					}
					if (h.uploadException != null) {
						if (!h.downloadCompleted) {
							try {
								sourceClient.abortCurrentDataTransfer(true);
							} catch (Throwable t) {
							}
						}
						throw h.uploadException;
					}
					if (h.downloadCompleted && h.uploadCompleted) {
						return;
					}
				} while (true);
			}
		} catch (Exception e) {
			if (e instanceof IllegalStateException) {
				throw (IllegalStateException) e;
			}
			if (e instanceof IOException) {
				throw (IOException) e;
			}
			if (e instanceof FTPIllegalReplyException) {
				throw (FTPIllegalReplyException) e;
			}
			if (e instanceof FTPException) {
				throw (FTPException) e;
			}
			if (e instanceof FTPDataTransferException) {
				throw (FTPDataTransferException) e;
			}
			if (e instanceof FTPAbortedException) {
				throw (FTPAbortedException) e;
			}
			throw new RuntimeException(e);
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (Throwable t) {
				}
			}
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (Throwable t) {
				}
			}
			if (fileInputStream != null) {
				try {
					fileInputStream.close();
				} catch (Throwable t) {
				}
			}
			if (fileOutputStream != null) {
				try {
					fileOutputStream.close();
				} catch (Throwable t) {
				}
			}
			tmpFile.delete();
		}
	}

	private static class _transferFromServerToServer_helper_struct {
		public boolean downloadCompleted = false;
		public boolean uploadCompleted = false;
		public Exception downloadException = null;
		public Exception uploadException = null;
	}

}
