package com.schnobosoft.semeval.cortical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.schnobosoft.semeval.cortical.Util.Measure;
import io.cortical.rest.model.Metric;
import io.cortical.rest.model.Text;
import io.cortical.services.Compare;
import io.cortical.services.RetinaApis;
import io.cortical.services.api.client.ApiException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read text pairs from a SemEval input file, compare using the Cortical.io Compare API, and write
 * comparison metrics to separate files. The output values are scaled to the range [0,5] as defined
 * by {@link #MIN_OUT} and {@link #MAX_OUT}.
 * <p>
 * The first parameter is a SemEval input file, beginning with the prefix specified by {@link Util#INPUT_FILE_PREFIX}.
 * From each similarity metric, one output file is stored in the same directory as the input file, named
 * after the used API and the input file suffix.
 * </p>
 *
 * @author Carsten Schnober
 * @see <a href="http://documentation.cortical.io/index.html">Cortical.io API documentation</a>
 * @see <a href="http://ixa2.si.ehu.es/stswiki/index.php/Main_Page">
 * Semantic Textual Similarity Wiki</a>
 */
public class SemEvalTextSimilarity
{
    /* the Retina and API to use */
    //    public static final String RETINA_NAME = "en_associative";
    public static final String RETINA_NAME = "en_synonymous";
    private static final String RETINA_IP = "api.cortical.io";

    private static final Log LOG = LogFactory.getLog(SemEvalTextSimilarity.class);

    /* the range of output scores for scaling */
    private static final double MAX_OUT = 5;
    private static final double MIN_OUT = 0;

    public static void main(String[] args)
            throws IOException, ApiException
    {
        /* read command line arguments (input file and API key) */
        String apiKey;
        File inputFile;
        if (args.length >= 2) {
            inputFile = new File(args[0]);
            assert inputFile.getName().startsWith(Util.INPUT_FILE_PREFIX);
            apiKey = args[1];
        }
        else {
            throw new IllegalArgumentException(
                    "Please specify input file and API key as 1st and 2nd command line arguments!");
        }

        List<Pair<Text, Text>> input = readInput(inputFile);
        List<Metric> scores = retrieveSimilarities(input, apiKey);
        assert input.size() == scores.size();
        saveScores(scores, inputFile);
    }

    /**
     * Get the similarity metrics for each text pair
     *
     * @param input  a list of {@link Text} pairs
     * @param apiKey the API key
     * @return a List of {@link Metric}s, one for each input pair
     */
    private static List<Metric> retrieveSimilarities(List<Pair<Text, Text>> input, String apiKey)
            throws JsonProcessingException, ApiException
    {
        Compare compareApiInstance = new RetinaApis(RETINA_NAME, RETINA_IP, apiKey).compareApi();

        // TODO: use compareBulk()?
        List<Metric> metrics = new ArrayList<>(input.size());
        for (Pair<Text, Text> pair : input) {
            metrics.add(compareApiInstance.compare(pair.getLeft(), pair.getRight()));
        }
        return metrics;
    }

    /**
     * Concatenate the contents of a list of pairs into a single array.
     *
     * @param input a list of pairs
     * @param c     the class of the pair contents
     * @return an array of class c
     */
    @SuppressWarnings(value = "unused")
    private static <T> T[] pairsToArray(List<Pair<T, T>> input, Class<T> c)
    {
        assert input.size() % 2 == 0;
        T[] inputArray = (T[]) Array.newInstance(c, input.size() / 2);
        //                new Object[input.size() / 2];
        int i = 0;
        for (Pair<T, T> in : input) {
            inputArray[i] = in.getLeft();
            inputArray[i + 1] = in.getRight();
            i += 2;
        }
        return inputArray;
    }

    /**
     * Get the value for a specific measure type from a {@link Metric}.
     * Distance measures ({@link Measure#EUCLIDIAN_DIST} and {@link Measure#JACCARD_DIST}) are
     * negated so that larger values mean more similarity in all cases.
     *
     * @param metric  a {@link Metric} object
     * @param measure a {@link Measure} definition
     * @return the value for the measure
     */
    private static Double getSimilarity(Metric metric, Measure measure)
    {
        switch (measure) {
        case WEIGHTED:
            return metric.getWeightedScoring();
        case COSINE_SIM:
            return metric.getCosineSimilarity();
        case EUCLIDIAN_DIST:
            return -metric.getEuclideanDistance();  // negative for distance -> similarity
        case JACCARD_DIST:
            return -metric.getJaccardDistance();    // negative for distance -> similarity
        case OVERLAP:
            return (double) metric.getOverlappingAll();
        default:
            throw new IllegalArgumentException("Invalid measure: " + measure);
        }
    }

    /**
     * Save the values for the metrics using all measures defined in {@link Measure}. All values
     * are scaled to the range [0,5].
     *
     * @param metrics   a list of {@link Metric}s
     * @param inputFile the input file, used for specifying the output files
     * @throws IOException
     */
    private static void saveScores(List<Metric> metrics, File inputFile)
            throws IOException
    {
        for (Measure measure : Measure.values()) {
            File outputFile = Util.getOutputFile(inputFile, measure);
            Writer writer = new BufferedWriter(new FileWriter(outputFile));

            List<Double> scores = scale(getScores(metrics, measure), measure);

            LOG.info("Writing output for '" + inputFile + "'.");
            for (Double score : scores) {
                writer.write(String.valueOf(score) + "\n");
            }
            writer.close();
        }
    }

    /**
     * Get the scores for a specific measure from a list of {@link Metric}s.
     *
     * @param metrics a list of {@link Metric} objects
     * @param measure the {@link Measure} type
     * @return a list of scores, one for each input {@link Metric}
     */
    private static List<Double> getScores(List<Metric> metrics, Measure measure)
    {
        return metrics.stream()
                .map(metric -> getSimilarity(metric, measure))
                .collect(Collectors.toList());
    }

    /**
     * Read an input file of tab-separated texts. Ignoring empty lines.
     *
     * @param inputFile the input {@link File}
     * @return a list of {@link Pair}s, each holding two {@link Text}s as read from the file.
     * @throws IOException
     */
    private static List<Pair<Text, Text>> readInput(File inputFile)
            throws IOException
    {
        LOG.info("Reading input file " + inputFile);
        assert (inputFile.getName().startsWith(Util.INPUT_FILE_PREFIX));
        return Files.lines(inputFile.toPath())
                .filter((s) -> !s.isEmpty())
                .map(l -> l.split("\t"))
                .map(l -> Pair.of(new Text(l[0]), new Text(l[1])))
                .collect(Collectors.toList());
    }

    /**
     * Scale a collection of double values to a new scale as defined by min and max.
     *
     * @param values  the values to scale
     * @param measure the {@link Measure} to use for scaling (some have predefined min/max boundaries)
     * @return a list of doubles in the range between {@link #MIN_OUT} and {@link #MAX_OUT}
     */
    private static List<Double> scale(Collection<Double> values, Measure measure)
    {
        double maxIn;
        double minIn;

        switch (measure) {
        case COSINE_SIM:
            maxIn = 1.0;
            minIn = 0.0;
            break;
        case JACCARD_DIST:
        case EUCLIDIAN_DIST:
            maxIn = 0.0;
            minIn = -1.0;
            break;
        default:
            maxIn = values.stream().max(Double::compare).get();
            minIn = values.stream().min(Double::compare).get();
        }
        return values.stream()
                .map(value -> scaleValue(MIN_OUT, MAX_OUT, maxIn, minIn, value))
                .collect(Collectors.toList());
    }

    private static double scaleValue(double min, double max, double maxIn, double minIn,
            Double value)
    {
        return (((value - minIn) * (max - min)) / (maxIn - minIn)) + min;
    }

}
