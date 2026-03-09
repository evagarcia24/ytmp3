package yt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Ytmp3 extends JFrame {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JTextField urlField;
    private JTextArea logArea;
    private JButton downloadButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private File ytDlpExe;
    
    public Ytmp3() {
        setTitle("YouTube MP3 Downloader v3.0 - Sin ffmpeg");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        
        // Configurar ejecutable
        setupExecutables();
        
        initComponents();
        layoutComponents();
    }
    
    private void setupExecutables() {
        // Crear carpeta lib si no existe
        File libDir = new File("lib");
        if (!libDir.exists()) {
            libDir.mkdirs();
        }
        
        ytDlpExe = new File("lib/yt-dlp.exe");
        
        if (!ytDlpExe.exists()) {
            // Intentar descargar automáticamente
            downloadYtDlp();
        }
    }
    
    private void downloadYtDlp() {
        log("Descargando yt-dlp...");
        try {
            URL url = new URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(ytDlpExe)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            log("yt-dlp descargado exitosamente!");
            
        } catch (Exception e) {
            log("Error descargando yt-dlp: " + e.getMessage());
            log("Por favor descarga manualmente yt-dlp.exe y colócalo en la carpeta lib/");
        }
    }
    
    private void initComponents() {
        urlField = new JTextField(40);
        urlField.setToolTipText("Pega aquí la URL de YouTube");
        
        downloadButton = new JButton("Descargar MP3");
        downloadButton.addActionListener(new DownloadAction());
        
        logArea = new JTextArea(15, 50);
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.WHITE);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        
        statusLabel = new JLabel("Listo para descargar");
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // Panel superior con controles
        JPanel topPanel = new JPanel();
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        topPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("URL:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1;
        topPanel.add(urlField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(downloadButton, gbc);
        
        JButton clearButton = new JButton("Limpiar Logs");
        clearButton.addActionListener(e -> logArea.setText(""));
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(clearButton, gbc);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Panel central con logs
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logs de Descarga"));
        add(scrollPane, BorderLayout.CENTER);
        
        // Panel inferior con progreso y estado
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    private class DownloadAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String url = urlField.getText().trim();
            
            if (url.isEmpty()) {
                JOptionPane.showMessageDialog(Ytmp3.this, 
                    "Por favor ingresa una URL válida", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Ejecutar en hilo separado para no bloquear la UI
            new Thread(new DownloadTask(url)).start();
        }
    }
    
    private class DownloadTask implements Runnable {
        private String youtubeUrl;
        
        public DownloadTask(String url) {
            this.youtubeUrl = url;
        }
        
        @Override
        public void run() {
            SwingUtilities.invokeLater(() -> {
                downloadButton.setEnabled(false);
                statusLabel.setText("Iniciando descarga...");
                progressBar.setIndeterminate(true);
            });
            
            try {
                // Verificar que yt-dlp existe
                if (!ytDlpExe.exists()) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("yt-dlp no encontrado");
                        log("ERROR: yt-dlp.exe no está disponible");
                        downloadButton.setEnabled(true);
                        progressBar.setIndeterminate(false);
                    });
                    return;
                }
                
                // Crear directorio downloads si no existe
                File downloadsDir = new File("downloads");
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                    log("Creado directorio: " + downloadsDir.getAbsolutePath());
                }
                
                log("=== INICIANDO DESCARGA ===");
                log("URL: " + youtubeUrl);
                log("YT-DLP: " + ytDlpExe.getAbsolutePath());
                log("");
                
                // Comando yt-dlp para formato nativo MP3
                String[] command = {
                    ytDlpExe.getAbsolutePath(),
                    "--format", "bestaudio",      // Mejor calidad de audio disponible
                    "--extract-audio",            // Extraer audio
                    "--audio-format", "mp3",      // Formato MP3
                    "--audio-quality", "0",       // Máxima calidad
                    "--embed-thumbnail",          // Incluir miniatura
                    "--add-metadata",             // Añadir metadatos
                    "-o", "downloads/%(title)s.%(ext)s", // Plantilla de salida
                    youtubeUrl
                };
                
                log("Ejecutando: " + String.join(" ", command));
                log("");
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File("."));
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                // Leer output en tiempo real
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    final String logLine = line;
                    SwingUtilities.invokeLater(() -> {
                        log(logLine);
                        
                        // Actualizar progreso
                        if (logLine.contains("[download]") && logLine.contains("%")) {
                            try {
                                String[] parts = logLine.split("\\s+");
                                for (String part : parts) {
                                    if (part.contains("%")) {
                                        String percentStr = part.replace("%", "").trim();
                                        float percent = Float.parseFloat(percentStr);
                                        progressBar.setValue((int)percent);
                                        progressBar.setIndeterminate(false);
                                        statusLabel.setText("Descargando... " + percentStr + "%");
                                        break;
                                    }
                                }
                            } catch (Exception ex) {
                                // Ignorar errores de parseo
                            }
                        }
                    });
                }
                
                int exitCode = process.waitFor();
                
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        statusLabel.setText("¡Descarga completada!");
                        log("\n=== DESCARGA COMPLETADA ===");
                        JOptionPane.showMessageDialog(Ytmp3.this,
                            "Descarga completada exitosamente",
                            "Éxito",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText("Error en la descarga");
                        log("\n=== ERROR EN LA DESCARGA (Código: " + exitCode + ") ===");
                        
                        // Sugerencias para errores comunes
                        if (exitCode == 1) {
                            log("Posibles causas:");
                            log("- URL inválida o video no disponible");
                            log("- Problemas de conexión a internet");
                            log("- Formato no compatible");
                        }
                        
                        JOptionPane.showMessageDialog(Ytmp3.this,
                            "Error durante la descarga. Revisa los logs para más detalles.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                    
                    downloadButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                });
                
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                    log("ERROR: " + ex.getMessage());
                    downloadButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                });
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Ytmp3().setVisible(true);
        });
    }
}
