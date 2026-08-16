package com.expensesplitter.utility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathValidationService {

    public static class PathValidationResult {
        private final boolean valid;
        private final String message;
        private final Path validatedPath;

        public PathValidationResult(boolean valid, String message, Path validatedPath) {
            this.valid = valid;
            this.message = message;
            this.validatedPath = validatedPath;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public Path getValidatedPath() { return validatedPath; }
    }

    public static PathValidationResult validateAndNormalizePath(String pathString) {
        try {
            if (pathString == null || pathString.trim().isEmpty()) {
                return new PathValidationResult(false, "Path cannot be empty.", null);
            }

            Path path = Paths.get(pathString.trim());
            Path absolutePath = path.toAbsolutePath();

            if (!Files.exists(absolutePath.getParent())) {
                return new PathValidationResult(false, 
                    "Parent directory does not exist: " + absolutePath.getParent(), null);
            }

            if (Files.exists(absolutePath) && !Files.isDirectory(absolutePath)) {
                return new PathValidationResult(false, 
                    "Path exists but is not a directory: " + absolutePath, null);
            }

            if (!Files.exists(absolutePath)) {
                try {
                    Files.createDirectories(absolutePath);
                } catch (Exception e) {
                    return new PathValidationResult(false, 
                        "Cannot create directory: " + e.getMessage(), null);
                }
            }

            if (!Files.isWritable(absolutePath)) {
                return new PathValidationResult(false, 
                    "Directory is not writable: " + absolutePath, null);
            }

            return new PathValidationResult(true, "Path is valid and writable.", absolutePath);

        } catch (Exception e) {
            return new PathValidationResult(false, 
                "Invalid path format: " + e.getMessage(), null);
        }
    }

    public static boolean isValidDirectory(Path path) {
        return path != null && Files.exists(path) && Files.isDirectory(path) && Files.isWritable(path);
    }

    public static String getPathErrorMessage(String path) {
        PathValidationResult result = validateAndNormalizePath(path);
        return result.getMessage();
    }
}
