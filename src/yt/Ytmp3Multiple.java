package yt;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.List;

/* ==========================================================
   Ytmp3Multiple – Descarga en lote de varios enlaces YouTube
   ========================================================== */
public class Ytmp3Multiple extends JFrame {

	private static final long serialVersionUID = 1L;

	/* -------------------- COMPONENTES -------------------- */
	private JTextField urlField; // campo para escribir una URL
	private JTextArea logArea; // salida de logs
	private JButton addUrlButton; // “Añadir URL”
	private JButton downloadAllButton; // “Descargar todos”
	private JButton clearListButton; // “Limpiar lista”
	private JList<String> urlList; // lista visual de URLs
	private DefaultListModel<String> urlListModel;
	private JButton downloadButton; // “Descargar MP3” (única)
	private JProgressBar progressBar;
	private JLabel statusLabel;
	private File ytDlpExe; // ejecutable yt‑dlp

	/* -------------------- CONSTRUCTOR -------------------- */
	public Ytmp3Multiple() {

		/* ---- 1️⃣ LOOK‑AND‑FEEL (tema del SO) ---- */
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
		} catch (Exception ignored) {
		}

		setTitle("YouTube MP3 Downloader – Multiples URLs");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(850, 620);
		setLocationRelativeTo(null);

		// 2️⃣ Preparar yt‑dlp (descargar si falta)
		setupExecutables();

		// 3️⃣ Modelo y lista para URLs
		urlListModel = new DefaultListModel<>();
		urlList = new JList<>(urlListModel);
		urlList.setVisibleRowCount(5);
		urlList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// 4️⃣ Botones extra
		addUrlButton = new JButton("Añadir URL");
		downloadAllButton = new JButton("Descargar todos");
		clearListButton = new JButton("Limpiar lista");

		// Acción “Añadir URL”
		addUrlButton.addActionListener(e -> {
			String txt = urlField.getText().trim();
			if (!txt.isEmpty()) {
				urlListModel.addElement(txt);
				urlField.setText("");
			}
		});

		// Acción “Limpiar lista”
		clearListButton.addActionListener(e -> urlListModel.clear());

