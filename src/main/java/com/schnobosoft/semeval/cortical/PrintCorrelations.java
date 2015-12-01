package com.schnobosoft.semeval.cortical;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.schnobosoft.semeval.cortical.Util.*;

/**
 * Print correlations between a SemEval gold standards file and scores stored in appropriate files
 * using {@link SemEvalTextSimilarity}.
 * <p>
 * All the filenames are derived from the input file, beginning with the prefix specified in {@link Util#INPUT_FILE_PREFIX}.
 * Therefore, the original input file name has to be specified, exactly as used in {@link SemEvalTextSimilarity#main(String[])}.
 * </p>
 *
 * @author Carsten Schnober
 */
public class PrintCorrelations
{
    private static final Log LOG = LogFactory.getLog(PrintCorrelations.class);
    private static Retina RETINA_NAME = Retina.EN_ASSOCIATIVE;    // default retina name

    public static void main(String[] args)
            throws IOException
    {
        File inputFile;
        if (args.length > 0) {
            inputFile = new File(args[0]);
            if (args.length > 1 && args[1].toLowerCase().startsWith("syn")) {
                RETINA_NAME = Retina.EN_SYNONYMOUS;
            }
        }
        else {
            throw new IllegalArgumentException("Call: " + PrintCorrelations.class.getCanonicalName()
                    + " <input file> [<syn>]");
        }
        LOG.info("Using Retina " + RETINA_NAME.name().toLowerCase());
        printCorrelations(inputFile);
    }

    private static void printCorrelations(File inputFile)
            throws IOException
    {
        assert inputFile.getName().startsWith(INPUT_FILE_PREFIX);

        File gsFile = new File(inputFile.getCanonicalPath()
                .replace(INPUT_FILE_PREFIX, GS_FILE_PREFIX));
        List<Optional> gs = readScoresFile(gsFile);

        for (Measure correlationMeasure : Measure.values()) {
            List<Optional> scores = readScoresFile(
                    getOutputFile(inputFile, correlationMeasure, RETINA_NAME));

            double pearson = getPearson(gs, scores);
            System.out.printf("Pearson correlation (%s, %s): %.4f%n",
                    RETINA_NAME, correlationMeasure, pearson);
        }
    }

    private static double getPearson(List<Optional> gold, List<Optional> scores)
    {
        assert gold.size() == scores.size();

        List<Double> gsList = new ArrayList<>(gold.size());
        List<Double> sList = new ArrayList<>(scores.size());

        for (int i = 0; i < gold.size(); i++) {
            Optional goldOpt = gold.get(i);
            Optional scoreOpt = scores.get(i);
            if (goldOpt.isPresent() && scoreOpt.isPresent()) {
                gsList.add((Double) goldOpt.get());
                sList.add((Double) scoreOpt.get());
            }
            else {
                LOG.warn("No score found in line " + i + ".");
            }
        }
        return new PearsonsCorrelation()
                .correlation(listToArray(gsList), listToArray(sList));
    }
}
