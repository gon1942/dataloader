/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.cli;

import org.apache.commons.cli.*;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.containers.StaticLayoutContainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMain {

    private static final Logger LOGGER = Logger.getLogger(CLIMain.class.getCanonicalName());
    private static final Set<String> OFFICE_EXTENSIONS = new HashSet<>(
        Arrays.asList(".doc", ".docx", ".hwp", ".hwpx"));

    private static final String HELP = "[options] <INPUT FILE OR FOLDER>...\n Options:";

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the CLI with the given arguments and returns the exit code.
     *
     * @param args command-line arguments
     * @return 0 on success, non-zero on failure
     */
    static int run(String[] args) {
        Options options = CLIOptions.defineOptions();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(HELP, options);
            return 2;
        }

        // Handle --export-options before requiring input files
        if (commandLine.hasOption(CLIOptions.EXPORT_OPTIONS_LONG_OPTION)) {
            CLIOptions.exportOptionsAsJson(System.out);
            return 0;
        }

        if (commandLine.getArgs().length < 1) {
            formatter.printHelp(HELP, options);
            return 0;
        }

        String[] arguments = commandLine.getArgs();
        Config config;
        boolean quiet;
        try {
            config = CLIOptions.createConfigFromCommandLine(commandLine);
            quiet = commandLine.hasOption(CLIOptions.QUIET_OPTION) || commandLine.hasOption("quiet");
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
            formatter.printHelp(HELP, options);
            return 2;
        }
        configureLogging(quiet);
        boolean hasFailure = false;
        try {
            for (String argument : arguments) {
                if (!processPath(new File(argument), config, true)) {
                    hasFailure = true;
                }
            }
        } finally {
            // Release resources (e.g., hybrid client thread pools)
            OpenDataLoaderPDF.shutdown();
        }
        return hasFailure ? 1 : 0;
    }

    private static void configureLogging(boolean quiet) {
        Logger rootLogger = Logger.getLogger("");
        if (!quiet) {
            rootLogger.setLevel(Level.INFO);
            for (Handler handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            LOGGER.setLevel(Level.INFO);
            return;
        }
        rootLogger.setLevel(Level.OFF);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.OFF);
        }
        LOGGER.setLevel(Level.OFF);
    }

    /**
     * Processes a file or directory, returning true if all files succeeded.
     */
    private static boolean processPath(File file, Config config, boolean topLevelInput) {
        if (!file.exists()) {
            LOGGER.log(Level.WARNING, "File or folder " + file.getAbsolutePath() + " not found.");
            return false;
        }
        if (file.isDirectory()) {
            return processDirectory(file, config);
        } else if (file.isFile()) {
            return processFile(file, config, topLevelInput);
        }
        return true;
    }

    private static boolean processDirectory(File file, Config config) {
        File[] children = file.listFiles();
        if (children == null) {
            LOGGER.log(Level.WARNING, "Unable to read folder " + file.getAbsolutePath());
            return false;
        }
        boolean allSucceeded = true;
        for (File child : children) {
            if (!processPath(child, config, false)) {
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    /**
     * Processes a single PDF file.
     *
     * @return true if processing succeeded, false if an error occurred.
     */
    private static boolean processFile(File file, Config config, boolean topLevelInput) {
        File fileToProcess = file;
        Path tempDir = null;

        if (!isPdfFile(file)) {
            if (isOfficeFile(file)) {
                try {
                    tempDir = Files.createTempDirectory("opendataloader-office-");
                    fileToProcess = convertOfficeToPdf(file, tempDir);
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    LOGGER.log(Level.WARNING, "Failed to convert document " + file.getAbsolutePath()
                        + " to PDF: " + exception.getMessage());
                    return false;
                }
            } else {
                if (topLevelInput) {
                    LOGGER.log(Level.WARNING, "Unsupported input file " + file.getAbsolutePath()
                        + ". Supported inputs: PDF, DOC, DOCX, HWP, HWPX.");
                    return false;
                }
                LOGGER.log(Level.FINE, "Skipping unsupported file " + file.getAbsolutePath());
                return true;
            }
        }

        try {
            OpenDataLoaderPDF.processFile(fileToProcess.getAbsolutePath(), config);
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Exception during processing file " + file.getAbsolutePath() + ": " +
                exception.getMessage(), exception);
            return false;
        } finally {
            StaticLayoutContainers.closeContrastRatioConsumer();
            cleanupTemporaryDirectory(tempDir);
        }
    }

    private static boolean isPdfFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName();
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static boolean isOfficeFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String extension : OFFICE_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static File convertOfficeToPdf(File inputFile, Path tempDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
            "soffice",
            "--headless",
            "--convert-to", "pdf",
            "--outdir", tempDir.toString(),
            inputFile.getAbsolutePath()
        ).redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("LibreOffice exited with code " + exitCode + ": " + output.trim());
        }

        String baseName = inputFile.getName();
        int extensionIndex = baseName.lastIndexOf('.');
        if (extensionIndex >= 0) {
            baseName = baseName.substring(0, extensionIndex);
        }
        File convertedFile = tempDir.resolve(baseName + ".pdf").toFile();
        if (!convertedFile.isFile()) {
            throw new IOException("Converted PDF not found at " + convertedFile.getAbsolutePath()
                + ". Converter output: " + output.trim());
        }
        return convertedFile;
    }

    private static void cleanupTemporaryDirectory(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try {
            Files.walk(tempDir)
                .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        LOGGER.log(Level.FINE, "Unable to delete temporary path " + path + ": " + exception.getMessage());
                    }
                });
        } catch (IOException exception) {
            LOGGER.log(Level.FINE, "Unable to clean up temporary directory " + tempDir + ": " + exception.getMessage());
        }
    }
}
