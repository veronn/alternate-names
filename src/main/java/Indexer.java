import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class Indexer {

    public static StandardAnalyzer analyzer;
    public static Directory index;
    public static IndexWriterConfig config;
    public static IndexWriter writer;
    public static IndexReader reader;
    public static IndexSearcher searcher;

    public static void initWriter() throws IOException {
        Runtime.getRuntime().addShutdownHook(new MessageWriter());
        analyzer = new StandardAnalyzer();
        index = FSDirectory.open(Paths.get("./src/main/resources/index.lucene").toFile());
        config = new IndexWriterConfig(Version.LATEST, analyzer);
        writer = new IndexWriter(index, config);
    }

    public static void initReader() throws IOException {
        Runtime.getRuntime().addShutdownHook(new MessageReader());
        analyzer = new StandardAnalyzer();
        index = FSDirectory.open(Paths.get("./src/main/resources/index.lucene").toFile());
        config = new IndexWriterConfig(Version.LATEST, analyzer);
        reader = DirectoryReader.open(index);
        searcher = new IndexSearcher(reader);
    }

    public static void addDocument(String title, String titleFull, String infoboxName, String alternateName, String alternateNameFull) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("title", title, Field.Store.YES));
        doc.add(new StringField("titleLowerCase", title.toLowerCase(), Field.Store.YES));
        doc.add(new StringField("titleFull", titleFull, Field.Store.YES));
        doc.add(new TextField("infoboxName", infoboxName, Field.Store.YES));
        doc.add(new TextField("alternateName", alternateName, Field.Store.YES));
        doc.add(new TextField("alternateNameFull", alternateNameFull, Field.Store.YES));
        doc.add(new TextField("fullText", titleFull.toLowerCase() + " " + alternateNameFull.toLowerCase(), Field.Store.YES));
        //System.out.println(title + " - " + titleFull + " - " + infoboxName + " - " + alternateName + " - " + alternateNameFull); // ** DEBUG **
        writer.addDocument(doc);
    }

    public static List<Document> getDocuments(String field, String query, BooleanClause.Occur occur) throws ParseException, IOException {
        List<Document> documents = new ArrayList<>();

        BooleanQuery q = new BooleanQuery();
        q.add(new TermQuery(new Term(field, query)), occur);

        //Query q = new QueryParser(field, analyzer).parse('"' + query + '"');

        TopDocs docs = searcher.search(q, reader.numDocs());
        ScoreDoc[] hits = docs.scoreDocs;

        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document document = searcher.doc(docId);
            documents.add(document);
        }
        return documents;
    }

    static class MessageReader extends Thread {
        public void run() {
            try {
                // close reader on exit
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //System.out.println("\nClosing reader.");
        }
    }

    static class MessageWriter extends Thread {
        public void run() {
            try {
                // close writer on exit
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //System.out.println("\nClosing writer.");
        }
    }
}