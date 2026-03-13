package yt;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

/* ==========================================================
   Ytmp3Multiple – Descarga en lote de varios enlaces YouTube
   ========================================================== */
public class Ytmp3Multiple extends JFrame {

	private static final long serialVersionUID = 1L;

	/* ----- CONSTANTES ----- */
	private static final String WINDOW_TITLE = "YouTube MP3 Downloader – Multiples URLs y/o Listas de reproducción";
	private static final String YT_DLP_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
	private static final String LIB_DIR = "lib";
	private static final String DOWNLOADS_DIR = "downloads";
	private static final String YT_DLP_FILENAME = "yt-dlp.exe";
	private static final int CLIPBOARD_POLL_MS = 2000;
	private static final int TRUNCATE_URL_LENGTH = 50;

	// Paleta dark

	private static final Color DARK_BORDER   = new Color(0x4A4A4A);
	private static final Color ACCENT        = new Color(0x9E9E9E);
	private static final Color SUCCESS_GREEN = new Color(0x6FCF70);
	private static final Color LOG_BG        = new Color(0x121212);
	private static final Color LOG_FG        = new Color(0xD6D6D6);

	private static final Color PROGRESS_FG     = ACCENT;
	private static final Color PROGRESS_BG     = DARK_BORDER;
	private static final Color STATUS_FG       = SUCCESS_GREEN;
	private static final Color BORDER_COLOR    = DARK_BORDER;

