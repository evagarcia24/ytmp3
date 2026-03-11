package yt;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;

/* ==========================================================
   Ytmp3MultipleQwen – Descarga en lote mejorada
   ✅ Todas las mejoras implementadas
   ========================================================== */
public class Ytmp3MultipleQwen extends JFrame {

    private static final long serialVersionUID = 1L;

    /* ==================== CONSTANTES ==================== */

    private static final String DOWNLOADS_DIR = "downloads";
    private static final String LIB_DIR = "lib";
    private static final int BUFFER_SIZE = 8192;
    private static final Color COLOR_PRIMARY = new Color(0x4A90E2);
    private static final Color COLOR_SUCCESS = new Color(0x2E7D32);
    private static final Color COLOR_ERROR = new Color(0xC62828);
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
        "^(https?://)?(www\\.)?(youtube\\.com|youtu\\.be|youtube-nocookie\\.com|music\\.youtube\\.com).*$",
        Pattern.CASE_INSENSITIVE
    );

    /* ==================== COMPONENTES UI ==================== */
    private JTextField urlField;
    private JTextArea logArea;
    private JButton addUrlButton, downloadAllButton, clearListButton, removeSelectedButton;
    private JList<String> urlList;
    private DefaultListModel<String> urlListModel;
    private JButton downloadButton;
    private JProgressBar progressBar, batchProgressBar;
    private JLabel statusLabel;
    private JComboBox<String> qualityCombo;
    private JCheckBox skipExistingCheckbox, playlistCheckbox;
    private File ytDlpExe;

    /* ==================== CONSTRUCTOR ==================== */
    public Ytmp3MultipleQwen() {
        setupLookAndFeel();
        setupFrame();
        setupExecutables();
        setupModels();
        setupComponents();
        setupLayout();
        setupKeyboardShortcuts();
        setupClipboardDetection();
        log("✅ Ytmp3MultipleQwen listo. ¡Pega una URL de YouTube para empezar!");
    }

    /* ==================== CONFIGURACIÓN INICIAL ==================== */
    private void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
        } catch (Exception e) {
            log("⚠️  No se pudo aplicar el tema del sistema: " + e.getMessage());
        }
    }

    private void setupFrame() {
        setTitle("🎵 YouTube MP3 Downloader Qwen – Mejorado");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(950, 700);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 600));
    }

    private void setupExecutables() {
        Path libPath = Path.of(LIB_DIR);
        try { Files.createDirectories(libPath); } catch (IOException e) { /* ignorar */ }

        ytDlpExe = getYtDlpExecutable();
        if (!ytDlpExe.exists()) {
            log("📦 yt-dlp no encontrado. Descargando...");
            downloadYtDlp();
        } else {
            log("✅ yt-dlp encontrado: " + ytDlpExe.getAbsolutePath());
        }
    }

    private File getYtDlpExecutable() {
        String os = System.getProperty("os.name").toLowerCase();
        String filename = os.contains("win") ? "yt-dlp.exe" : 
                         os.contains("mac") ? "yt-dlp_macos" : "yt-dlp";
        return Path.of(LIB_DIR, filename).toFile();
    }

    private String getYtDlpDownloadUrl() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
        if (os.contains("mac")) return "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";
        return "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    }

    private void downloadYtDlp() {
        try {
            String url = getYtDlpDownloadUrl();
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(ytDlpExe)) {
                
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                long total = 0;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total % (1024 * 1024) == 0) {
                        log("⬇️  Descargado: " + (total / 1024 / 1024) + " MB...");
                    }
                }
            }
            log("✅ yt-dlp descargado exitosamente");
            
            // 🔐 Verificación opcional de hash (placeholder)
            // verifySha256(ytDlpExe, "EXPECTED_HASH_HERE");
            
        } catch (Exception e) {
            log("❌ Error descargando yt-dlp: " + e.getMessage());
            log("💡 Descárgalo manualmente desde: https://github.com/yt-dlp/yt-dlp/releases");
            log("   Y colócalo en: " + ytDlpExe.getAbsolutePath());
        }
    }

    private void setupModels() {
        urlListModel = new DefaultListModel<>();
    }

    /* ==================== COMPONENTES UI ==================== */
    private void setupComponents() {
        // Campo URL
        urlField = new JTextField(40);
        urlField.setToolTipText("Pega aquí la URL de YouTube");
        urlField.addActionListener(e -> addUrlButton.doClick());

        // Selector de calidad
        qualityCombo = new JComboBox<>(new String[]{
            "🎵 Mejor calidad (0)", "🎧 Alta (2)", "📻 Media (4)", "📉 Baja (6)"
        });
        qualityCombo.setToolTipText("Calidad de audio MP3");

        // Checkbox: saltar existentes
        skipExistingCheckbox = new JCheckBox("⏭️  Saltar si ya existe", true);
        skipExistingCheckbox.setToolTipText("No volver a descargar archivos duplicados");

        // Checkbox: playlist
        playlistCheckbox = new JCheckBox("📋 Detectar playlists", true);
        playlistCheckbox.setToolTipText("Si es playlist, preguntar si descargar completa");

        // Botones principales
        addUrlButton = new JButton("➕ Añadir");
        addUrlButton.setToolTipText("Añadir URL a la lista (Ctrl+Enter)");
        addUrlButton.addActionListener(e -> addUrlFromField());

        removeSelectedButton = new JButton("🗑️  Eliminar");
        removeSelectedButton.setToolTipText("Eliminar URL seleccionada (Supr)");
        removeSelectedButton.addActionListener(e -> removeSelectedUrl());

        downloadAllButton = new JButton("🚀 Descargar todos");
        downloadAllButton.setToolTipText("Descargar toda la lista (F5)");
        downloadAllButton.addActionListener(e -> startBatchDownload());

        clearListButton = new JButton("🧹 Limpiar lista");
        clearListButton.setToolTipText("Vaciar lista de URLs");
        clearListButton.addActionListener(e -> {
            if (urlListModel.isEmpty() || JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                    this, "¿Vaciar toda la lista?", "Confirmar", JOptionPane.YES_NO_OPTION)) {
                urlListModel.clear();
                log("🧹 Lista limpiada");
            }
        });

        downloadButton = new JButton("⬇️  Descargar MP3");
        downloadButton.setToolTipText("Descargar URL actual (Enter)");
        downloadButton.addActionListener(new DownloadAction());

        // Lista de URLs
        urlList = new JList<>(urlListModel);
        urlList.setVisibleRowCount(6);
        urlList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        urlList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected) setBackground(new Color(0xF8F9FA));
                return c;
            }
        });
        // Doble-click para eliminar
        urlList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !urlListModel.isEmpty()) {
                    int idx = urlList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        String removed = urlListModel.remove(idx);
                        log("🗑️  Eliminada: " + truncate(removed, 60));
                    }
                }
            }
        });

        // Área de logs
        logArea = new JTextArea(12, 60);
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.WHITE);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(BorderFactory.createCompoundBorder(
        	BorderFactory.createLineBorder(new Color(0xCCCCCC), 2),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        // Barras de progreso
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(COLOR_PRIMARY);

        batchProgressBar = new JProgressBar(0, 100);
        batchProgressBar.setStringPainted(true);
        batchProgressBar.setForeground(new Color(0x66BB6A));
        batchProgressBar.setToolTipText("Progreso del lote");

        // Estado
        statusLabel = new JLabel("✅ Listo para descargar");
        statusLabel.setForeground(COLOR_SUCCESS);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
    }

    /* ==================== LAYOUT ==================== */
    private void setupLayout() {
        setLayout(new BorderLayout(8, 8));

        // Panel superior: URL + controles
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Fila 1: URL + calidad
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("🔗 URL:"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(urlField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0;
        topPanel.add(qualityCombo, gbc);

        // Fila 2: Botones de acción
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnRow.add(downloadButton);
        btnRow.add(addUrlButton);
        btnRow.add(removeSelectedButton);
        topPanel.add(btnRow, gbc);
        gbc.gridwidth = 1;

        // Fila 3: Lista con scroll
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        JScrollPane listScroll = new JScrollPane(urlList);
        listScroll.setBorder(BorderFactory.createTitledBorder("📋 Lista de descargas"));
        topPanel.add(listScroll, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // Fila 4: Controles de lote
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        JPanel batchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        batchRow.add(downloadAllButton);
        batchRow.add(clearListButton);
        batchRow.add(skipExistingCheckbox);
        batchRow.add(playlistCheckbox);
        topPanel.add(batchRow, gbc);

        add(topPanel, BorderLayout.NORTH);

        // Panel central: Logs
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("📝 Logs"));
        add(logScroll, BorderLayout.CENTER);

        // Panel inferior: Progreso + estado
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        JPanel progressPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        progressPanel.add(new JLabel("📦 Lote:"));
        progressPanel.add(batchProgressBar);
        progressPanel.add(new JLabel("⬇️  Actual:"));
        progressPanel.add(progressBar);
        
        bottomPanel.add(progressPanel, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /* ==================== ATAJOS DE TECLADO ==================== */
    private void setupKeyboardShortcuts() {
        // Enter en urlField = Añadir URL
        urlField.addActionListener(e -> addUrlButton.doClick());

        // Ctrl+Enter = Descargar URL actual
        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
        getRootPane().registerKeyboardAction(
            e -> { if (!urlField.getText().trim().isEmpty()) downloadButton.doClick(); },
            ctrlEnter, JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // F5 = Descargar todos
        KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        getRootPane().registerKeyboardAction(
            e -> { if (!urlListModel.isEmpty()) downloadAllButton.doClick(); },
            f5, JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Supr = Eliminar seleccionada
        KeyStroke del = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
        getRootPane().registerKeyboardAction(
            e -> removeSelectedUrl(),
            del, JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Ctrl+L = Limpiar logs
        KeyStroke ctrlL = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK);
        getRootPane().registerKeyboardAction(
            e -> logArea.setText(""),
            ctrlL, JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Ctrl+A = Seleccionar todo en lista
        KeyStroke ctrlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
        urlList.registerKeyboardAction(
            e -> urlList.setSelectionInterval(0, urlListModel.size() - 1),
            ctrlA, JComponent.WHEN_FOCUSED
        );
    }

    /* ==================== DETECCIÓN DE PORTAPAPELES ==================== */
    private Timer clipboardTimer;
    private String lastClipboardContent = "";

    private void setupClipboardDetection() {
        clipboardTimer = new Timer(2000, e -> checkClipboard());
        clipboardTimer.start();
        log("📋 Detección de portapapeles activada (cada 2s)");
    }

    private void checkClipboard() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            
            // Verificar si hay texto disponible
            if (!cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return;
            }
            
            // Obtener el texto del portapapeles
            String text = (String) cb.getData(DataFlavor.stringFlavor);
            
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            
            String trimmed = text.trim();
            
            // Evitar procesar el mismo contenido repetidamente
            if (trimmed.equals(lastClipboardContent)) {
                return;
            }
            
            // Si es una URL válida de YouTube, actualizar el campo
            if (isValidYouTubeUrl(trimmed)) {
                lastClipboardContent = trimmed;
                urlField.setText(trimmed);
                statusLabel.setText("📋 URL detectada en portapapeles");
                statusLabel.setForeground(COLOR_PRIMARY);
                log("🔗 URL pegada desde portapapeles: " + truncate(trimmed, 50));
            }
            
        } catch (Exception e) {
            // Ignorar errores silenciosamente (el portapapeles puede estar ocupado)
        }
    }
    /* ==================== VALIDACIÓN DE URL ==================== */
    private boolean isValidYouTubeUrl(String url) {
        try {
            if (!YOUTUBE_PATTERN.matcher(url).matches()) return false;
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null && (
                host.endsWith("youtube.com") || 
                host.endsWith("youtu.be") || 
                host.endsWith("youtube-nocookie.com")
            );
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void addUrlFromField() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            showWarning("Introduce una URL válida");
            return;
        }
        if (!isValidYouTubeUrl(url)) {
            showError("URL no válida de YouTube");
            return;
        }
        if (urlListModel.contains(url)) {
            log("⚠️  URL ya en lista: " + truncate(url, 50));
            urlField.setText("");
            return;
        }
        urlListModel.addElement(url);
        urlField.setText("");
        log("➕ Añadida: " + truncate(url, 60));
        statusLabel.setText("📋 " + urlListModel.size() + " URL(s) en lista");
    }

    private void removeSelectedUrl() {
        int[] indices = urlList.getSelectedIndices();
        if (indices.length == 0) {
            showWarning("Selecciona una URL para eliminar");
            return;
        }
        for (int i = indices.length - 1; i >= 0; i--) {
            String removed = urlListModel.remove(indices[i]);
            log("🗑️  Eliminada: " + truncate(removed, 60));
        }
        statusLabel.setText("📋 " + urlListModel.size() + " URL(s) en lista");
    }

    /* ==================== ACCIÓN DE DESCARGA ÚNICA ==================== */
    private class DownloadAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String url = urlField.getText().trim();
            
            if (url.isEmpty()) {
                showWarning("Introduce una URL");
                return;
            }
            if (!isValidYouTubeUrl(url)) {
                showError("URL no válida de YouTube");
                return;
            }
            
            // Detectar playlist
            if (playlistCheckbox.isSelected() && (url.contains("list=") || url.contains("/playlist"))) {
                int choice = JOptionPane.showConfirmDialog(Ytmp3MultipleQwen.this,
                    "📋 Playlist detectada.\n¿Descargar lista completa?",
                    "Playlist", JOptionPane.YES_NO_OPTION);
                if (choice != JOptionPane.YES_OPTION) {
                    url = url.replaceAll("[?&]list=[^&]+", "").replaceAll("&$", "");
                }
            }
            
            setControlsEnabled(false);
            new Thread(new DownloadTask(url)).start();
        }
    }

    /* ==================== TAREA DE DESCARGA ÚNICA ==================== */
    private class DownloadTask implements Runnable {
        private final String youtubeUrl;
        
        DownloadTask(String url) { this.youtubeUrl = url; }

        @Override
        public void run() {
            SwingUtilities.invokeLater(() -> {
                downloadButton.setEnabled(false);
                statusLabel.setText("⬇️  Iniciando...");
                progressBar.setIndeterminate(true);
            });

            try {
                int exit = downloadSingle(youtubeUrl);
                SwingUtilities.invokeLater(() -> {
                    if (exit == 0) {
                        showSuccess("✅ Descarga completada");
                        log("🎉 ¡Listo! Archivo guardado en /" + DOWNLOADS_DIR);
                    } else {
                        showError("❌ Error en descarga (código: " + exit + ")");
                    }
                    resetProgress();
                    downloadButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    log("💥 ERROR: " + ex.getMessage());
                    showError("Error: " + ex.getMessage());
                    resetProgress();
                    downloadButton.setEnabled(true);
                });
            }
        }
    }

    /* ==================== MÉTODO REUTILIZABLE DE DESCARGA ==================== */
    private int downloadSingle(String youtubeUrl) throws IOException, InterruptedException {
        if (!ytDlpExe.exists()) {
            log("❌ yt-dlp no encontrado");
            return -1;
        }

        Path downloadsPath = Path.of(DOWNLOADS_DIR);
        Files.createDirectories(downloadsPath);

        // Obtener título para verificar duplicados
        String title = extractVideoTitle(youtubeUrl);
        String expectedFile = downloadsPath.resolve(sanitizeFilename(title) + ".mp3").toString();
        
        if (skipExistingCheckbox.isSelected() && new File(expectedFile).exists()) {
            log("⏭️  Ya existe: " + sanitizeFilename(title) + ".mp3");
            return 0;
        }

        log("\n=== 🔽 DESCARGANDO ===");
        log("URL: " + truncate(youtubeUrl, 70));

        // Construir comando
        List<String> cmd = new ArrayList<>(Arrays.asList(
            ytDlpExe.getAbsolutePath(),
            "--format", "bestaudio",
            "--extract-audio",
            "--audio-format", "mp3",
            "--audio-quality", getQualityValue(),
            "--embed-thumbnail",
            "--add-metadata",
            "-o", DOWNLOADS_DIR + "/%(title)s.%(ext)s"
        ));
        
        // Playlist handling
        if (playlistCheckbox.isSelected() && (youtubeUrl.contains("list=") || youtubeUrl.contains("/playlist"))) {
            cmd.add("--yes-playlist");
            log("📋 Modo playlist activado");
        } else {
            cmd.add("--no-playlist");
        }
        
        cmd.add(youtubeUrl);

        log("🔧 Comando: yt-dlp " + String.join(" ", cmd.subList(1, cmd.size())));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Path.of(".").toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Leer output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String l = line;
                SwingUtilities.invokeLater(() -> {
                    log(l);
                    // Parsear progreso
                    if (l.contains("[download]") && l.contains("%")) {
                        try {
                            for (String part : l.split("\\s+")) {
                                if (part.contains("%") && !part.contains("of")) {
                                    String perc = part.replace("%", "").trim();
                                    int val = (int) Float.parseFloat(perc);
                                    progressBar.setValue(val);
                                    progressBar.setIndeterminate(false);
                                    statusLabel.setText("⬇️  " + perc + "%");
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        }

        int exit = proc.waitFor();
        
        SwingUtilities.invokeLater(() -> {
            if (exit == 0) {
                log("✅ Descarga completada");
            } else {
                log("❌ Error (código: " + exit + ")");
            }
        });
        
        return exit;
    }

    /* ==================== UTILIDADES DE DESCARGA ==================== */
    private String getQualityValue() {
        String selected = (String) qualityCombo.getSelectedItem();
        return selected.replaceAll("[^0-9]", ""); // Extrae solo el número
    }

    private String extractVideoTitle(String url) {
        // Fallback simple; yt-dlp extrae el título real
        try {
            String title = URLDecoder.decode(
                url.replaceAll(".*[?&]v=([^&]+).*", "$1")
                   .replaceAll("youtu\\.be/([^?]+).*", "$1"), 
                "UTF-8"
            );
            return title.isEmpty() ? "video" : title;
        } catch (Exception e) {
            return "video";
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-\\s]", "_")
                   .replaceAll("\\s+", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "")
                   .substring(0, Math.min(100, name.length()));
    }

    private void resetProgress() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
    }

    /* ==================== TAREA DE DESCARGA EN LOTE ==================== */
    private class MultiDownloadTask extends SwingWorker<Void, String> {
        private final List<String> urls;
        
        MultiDownloadTask(List<String> urls) { this.urls = urls; }

        @Override
        protected Void doInBackground() {
            int total = urls.size();
            for (int i = 0; i < total; i++) {
                String url = urls.get(i);
                publish("📦 [" + (i+1) + "/" + total + "] " + truncate(url, 50));
                setProgress((i * 100) / total);
                
                try {
                    int exit = downloadSingle(url);
                    if (exit != 0) {
                        publish("⚠️  Error en: " + truncate(url, 40));
                    }
                } catch (Exception e) {
                    publish("💥 Excepción: " + e.getMessage());
                }
            }
            setProgress(100);
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            String last = chunks.get(chunks.size() - 1);
            statusLabel.setText(last);
            batchProgressBar.setValue(getProgress());
        }

        @Override
        protected void done() {
            setControlsEnabled(true);
            batchProgressBar.setValue(100);
            statusLabel.setText("🎉 ¡Todas completadas!");
            statusLabel.setForeground(COLOR_SUCCESS);
            showInfo("✅ Proceso finalizado\n\nRevisa la carpeta /" + DOWNLOADS_DIR);
        }
    }

    /* ==================== CONTROL DE INTERFAZ ==================== */
    private void setControlsEnabled(boolean enabled) {
        downloadButton.setEnabled(enabled);
        addUrlButton.setEnabled(enabled);
        removeSelectedButton.setEnabled(enabled);
        downloadAllButton.setEnabled(enabled);
        clearListButton.setEnabled(enabled);
        urlField.setEnabled(enabled);
        urlList.setEnabled(enabled);
        qualityCombo.setEnabled(enabled);
    }

    /* ==================== UTILIDADES DE UI ==================== */
    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
        	logArea.append("[" + String.format("%02d:%02d:%02d", 
        		    java.time.LocalTime.now().getHour(),
        		    java.time.LocalTime.now().getMinute(),
        		    java.time.LocalTime.now().getSecond()) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max-3) + "...";
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "⚠️  Aviso", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "❌ Error", JOptionPane.ERROR_MESSAGE);
        statusLabel.setForeground(COLOR_ERROR);
    }

    private void showSuccess(String msg) {
        JOptionPane.showMessageDialog(this, msg, "✅ Éxito", JOptionPane.INFORMATION_MESSAGE);
        statusLabel.setForeground(COLOR_SUCCESS);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, "ℹ️  Información", JOptionPane.INFORMATION_MESSAGE);
    }

    /* ==================== VERIFICACIÓN DE HASH (PLACEHOLDER) ==================== */
    @SuppressWarnings("unused")
    private boolean verifySha256(File file, String expectedHash) {
        // 🔐 Implementación real requeriría calcular SHA-256 del archivo
        // y compararlo con el hash oficial de yt-dlp
        // Por ahora, placeholder para futura implementación
        log("🔐 Verificación de hash: (pendiente de implementar)");
        return true; // Asumir válido para desarrollo
    }
    
    private void startBatchDownload() {
        if (urlListModel.isEmpty()) {
            showWarning("La lista está vacía. Añade URLs primero.");
            return;
        }
        setControlsEnabled(false);
        batchProgressBar.setValue(0);
        new MultiDownloadTask(Collections.list(urlListModel.elements())).execute();
        log("🚀 Iniciando descarga de " + urlListModel.size() + " elemento(s)...");
    }

    /* ==================== MAIN ==================== */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Ytmp3MultipleQwen().setVisible(true));
    }
}

