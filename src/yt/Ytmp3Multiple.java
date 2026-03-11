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
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import javax.swing.Timer;  // ← Importante: Timer de Swing, no java.util
import javax.swing.border.LineBorder;


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
	private JList<String> urlList; // lista visual de URLs
	private DefaultListModel<String> urlListModel;
	private JButton downloadButton; // “Descargar MP3” (única)
	private JProgressBar progressBar;
	private JLabel statusLabel;
	private File ytDlpExe; // ejecutable yt‑dlp
	private Timer clipboardTimer;  // ← AÑADIR ESTA LÍNEA
	private String lastClipboard = "";  // ← PARA EVITAR REPETICIONES
	private JButton removeButtonLocal; // “Eliminar seleccionado”
	private JButton openFolderBtn;
	private List<String> downloadedTitles = new ArrayList<>(); // Guarda la lista de titulos descargados
	

	/* -------------------- CONSTRUCTOR -------------------- */
	public Ytmp3Multiple() {

		setTitle("YouTube MP3 Downloader – Multiples URLs y/o Listas de reproducción");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(900, 670);
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

		// Acción "Añadir URL"
		addUrlButton.addActionListener(e -> {
		    String txt = urlField.getText().trim();
		    
		    // ¿Está vacío?
		    if (txt.isEmpty()) {
		        statusLabel.setText("⚠️ Escribe una URL");
		        return;
		    }
		    
		    // ¿Ya está en la lista?
		    if (urlListModel.contains(txt)) {
		        JOptionPane.showMessageDialog(this, 
		            "Este link ya está en la lista.", 
		            "URL duplicada", 
		            JOptionPane.WARNING_MESSAGE);
		        statusLabel.setText("⚠️ Link ya añadido");
		        return;
		    }
		    
		    //  ¿Es una playlist?
		    if (txt.contains("list=") || txt.contains("/playlist")) {
		        int choice = JOptionPane.showConfirmDialog(this,
		            "📋 Esta URL es una lista de reproducción.\n\n" +
		            "¿Quieres añadirla para descargar TODAS las canciones?",
		            "Playlist detectada",
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE);
		        
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


		// Acción “Descargar todos”
		downloadAllButton.addActionListener(e -> {
			if (urlListModel.isEmpty()) {
				JOptionPane.showMessageDialog(this, "La lista está vacía. Añade al menos una URL.", "Aviso",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			
			  // ✅ Limpiar lista de títulos anteriores
		    downloadedTitles.clear();
		    
			// desactivar controles mientras se procesa el lote
			setControlsEnabled(false);
			new MultiDownloadTask(Collections.list(urlListModel.elements())).execute();
		});

		// 5️⃣ Componentes originales (log, barra, etc.)
		initComponents(); // crea urlField, downloadButton, logArea…
		layoutComponents(); // coloca todo en la ventana
		
		
		// 🔍 Activar detección de portapapeles
		setupClipboardDetection();
	}

	private void setupClipboardDetection() {
		clipboardTimer = new Timer(2000, e -> {
			try {
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
					String text = (String) cb.getData(DataFlavor.stringFlavor);
					if (text != null && !text.trim().isEmpty()) {
						String trimmed = text.trim();
						// Solo si es YouTube y no es lo mismo que ya tenemos
						if ((trimmed.contains("youtube.") || trimmed.contains("youtu.be")) 
								&& !trimmed.equals(lastClipboard)
								&& !urlListModel.contains(trimmed)) {
							lastClipboard = trimmed;
							urlField.setText(trimmed);
							statusLabel.setText("📋 URL detectada en portapapeles");
							log("🔗 URL pegada automáticamente: " + truncateUrl(trimmed));
						}
					}
				}
			} catch (Exception ignored) {
				// Ignorar errores silenciosamente
			}
		});
		clipboardTimer.start();
	}

	// Helper para mostrar URLs largas sin romper el log
	private String truncateUrl(String url) {
		return url.length() > 50 ? url.substring(0, 47) + "..." : url;
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
		logArea.setBackground(Color.BLACK);
		logArea.setForeground(Color.WHITE);
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
		
		urlList = new JList<>(urlListModel);
		urlList.setVisibleRowCount(5);
		urlList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		// ← AÑADIR: Botón Eliminar
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

		// ← AÑADIR: Botón Abrir Descargas
		openFolderBtn = new JButton("📂 Abrir Descargas");
		openFolderBtn.setToolTipText("Abrir carpeta de descargas");
		openFolderBtn.addActionListener(e -> {
			try {
				Desktop.getDesktop().open(new File("downloads"));
			} catch (Exception ex) {
				log("❌ No se pudo abrir la carpeta");
			}
		});
		
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

		/* ----- fila 3 : botones con separación ----- */
		JPanel batchPanel = new JPanel();
		batchPanel.setLayout(new BoxLayout(batchPanel, BoxLayout.X_AXIS));
		batchPanel.setOpaque(false);

		// Botones de la izquierda
		batchPanel.add(downloadAllButton);
		batchPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		batchPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		batchPanel.add(removeButtonLocal);  // ← Ahora SÍ funciona (es variable de clase)

		// ESPACIO FLEXIBLE (empuja a la derecha)
		batchPanel.add(Box.createHorizontalGlue());

		// Botón de la derecha
		batchPanel.add(openFolderBtn);  // ← Ahora SÍ funciona (es variable de clase)

		// Layout constraints
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 5;
		topPanel.add(batchPanel, gbc);
		gbc.gridwidth = 1;

		/* ----- Área central (logs) ----- */
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logs de Descarga"));
        add(scrollPane, BorderLayout.CENTER);

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
	        String url = urlField.getText().trim();

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
				        // ✅ Mostrar título de la canción (no la URL)
				        String lastTitle = downloadedTitles.isEmpty() ? youtubeUrl : downloadedTitles.get(downloadedTitles.size() - 1);
				        String message = "✅ Descarga completada\n\n🎵 " + lastTitle;
				        JOptionPane.showMessageDialog(Ytmp3Multiple.this, 
				            message, "Éxito", JOptionPane.INFORMATION_MESSAGE);
				    } else {
				        JOptionPane.showMessageDialog(Ytmp3Multiple.this, 
				            "Error en la descarga. Revisa los logs.", 
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

		// ✅ Barra animada al iniciar
		SwingUtilities.invokeLater(() -> {
			progressBar.setIndeterminate(true);
			statusLabel.setText("⬇️  Descargando...");
		});

		String[] command = { ytDlpExe.getAbsolutePath(), "--format", "bestaudio", "--extract-audio", "--audio-format",
				"mp3", "--audio-quality", "0", "--embed-thumbnail", "--add-metadata", "-o",
				"downloads/%(title)s.%(ext)s", youtubeUrl };

		log("Ejecutando: " + String.join(" ", command));

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File("."));
		pb.redirectErrorStream(true);
		Process proc = pb.start();

		// ✅ Capturar output completo
		StringBuilder outputBuilder = new StringBuilder();
		
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				outputBuilder.append(line).append("\n");
				final String lineCopy = line;
				SwingUtilities.invokeLater(() -> log(lineCopy));
			}
		}

		int exit = proc.waitFor();

		// ✅ Extraer TODOS los títulos (playlist o individual)
		List<String> titles = extractAllTitlesFromOutput(outputBuilder.toString());
		
		// Si no se extrajo ningún título, usar la URL como fallback
		if (titles.isEmpty()) {
			titles.add(youtubeUrl);
		}

		// ✅ Actualizar barra y guardar títulos al terminar
		SwingUtilities.invokeLater(() -> {
			if (exit == 0) {
				log("\n=== DESCARGA COMPLETADA ===");
				log("🎵 Canciones descargadas: " + titles.size());
				
				// Añadir todos los títulos a la lista
				for (String title : titles) {
					downloadedTitles.add(title);
					log("  ✓ " + title);
				}
				
				progressBar.setIndeterminate(false);
				progressBar.setValue(100);
			} else {
				log("\n=== ERROR EN DESCARGA (código: " + exit + ") ===");
				progressBar.setIndeterminate(false);
				progressBar.setValue(0);
			}
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
			
			// ✅ Construir mensaje con todos los títulos
			StringBuilder sb = new StringBuilder();
			sb.append("✅ Descargas completadas: ").append(downloadedTitles.size()).append("\n\n");
			
			for (int i = 0; i < downloadedTitles.size(); i++) {
				sb.append((i+1)).append(". 🎵 ").append(downloadedTitles.get(i)).append("\n");
			}
			
			JOptionPane.showMessageDialog(Ytmp3Multiple.this, 
				sb.toString(), 
				"🎉 Proceso finalizado", 
				JOptionPane.INFORMATION_MESSAGE);
			
			// Limpiar lista para la próxima vez
			downloadedTitles.clear();
		}
		

		// Helper para mostrar URLs largas sin romper el log
		private String truncateUrl(String url) {
			return url.length() > 50 ? url.substring(0, 47) + "..." : url;
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
	}

	/*
	 * ------------------------------------------------------- 🛑 Limpiar recursos al cerrar
	 * -------------------------------------------------------
	 */
	@Override
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING && clipboardTimer != null) {
			clipboardTimer.stop();
		}
		super.processWindowEvent(e);
	}
	
	/*
	 * ------------------------------------------------------- 🎵 Extraer título del video
	 * -------------------------------------------------------
	 */

		private String extractTitleFromOutput(String output) {
		    try {
		        String[] lines = output.split("\n");
		        for (String line : lines) {
		            if (line.contains("Destination:")) {
		                String filename = line.substring(line.indexOf("Destination:") + 12).trim();
		                
		                // ✅ Obtener solo el nombre del archivo (sin ruta "downloads\")
		                File f = new File(filename);
		                String name = f.getName();  // Solo "Farruko - Pepas.webm"
		                
		                // ✅ Quitar extensión (.webm, .mp3, etc.)
		                int lastDot = name.lastIndexOf(".");
		                if (lastDot > 0) {
		                    name = name.substring(0, lastDot);
		                }
		                
		                return name;  // Solo "Farruko - Pepas"
		            }
		        }
		    } catch (Exception e) {
		    }
		    return null;
		}
		/*
		 * ------------------------------------------------------- 🎵 Extraer TODOS los títulos (playlist)
		 * -------------------------------------------------------
		 */
		private List<String> extractAllTitlesFromOutput(String output) {
			List<String> titles = new ArrayList<>();
			
			try {
				String[] lines = output.split("\n");
				for (String line : lines) {
					// Buscar líneas como: "[download] Destination: Nombre Cancion.mp3"
					if (line.contains("[download] Destination:")) {
						String filename = line.substring(line.indexOf("Destination:") + 12).trim();
						
						// Obtener solo el nombre del archivo (sin ruta)
						File f = new File(filename);
						String name = f.getName();
						
						// Quitar extensión
						int lastDot = name.lastIndexOf(".");
						if (lastDot > 0) {
							name = name.substring(0, lastDot);
						}
						
						// Añadir si no está duplicado
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