	/* ----- Regex para validar URLs de YouTube (cubre www., m., music., youtu.benocookie) ----- */
	private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
			"^(https?://)?(www\\.|m\\.|music\\.)?youtu(\\.be/|be\\.com/|be-nocookie\\.com/).+",
			Pattern.CASE_INSENSITIVE);

	/* ----- COMPONENTES ----- */
	private JTextField urlField;
	private JTextArea logArea;
	private JButton addUrlButton;
	private JButton downloadAllButton;
	private JList<String> urlList;
	private DefaultListModel<String> urlListModel;
	private JButton downloadButton;
	private JButton cancelButton;
	private JProgressBar progressBar;
	private JLabel statusLabel;
	private File ytDlpExe;
	private Timer clipboardTimer;
	private String lastClipboard = "";
	private JButton removeButtonLocal;
	private JButton openFolderBtn;

	/* ----- Lista de títulos descargados – sincronizada para acceso desde worker + EDT ----- */
	private final List<String> downloadedTitles = Collections.synchronizedList(new ArrayList<>());

	/** ----- Proceso actual de yt-dlp (volatile para visibilidad entre hilos) ----- */
	private volatile Process currentProcess;

	/** ----- Worker activo (para poder cancelarlo) ----- */
	private volatile SwingWorker<?, ?> activeWorker;

	/* -------------------- CONSTRUCTOR -------------------- */
	public Ytmp3Multiple() {

		setTitle(WINDOW_TITLE);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(900, 670);
		setLocationRelativeTo(null);

		// Preparar yt‑dlp (descargar si falta)
		setupExecutables();

		// Modelo para URLs (la JList se crea en initComponents)
		urlListModel = new DefaultListModel<>();

		// Botones extra
		addUrlButton = new JButton("Añadir URL");
		downloadAllButton = new JButton("Descargar todos");

		// Acción "Añadir URL"
		addUrlButton.addActionListener(e -> {
			String txt = urlField.getText().trim();

			if (txt.isEmpty()) {
				statusLabel.setText("⚠️ Escribe una URL");
				return;
			}

			if (urlListModel.contains(txt)) {
				JOptionPane.showMessageDialog(this, "Este link ya está en la lista.", "URL duplicada",
						JOptionPane.WARNING_MESSAGE);
				statusLabel.setText("⚠️ Link ya añadido");
				return;
			}

			// ¿Es una playlist?
			if (txt.contains("list=") || txt.contains("/playlist")) {
				int choice = JOptionPane.showConfirmDialog(this,
						"📋 Esta URL es una lista de reproducción.\n\n"
								+ "¿Quieres añadirla para descargar TODAS las canciones?",
						"Playlist detectada", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

				if (choice != JOptionPane.YES_OPTION) {
					statusLabel.setText("⚠️ Playlist no añadida");
					return;
				}
			}

			// ✅ Añadir a la lista
			urlListModel.addElement(txt);
			urlField.setText("");
			statusLabel.setText("✅ URL añadida");
			log("➕ Añadida: " + truncateUrl(txt));
		});

		// Acción "Descargar todos"
		downloadAllButton.addActionListener(e -> {
			if (urlListModel.isEmpty()) {
				JOptionPane.showMessageDialog(this, "La lista está vacía. Añade al menos una URL.", "Aviso",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			downloadedTitles.clear();

			setControlsEnabled(false);
			cancelButton.setEnabled(true);
			MultiDownloadTask worker = new MultiDownloadTask(Collections.list(urlListModel.elements()));
			activeWorker = worker;
			worker.execute();
		});

		// Componentes originales (log, barra, etc.)
		initComponents();
		layoutComponents();

		// Activar detección de portapapeles
		setupClipboardDetection();
	}

	/* -------------------- CLIPBOARD -------------------- */
	private void setupClipboardDetection() {
		clipboardTimer = new Timer(CLIPBOARD_POLL_MS, e -> {
			try {
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
					String text = (String) cb.getData(DataFlavor.stringFlavor);
					if (text != null && !text.trim().isEmpty()) {
						String trimmed = text.trim();
						if (isYouTubeUrl(trimmed) && !trimmed.equals(lastClipboard)
								&& !urlListModel.contains(trimmed)) {
							lastClipboard = trimmed;
							urlField.setText(trimmed);
							statusLabel.setText("📋 URL detectada en portapapeles");
							log("🔗 URL pegada automáticamente: " + truncateUrl(trimmed));
						}
					}
				}
			} catch (Exception ex) {
				System.err.println("Error leyendo portapapeles: " + ex.getMessage());
			}
		});
		clipboardTimer.start();
	}

	/* -------------------- HELPERS -------------------- */

	/** Trunca URLs largas para no romper el log */
	private String truncateUrl(String url) {
		return url.length() > TRUNCATE_URL_LENGTH ? url.substring(0, TRUNCATE_URL_LENGTH - 3) + "..." : url;
	}

	/** Valida si una URL pertenece a YouTube usando regex */
	private boolean isYouTubeUrl(String url) {
		return YOUTUBE_PATTERN.matcher(url).matches();
	}

	/* -------------------- 1️⃣ Preparar ejecutable yt‑dlp (descargar si falta) -------------------- */
	private void setupExecutables() {
		File libDir = new File(LIB_DIR);
		if (!libDir.exists())
			libDir.mkdirs();

		ytDlpExe = new File(LIB_DIR, YT_DLP_FILENAME);
		if (!ytDlpExe.exists())
			downloadYtDlp();
	}

	private void downloadYtDlp() {
		log("Descargando yt‑dlp…");
		HttpURLConnection conn = null;
		try {
			URL url = URI.create(YT_DLP_URL).toURL();

			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");

			try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(ytDlpExe)) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) != -1)
					out.write(buf, 0, n);
			}
			log("yt‑dlp descargado exitosamente!");
		} catch (Exception e) {
			log("Error descargando yt‑dlp: " + e.getMessage());
			log("Descárgalo manualmente y ponlo en la carpeta " + LIB_DIR + "/");
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/* -------------------- 2️⃣ Instanciar componentes UI-------------------- */
	private void initComponents() {
		urlField = new JTextField(40);
		urlField.setToolTipText("Pega aquí la URL de YouTube");

		downloadButton = new JButton("Descargar MP3 (única)");
		downloadButton.addActionListener(e -> downloadSingleFromField());

		// Botón Cancelar
		cancelButton = new JButton("⛔ Cancelar descargas");
		cancelButton.setToolTipText("Cancelar la descarga en curso");
		cancelButton.setEnabled(false);
		cancelButton.addActionListener(e -> cancelCurrentDownload());

		logArea = new JTextArea(15, 55);
		logArea.setEditable(false);
		logArea.setBackground(LOG_BG);
		logArea.setForeground(LOG_FG);
		logArea.setCaretColor(LOG_FG);
		logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
		logArea.setBorder(BorderFactory.createCompoundBorder(new RoundedLineBorder(BORDER_COLOR, 8, 2),
				BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setPreferredSize(new Dimension(600, 24));
		progressBar.setForeground(PROGRESS_FG);
		progressBar.setBackground(PROGRESS_BG);
		progressBar.setBorderPainted(false);

		statusLabel = new JLabel("Listo para descargar");
		statusLabel.setForeground(STATUS_FG);

		urlList = new JList<>(urlListModel);
		urlList.setVisibleRowCount(5);
		urlList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Botón Eliminar
		removeButtonLocal = new JButton("🗑️ Eliminar");
		removeButtonLocal.setToolTipText("Eliminar URL seleccionada");
		removeButtonLocal.addActionListener(e -> {
			int idx = urlList.getSelectedIndex();
			if (idx >= 0) {
				String removed = urlListModel.remove(idx);
				log("🗑️ Eliminada: " + truncateUrl(removed));
			} else {
				statusLabel.setText("⚠️ Selecciona una URL para eliminar");
			}
		});

		// Botón Abrir Descargas
		openFolderBtn = new JButton("📂 Abrir Descargas");
		openFolderBtn.setToolTipText("Abrir carpeta de descargas");
		openFolderBtn.addActionListener(e -> {
			try {
				File dir = new File(DOWNLOADS_DIR);
				if (!dir.exists())
					dir.mkdirs();
				Desktop.getDesktop().open(dir);
			} catch (Exception ex) {
				log("❌ No se pudo abrir la carpeta: " + ex.getMessage());
			}
		});
	}

	/* -------------------- 3️⃣ Layout GridBagLayout –-------------------- */
	private void layoutComponents() {

		JPanel topPanel = new JPanel(new GridBagLayout());
		topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(5, 5, 5, 5);

		/* ----- fila 1 : URL + botones ----- */
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		topPanel.add(new JLabel("URL:"), gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 1;
		topPanel.add(urlField, gbc);

		gbc.gridx = 2;
		gbc.gridy = 0;
		gbc.weightx = 0;
		topPanel.add(downloadButton, gbc);

		gbc.gridx = 3;
		gbc.gridy = 0;
		topPanel.add(addUrlButton, gbc);

		JButton clearLogBtn = new JButton("Limpiar Logs");
		clearLogBtn.addActionListener(e -> logArea.setText(""));
		gbc.gridx = 4;
		gbc.gridy = 0;
		topPanel.add(clearLogBtn, gbc);

		/* ----- fila 2 : lista de URLs (scroll) ----- */
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 5;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		topPanel.add(new JScrollPane(urlList), gbc);
		gbc.gridwidth = 1;
		gbc.weighty = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		/* ----- fila 3 : botones de acción ----- */
		JPanel batchPanel = new JPanel();
		batchPanel.setLayout(new BoxLayout(batchPanel, BoxLayout.X_AXIS));
		batchPanel.setOpaque(false);

		batchPanel.add(downloadAllButton);
		batchPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		batchPanel.add(cancelButton);
		batchPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		batchPanel.add(removeButtonLocal);

		// ESPACIO FLEXIBLE (empuja a la derecha)
		batchPanel.add(Box.createHorizontalGlue());

		batchPanel.add(openFolderBtn);

		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 5;
		topPanel.add(batchPanel, gbc);
		gbc.gridwidth = 1;

		// Area central (logs)
		JScrollPane scrollPane = new JScrollPane(logArea);
		scrollPane.setBorder(BorderFactory.createTitledBorder("Logs de Descarga"));

		// Panel inferior (progreso + estado)
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		bottomPanel.add(progressBar, BorderLayout.NORTH);
		bottomPanel.add(statusLabel, BorderLayout.SOUTH);

		// Añadir al JFrame 
		add(topPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(bottomPanel, BorderLayout.SOUTH);
	}

	/* ----- 4️⃣ Helper: escribir en el área de log (thread‑safe) ----- */
	private void log(String msg) {
		SwingUtilities.invokeLater(() -> {
			logArea.append(msg + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
	}

	/* ----- 5️⃣ Descarga única desde el campo de texto ----- */
		private void downloadSingleFromField() {
		String url = urlField.getText().trim();

		if (url.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Introduce una URL válida", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (!isYouTubeUrl(url)) {
			JOptionPane.showMessageDialog(this, "Por favor ingresa una URL válida de YouTube", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Lanzar descarga con SwingWorker
		setControlsEnabled(false);
		cancelButton.setEnabled(true);
		downloadedTitles.clear();

		SwingWorker<Integer, Void> worker = new SwingWorker<>() {
			@Override
			protected Integer doInBackground() throws Exception {
				return downloadSingle(url);
			}

			@Override
			protected void done() {
				cancelButton.setEnabled(false);
				setControlsEnabled(true);
				try {
					int exit = get();
					if (exit == 0) {
						String lastTitle = downloadedTitles.isEmpty() ? url
								: downloadedTitles.get(downloadedTitles.size() - 1);
						JOptionPane.showMessageDialog(Ytmp3Multiple.this, "✅ Descarga completada\n\n🎵 " + lastTitle,
								"Éxito", JOptionPane.INFORMATION_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(Ytmp3Multiple.this, "Error en la descarga. Revisa los logs.",
								"Error", JOptionPane.ERROR_MESSAGE);
					}
				} catch (java.util.concurrent.CancellationException ce) {
					statusLabel.setText("⛔ Descarga cancelada");
					log("⛔ Descarga cancelada por el usuario");
				} catch (Exception ex) {
					statusLabel.setText("Error: " + ex.getMessage());
					log("ERROR: " + ex.getMessage());
				}
			}
		};
		activeWorker = worker;
		worker.execute();
	}

	/* ----- 6️⃣ Cancelar la descarga en curso ----- */
	private void cancelCurrentDownload() {
		// Destruir el proceso de yt-dlp si existe
		Process proc = currentProcess;
		if (proc != null) {
			proc.destroyForcibly();
			currentProcess = null;
		}

		// Cancelar el worker activo
		SwingWorker<?, ?> worker = activeWorker;
		if (worker != null && !worker.isDone()) {
			worker.cancel(true);
		}

		cancelButton.setEnabled(false);
		setControlsEnabled(true);
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		statusLabel.setText("⛔ Descarga cancelada");
		log("⛔ Descarga cancelada por el usuario");
	}

	/* ----- 7️⃣ MÉTODO REUTILIZABLE – descarga una URL ----- */
	private int downloadSingle(String youtubeUrl) throws IOException, InterruptedException {
		// Verificación del ejecutable
		if (!ytDlpExe.exists()) {
			SwingUtilities.invokeLater(() -> {
				statusLabel.setText("yt‑dlp no encontrado");
				log("ERROR: yt‑dlp.exe no está disponible");
			});
			return -1;
		}

		// Carpeta downloads (SE crea si no existe)
		File downloadsDir = new File(DOWNLOADS_DIR);
		if (!downloadsDir.exists()) {
			downloadsDir.mkdirs();
			log("Creado directorio: " + downloadsDir.getAbsolutePath());
		}

		log("\n=== INICIANDO DESCARGA ===");
		log("URL: " + youtubeUrl);
		log("YT‑DLP: " + ytDlpExe.getAbsolutePath());

		SwingUtilities.invokeLater(() -> {
			progressBar.setIndeterminate(true);
			statusLabel.setText("⬇️  Descargando...");
		});

		// Construir comando dinámicamente (añadir --yes-playlist si es playlist)
		List<String> cmdList = new ArrayList<>();
		cmdList.add(ytDlpExe.getAbsolutePath());
		cmdList.add("--format");
		cmdList.add("bestaudio");
		cmdList.add("--extract-audio");
		cmdList.add("--audio-format");
		cmdList.add("mp3");
		cmdList.add("--audio-quality");
		cmdList.add("0");
		cmdList.add("--embed-thumbnail");
		cmdList.add("--add-metadata");
		cmdList.add("--ignore-errors"); // Continuar si un video falla
		cmdList.add("--no-abort-on-error"); // No abortar el lote/playlist por errores

		// Si la URL es una playlist, forzar descarga completa
		if (youtubeUrl.contains("list=") || youtubeUrl.contains("/playlist")) {
			cmdList.add("--yes-playlist");
		}

		cmdList.add("-o");
		cmdList.add(DOWNLOADS_DIR + "/%(title)s.%(ext)s");
		cmdList.add(youtubeUrl);

		String[] command = cmdList.toArray(new String[0]);

		log("Ejecutando: " + String.join(" ", command));

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File("."));
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		currentProcess = proc;

		// Capturar output completo
		StringBuilder outputBuilder = new StringBuilder();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// Check de cancelación
				if (Thread.currentThread().isInterrupted()) {
					proc.destroyForcibly();
					return -2;
				}
				outputBuilder.append(line).append("\n");
				final String lineCopy = line;
				SwingUtilities.invokeLater(() -> log(lineCopy));

				// Parsear porcentaje de progreso de yt-dlp (ej: "[download] 45.2% of 5.23MiB")
				if (line.contains("[download]") && line.contains("%")) {
					try {
						String trimmed = line.substring(line.indexOf("[download]") + 10).trim();
						int pctEnd = trimmed.indexOf('%');
						if (pctEnd > 0) {
							double pct = Double.parseDouble(trimmed.substring(0, pctEnd).trim());
							final int pctInt = (int) Math.round(pct);
							SwingUtilities.invokeLater(() -> {
								progressBar.setIndeterminate(false);
								progressBar.setValue(pctInt);
								progressBar.setString(pctInt + "%");
							});
						}
					} catch (NumberFormatException ignored) {
						// Ignorar líneas que no tengan porcentaje numérico
					}
				}
			}
		}

		int exit = proc.waitFor();
		currentProcess = null;

		// Extraer TODOS los títulos (playlist o individual)
		List<String> titles = extractAllTitlesFromOutput(outputBuilder.toString());

		if (titles.isEmpty()) {
			titles.add(youtubeUrl);
		}

		// Guardar títulos (thread-safe gracias a synchronizedList)
		downloadedTitles.addAll(titles);

		// Actualizar UI
		final List<String> finalTitles = titles;
		final int exitCode = exit;
		SwingUtilities.invokeLater(() -> {
			if (exitCode == 0) {
				log("\n=== DESCARGA COMPLETADA ===");
				log("🎵 Canciones descargadas: " + finalTitles.size());
				for (String title : finalTitles) {
					log("  ✓ " + title);
				}
				progressBar.setIndeterminate(false);
				progressBar.setValue(100);
				progressBar.setString("100%");
			} else {
				log("\n=== ERROR EN DESCARGA (código: " + exitCode + ") ===");
				progressBar.setIndeterminate(false);
				progressBar.setValue(0);
				progressBar.setString("0%");
			}
		});
		return exit;
	}

	/* ----- 8️⃣ SwingWorker para lote de URLs ----- */
	private class MultiDownloadTask extends SwingWorker<Void, String> {
		private final List<String> urls;

		MultiDownloadTask(List<String> urls) {
			this.urls = urls;
		}

		@Override
		protected Void doInBackground() throws Exception {
			int total = urls.size();
			int idx = 1;
			for (String u : urls) {
				if (isCancelled())
					break;

				publish("Descargando (" + idx + "/" + total + "): " + truncateUrl(u));
				int exit = downloadSingle(u);
				if (exit != 0 && !isCancelled()) {
					publish("⚠️  Error al descargar: " + truncateUrl(u));
					// Mostrar ventana de error
					final String failedUrl = truncateUrl(u);
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Ytmp3Multiple.this,
							"❌ Error en la descarga:\n\n" + failedUrl + "\n\nRevisa los logs para más detalles.",
							"Error en la descarga", JOptionPane.ERROR_MESSAGE));
				}

				// Actualizar progreso real del lote
				final int progress = (idx * 100) / total;
				SwingUtilities.invokeLater(() -> {
					progressBar.setIndeterminate(false);
					progressBar.setValue(progress);
					progressBar.setString(progress + "%");
				});

				idx++;
			}
			return null;
		}

		@Override
		protected void process(List<String> chunks) {
			String last = chunks.get(chunks.size() - 1);
			statusLabel.setText(last);
		}

		@Override
		protected void done() {
			cancelButton.setEnabled(false);
			setControlsEnabled(true);

			if (isCancelled()) {
				statusLabel.setText("⛔ Lote cancelado");
				log("⛔ Lote de descargas cancelado por el usuario");
				downloadedTitles.clear();
				return;
			}

			statusLabel.setText("Todas las descargas completadas");

			// Construir mensaje con todos los títulos
			StringBuilder sb = new StringBuilder();
			synchronized (downloadedTitles) {
				sb.append("✅ Descargas completadas: ").append(downloadedTitles.size()).append("\n\n");
				for (int i = 0; i < downloadedTitles.size(); i++) {
					sb.append((i + 1)).append(". 🎵 ").append(downloadedTitles.get(i)).append("\n");
				}
			}

			JOptionPane.showMessageDialog(Ytmp3Multiple.this, sb.toString(), "🎉 Proceso finalizado",
					JOptionPane.INFORMATION_MESSAGE);

			downloadedTitles.clear();
		}
	}

	/* ----- 9️⃣ Habilitar / deshabilitar controles ----- */
	private void setControlsEnabled(boolean enabled) {
		downloadButton.setEnabled(enabled);
		addUrlButton.setEnabled(enabled);
		downloadAllButton.setEnabled(enabled);
		urlField.setEnabled(enabled);
		removeButtonLocal.setEnabled(enabled);
	}

	/* ----- 🛑 Limpiar recursos AL CERRAR ----- */
	@Override
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			if (clipboardTimer != null) {
				clipboardTimer.stop();
			}
			// Destruir proceso de yt-dlp si sigue corriendo
			Process proc = currentProcess;
			if (proc != null) {
				proc.destroyForcibly();
			}
		}
		super.processWindowEvent(e);
	}

	/* ----- 🎵 Extraer TODOS los títulos del output de yt-dlp ----- */
	private List<String> extractAllTitlesFromOutput(String output) {
	    List<String> titles = new ArrayList<>();

	    try {
	        String[] lines = output.split("\n");

	        for (String line : lines) {

	            // 1) Nuevo formato de yt-dlp: "[download] Downloading video title: ..."
	            if (line.contains("Downloading video title:")) {
	                String title = line.substring(line.indexOf("Downloading video title:") + 25).trim();
	                if (!title.isEmpty() && !titles.contains(title)) {
	                    titles.add(title);
	                }
	            }

	            // 2) Formato clásico: "[download] Destination: ..."
	            if (line.contains("Destination:")) {
	                String filename = line.substring(line.indexOf("Destination:") + 12).trim();

	                File f = new File(filename);
	                String name = f.getName();

	                int lastDot = name.lastIndexOf(".");
	                if (lastDot > 0) {
	                    name = name.substring(0, lastDot);
	                }

	                if (!name.isEmpty() && !titles.contains(name)) {
	                    titles.add(name);
	                }
	            }
	        }

	    } catch (Exception e) {
	        log("⚠️  Error extrayendo títulos: " + e.getMessage());
	    }

	    return titles;
	}

	/* ----- 10️⃣ MAIN ----- */
	public static void main(String[] args) {
		// Tema dark global via UIManager (cross-platform)
		applyDarkTheme();
		SwingUtilities.invokeLater(() -> new Ytmp3Multiple().setVisible(true));
	}

	// Configura un tema dark completo a nivel UIManager (funciona en Windows, Mac y Linux)
	private static void applyDarkTheme() {

	    // 🎨 Paleta premium negro + grafito
	    Color BLACK          = new Color(0x000000); // negro puro
	    Color GRAPHITE_D1    = new Color(0x1A1A1A); // grafito muy oscuro
	    Color GRAPHITE_D2    = new Color(0x2E2E2E); // grafito medio
	    Color GRAPHITE_LIGHT = new Color(0x3C3C3C); // grafito claro premium
	    Color TEXT_LIGHT     = new Color(0xD6D6D6); // gris claro elegante
	    Color ACCENT_GRAY    = new Color(0x8A8A8A); // gris metálico acento

	    // PANEL
	    UIManager.put("Panel.background", BLACK);
	    UIManager.put("Panel.foreground", TEXT_LIGHT);

	    // BOTONES
	    // Fondo grafito claro premium
	    UIManager.put("Button.background", GRAPHITE_LIGHT);
	    UIManager.put("Button.foreground", TEXT_LIGHT);

	    // PADDING INTERNO
	    UIManager.put("Button.margin", new Insets(12, 26, 12, 26));

	    // TAMAÑO
	    UIManager.put("Button.minimumSize", new Dimension(160, 48));

	    // BORDE PREMIUM (grafito medio + padding interno)
	    UIManager.put("Button.border",
	        BorderFactory.createCompoundBorder(
	            BorderFactory.createLineBorder(GRAPHITE_D2, 2),
	            BorderFactory.createEmptyBorder(10, 20, 10, 20)
	        )
	    );

	    // CAMPOS DE TEXTO
	    UIManager.put("TextField.background", GRAPHITE_D1);
	    UIManager.put("TextField.foreground", TEXT_LIGHT);
	    UIManager.put("TextField.caretForeground", TEXT_LIGHT);
	    UIManager.put("TextField.border",
	        BorderFactory.createLineBorder(GRAPHITE_D2, 1)
	    );

	    // TEXTAREA (LOGS)
	    UIManager.put("TextArea.background", BLACK);
	    UIManager.put("TextArea.foreground", TEXT_LIGHT);
	    UIManager.put("TextArea.caretForeground", TEXT_LIGHT);

	    // LISTAS
	    UIManager.put("List.background", GRAPHITE_D1);
	    UIManager.put("List.foreground", TEXT_LIGHT);
	    UIManager.put("List.selectionBackground", ACCENT_GRAY);
	    UIManager.put("List.selectionForeground", BLACK);

	    // SCROLLBARS
	    UIManager.put("ScrollBar.background", BLACK);
	    UIManager.put("ScrollBar.thumb", GRAPHITE_D2);
	    UIManager.put("ScrollBar.track", BLACK);

	    // SCROLLPANE
	    UIManager.put("ScrollPane.background", BLACK);
	    UIManager.put("ScrollPane.border",
	        BorderFactory.createLineBorder(GRAPHITE_D2, 1)
	    );

	    // PROGRESSBAR
	    UIManager.put("ProgressBar.background", GRAPHITE_D2);
	    UIManager.put("ProgressBar.foreground", ACCENT_GRAY);
	    UIManager.put("ProgressBar.selectionBackground", TEXT_LIGHT);
	    UIManager.put("ProgressBar.selectionForeground", BLACK);

	    // LABELS
	    UIManager.put("Label.foreground", TEXT_LIGHT);

	    // OPTIONPANE
	    UIManager.put("OptionPane.background", BLACK);
	    UIManager.put("OptionPane.messageForeground", TEXT_LIGHT);

	    // TITLED BORDERS
	    UIManager.put("TitledBorder.titleColor", TEXT_LIGHT);
	    UIManager.put("TitledBorder.border",
	        BorderFactory.createLineBorder(GRAPHITE_D2, 1)
	    );

	    //  TOOLTIPS
	    UIManager.put("ToolTip.background", GRAPHITE_LIGHT);
	    UIManager.put("ToolTip.foreground", TEXT_LIGHT);
	    UIManager.put("ToolTip.border",
	        BorderFactory.createLineBorder(GRAPHITE_D1, 1)
	    );
	}

/* ----- lase utilitaria – borde redondeado con antialiasing ----- */
class RoundedLineBorder extends LineBorder {

	private static final long serialVersionUID = 1L;
	private final int radius;

	RoundedLineBorder(Color color, int radius, int thickness) {
		super(color, thickness);
		this.radius = radius;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(lineColor);
		g2.setStroke(new BasicStroke(thickness));
		g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
		g2.dispose();
	}
}
}
