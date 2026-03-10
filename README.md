📥 Ytmp3 / Ytmp3Multiple
Descargador de MP3 desde YouTube escrito en Java Swing.

Ytmp3Multiple – permite añadir enlace unico, o cantidad ilimitada de enlaces y los procesa uno detrás del otro, o una lista de reproduccion.

▶️ Características rápidas
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
  java -cp out yt.Ytmp3           # descarga el audio de un solo video
  java -cp out yt.Ytmp3Multiple   # descarga el audio de varios videos o tambien descarga listas de reproduccion completas

🛠️ Uso
  java -cp out yt.Ytmp3Multiple
    Escribe una URL → pulsa «Añadir URL».
    Repite para todas las URLs que necesites (aparecerán en la lista).
    Cuando la lista esté completa, pulsa «Descargar todos».
    Cada archivo se descarga en orden; cuando termine el lote aparecerá un mensaje de confirmación.


Se incluye archivo Ytmp3Multiple.jar y runYtmp3Multiple.bat para poder ejecutar desde cualquier pc (requisito tener jre y/o jdk instalado)
