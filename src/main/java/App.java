import org.apache.commons.compress.compressors.CompressorException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class App {
    // add file(s) to a list and parse them one by one
    public static void main(String[] args) throws IOException, CompressorException {
        //List<String> fileNames = new ArrayList(Downloader.download()); // uncomment to download files (partial dumps) from the internet
        List<String> fileNames = new ArrayList();
        fileNames.add("./src/main/resources/enwiki-latest-pages-articles.xml.bz2");
        for (int i = 0; i < fileNames.size(); i++) {
            Parser.parse(fileNames.get(i));
        }
    }
}
