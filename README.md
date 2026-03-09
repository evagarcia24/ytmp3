📥 Ytmp3 / Ytmp3Multiple
Descargador de MP3 desde YouTube escrito en Java Swing.

Ytmp3 – descarga un solo enlace.
Ytmp3Multiple – permite añadir cantidad ilimitada de enlaces y los procesa uno detrás del otro.

Ambas versiones usan el mismo binario yt‑dlp (se descarga automáticamente la primera vez que se ejecuta la aplicación).

▶️ Características rápidas
Clase	Qué hace
Ytmp3	Campo de URL → botón «Descargar MP3» → descarga el video y guarda el MP3.
Ytmp3Multiple	Campo de URL + «Añadir URL» → lista de enlaces → botón «Descargar todos» → procesa la lista secuencialmente.
Barra de progreso y registro de logs en tiempo real.	

📦 Instalación
Con Maven (recomendado)
  git clone https://github.com/TU_USUARIO/ytmp3multiple.git
  cd ytmp3multiple
  mvn clean package               # genera target/ytmp3multiple‑shaded.jar
  java -jar target/ytmp3multiple‑shaded.jar

Sin Maven (solo javac)
  javac -d out -sourcepath src src/yt/Ytmp3.java src/yt/Ytmp3Multiple.java
  java -cp out yt.Ytmp3           # descarga un solo video o lista de reproduccion
  java -cp out yt.Ytmp3Multiple   # descarga varios videos o lista de reproduccion

🛠️ Uso
1️⃣ Descargar un único video
  java -cp out yt.Ytmp3
    Introduce la URL en el campo superior.
    Pulsa «Descargar MP3».
    El MP3 se guarda en la carpeta downloads.

2️⃣ Descargar varios videos (ilimitado)
  java -cp out yt.Ytmp3Multiple
    Escribe una URL → pulsa «Añadir URL».
    Repite para todas las URLs que necesites (aparecerán en la lista).
    Cuando la lista esté completa, pulsa «Descargar todos».
    Cada archivo se descarga en orden; cuando termine el lote aparecerá un mensaje de confirmación.

📂 Estructura de clases
yt.Ytmp3	- UI para un solo enlace.
  Lógica de descarga (downloadSingle).

yt.Ytmp3Multiple	- UI con JList y botones para manejo de lista.
  MultiDownloadTask (SwingWorker) que recorre la lista y llama a downloadSingle.
  downloadSingle(String url)	Método reutilizable que ejecuta yt‑dlp y actualiza la barra de progreso.
  RoundedLineBorder	Borde redondeado usado por los componentes de texto.
