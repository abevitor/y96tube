package com.seuapp.core;

import com.seuapp.core.model.OutputType;
import com.seuapp.core.model.VideoFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Camada fina sobre os binários externos yt-dlp e ffmpeg.
 * Não deve depender de JavaFX nem de Spring: pode ser usada tanto
 * pelo app-desktop quanto por uma futura versão web.
 */
public class YtDlpService {

    // TODO: campos com os caminhos dos binários (yt-dlp e ffmpeg)

    // TODO: construtor recebendo os caminhos dos binários

    // TODO: factory method estático para localizar os binários automaticamente
    // (ex: procurar dentro de uma pasta "tools/" ao lado do app)
    public static YtDlpService withDefaultLocations(Path appDir) {
        return null;
    }

    /**
     * Consulta o yt-dlp (yt-dlp -J <url>) e devolve a lista de formatos disponíveis.
     */
    public List<VideoFormat> listFormats(String url) throws IOException, InterruptedException {
        // TODO: rodar "yt-dlp -J <url>" via ProcessBuilder, ler o JSON da saída,
        // parsear com Jackson e transformar em List<VideoFormat>
        return null;
    }

    /**
     * Baixa (e converte, se necessário) o vídeo.
     *
     * @param url              link do YouTube
     * @param formatId         id do formato escolhido (ignorado se outputType == MP3)
     * @param outputType       MP4 (vídeo) ou MP3 (extrai só o áudio)
     * @param outputDir        pasta onde salvar o arquivo final
     * @param progressListener callback chamado a cada atualização de progresso (0-100), pode ser null
     * @return código de saída do processo — 0 significa sucesso
     */
    public int download(String url,
                         String formatId,
                         OutputType outputType,
                         Path outputDir,
                         Consumer<Double> progressListener) throws IOException, InterruptedException {
        // TODO: montar o comando yt-dlp (com flags diferentes para MP3 e MP4),
        // rodar via ProcessBuilder, ler a saída linha a linha pra extrair o progresso
        // (ex: regex em cima de "[download]  45.2% of ...") e chamar progressListener
        return -1;
    }
}
