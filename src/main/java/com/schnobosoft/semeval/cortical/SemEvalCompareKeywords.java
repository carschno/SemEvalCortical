package com.schnobosoft.semeval.cortical;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cortical.rest.model.Metric;
import io.cortical.rest.model.Text;
import io.cortical.services.Compare;
import io.cortical.services.RetinaApis;
import io.cortical.services.Texts;
import io.cortical.services.api.client.ApiException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.util.Pair;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static com.schnobosoft.semeval.cortical.Util.*;

/**
 * Perform comparison of texts only after extracting the keywords.
 * <p>
 * Note: initial experiments indicate that this performs significantly worse than immediately
 * calling compare on the input texts as done in {@link SemEvalTextSimilarity}.
 *
 * @author Carsten Schnober
 */
public class SemEvalCompareKeywords
{
    private static final Log LOG = LogFactory.getLog(SemEvalCompareKeywords.class);
    private static final String OUTPUT_FILE_SUFFIX = ".keywords";
    private static Util.Retina DEFAULT_RETINA_NAME = Util.Retina.EN_ASSOCIATIVE;    // default retina name

    public static void main(String[] args)
            throws IOException
    {
        /* read command line arguments (input file and API key) */
        String apiKey;
        File inputFile;
        Util.Retina retinaName;
        if (args.length >= 2) {
            inputFile = new File(args[0]);
            assert inputFile.getName().startsWith(Util.INPUT_FILE_PREFIX);
            apiKey = args[1];
            retinaName = (args.length > 2 && args[2].toLowerCase().startsWith("syn")) ?
                    Util.Retina.EN_SYNONYMOUS :
                    DEFAULT_RETINA_NAME;
        }
        else {
            throw new IllegalArgumentException(
                    "Call: " + SemEvalTextSimilarity.class.getCanonicalName()
                            + " <input file> <api key> [<syn>]");
        }
        LOG.info("Using Retina " + retinaName.name().toLowerCase() + " at " + Util.RETINA_IP + ".");

        RetinaApis api = Util.getApi(apiKey, retinaName, Util.RETINA_IP);
        List<Pair<String, String>> input = readInput(inputFile);
        List<Metric> metrics = compareByKeyword(input, api);
        saveScores(metrics, inputFile, retinaName);

    }

    /**
     * Read an input file of tab-separated texts. Ignoring empty lines.
     *
     * @param inputFile the input {@link File}
     * @return an array {@link Compare.CompareModels}, each holding two {@link Text}s which have been read from the file.
     * @throws IOException
     */
    private static List<Pair<String, String>> readInput(File inputFile)
            throws IOException
    {
        LOG.info("Reading input file " + inputFile);
        assert inputFile.getName().startsWith(INPUT_FILE_PREFIX);
        return Files.lines(inputFile.toPath())
                .filter((s) -> !s.isEmpty())
                .map(line -> line.split("\t"))
                .map(lineArray -> Pair.create(lineArray[0], lineArray[1]))
                .collect(Collectors.toList());
    }

    private static List<Metric> compareByKeyword(List<Pair<String, String>> input, RetinaApis api)
    {
        return input.stream()
                .map(p -> comparePair(p, api))
                .collect(Collectors.toList());
    }

    private static Metric comparePair(Pair<String, String> pair, RetinaApis api)
    {
        Texts textApi = api.textApi();
        Compare compareApi = api.compareApi();

        try {
            Text keywordText1 = keywordText(textApi, pair.getFirst());
            Text keywordText2 = keywordText(textApi, pair.getSecond());
            return (keywordText1.getText().isEmpty() || keywordText2.getText().isEmpty()) ?
                    new Metric() : compareApi.compare(keywordText1, keywordText2);
        }
        catch (JsonProcessingException | ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private static Text keywordText(Texts textApi, String input)
            throws ApiException
    {
        return new Text(String.join(" ", textApi.getKeywords(input)));
    }

    /**
     * Save the values for the metrics using all measures defined in {@link Util.Measure}. All values
     * are scaled to the range [0,5].
     *
     * @param metrics   a list of {@link Metric}s
     * @param inputFile the input file, used for specifying the output files
     * @throws IOException
     */
    private static void saveScores(List<Metric> metrics, File inputFile, Util.Retina retinaName)
            throws IOException
    {
        for (Util.Measure measure : Util.Measure.values()) {
            File outputFile = new File(
                    getOutputFile(inputFile, measure, retinaName).getCanonicalPath()
                            + OUTPUT_FILE_SUFFIX);
            Writer writer = new BufferedWriter(new FileWriter(outputFile));

            List<Double> scores = scale(
                    getScores(metrics.toArray(new Metric[metrics.size()]), measure), measure);

            LOG.info("Writing output for '" + inputFile + "'.");
            for (Double score : scores) {
                writer.write(String.valueOf(score) + "\n");
            }
            writer.close();
        }
    }
}
