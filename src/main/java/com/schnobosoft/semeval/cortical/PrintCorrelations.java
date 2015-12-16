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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
    private static Retina DEFAULT_RETINA_NAME = Retina.EN_ASSOCIATIVE;    // default retina name

    public static void main(String[] args)
            throws IOException
    {
        File inputFile;
        Retina retinaName;
        if (args.length > 0) {
            inputFile = new File(args[0]);
            retinaName = (args.length > 1 && args[1].toLowerCase().startsWith("syn")) ?
                    Retina.EN_SYNONYMOUS :
                    DEFAULT_RETINA_NAME;
        }
        else {
            throw new IllegalArgumentException("Call: " + PrintCorrelations.class.getCanonicalName()
                    + " <input file> [<syn>]");
        }
        LOG.info("Using Retina " + retinaName.name().toLowerCase());
        //        printCorrelations(inputFile);
        saveCorrelations(inputFile);
    }

    private static void printCorrelations(File inputFile, Retina retinaName)
            throws IOException
    {
        assert inputFile.getName().startsWith(INPUT_FILE_PREFIX);

        File gsFile = new File(inputFile.getCanonicalPath()
                .replace(INPUT_FILE_PREFIX, GS_FILE_PREFIX));
        List<Optional> gs = readScoresFile(gsFile);

        for (Measure correlationMeasure : Measure.values()) {
            List<Optional> scores = readScoresFile(
                    getOutputFile(inputFile, correlationMeasure, retinaName));

            double pearson = getPearson(gs, scores);
            System.out.printf("Pearson correlation (%s, %s): %.4f%n",
                    retinaName.name().toLowerCase(), correlationMeasure, pearson);
        }
    }

    private static void saveCorrelations(File inputFile)
            throws IOException
    {
        assert inputFile.getName().startsWith(INPUT_FILE_PREFIX);
        File gsFile = new File(inputFile.getCanonicalPath()
                .replace(INPUT_FILE_PREFIX, GS_FILE_PREFIX));
        List<Optional> gs = readScoresFile(gsFile);

        File targetFile = new File(inputFile.getCanonicalPath() + ".cortical.scores");
        BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile));
        LOG.info("Writing scores to " + targetFile);

        for (Retina retinaName : Retina.values()) {
            for (Measure correlationMeasure : Measure.values()) {
                File outputFile = getOutputFile(inputFile, correlationMeasure, retinaName);
                if (outputFile.exists()) {
                    List<Optional> scores = readScoresFile(outputFile);
                    double pearson = getPearson(gs, scores);
                    writer.write(String.format("Pearson correlation (%s, %s):\t%.4f%n",
                            retinaName.name().toLowerCase(), correlationMeasure, pearson));
                }
                else {
                    LOG.warn("Output file not found: " + outputFile);
                }
            }
        }
        writer.close();
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
