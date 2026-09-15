package com.seuapp.core;
import java.nio.file.Paths;

public class TesteManual {
    public static void main(String[] args) throws Exception {
        YtDlpService service = YtDlpService.withDefaultLocations(Paths.get(System.getProperty("user.dir")));
        var formatos = service.listFormats("https://www.youtube.com/watch?v=xxxxx");
        formatos.forEach(System.out::println);
    }
    
}
