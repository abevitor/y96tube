package com.seuapp.core;

import java.nio.file.Paths;
import com.seuapp.core.model.OutputType;

public class TesteManual {
    public static void main(String[] args) throws Exception {
        YtDlpService service = YtDlpService.withDefaultLocations(Paths.get(System.getProperty("user.dir")));

        // primeiro lista os formatos, só pra conferir os IDs disponíveis
        var formatos = service.listFormats("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        formatos.forEach(System.out::println);

        // agora testa o download em si (MP3, mais simples pra testar)
        int resultado2 = service.download(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "137",
                OutputType.MP4,
                Paths.get("downloads-teste"),
                percent -> System.out.println("Progresso: " + percent + "%"));
        System.out.println("Terminou com código: " + resultado2);

    }
}
