/**
 * This file is part of SemEvalCortical.
 * <p>
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SemEvalCortical.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.schnobosoft.semeval.cortical;

import io.cortical.rest.model.Metric;
import io.cortical.services.RetinaApis;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static final String RETINA_IP = "api.cortical.io";
    public static final double MAX_OUT = 5;
    public static final double MIN_OUT = 0;
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
     * Scale a collection of double values to a new scale as defined by min and max.
     *
     * @param values  the values to scale
     * @param measure the {@link Measure} to use for scaling (some have predefined min/max boundaries)
     * @return a list of doubles in the range between {@link #MIN_OUT} and {@link #MAX_OUT}
     */
    public static List<Double> scale(Collection<Double> values, Measure measure)
    {
        double maxIn;
        double minIn;

        switch (measure) {
        case COSINE_SIM:
            maxIn = 1.0;
            minIn = 0.0;
            break;
        case JACCARD_DIST:
            maxIn = 0.0;
            minIn = -1.0;
            break;
        case EUCLIDIAN_DIST:
            maxIn = 0;
            minIn = values.stream().min(Double::compare).get();
            break;
        default:
            maxIn = values.stream().max(Double::compare).get();
            minIn = values.stream().min(Double::compare).get();
        }
        return values.stream()
                .map(value -> scaleValue(MIN_OUT, MAX_OUT, maxIn, minIn, value))
                .collect(Collectors.toList());
    }

    public static double scaleValue(double min, double max, double maxIn, double minIn,
            Double value)
    {
        return (((value - minIn) * (max - min)) / (maxIn - minIn)) + min;
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
     * Get the scores for a specific measure from a list of {@link Metric}s.
     *
     * @param metrics a list of {@link Metric} objects
     * @param measure the {@link Measure} type
     * @return a list of scores, one for each input {@link Metric}
     */
    public static List<Double> getScores(Metric[] metrics, Measure measure)
    {
        return Stream.of(metrics)
                .map(metric -> getSimilarity(metric, measure))
                .collect(Collectors.toList());
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
