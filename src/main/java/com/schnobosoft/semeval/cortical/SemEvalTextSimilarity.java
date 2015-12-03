package com.schnobosoft.semeval.cortical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.schnobosoft.semeval.cortical.Util.Measure;
import com.schnobosoft.semeval.cortical.Util.Retina;
import io.cortical.rest.model.Metric;
import io.cortical.rest.model.Text;
import io.cortical.services.Compare;
import io.cortical.services.RetinaApis;
import io.cortical.services.api.client.ApiException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static com.schnobosoft.semeval.cortical.Util.INPUT_FILE_PREFIX;
import static com.schnobosoft.semeval.cortical.Util.Retina.EN_ASSOCIATIVE;
import static com.schnobosoft.semeval.cortical.Util.Retina.EN_SYNONYMOUS;
import static com.schnobosoft.semeval.cortical.Util.getOutputFile;
import static io.cortical.services.Compare.CompareModels;

/**
 * Read text pairs from a SemEval input file, compare using the Cortical.io Compare API, and write
 * comparison metrics to separate files. The output values are scaled to the range [0,5] as defined
 * by {@code Util#MIN_OUT} and {@code Util#MAX_OUT}.
 * <p>
 * The first parameter is a SemEval input file, beginning with the prefix specified by {@link Util#INPUT_FILE_PREFIX}.
 * From each similarity metric, one output file is stored in the same directory as the input file, named
 * after the used API and the input file suffix.
 * <p>
 * Call arguments: {@code <input file> <api key> [<syn|ass>]}
 * <p>
 * If the latest argument is given, the Retina is changed to {@link Retina#EN_SYNONYMOUS} or {@link Retina#EN_ASSOCIATIVE}
 * respectively. Otherwise, the default is used ({@link #DEFAULT_RETINA_NAME}).
 *
 * @author Carsten Schnober
 * @see <a href="http://documentation.cortical.io/index.html">Cortical.io API documentation</a>
 * @see <a href="http://ixa2.si.ehu.es/stswiki/index.php/Main_Page">
 * Semantic Textual Similarity Wiki</a>
 */
public class SemEvalTextSimilarity
{
    private static final Log LOG = LogFactory.getLog(SemEvalTextSimilarity.class);

    private static Retina DEFAULT_RETINA_NAME = EN_ASSOCIATIVE;    // default retina name

    public static void main(String[] args)
            throws IOException, ApiException
    {
        /* read command line arguments (input file and API key) */
        String apiKey;
        File inputFile;
        Retina retinaName;
        if (args.length >= 2) {
            inputFile = new File(args[0]);
            assert inputFile.getName().startsWith(INPUT_FILE_PREFIX);
            apiKey = args[1];
            retinaName = (args.length > 2 && args[2].toLowerCase().startsWith("syn")) ?
                    EN_SYNONYMOUS : DEFAULT_RETINA_NAME;
        }
        else {
            throw new IllegalArgumentException(
                    "Call: " + SemEvalTextSimilarity.class.getCanonicalName()
                            + " <input file> <api key> [<syn>]");
        }
        LOG.info("Using Retina " + retinaName.name().toLowerCase() + " at " + Util.RETINA_IP + ".");

        CompareModels[] input = readInput(inputFile);
        RetinaApis api = Util.getApi(apiKey, retinaName, Util.RETINA_IP);
        Metric[] scores = compare(input, api);
        assert input.length == scores.length;

        saveScores(scores, inputFile, retinaName);
    }

    /**
     * Get the similarity metrics for each text pair
     *
     * @param input a list of {@link CompareModels}
     * @param api   the {@link RetinaApis} object to use
     * @return a List of {@link Metric}s, one for each input pair
     */
    private static Metric[] compare(CompareModels[] input, RetinaApis api)
            throws JsonProcessingException, ApiException
    {
        Compare compareApiInstance = api.compareApi();
        return compareApiInstance.compareBulk(input);
    }

    /**
     * Save the values for the metrics using all measures defined in {@link Measure}. All values
     * are scaled to the range [0,5].
     *
     * @param metrics   a list of {@link Metric}s
     * @param inputFile the input file, used for specifying the output files
     * @throws IOException
     */
    private static void saveScores(Metric[] metrics, File inputFile, Retina retinaName)
            throws IOException
    {
        for (Measure measure : Measure.values()) {
            File outputFile = getOutputFile(inputFile, measure, retinaName);
            Writer writer = new BufferedWriter(new FileWriter(outputFile));

            List<Double> scores = Util.scale(Util.getScores(metrics, measure), measure);

            LOG.info("Writing output for '" + inputFile + "'.");
            for (Double score : scores) {
                writer.write(String.valueOf(score) + "\n");
            }
            writer.close();
        }
    }

    /**
     * Read an input file of tab-separated texts. Ignoring empty lines.
     *
     * @param inputFile the input {@link File}
     * @return an array {@link CompareModels}, each holding two {@link Text}s which have been read from the file.
     * @throws IOException
     */
    private static CompareModels[] readInput(File inputFile)
            throws IOException
    {
        LOG.info("Reading input file " + inputFile);
        assert inputFile.getName().startsWith(INPUT_FILE_PREFIX);
        List<CompareModels> lines = Files.lines(inputFile.toPath())
                .filter((s) -> !s.isEmpty())
                .map(line -> line.split("\t"))
                .map(line -> new CompareModels(new Text(line[0]), new Text(line[1])))
                .collect(Collectors.toList());
        return lines.toArray(new CompareModels[lines.size()]);
    }

}
