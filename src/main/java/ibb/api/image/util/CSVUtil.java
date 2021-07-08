package ibb.api.image.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jboss.logging.Logger;

public class CSVUtil {
    
    private static final Logger LOG = Logger.getLogger(CSVUtil.class);
    
    public static Stream<CSVRecord> parseCSVFile(String URIString, String ...headers) throws IOException {
        URL url = getURL(URIString);
        if (url == null) {
            return Stream.empty();
        }
        
        return StreamSupport.stream(getCSVParser(url, headers).spliterator(), false);
    }

    private static CSVParser getCSVParser(URL url, String[] headers) throws IOException {
        return new CSVParser(new BufferedReader(new InputStreamReader(url.openStream())),
                CSVFormat.DEFAULT
                    .withHeader(headers)
                    .withSkipHeaderRecord()
                    .withDelimiter('\t')
                    .withTrim());
    }

    private static URL getURL(String str) {
        try {
            URI uri = new URI(str);
            if (!uri.isAbsolute()) {
                uri = Paths.get(str).toAbsolutePath().toUri();
            }
            return uri.toURL();
        } catch (URISyntaxException | IllegalArgumentException | MalformedURLException e) {
            LOG.errorv("Bad URL {0}.", str);
        }
        return null;
    }
}
