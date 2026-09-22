package com.seuapp.core;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class PlatformUtils {

    private PlatformUtils() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    public static boolean isMacOS() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }

    public static boolean isLinux() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("linux");
    }

    public static String platformFolder() {

        if (isWindows()) {
            return "windows";
        }

        if (isMacOS()) {
            return "macos";
        }

        if (isLinux()) {
            return "linux";
        }

        throw new IllegalStateException(
                "Sistema operacional não suportado: "
                        + System.getProperty("os.name")
        );
    }

    public static String architectureFolder() {

        String arch = System
                .getProperty("os.arch", "")
                .toLowerCase(Locale.ROOT);

        return switch (arch) {

            case "amd64",
                 "x86_64",
                 "x64" ->
                    "x64";

            case "aarch64",
                 "arm64" ->
                    "arm64";

            case "x86",
                 "i386",
                 "i486",
                 "i586",
                 "i686" ->
                    "x86";

            case "arm",
                 "arm32",
                 "armv7l" ->
                    "arm";

            default ->
                    arch;
        };
    }

    public static String ytDlpFileName() {

        if (isWindows()) {
            return "yt-dlp.exe";
        }

        return "yt-dlp";
    }

    public static String ffmpegFileName() {
        return isWindows()
                ? "ffmpeg.exe"
                : "ffmpeg";
    }

    /**
     * Descobre a pasta onde o aplicativo está.
     *
     * Durante o desenvolvimento normalmente será algo como:
     *
     * build/classes/java/main
     *
     * Em um aplicativo empacotado pelo jpackage,
     * normalmente será a pasta app do bundle.
     */
    public static Path applicationDirectory(Class<?> anchorClass) {

        try {

            URI location =
                    anchorClass
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI();

            Path path =
                    Paths.get(location)
                            .toAbsolutePath()
                            .normalize();

            if (Files.isDirectory(path)) {
                return path;
            }

            Path parent = path.getParent();

            if (parent != null) {
                return parent;
            }

        } catch (Exception e) {

            System.out.println(
                    "Não foi possível descobrir o diretório da aplicação: "
                            + e.getMessage()
            );
        }

        return Paths.get(
                System.getProperty("user.dir")
        ).toAbsolutePath();
    }

    /**
     * Procura o executável dentro de:
     *
     * tools/<plataforma>/<arquitetura>/
     * tools/<plataforma>/
     * tools/
     *
     * A busca sobe pelos diretórios pais para funcionar
     * tanto no Gradle quanto no aplicativo empacotado.
     */
    public static Path findTool(
            Path initialDirectory,
            String toolName) {

        List<String> possibleNames =
                possibleToolNames(toolName);

        Path current =
                initialDirectory
                        .toAbsolutePath()
                        .normalize();

        for (int level = 0;
             level < 10 && current != null;
             level++) {

            List<Path> candidates =
                    candidatePaths(
                            current,
                            possibleNames
                    );

            for (Path candidate : candidates) {

                if (Files.isRegularFile(candidate)) {

                    return candidate
                            .toAbsolutePath()
                            .normalize();
                }
            }

            current = current.getParent();
        }

        return null;
    }

    private static List<Path> candidatePaths(
            Path root,
            List<String> names) {

        List<Path> result =
                new ArrayList<>();

        Path toolsRoot =
                root.resolve("tools");

        String platform =
                platformFolder();

        String architecture =
                architectureFolder();

        /*
         * Mais específico primeiro.
         */
        for (String name : names) {

            result.add(
                    toolsRoot
                            .resolve(platform)
                            .resolve(architecture)
                            .resolve(name)
            );
        }

        /*
         * Depois a pasta da plataforma.
         */
        for (String name : names) {

            result.add(
                    toolsRoot
                            .resolve(platform)
                            .resolve(name)
            );
        }

        /*
         * Compatibilidade com sua estrutura antiga:
         *
         * tools/yt-dlp.exe
         * tools/ffmpeg.exe
         */
        for (String name : names) {

            result.add(
                    toolsRoot.resolve(name)
            );
        }

        return result;
    }

    private static List<String> possibleToolNames(
            String toolName) {

        List<String> names =
                new ArrayList<>();

        if ("yt-dlp".equals(toolName)) {

            if (isWindows()) {

                names.add("yt-dlp.exe");

            } else if (isMacOS()) {

                /*
                 * Aceita tanto o nome oficial
                 * quanto o nome padronizado do projeto.
                 */
                names.add("yt-dlp");
                names.add("yt-dlp_macos");

            } else {

                names.add("yt-dlp");
                names.add("yt-dlp_linux");
            }

        } else if ("ffmpeg".equals(toolName)) {

            if (isWindows()) {
                names.add("ffmpeg.exe");
            } else {
                names.add("ffmpeg");
            }
        }

        return names;
    }

    /**
     * Encerra o processo e todos os seus descendentes.
     *
     * Windows:
     * usa taskkill /T /F para garantir que processos
     * como ffmpeg também sejam encerrados.
     *
     * Linux/macOS:
     * usa ProcessHandle, disponível desde Java 9.
     */
    public static void destroyProcessTree(
            Process process) {

        if (process == null) {
            return;
        }

        try {

            List<ProcessHandle> descendants =
                    process.toHandle()
                            .descendants()
                            .toList();

            if (isWindows()) {

                Process killer =
                        new ProcessBuilder(
                                "taskkill",
                                "/PID",
                                String.valueOf(
                                        process.pid()
                                ),
                                "/T",
                                "/F"
                        )
                                .redirectErrorStream(true)
                                .start();

                killer.waitFor(
                        10,
                        TimeUnit.SECONDS
                );

            } else {

                /*
                 * Mata filhos primeiro.
                 */
                for (int i = descendants.size() - 1;
                     i >= 0;
                     i--) {

                    descendants.get(i)
                            .destroyForcibly();
                }

                process.destroyForcibly();
            }

        } catch (IOException e) {

            System.out.println(
                    "Erro encerrando processo: "
                            + e.getMessage()
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } finally {

            /*
             * Garantia adicional.
             */
            try {

                process.toHandle()
                        .descendants()
                        .forEach(
                                ProcessHandle::destroyForcibly
                        );

                process.destroyForcibly();

            } catch (Exception ignored) {
            }
        }
    }
}