import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
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

    // initialize writer to enable writing to the index
    public static void initWriter() throws IOException {
        Runtime.getRuntime().addShutdownHook(new MessageWriter());
        analyzer = new StandardAnalyzer();
        index = FSDirectory.open(Paths.get("./src/main/resources/index.lucene").toFile());
        config = new IndexWriterConfig(Version.LATEST, analyzer);
        writer = new IndexWriter(index, config);
    }

    // initialize reader to enable reading from the index
    public static void initReader() throws IOException {
        Runtime.getRuntime().addShutdownHook(new MessageReader());
        analyzer = new StandardAnalyzer();
        index = FSDirectory.open(Paths.get("./src/main/resources/index.lucene").toFile());
        config = new IndexWriterConfig(Version.LATEST, analyzer);
        reader = DirectoryReader.open(index);
        searcher = new IndexSearcher(reader);
    }

    // add document to index
    public static void addDocument(String title, String infoboxName, String alternateName, String tag, int frequency) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("titleLowerCase", title.toLowerCase(), Field.Store.YES));
        doc.add(new TextField("infoboxName", infoboxName, Field.Store.YES));
        doc.add(new TextField("alternateName", alternateName, Field.Store.YES));
        doc.add(new TextField("fullText", title.toLowerCase() + " " + alternateName.toLowerCase(), Field.Store.YES));
        doc.add(new StringField("tag", tag.toLowerCase(), Field.Store.YES));
        doc.add(new IntField("frequency", frequency, Field.Store.YES));
        writer.addDocument(doc);
    }

    // get document from index by field and query
    public static List<Document> getDocuments(String field, String query, BooleanClause.Occur occur) throws ParseException, IOException {
        List<Document> documents = new ArrayList<>();
        Query q = new QueryParser(field, analyzer).parse(query);

        TopDocs docs = searcher.search(q, reader.numDocs());
        ScoreDoc[] hits = docs.scoreDocs;

        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document document = searcher.doc(docId);
            if ("all_tags".equals(document.get("tag"))) // consider only general tag "all_tags" to avoid getting duplicates
                documents.add(document);
        }
        return documents;
    }

    // get document from the index by tag
    public static void getDocumentsByTag(String tag) throws ParseException, IOException {
        List<Document> documents = new ArrayList<>();
        Query q = new QueryParser("tag", analyzer).parse(tag);

        TopDocs docs = searcher.search(q, reader.numDocs());
        ScoreDoc[] hits = docs.scoreDocs;

        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document document = searcher.doc(docId);
            documents.add(document);
        }
        System.out.println("Alternate names found in parameter " + tag + ": " + documents.size());
    }

    // get articles by number of their alternate names
    public static void getDocumentsByFrequency(int frequency) throws IOException {
        Query q = NumericRangeQuery.newIntRange("frequency", 1, frequency, frequency, true, true);

        TopDocs docs = searcher.search(q, reader.numDocs());
        ScoreDoc[] hits = docs.scoreDocs;

        List<String> results = new ArrayList<>();
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document document = searcher.doc(docId);
            String title = document.get("title");
            if (!results.contains(title) && "all_tags".equals(document.get("tag"))) {
                results.add(title);
                System.out.println(title);
            }
        }
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