		// Acción “Descargar todos”
		downloadAllButton.addActionListener(e -> {
			if (urlListModel.isEmpty()) {
				JOptionPane.showMessageDialog(this, "La lista está vacía. Añade al menos una URL.", "Aviso",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			// desactivar controles mientras se procesa el lote
			setControlsEnabled(false);
			new MultiDownloadTask(Collections.list(urlListModel.elements())).execute();
		});

		// 5️⃣ Componentes originales (log, barra, etc.)
		initComponents(); // crea urlField, downloadButton, logArea…
		layoutComponents(); // coloca todo en la ventana
	}

	/*
	 * ------------------------------------------------------- 1️⃣ Preparar
	 * ejecutable yt‑dlp (código idéntico al original)
	 * -------------------------------------------------------
	 */
	private void setupExecutables() {
		File libDir = new File("lib");
		if (!libDir.exists())
			libDir.mkdirs();

		ytDlpExe = new File("lib/yt-dlp.exe");
		if (!ytDlpExe.exists())
			downloadYtDlp();
	}

	private void downloadYtDlp() {
		log("Descargando yt‑dlp…");
		try {
			URL url = URI.create("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe").toURL();

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
			log("Descárgalo manualmente y ponlo en la carpeta lib/");
		}
	}

	/*
	 * ------------------------------------------------------- 2️⃣ Instanciar
	 * componentes UI (textos, botones, logs…)
	 * -------------------------------------------------------
	 */
	private void initComponents() {
		urlField = new JTextField(40);
		urlField.setToolTipText("Pega aquí la URL de YouTube");

		downloadButton = new JButton("Descargar MP3 (única)");
		downloadButton.addActionListener(new DownloadAction());

		logArea = new JTextArea(15, 55);
		logArea.setEditable(false);
		logArea.setBackground(Color.WHITE);
		logArea.setForeground(Color.DARK_GRAY);
		logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
		logArea.setBorder(BorderFactory.createCompoundBorder(new RoundedLineBorder(new Color(0xCCCCCC), 8, 2),
				BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setPreferredSize(new Dimension(600, 24));
		progressBar.setForeground(new Color(0x4A90E2));
		progressBar.setBackground(new Color(0xE0E0E0));

		statusLabel = new JLabel("Listo para descargar");
		statusLabel.setForeground(new Color(0x2E7D32));
	}

	/*
	 * ------------------------------------------------------- 3️⃣ Layout – usamos
	 * GridBagLayout (puedes cambiar a MigLayout)
	 * -------------------------------------------------------
	 */
	private void layoutComponents() {

		JPanel topPanel = new JPanel(new GridBagLayout());
		topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.insets = new Insets(5, 5, 5, 5);

		/* ----- fila 1 : URL + botón “Descargar MP3” ----- */
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

		/* ----- fila 1 (continuación) : botón “Añadir URL” ----- */
		gbc.gridx = 3;
		gbc.gridy = 0;
		topPanel.add(addUrlButton, gbc);

		/* ----- fila 1 (continuación) : botón “Limpiar Logs” ----- */
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

		/* ----- fila 3 : botones “Descargar todos” + “Limpiar lista” ----- */
		JPanel batchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		batchPanel.add(downloadAllButton);
		batchPanel.add(clearListButton);
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 5;
		topPanel.add(batchPanel, gbc);
		gbc.gridwidth = 1;

		/* ----- Área central (logs) ----- */
		JScrollPane scrollPane = new JScrollPane(logArea);
		scrollPane.setBorder(BorderFactory.createTitledBorder("Logs de descarga"));

		/* ----- Panel inferior (progreso + estado) ----- */
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		bottomPanel.add(progressBar, BorderLayout.NORTH);
		bottomPanel.add(statusLabel, BorderLayout.SOUTH);

		/* ----- Añadir al JFrame ----- */
		add(topPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(bottomPanel, BorderLayout.SOUTH);
	}

	/*
	 * ------------------------------------------------------- 4️⃣ Helper: escribir
	 * en el área de log (thread‑safe)
	 * -------------------------------------------------------
	 */
	private void log(String msg) {
		SwingUtilities.invokeLater(() -> {
			logArea.append(msg + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
	}

	/*
	 * ------------------------------------------------------- 5️⃣ Acción “Descargar
	 * MP3” (una sola URL) -------------------------------------------------------
	 */
	private class DownloadAction implements ActionListener {

	    @Override
	    public void actionPerformed(ActionEvent e) {
	        // Obtén la URL y conviértela a minúsculas (para la comparación con contains)
	        String url = urlField.getText().trim().toLowerCase();

	        /* -------------------------------------------------
	         * 1️⃣  ¿La caja está vacía?
	         * ------------------------------------------------- */
	        if (url.isEmpty()) {
	            JOptionPane.showMessageDialog(
	                    Ytmp3Multiple.this,
	                    "Introduce una URL válida",
	                    "Error",
	                    JOptionPane.ERROR_MESSAGE);
	            return;                     // Salimos, no hay nada que procesar
	        }

	        /* -------------------------------------------------
	         * 2️⃣  ¿La URL pertenece a YouTube?
	         *    • youtube (cubre www., m., music., DIFERENTES .COM, .ES, ETC...)
	         *    • youtu.be  (enlace corto)
	         *    • youtube-nocookie.com (embed sin cookies)
	         * ------------------------------------------------- */
	        if (!url.contains("youtube.")          // ej.: youtube.com, www.youtube.es, m.youtube.com …
	                && !url.contains("youtu.be")      // ej.: youtu.be/abc123XYZ45
	                && !url.contains("youtube-nocookie.com")) { // embed sin cookies

	            JOptionPane.showMessageDialog(
	                    Ytmp3Multiple.this,
	                    "Por favor ingresa una URL válida de YouTube",
	                    "Error",
	                    JOptionPane.ERROR_MESSAGE);
	            return;                     // Salimos, la URL no es de YouTube
	        }

	        /* -------------------------------------------------
	         * 3️⃣  Todo está correcto → lanzamos la descarga en
	         *     un hilo independiente para no bloquear la UI.
	         * ------------------------------------------------- */
	        new Thread(new DownloadTask(url)).start();
	    }
	}

	/*
	 * ------------------------------------------------------- 6️⃣ TAREA PARA UNA
	 * URL (copia del código original)
	 * -------------------------------------------------------
	 */
	private class DownloadTask implements Runnable {
		private final String youtubeUrl;

		DownloadTask(String url) {
			this.youtubeUrl = url;
		}

		@Override
		public void run() {
			SwingUtilities.invokeLater(() -> {
				downloadButton.setEnabled(false);
				statusLabel.setText("Iniciando descarga…");
				progressBar.setIndeterminate(true);
			});

			try {
				// Usa el método reutilizable (ver punto 7)
				int exit = downloadSingle(youtubeUrl);
				// El método ya actualiza la barra y el log; aquí sólo mostramos el diálogo
				// final
				SwingUtilities.invokeLater(() -> {
					if (exit == 0) {
						JOptionPane.showMessageDialog(Ytmp3Multiple.this, "Descarga completada", "Éxito",
								JOptionPane.INFORMATION_MESSAGE);
					} else {
						JOptionPane.showMessageDialog(Ytmp3Multiple.this, "Error en la descarga. Revisa los logs.",
								"Error", JOptionPane.ERROR_MESSAGE);
					}
					downloadButton.setEnabled(true);
				});
			} catch (Exception ex) {
				SwingUtilities.invokeLater(() -> {
					statusLabel.setText("Error: " + ex.getMessage());
					log("ERROR: " + ex.getMessage());
					downloadButton.setEnabled(true);
				});
			}
		}
	}

	/*
	 * ------------------------------------------------------- 7️⃣ MÉTODO
	 * REUTILIZABLE – descarga una URL (usado por ambos tipos de tarea: única y
	 * lote) -------------------------------------------------------
	 */
	private int downloadSingle(String youtubeUrl) throws IOException, InterruptedException {
		// Verificación del ejecutable
		if (!ytDlpExe.exists()) {
			SwingUtilities.invokeLater(() -> {
				statusLabel.setText("yt‑dlp no encontrado");
				log("ERROR: yt‑dlp.exe no está disponible");
			});
			return -1;
		}

		// Carpeta downloads (crea si no existe)
		File downloadsDir = new File("downloads");
		if (!downloadsDir.exists()) {
			downloadsDir.mkdirs();
			log("Creado directorio: " + downloadsDir.getAbsolutePath());
		}

		log("\n=== INICIANDO DESCARGA ===");
		log("URL: " + youtubeUrl);
		log("YT‑DLP: " + ytDlpExe.getAbsolutePath());

		String[] command = { ytDlpExe.getAbsolutePath(), "--format", "bestaudio", "--extract-audio", "--audio-format",
				"mp3", "--audio-quality", "0", "--embed-thumbnail", "--add-metadata", "-o",
				"downloads/%(title)s.%(ext)s", youtubeUrl };

		log("Ejecutando: " + String.join(" ", command));

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File("."));
		pb.redirectErrorStream(true);
		Process proc = pb.start();

		// Lectura del output (logs y barra de progreso)
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {

			String line;
			while ((line = reader.readLine()) != null) {
				final String l = line;
				SwingUtilities.invokeLater(() -> {
					log(l);
					if (l.contains("[download]") && l.contains("%")) {
						try {
							for (String part : l.split("\\s+")) {
								if (part.contains("%")) {
									String perc = part.replace("%", "").trim();
									int val = (int) Float.parseFloat(perc);
									progressBar.setValue(val);
									progressBar.setIndeterminate(false);
									statusLabel.setText("Descargando… " + perc + "%");
									break;
								}
							}
						} catch (Exception ignored) {
						}
					}
				});
			}
		}

		int exit = proc.waitFor();

		SwingUtilities.invokeLater(() -> {
			if (exit == 0) {
				log("\n=== DESCARGA COMPLETADA ===");
			} else {
				log("\n=== ERROR EN DESCARGA (código: " + exit + ") ===");
			}
			// Reset barra al terminar cada archivo
			progressBar.setIndeterminate(false);
			progressBar.setValue(0);
		});
		return exit;
	}

	/*
	 * ------------------------------------------------------- 8️⃣ SwingWorker para
	 * **lote** de URLs -------------------------------------------------------
	 */
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
				publish("Descargando (" + idx + "/" + total + "): " + u);
				int exit = downloadSingle(u); // reutiliza el método anterior
				if (exit != 0) {
					publish("⚠️  Error al descargar: " + u);
					// Si prefieres detener el lote en el primer error, descomenta la línea
					// siguiente:
					// break;
				}
				idx++;
			}
			return null;
		}

		@Override
		protected void process(List<String> chunks) {
			// Muestra el último mensaje recibido en la barra de estado
			String last = chunks.get(chunks.size() - 1);
			statusLabel.setText(last);
		}

		@Override
		protected void done() {
			setControlsEnabled(true);
			statusLabel.setText("Todas las descargas completadas");
			JOptionPane.showMessageDialog(Ytmp3Multiple.this,
					"Proceso finalizado. Revisa los logs para más información.", "Fin",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}

	/*
	 * ------------------------------------------------------- 9️⃣ Habilitar /
	 * deshabilitar controles mientras se ejecuta una descarga (evita clicks extra)
	 * -------------------------------------------------------
	 */
	private void setControlsEnabled(boolean enabled) {
		downloadButton.setEnabled(enabled);
		addUrlButton.setEnabled(enabled);
		downloadAllButton.setEnabled(enabled);
		clearListButton.setEnabled(enabled);
	}

	/*
	 * ------------------------------------------------------- 10️⃣ MAIN
	 * -------------------------------------------------------
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new Ytmp3Multiple().setVisible(true));
	}
}

/*
 * ------------------------------------------------------- Clase utilitaria –
 * borde redondeado (igual que antes)
 * -------------------------------------------------------
 */
class RoundedLineBorder extends LineBorder {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final int radius;

	RoundedLineBorder(Color color, int radius, int thickness) {
		super(color, thickness);
		this.radius = radius;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(lineColor);
		g2.setStroke(new BasicStroke(thickness));
		g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
		g2.dispose();
	}
}
