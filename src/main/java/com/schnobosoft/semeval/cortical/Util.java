package com.schnobosoft.semeval.cortical;

import io.cortical.rest.model.Metric;
import io.cortical.services.RetinaApis;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utilities class for I/O and data conversion.
 *
 * @author Carsten Schnober
 */
public class Util
{
    /* the file prefixes for SemEval input and gold standard files */
    public static final String COMMON_PREFIX = "STS.";
    public static final String INPUT_FILE_PREFIX = COMMON_PREFIX + "input.";
    public static final String GS_FILE_PREFIX = COMMON_PREFIX + "gs.";

    private static final Log LOG = LogFactory.getLog(Util.class);

    /**
     * Get the output file object for an input file. The output file begins with the {@link #COMMON_PREFIX},
     * and appends the retina name and the {@code measure} name.
     *
     * @param inputFile  the input file object, beginning with {@link #INPUT_FILE_PREFIX}.
     * @param measure    the {@link Measure}
     * @param retinaName the {@link Retina}
     * @return a {@link File} object for the output file
     * @throws IOException
     */
    public static File getOutputFile(File inputFile, Measure measure, Retina retinaName)
            throws IOException
    {
        if (!inputFile.getName().startsWith(INPUT_FILE_PREFIX)) {
            throw new IllegalArgumentException(inputFile + " does not match expected pattern.");
        }

        return new File(inputFile.getCanonicalPath().replace(INPUT_FILE_PREFIX,
                COMMON_PREFIX + retinaName.name().toLowerCase() + "." + measure.name() + "."));
    }

    /**
     * Read a file that contains one score per line, as a SemEval gold {@code .gs} file. Empty lines
     * are allowed and are read as missing values.
     *
     * @param scoresFile the scores file to read
     * @return a list of optional double values of the same length as the input file. For an empty
     * line, a {@link Optional#EMPTY} object is added to the output list.
     * @throws IOException
     */
    public static List<Optional> readScoresFile(File scoresFile)
            throws IOException
    {
        if (!scoresFile.getName().startsWith(GS_FILE_PREFIX)) {
            throw new IllegalArgumentException(scoresFile + " does not match expected pattern.");
        }
        LOG.info("Reading scores file " + scoresFile);

        return Files.lines(scoresFile.toPath())
                .map(line -> line.isEmpty() ? Optional.empty() : Optional.of(Double.valueOf(line)))
                .collect(Collectors.toList());
    }

    /**
     * Convert a list of Doubles to an array of primitive double types.
     *
     * @param doubles a list of Doubles
     * @return an array of doubles of the length of the input list
     */
    public static double[] listToArray(List<Double> doubles)
    {
        double[] array = new double[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            array[i] = doubles.get(i);
        }
        return array;
    }

    public static RetinaApis getApi(String apiKey, Retina retinaName, String ip)
    {
        return new RetinaApis(retinaName.name().toLowerCase(), ip, apiKey);
    }

    /**
     * All known measure (metric) types returned by the API.
     * <p>
     * This class is named {@link Measure} in order to distinguish it from the {@link Metric} class.
     * </p>
     */
    public enum Measure
    {
        COSINE_SIM, EUCLIDIAN_DIST, JACCARD_DIST, OVERLAP, WEIGHTED
    }

    public enum Retina
    {
        EN_ASSOCIATIVE, EN_SYNONYMOUS
    }
}
