package com.schnobosoft.semeval.cortical;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public static void main(String[] args)
            throws IOException
    {
        File inputFile;
        if (args.length > 0) {
            inputFile = new File(args[0]);
        }
        else {
            throw new IllegalArgumentException(
                    "Please specify input file as command line argument!");
        }
        printCorrelations(inputFile);
    }

    private static void printCorrelations(File inputFile)
            throws IOException
    {
        assert inputFile.getName().startsWith(Util.INPUT_FILE_PREFIX);

        File gsFile = new File(inputFile.getCanonicalPath()
                .replace(Util.INPUT_FILE_PREFIX, Util.GS_FILE_PREFIX));
        List<Optional> gs = Util.readScoresFile(gsFile);

        for (Util.Measure correlationMeasure : Util.Measure.values()) {
            List<Optional> scores = Util.readScoresFile(
                    Util.getOutputFile(inputFile, correlationMeasure));

            double pearson = getPearson(gs, scores);
            System.out.printf("Pearson correlation (%s, %s): %.4f%n",
                    SemEvalTextSimilarity.RETINA_NAME, correlationMeasure, pearson);
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
                .correlation(Util.listToArray(gsList), Util.listToArray(sList));
    }
}
