import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

public class Downloader {

    public Downloader() {
    }

    public static List<String> download() throws IOException {
        URL url = new URL("https://dumps.wikimedia.org/enwiki/latest/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        Pattern pattern = Pattern.compile("(?:<a\\s+href\\s*=\\s*\")(enwiki-latest-pages-articles[^\"]*[^xml])(?:\"\\s*>)", Pattern.CASE_INSENSITIVE);
        Matcher matcher;
        String fileName, fileUrl;
        List<String> fileNames = new ArrayList();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            matcher = pattern.matcher(inputLine);
            boolean matchFound = matcher.find();
            if (matchFound) {
                fileName = matcher.group(1);
                fileNames.add(fileName);
                fileUrl = url.toString() + fileName;
                //System.out.println("Downloading " + fileUrl); // ** DEBUG **
                InputStream inputStream = new URL(fileUrl).openStream();
                Files.copy(inputStream, Paths.get("./src/main/resources/" + fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        in.close();
        return fileNames;
    }
}
